package com.crazystudio.sportrecorder.ui.insights.map

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateSetOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.crazystudio.sportrecorder.domain.insights.LocationCount
import com.crazystudio.sportrecorder.domain.model.GeoPoint
import com.crazystudio.sportrecorder.shared.resources.Res
import com.crazystudio.sportrecorder.shared.resources.ic_baseline_close_24
import com.crazystudio.sportrecorder.shared.resources.insights_map_close
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** How far past the fitted view the user may zoom out, in zoom levels (each level halves the scale). */
private const val ZOOM_OUT_ALLOWANCE = 2.0

/** A double-tap doubles the scale around the tap — one zoom level. */
private const val DOUBLE_TAP_ZOOM = 2f

private const val CLOSE_BUTTON_ALPHA = 0.8f
private val CLOSE_BUTTON_MARGIN = 8.dp

/**
 * The places card, full screen: the same tiles and neutral markers, now pinch-zoomable (around the
 * fingers), draggable, and double-tap-zoomable (around the tap), from a little wider than the
 * card's fit down to street level ([MapCamera.MAX_ZOOM]). Overlapping markers are re-clustered at
 * every camera, so they split apart as you zoom in. Markers have no tap action — this is a closer
 * look at the reflection, not a navigation tool (no search, no routing, no "open in Maps").
 *
 * The map is one accessibility node described by [contentDescription] (the card's summary); the
 * close button sits outside it so it stays reachable. System back also dismisses.
 */
@Composable
fun FullScreenPlacesMap(
    locations: List<LocationCount>,
    contentDescription: String,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant)) {
            ZoomableMap(
                locations = locations,
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
            )
            IconButton(
                onClick = onDismiss,
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = CLOSE_BUTTON_ALPHA),
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(CLOSE_BUTTON_MARGIN),
            ) {
                Icon(
                    painter = painterResource(Res.drawable.ic_baseline_close_24),
                    contentDescription = stringResource(Res.string.insights_map_close),
                )
            }
            MapAttribution(Modifier.align(Alignment.BottomEnd).navigationBarsPadding())
        }
    }
}

/** Tiles + clustered markers driven by a [MapCamera] that gestures move. */
@Composable
private fun ZoomableMap(
    locations: List<LocationCount>,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    BoxWithConstraints(
        modifier = modifier
            .clipToBounds()
            .clearAndSetSemantics { this.contentDescription = contentDescription },
    ) {
        val widthPx = with(density) { maxWidth.roundToPx() }
        val heightPx = with(density) { maxHeight.roundToPx() }
        val paddingPx = with(density) { FIT_PADDING.roundToPx() }
        val tileSizePx = density.mapTileSizePx()
        val mergeDistancePx = density.clusterMergeDistancePx()
        val fitted = remember(locations, widthPx, heightPx, tileSizePx, paddingPx) {
            MapViewport.fit(
                points = locations.map { GeoPoint(it.lat, it.lng) },
                widthPx = widthPx,
                heightPx = heightPx,
                tileSizePx = tileSizePx,
                paddingPx = paddingPx,
            )
        } ?: return@BoxWithConstraints
        val minZoom = (fitted.zoom - ZOOM_OUT_ALLOWANCE).coerceAtLeast(0.0)
        var camera by remember(fitted) { mutableStateOf(MapCamera.fromViewport(fitted)) }

        // Crossing a zoom level swaps every tile; until the new level's tiles arrive, the last level
        // whose on-screen tiles all loaded stays underneath (only tiles already in hand — no new requests).
        val loaded = remember(fitted) { mutableStateSetOf<TileKey>() }
        var settledLevel by remember(fitted) { mutableStateOf<Int?>(null) }

        // Gesture lambdas live across recompositions, so they must build the viewport from the
        // latest `camera` (read through the state delegate) rather than a value captured earlier.
        fun viewportFor(c: MapCamera): MapViewport = MapViewport.at(c, widthPx, heightPx, tileSizePx)

        fun onTileLoaded(key: TileKey) {
            loaded += key
            val current = viewportFor(camera)
            if (current.tiles().all { it.key in loaded }) {
                settledLevel = current.zoom
                loaded.retainAll { it.zoom == current.zoom }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                // Keyed on `fitted` only: it changes with the size or the places, never mid-gesture.
                .pointerInput(fitted) {
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        // Drag first — the point under the previous centroid moves to the current
                        // one — then scale around the current centroid, which keeps it there.
                        val dragged = camera.pannedBy(pan.x, pan.y, viewportFor(camera))
                        camera = dragged.zoomedAround(zoom, centroid.x, centroid.y, viewportFor(dragged), minZoom)
                    }
                }
                .pointerInput(fitted) {
                    detectTapGestures(
                        onDoubleTap = { tap ->
                            camera = camera.zoomedAround(DOUBLE_TAP_ZOOM, tap.x, tap.y, viewportFor(camera), minZoom)
                        },
                    )
                },
        ) {
            val viewport = viewportFor(camera)
            val clusters = remember(locations, viewport, mergeDistancePx) {
                clusterPlaces(locations, viewport, mergeDistancePx)
            }
            TileLayer(
                tiles = layeredTiles(camera, widthPx, heightPx, tileSizePx, settledLevel, loaded),
                onLoaded = ::onTileLoaded,
            )
            clusters.forEach { cluster -> ClusterMarker(viewport, cluster) }
        }
    }
}
