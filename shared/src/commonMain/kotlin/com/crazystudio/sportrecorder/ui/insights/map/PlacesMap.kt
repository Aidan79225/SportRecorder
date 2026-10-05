package com.crazystudio.sportrecorder.ui.insights.map

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.crazystudio.sportrecorder.domain.insights.LocationCount
import com.crazystudio.sportrecorder.domain.model.GeoPoint
import com.crazystudio.sportrecorder.shared.resources.Res
import com.crazystudio.sportrecorder.shared.resources.insights_map_expand_hint
import com.crazystudio.sportrecorder.shared.resources.insights_map_venues
import com.crazystudio.sportrecorder.shared.resources.insights_map_venues_more
import org.jetbrains.compose.resources.stringResource

private val MAP_HEIGHT = 220.dp

/** Joins the summary and the "tap to enlarge" hint; the same separator the summary itself uses. */
private const val DESCRIPTION_SEPARATOR = " · "

/** Venue names are read out as a plain list; a comma is a pause in both English and Chinese speech. */
private const val NAME_SEPARATOR = ", "

/** A screen reader hears this many venue names on every focus; the rest are summarised as a count. */
internal const val DESCRIBED_VENUE_NAMES = 5

/** The venue names a description reads out (biggest first, at most [limit]) and how many more there are. */
internal fun describedVenueNames(
    locations: List<LocationCount>,
    limit: Int = DESCRIBED_VENUE_NAMES,
): Pair<List<String>, Int> {
    val names = locations.mapNotNull { it.name }
    return names.take(limit) to (names.size - limit).coerceAtLeast(0)
}

/**
 * The map's text equivalent: [summary], plus, when any marker carries a venue's name, the
 * biggest few names (and how many more), so a screen reader hears what a sighted user reads off
 * the map without a long list on every focus. With no named venue this is [summary] unchanged.
 */
@Composable
internal fun placesMapDescription(summary: String, locations: List<LocationCount>): String {
    val (names, more) = describedVenueNames(locations)
    if (names.isEmpty()) return summary
    val joined = names.joinToString(NAME_SEPARATOR)
    val venues = if (more == 0) {
        stringResource(Res.string.insights_map_venues, joined)
    } else {
        stringResource(Res.string.insights_map_venues_more, joined, more)
    }
    return summary + DESCRIPTION_SEPARATOR + venues
}

/**
 * A still map of where the period's meals were: raster tiles fitted around the places, and one
 * marker per place carrying its meal count — places whose markers would overlap share one marker
 * with the summed count. It does not pan or zoom — it is a picture for the Insights page to
 * reflect, not a map to navigate; with [onClick] it opens a closer look (`FullScreenPlacesMap`).
 * Markers are all the same colour: a place is a place, not a good or bad one.
 *
 * The whole map is one accessibility node described by [contentDescription]; tiles and markers
 * are otherwise invisible to screen readers, so the caller keeps a text summary beside the map.
 */
@Composable
fun PlacesMap(
    locations: List<LocationCount>,
    contentDescription: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val density = LocalDensity.current
    val description = if (onClick == null) {
        contentDescription
    } else {
        contentDescription + DESCRIPTION_SEPARATOR + stringResource(Res.string.insights_map_expand_hint)
    }
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(MAP_HEIGHT)
            .clip(RoundedCornerShape(MAP_CORNER))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            // Before clearAndSetSemantics: an inner clickable's action would be cleared with the rest.
            .then(if (onClick == null) Modifier else Modifier.clickable(onClick = onClick))
            .clearAndSetSemantics { this.contentDescription = description },
    ) {
        val widthPx = with(density) { maxWidth.roundToPx() }
        val heightPx = with(density) { maxHeight.roundToPx() }
        val paddingPx = with(density) { FIT_PADDING.roundToPx() }
        val tileSizePx = density.mapTileSizePx()
        val mergeDistancePx = density.clusterMergeDistancePx()
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
            val clusters = remember(locations, viewport, mergeDistancePx) {
                clusterPlaces(locations, viewport, mergeDistancePx)
            }
            TileLayer(remember(viewport) { viewport.tiles() })
            ClusterMarkers(viewport, clusters)
        }
        MapAttribution(Modifier.align(Alignment.BottomEnd))
    }
}
