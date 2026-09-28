package com.crazystudio.sportrecorder.ui.insights.map

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.crazystudio.sportrecorder.shared.resources.Res
import com.crazystudio.sportrecorder.shared.resources.insights_map_attribution
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

// Layers shared by the Insights card (`PlacesMap`) and its full-screen view (`FullScreenPlacesMap`),
// so both draw the same tiles, the same neutral markers and the same attribution.

internal val MAP_CORNER = 12.dp
internal val FIT_PADDING = 32.dp
private val MARKER_RADIUS = 14.dp
private val MARKER_GROWTH_PER_MEAL = 1.dp
private val MARKER_RING = 2.dp
private const val MARKER_GROWTH_CAP = 8
private const val ATTRIBUTION_ALPHA = 0.8f

/** A diameter is two radii: two markers at their largest size touch when their centres are one diameter apart. */
private const val RADII_PER_DIAMETER = 2

/**
 * Tile positions and sizes are rounded independently, so between integer zoom levels two
 * neighbours can leave a 1 px hairline; drawing each tile this much larger covers it.
 */
private const val TILE_SEAM_OVERLAP_PX = 1

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

/** On-screen size of one native tile for this screen density. */
internal fun Density.mapTileSizePx(): Int =
    (OSM_TILE_PX * maxOf(TILE_SCALE_MIN, density / TILE_SCALE_DENSITY_DIVISOR)).roundToInt()

/** Markers closer than this (the largest marker's diameter) would overlap, so they cluster. */
internal fun Density.clusterMergeDistancePx(): Float =
    (MARKER_RADIUS + MARKER_GROWTH_PER_MEAL * MARKER_GROWTH_CAP).toPx() * RADII_PER_DIAMETER

@Composable
internal fun TileLayer(viewport: MapViewport) {
    val tiles = remember(viewport) { viewport.tiles() }
    val density = LocalDensity.current
    tiles.forEach { tile ->
        // Keyed by tile identity so a pan moves an already-loaded image instead of reloading a slot.
        key(tile.zoom, tile.x, tile.y) {
            AsyncImage(
                model = tileUrl(tile),
                contentDescription = null,
                modifier = Modifier
                    .offset { IntOffset(tile.leftPx, tile.topPx) }
                    .size(with(density) { (tile.sizePx + TILE_SEAM_OVERLAP_PX).toDp() }),
            )
        }
    }
}

/**
 * A dot for one place — or several places too close to tell apart at this zoom — that grows a
 * little with the meal count and says the number out loud. Always the same colour.
 */
@Composable
internal fun ClusterMarker(viewport: MapViewport, cluster: MapCluster) {
    val radius = MARKER_RADIUS + MARKER_GROWTH_PER_MEAL * minOf(cluster.count, MARKER_GROWTH_CAP)
    val radiusPx = with(LocalDensity.current) { radius.toPx() }
    val (x, y) = viewport.pixelFor(cluster.lat, cluster.lng)
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
            text = cluster.count.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimary,
        )
    }
}

/** OSM's required credit line; place it in a corner with [modifier]. */
@Composable
internal fun MapAttribution(modifier: Modifier = Modifier) {
    Text(
        text = stringResource(Res.string.insights_map_attribution),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .background(
                MaterialTheme.colorScheme.surface.copy(alpha = ATTRIBUTION_ALPHA),
                RoundedCornerShape(topStart = MAP_CORNER / 2),
            )
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

private fun tileUrl(tile: TilePlacement): String = TILE_URL_TEMPLATE
    .replace("{z}", tile.zoom.toString())
    .replace("{x}", tile.x.toString())
    .replace("{y}", tile.y.toString())
