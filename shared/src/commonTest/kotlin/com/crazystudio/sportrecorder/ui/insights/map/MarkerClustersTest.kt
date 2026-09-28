package com.crazystudio.sportrecorder.ui.insights.map

import com.crazystudio.sportrecorder.domain.insights.LocationCount
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MarkerClustersTest {
    // 256 px tiles, 1000x1000 viewport centred on Taipei 101; fit() picks the zoom.
    private val viewport = MapViewport.fit(
        points = listOf(com.crazystudio.sportrecorder.domain.model.GeoPoint(25.0340, 121.5645)),
        widthPx = 1000, heightPx = 1000, tileSizePx = 256, paddingPx = 0, maxZoom = 16,
    )!!

    private fun place(lat: Double, lng: Double, count: Int) = LocationCount(lat, lng, count)

    @Test fun farApart_stayApart() {
        val clusters = clusterPlaces(listOf(place(25.0340, 121.5645, 2), place(25.0400, 121.5700, 1)), viewport, 40f)
        assertEquals(2, clusters.size)
    }

    @Test fun overlapping_merge_withSummedCount_andWeightedCentre() {
        val a = place(25.0340, 121.5645, 3)
        val b = place(25.03401, 121.56451, 1) // ~1.5 m away: same pixel at this viewport's fitted zoom
        val clusters = clusterPlaces(listOf(a, b), viewport, 40f)
        assertEquals(1, clusters.size)
        assertEquals(4, clusters[0].count)
        assertEquals(2, clusters[0].places)
        val expectedLat = (a.lat * 3 + b.lat * 1) / 4
        assertTrue(abs(clusters[0].lat - expectedLat) < 1e-9)
    }

    @Test fun mergeDistance_isInPixels_notDegrees() {
        val a = place(25.0340, 121.5645, 1)
        val b = place(25.0340, 121.5648, 1) // ~30 m apart; measured ~7 px apart at this viewport's fitted zoom (15)
        assertEquals(1, clusterPlaces(listOf(a, b), viewport, 20f).size)
        assertEquals(2, clusterPlaces(listOf(a, b), viewport, 5f).size)
    }

    @Test fun outlier_staysAlone_andOrderIsCountDescending() {
        val clusters = clusterPlaces(
            listOf(place(25.0340, 121.5645, 1), place(25.03401, 121.56451, 1), place(25.1, 121.7, 5)),
            viewport, 40f,
        )
        assertEquals(listOf(5, 2), clusters.map { it.count })
    }

    @Test fun empty_isEmpty() = assertEquals(emptyList(), clusterPlaces(emptyList(), viewport, 40f))
}
