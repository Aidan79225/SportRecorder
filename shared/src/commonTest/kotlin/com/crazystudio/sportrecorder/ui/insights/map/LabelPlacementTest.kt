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
        placeLabel(
            marker, others.map { it.asBlock() },
            naturalWidth = width, height = 20f, canvasWidth = canvas, gap = 2f, minWidth = 30f,
        )

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
        // Marker at x=400, radius 20, gap 2: the label starts at 422 and keeps its whole width.
        val slot = place(circle(400f), listOf(circle(460f, y = 300f)))!!
        assertEquals(LabelSlot(left = 422f, width = 200f), slot)
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
        // Marker at x=300 with a blocker at x=380: the right has 38 px, the left is open down to x=0
        // but only 278 px wide, so a 400 px name goes left, narrowed to what is there, never below 0.
        val slot = place(circle(300f), listOf(circle(380f)), width = 400f)!!
        assertTrue(slot.left >= 0f)
        assertTrue(slot.left + slot.width <= 300f - r)
        assertEquals(278f, slot.width, 0.001f)
    }

    @Test fun aShortNameThatFitsWholeIsDrawnEvenUnderTheMinimumWidth() {
        // "全家" is ~30 px wide, below the 30 px floor used here with room to spare: it still gets a slot.
        val slot = placeLabel(circle(400f), emptyList(), 24f, 20f, canvas, 2f, 30f)
        assertEquals(LabelSlot(left = 422f, width = 24f), slot)
    }

    @Test fun aLabelAlreadyPlaced_isKeptClearOf() {
        // First label occupies 122..422. On an 800 px canvas the second marker (x=600) has 178 px to its
        // right and, with the first label in the way, only 154 px to its left: it narrows on the right.
        // Without the placed-label obstacle it would flip left, onto the first label.
        val first = place(circle(100f), width = 300f)!!
        val second = placeLabel(
            circle(600f), listOf(first.asBlock(centreY = 100f, height = 20f)),
            naturalWidth = 300f, height = 20f, canvasWidth = 800f, gap = 2f, minWidth = 30f,
        )!!
        assertEquals(LabelSlot(left = 622f, width = 178f), second)
        assertTrue(second.left >= first.left + first.width)
    }

    @Test fun aMarkerOffTheCanvasHasNoLabelToDraw() {
        assertTrue(circle(500f).intersectsCanvas(canvas, 300f))
        assertTrue(!circle(canvas + r + 10f).intersectsCanvas(canvas, 300f))
        assertTrue(!circle(500f, y = -50f).intersectsCanvas(canvas, 300f))
        assertTrue(circle(canvas + r + 10f).intersectsCanvas(canvas + 100f, 300f))
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
