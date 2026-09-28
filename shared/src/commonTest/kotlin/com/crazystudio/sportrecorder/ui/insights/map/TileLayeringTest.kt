package com.crazystudio.sportrecorder.ui.insights.map

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TileLayeringTest {
    private val width = 1080
    private val height = 2400
    private val tile = 384
    private val camera = MapCamera(16.2, 25.0340, 121.5645) // level 16

    private fun tilesAt(level: Int) = MapViewport.at(camera, width, height, tile, level = level).tiles()

    @Test fun sameLevel_hasNoUnderlay() {
        val everything = (tilesAt(15) + tilesAt(16)).map { it.key }.toSet()
        val layered = layeredTiles(camera, width, height, tile, settledLevel = 16, loaded = everything)
        assertEquals(tilesAt(16), layered)
    }

    @Test fun noSettledLevel_hasNoUnderlay() {
        val everything = tilesAt(15).map { it.key }.toSet()
        assertEquals(tilesAt(16), layeredTiles(camera, width, height, tile, settledLevel = null, loaded = everything))
    }

    @Test fun underlay_comesFirst_andIsTheSettledLevel() {
        val settled = tilesAt(15)
        val loaded = settled.map { it.key }.toSet()
        val layered = layeredTiles(camera, width, height, tile, settledLevel = 15, loaded = loaded)
        assertEquals(settled + tilesAt(16), layered)
        assertTrue(layered.take(settled.size).all { it.zoom == 15 })
        assertTrue(layered.drop(settled.size).all { it.zoom == 16 })
    }

    @Test fun underlay_excludesTilesThatNeverLoaded() {
        val settled = tilesAt(15)
        val loaded = settled.drop(1).map { it.key }.toSet()
        val layered = layeredTiles(camera, width, height, tile, settledLevel = 15, loaded = loaded)
        assertTrue(settled.first() !in layered)
        assertEquals(settled.drop(1) + tilesAt(16), layered)
    }

    @Test fun settledLevelTooFarAway_isIgnored() {
        val loaded = tilesAt(12).map { it.key }.toSet()
        assertEquals(tilesAt(16), layeredTiles(camera, width, height, tile, settledLevel = 12, loaded = loaded))
    }

    @Test fun settledLevelThreeAway_isStillUnderlaid() {
        val settled = tilesAt(19)
        val loaded = settled.map { it.key }.toSet()
        assertEquals(settled + tilesAt(16), layeredTiles(camera, width, height, tile, settledLevel = 19, loaded = loaded))
    }
}
