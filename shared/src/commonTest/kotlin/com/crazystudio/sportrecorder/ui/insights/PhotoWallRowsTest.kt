package com.crazystudio.sportrecorder.ui.insights

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The wall's row shaping. The point of these tests is that **expanding reaches the whole period**:
 * Insights is the one place to look back at a period at once, so the preview is a preview, never a
 * ceiling on what the user may see.
 */
class PhotoWallRowsTest {

    private fun names(count: Int) = List(count) { "p$it.jpg" }

    private fun rows(count: Int, expanded: Boolean) =
        photoWallRows(names(count), expanded = expanded, columns = 3, previewCount = 12)

    @Test
    fun collapsed_showsOnlyThePreview() {
        val rows = rows(count = 40, expanded = false)

        assertEquals(4, rows.size)
        assertEquals(12, rows.sumOf { it.size })
    }

    @Test
    fun expanded_showsEveryPhotoInThePeriod() {
        val rows = rows(count = 40, expanded = true)

        assertEquals(40, rows.sumOf { it.size })
        assertEquals(14, rows.size) // 13 full rows of three plus a row of one
        assertEquals(1, rows.last().size)
    }

    @Test
    fun aPeriodShorterThanThePreview_looksTheSameEitherWay() {
        val collapsed = rows(count = 5, expanded = false)

        assertEquals(collapsed, rows(count = 5, expanded = true))
        assertEquals(2, collapsed.size)
        assertEquals(5, collapsed.sumOf { it.size })
    }

    @Test
    fun exactlyThePreviewCount_hasNothingMoreToShow() {
        assertEquals(rows(count = 12, expanded = false), rows(count = 12, expanded = true))
    }

    @Test
    fun noPhotos_noRows() {
        assertTrue(rows(count = 0, expanded = false).isEmpty())
        assertTrue(rows(count = 0, expanded = true).isEmpty())
    }

    /** Tapping a thumbnail opens the viewer at that photo of the *whole* period, not of the row. */
    @Test
    fun everyPhotoKeepsItsPlaceInTheFullList() {
        val rows = rows(count = 40, expanded = true)

        val flattened = rows.flatten()
        assertEquals((0 until 40).toList(), flattened.map { it.index })
        assertEquals(names(40), flattened.map { it.fileName })
        assertEquals(listOf(15, 16, 17), rows[5].map { it.index })
    }
}
