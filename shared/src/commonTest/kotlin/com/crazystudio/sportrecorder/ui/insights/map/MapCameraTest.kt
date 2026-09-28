package com.crazystudio.sportrecorder.ui.insights.map

import com.crazystudio.sportrecorder.domain.model.GeoPoint
import kotlin.math.abs
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MapCameraTest {
    private val taipei = GeoPoint(25.0340, 121.5645)

    @Test fun fromViewport_roundTripsTheCentre() {
        val v = MapViewport.fit(listOf(taipei), 800, 600, 256, 0, 16)!!
        val cam = MapCamera.fromViewport(v)
        assertTrue(abs(cam.centerLat - taipei.lat) < 1e-6 && abs(cam.centerLng - taipei.lng) < 1e-6)
        // A lone place fits at MapViewport.SINGLE_PLACE_ZOOM (15), not the 16 passed as maxZoom;
        // that "fixed neighbourhood view" behaviour of fit() is unchanged by this task (see
        // MapViewport.fit's kdoc) so the expectation here follows it rather than the brief's literal.
        assertEquals(15.0, cam.zoom)
    }

    @Test fun at_usesRoundedLevel_andScaleBetweenLevels() {
        val v = MapViewport.at(MapCamera(16.5, taipei.lat, taipei.lng), 800, 600, 256)
        assertEquals(17, v.zoom) // 16.5 rounds to 17
        assertTrue(abs(v.renderScale - 2.0.pow(-0.5)) < 1e-9)
        assertEquals(800, v.widthPx)
    }

    @Test fun at_anotherLevel_drawsTheSameView() {
        // Any coordinate lands on the same screen pixel whichever level draws it, so a neighbouring
        // level can sit underneath the current one while its tiles load.
        val cam = MapCamera(16.2, taipei.lat, taipei.lng) // level 16
        val lat = 25.0355
        val lng = 121.5620
        val (x, y) = MapViewport.at(cam, 1080, 2400, 384).pixelFor(lat, lng)
        listOf(15, 17).forEach { level ->
            val v = MapViewport.at(cam, 1080, 2400, 384, level = level)
            assertEquals(level, v.zoom)
            val (lx, ly) = v.pixelFor(lat, lng)
            assertTrue(abs(lx - x) < 0.5f && abs(ly - y) < 0.5f, "level $level: ($lx, $ly) vs ($x, $y)")
        }
    }

    @Test fun at_clampsTheRequestedLevel() {
        assertEquals(19, MapViewport.at(MapCamera(18.8, taipei.lat, taipei.lng), 800, 600, 256, level = 22).zoom)
        assertEquals(0, MapViewport.at(MapCamera(0.2, taipei.lat, taipei.lng), 800, 600, 256, level = -1).zoom)
    }

    @Test fun zoomedBy_isLog2_andClamped() {
        val cam = MapCamera(16.0, taipei.lat, taipei.lng)
        assertEquals(17.0, cam.zoomedBy(2f, minZoom = 10.0).zoom)
        assertEquals(19.0, cam.zoomedBy(64f, minZoom = 10.0).zoom)
        assertEquals(10.0, cam.zoomedBy(0.001f, minZoom = 10.0).zoom)
    }

    @Test fun pannedBy_movesTheCentreByScreenPixels() {
        val cam = MapCamera(16.0, taipei.lat, taipei.lng)
        val v = MapViewport.at(cam, 800, 600, 256)
        val moved = cam.pannedBy(dxPx = -100f, dyPx = 0f, viewport = v) // drag left -> centre moves east
        val v2 = MapViewport.at(moved, 800, 600, 256)
        val (x, _) = v2.pixelFor(taipei.lat, taipei.lng)
        assertTrue(abs(x - 300f) < 1f) // the old centre (400) is now 100 px left
    }

    @Test fun zoomedAround_keepsTheFocusPointFixed() {
        val cam = MapCamera(15.0, taipei.lat, taipei.lng)
        val v = MapViewport.at(cam, 800, 600, 256)
        val focus = GeoPoint(25.0300, 121.5600)
        val (fx, fy) = v.pixelFor(focus.lat, focus.lng)
        val zoomed = cam.zoomedAround(2f, fx, fy, v, minZoom = 10.0)
        val v2 = MapViewport.at(zoomed, 800, 600, 256)
        val (fx2, fy2) = v2.pixelFor(focus.lat, focus.lng)
        assertTrue(abs(fx2 - fx) < 1f && abs(fy2 - fy) < 1f)
    }

    // A 336 px tile (2.625x density) at a fractional zoom exercises the case where the viewport's
    // origin and its drawn tile size must agree exactly; any mismatch compounds on every gesture event.
    @Test fun panByZero_doesNotDrift_atFractionalZoom_withNonPowerOfTwoTiles() {
        val tile = 336
        var c = MapCamera(17.3, taipei.lat, taipei.lng)
        repeat(100) { c = c.pannedBy(0f, 0f, MapViewport.at(c, 1080, 2400, tile)) }
        val (x, y) = MapViewport.at(c, 1080, 2400, tile).pixelFor(taipei.lat, taipei.lng)
        assertTrue(abs(x - 540f) < 0.01f && abs(y - 1200f) < 0.01f, "drifted to ($x, $y)")
    }

    @Test fun zoomedAround_acrossALevelBoundary_keepsFocusFixed() {
        val tile = 336
        val cam = MapCamera(15.3, taipei.lat, taipei.lng)
        val v = MapViewport.at(cam, 1080, 2400, tile)
        val focus = WebMercator.unproject(
            (v.originX + 300.0) / v.drawnTilePx,
            (v.originY + 500.0) / v.drawnTilePx,
            v.zoom,
        )
        val zoomed = cam.zoomedAround(1.5f, 300f, 500f, v, minZoom = 10.0) // 15.3 -> ~15.88, level 15 -> 16
        val v2 = MapViewport.at(zoomed, 1080, 2400, tile)
        assertEquals(16, v2.zoom)
        val (fx, fy) = v2.pixelFor(focus.lat, focus.lng)
        assertTrue(abs(fx - 300f) < 0.5f && abs(fy - 500f) < 0.5f, "focus moved to ($fx, $fy)")
    }
}
