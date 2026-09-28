package com.crazystudio.sportrecorder.ui.insights.map

import com.crazystudio.sportrecorder.domain.model.GeoPoint
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MapProjectionTest {
    private val taipei = GeoPoint(lat = 25.033, lng = 121.5654)
    private val kaohsiung = GeoPoint(lat = 22.6273, lng = 120.3014)

    private val tile = 256
    private val width = 800
    private val height = 400
    private val padding = 32

    private fun assertClose(expected: Double, actual: Double, tolerance: Double = 1e-6) =
        assertTrue(abs(expected - actual) <= tolerance, "expected $expected, got $actual")

    // --- projection ---------------------------------------------------------

    @Test fun project_originAtZoomZero_isTheCentreOfTheSingleTile() {
        val p = WebMercator.project(0.0, 0.0, 0)
        assertClose(0.5, p.x)
        assertClose(0.5, p.y)
    }

    @Test fun project_eachZoomDoublesTheCoordinates() {
        val z2 = WebMercator.project(taipei.lat, taipei.lng, 2)
        val z3 = WebMercator.project(taipei.lat, taipei.lng, 3)
        assertClose(z2.x * 2, z3.x)
        assertClose(z2.y * 2, z3.y)
    }

    @Test fun project_northIsUpAndEastIsRight() {
        val here = WebMercator.project(taipei.lat, taipei.lng, 10)
        val north = WebMercator.project(taipei.lat + 1, taipei.lng, 10)
        val east = WebMercator.project(taipei.lat, taipei.lng + 1, 10)
        assertTrue(north.y < here.y)
        assertTrue(east.x > here.x)
    }

    @Test fun project_taipei101_landsOnTheKnownStreetLevelTile() {
        // Reference values from the standard slippy-map formula (z/x/y = 16/54898/28058).
        val p = WebMercator.project(taipei.lat, taipei.lng, 16)
        assertEquals(54898, p.x.toInt())
        assertEquals(28058, p.y.toInt())
    }

    @Test fun project_clampsLatitudeToTheMercatorLimit() {
        val pole = WebMercator.project(89.0, 0.0, 0)
        assertClose(0.0, pole.y, tolerance = 1e-9)
        val southPole = WebMercator.project(-89.0, 0.0, 0)
        assertClose(1.0, southPole.y, tolerance = 1e-9)
    }

    @Test fun unproject_roundTripsProject() {
        val p = WebMercator.project(taipei.lat, taipei.lng, 16)
        val back = WebMercator.unproject(p.x, p.y, 16)
        assertClose(taipei.lat, back.lat, tolerance = 1e-6)
        assertClose(taipei.lng, back.lng, tolerance = 1e-6)
    }

    @Test fun unproject_originAtZoomZero_isTheCentreOfTheSingleTile() {
        val p = WebMercator.unproject(0.5, 0.5, 0)
        assertClose(0.0, p.lat, tolerance = 1e-9)
        assertClose(0.0, p.lng, tolerance = 1e-9)
    }

    // --- renderScale ---------------------------------------------------------

    @Test fun renderScale_defaultsToOne_andLeavesPixelForAndTilesUnchanged() {
        val viewport = MapViewport.fit(listOf(taipei, kaohsiung), width, height, tile, padding)!!
        assertClose(1.0, viewport.renderScale.toDouble())
        assertClose(tile.toDouble(), viewport.drawnTilePx)
        assertTrue(viewport.tiles().all { it.sizePx == tile })
    }

    @Test fun renderScale_scalesDrawnTileSize_pixelForAndTileSizePx() {
        val z = 5
        val scale = 1.5f
        val drawn = tile * scale.toDouble()
        val p = WebMercator.project(taipei.lat, taipei.lng, z)
        val viewport = MapViewport(
            zoom = z,
            originX = p.x * drawn - width / 2.0,
            originY = p.y * drawn - height / 2.0,
            widthPx = width,
            heightPx = height,
            tileSizePx = tile,
            renderScale = scale,
        )
        assertClose(drawn, viewport.drawnTilePx)
        val (x, y) = viewport.pixelFor(taipei.lat, taipei.lng)
        assertClose(width / 2.0, x.toDouble(), tolerance = 0.5)
        assertClose(height / 2.0, y.toDouble(), tolerance = 0.5)
        assertTrue(viewport.tiles().isNotEmpty())
        assertTrue(viewport.tiles().all { it.sizePx == 384 }) // 256 * 1.5
    }

    // --- fitting ------------------------------------------------------------

    @Test fun fit_nothingToShow_isNull() {
        assertNull(MapViewport.fit(emptyList(), width, height, tile, padding))
        assertNull(MapViewport.fit(listOf(taipei), 0, height, tile, padding))
    }

    @Test fun fit_singlePlace_usesTheNeighbourhoodZoomAndCentresIt() {
        val viewport = MapViewport.fit(listOf(taipei), width, height, tile, padding)!!
        assertEquals(MapViewport.SINGLE_PLACE_ZOOM, viewport.zoom)
        val (x, y) = viewport.pixelFor(taipei.lat, taipei.lng)
        assertClose(width / 2.0, x.toDouble(), tolerance = 0.5)
        assertClose(height / 2.0, y.toDouble(), tolerance = 0.5)
    }

    @Test fun fit_identicalPlaces_countAsOne() {
        val viewport = MapViewport.fit(listOf(taipei, taipei), width, height, tile, padding)!!
        assertEquals(MapViewport.SINGLE_PLACE_ZOOM, viewport.zoom)
    }

    @Test fun fit_twoPlaces_picksTheLargestZoomThatKeepsBothInsideThePadding() {
        val viewport = MapViewport.fit(listOf(taipei, kaohsiung), width, height, tile, padding)!!
        // Taipei→Kaohsiung spans ~240 px tall at zoom 7 and ~480 px at zoom 8 (see the formula);
        // only zoom 7 fits a 400 px viewport with 32 px padding.
        assertEquals(7, viewport.zoom)
        listOf(taipei, kaohsiung).forEach { place ->
            val (x, y) = viewport.pixelFor(place.lat, place.lng)
            assertTrue(x >= padding && x <= width - padding, "x=$x outside padding")
            assertTrue(y >= padding && y <= height - padding, "y=$y outside padding")
        }
    }

    @Test fun fit_neverExceedsMaxZoom() {
        val nearby = GeoPoint(taipei.lat + 0.0001, taipei.lng + 0.0001)
        val viewport = MapViewport.fit(listOf(taipei, nearby), width, height, tile, padding, maxZoom = 12)
        assertEquals(12, viewport!!.zoom)
    }

    // --- tiles --------------------------------------------------------------

    @Test fun tiles_coverEveryPixelOfTheViewport() {
        val viewport = MapViewport.fit(listOf(taipei, kaohsiung), width, height, tile, padding)!!
        val tiles = viewport.tiles()
        fun covered(px: Int, py: Int) = tiles.any { t ->
            px >= t.leftPx && px < t.leftPx + tile && py >= t.topPx && py < t.topPx + tile
        }
        listOf(0 to 0, width - 1 to 0, 0 to height - 1, width - 1 to height - 1, width / 2 to height / 2)
            .forEach { (px, py) -> assertTrue(covered(px, py), "($px, $py) has no tile") }
        // Nothing wildly off-screen is requested either.
        tiles.forEach { t ->
            assertTrue(t.leftPx > -tile && t.leftPx < width, "tile at ${t.leftPx} is off-screen")
            assertTrue(t.topPx > -tile && t.topPx < height, "tile at ${t.topPx} is off-screen")
        }
    }

    @Test fun tiles_wrapColumnsAroundTheAntimeridianAndDropRowsPastThePoles() {
        // Zoom 1 is a 2×2 world; put the origin above and left of it so the viewport hangs over both edges.
        val viewport = MapViewport(
            zoom = 1,
            originX = -100.0,
            originY = -100.0,
            widthPx = 700,
            heightPx = 700,
            tileSizePx = tile,
        )
        val tiles = viewport.tiles()
        assertTrue(tiles.all { it.x in 0..1 }, "columns must wrap into 0..1")
        assertTrue(tiles.all { it.y in 0..1 }, "rows past the pole must not exist")
        // Tile column -1 starts 256 px left of the map edge, i.e. 156 px left of the viewport.
        assertTrue(tiles.any { it.leftPx == -156 && it.x == 1 }, "the column left of x=0 is x=1 wrapped")
        assertTrue(tiles.none { it.topPx < 0 }, "no row is drawn above the map")
    }
}
