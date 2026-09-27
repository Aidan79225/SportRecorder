package com.crazystudio.sportrecorder.ui.insights.map

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.crazystudio.sportrecorder.domain.insights.LocationCount
import com.crazystudio.sportrecorder.domain.model.GeoPoint
import com.crazystudio.sportrecorder.shared.resources.Res
import com.crazystudio.sportrecorder.shared.resources.insights_map_attribution
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

private val MAP_HEIGHT = 220.dp
private val MAP_CORNER = 12.dp
private val FIT_PADDING = 32.dp
private val MARKER_RADIUS = 14.dp
private val MARKER_GROWTH_PER_MEAL = 1.dp
private val MARKER_RING = 2.dp
private const val MARKER_GROWTH_CAP = 8
private const val ATTRIBUTION_ALPHA = 0.8f

/** A 256 px tile is tiny on a 3× screen; scale it up gently past 2× instead of blurring it 3×. */
private const val TILE_SCALE_MIN = 1f
private const val TILE_SCALE_DENSITY_DIVISOR = 2f

/**
 * The only place the tile provider is named. OpenStreetMap's tile servers are run by volunteers
 * and their usage policy asks apps to identify themselves (see `SportApplication`'s User-Agent)
 * and to cache (Coil's disk cache does). If usage ever outgrows that goodwill, point this at a
 * keyed provider — nothing else here changes.
 */
private const val TILE_URL_TEMPLATE = "https://tile.openstreetmap.org/{z}/{x}/{y}.png"

/**
 * A still map of where the period's meals were: raster tiles fitted around the places, and one
 * marker per place carrying its meal count. It does not pan or zoom — it is a picture for the
 * Insights page to reflect, not a map to navigate. Markers are all the same colour: a place is a
 * place, not a good or bad one.
 *
 * The whole map is one accessibility node described by [contentDescription]; tiles and markers
 * are otherwise invisible to screen readers, so the caller keeps a text summary beside the map.
 */
@Composable
fun PlacesMap(
    locations: List<LocationCount>,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(MAP_HEIGHT)
            .clip(RoundedCornerShape(MAP_CORNER))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clearAndSetSemantics { this.contentDescription = contentDescription },
    ) {
        val widthPx = with(density) { maxWidth.roundToPx() }
        val heightPx = with(density) { maxHeight.roundToPx() }
        val paddingPx = with(density) { FIT_PADDING.roundToPx() }
        val tileSizePx = (OSM_TILE_PX * maxOf(TILE_SCALE_MIN, density.density / TILE_SCALE_DENSITY_DIVISOR))
            .roundToInt()
        val viewport = remember(locations, widthPx, heightPx, tileSizePx, paddingPx) {
            MapViewport.fit(
                points = locations.map { GeoPoint(it.lat, it.lng) },
                widthPx = widthPx,
                heightPx = heightPx,
                tileSizePx = tileSizePx,
                paddingPx = paddingPx,
            )
        }
        if (viewport != null) {
            TileLayer(viewport)
            locations.forEach { place -> PlaceMarker(viewport, place) }
        }
        Text(
            text = stringResource(Res.string.insights_map_attribution),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .background(
                    MaterialTheme.colorScheme.surface.copy(alpha = ATTRIBUTION_ALPHA),
                    RoundedCornerShape(topStart = MAP_CORNER / 2),
                )
                .padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun TileLayer(viewport: MapViewport) {
    val tiles = remember(viewport) { viewport.tiles() }
    val tileSize = with(LocalDensity.current) { viewport.tileSizePx.toDp() }
    tiles.forEach { tile ->
        AsyncImage(
            model = tileUrl(tile),
            contentDescription = null,
            modifier = Modifier
                .offset { IntOffset(tile.leftPx, tile.topPx) }
                .size(tileSize),
        )
    }
}

/** A dot that grows a little with the meal count and says the number out loud. */
@Composable
private fun PlaceMarker(viewport: MapViewport, place: LocationCount) {
    val radius = MARKER_RADIUS + MARKER_GROWTH_PER_MEAL * minOf(place.count, MARKER_GROWTH_CAP)
    val radiusPx = with(LocalDensity.current) { radius.toPx() }
    val (x, y) = viewport.pixelFor(place.lat, place.lng)
    Box(
        modifier = Modifier
            .offset { IntOffset((x - radiusPx).roundToInt(), (y - radiusPx).roundToInt()) }
            .size(radius * 2)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary)
            .border(MARKER_RING, MaterialTheme.colorScheme.surface, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = place.count.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimary,
        )
    }
}

private fun tileUrl(tile: TilePlacement): String = TILE_URL_TEMPLATE
    .replace("{z}", tile.zoom.toString())
    .replace("{x}", tile.x.toString())
    .replace("{y}", tile.y.toString())
