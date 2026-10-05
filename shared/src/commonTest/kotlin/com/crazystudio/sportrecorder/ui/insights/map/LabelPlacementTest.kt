package com.crazystudio.sportrecorder.ui.insights.map

import com.crazystudio.sportrecorder.domain.insights.LocationCount
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LabelPlacementTest {
    private val canvas = 1000f
    private val r = 20f
    private fun circle(x: Float, y: Float = 100f) = MarkerCircle(x, y, r)

    private fun place(marker: MarkerCircle, others: List<MarkerCircle> = emptyList(), width: Float = 200f) =
        placeLabel(marker, others, naturalWidth = width, height = 20f, canvasWidth = canvas, gap = 2f, minWidth = 30f)

    @Test fun roomOnTheRight_putsTheLabelRightOfTheMarker() {
        assertEquals(LabelSlot(left = 400f + r + 2f, width = 200f), place(circle(400f)))
    }

    @Test fun nearTheRightEdge_flipsToTheLeft() {
        val slot = place(circle(950f))!!
        assertEquals(200f, slot.width)
        assertEquals(950f - r - 2f - 200f, slot.left, 0.001f)
        assertTrue(slot.left + slot.width <= 950f - r)
    }

    @Test fun anotherMarkerOnTheRight_flipsLeftInsteadOfCoveringIt() {
        val slot = place(circle(400f), listOf(circle(460f)))!!
        assertTrue(slot.left + slot.width <= 400f - r)
    }

    @Test fun markersAboveOrBelowTheLabelBandAreNoObstacle() {
        assertEquals(402f + 400f - 400f + r, place(circle(400f), listOf(circle(460f, y = 300f)))!!.left)
    }

    @Test fun noRoomForTheWholeName_narrowsOnTheRoomierSide() {
        // Blockers 120 px either side leave ~76 px each; the name is narrowed rather than covering a circle.
        val slot = place(circle(500f), listOf(circle(620f), circle(380f)))!!
        assertTrue(slot.width < 200f)
        assertTrue(slot.left + slot.width <= 620f - r)
        assertTrue(slot.left >= 380f + r)
    }

    @Test fun aSliverOfRoom_leavesTheLabelOff() {
        assertNull(place(circle(500f), listOf(circle(555f), circle(445f))))
    }

    @Test fun theLeftEdgeIsRespected() {
        val slot = placeLabel(circle(30f), listOf(circle(100f)), 200f, 20f, 100f, 2f, 30f)
        assertNull(slot)
    }

    @Test fun describedVenueNames_capsAtFiveAndCountsTheRest() {
        val locations = (1..8).map { LocationCount(25.0, 121.0, 10 - it, "v$it") } + LocationCount(24.0, 120.0, 1)
        val (names, more) = describedVenueNames(locations)
        assertEquals(listOf("v1", "v2", "v3", "v4", "v5"), names)
        assertEquals(3, more)
    }

    @Test fun describedVenueNames_fewNamesAreAllListed() {
        assertEquals(listOf("a") to 0, describedVenueNames(listOf(LocationCount(1.0, 1.0, 1, "a"), LocationCount(2.0, 2.0, 1))))
    }
}
