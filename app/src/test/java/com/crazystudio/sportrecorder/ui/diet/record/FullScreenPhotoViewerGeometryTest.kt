package com.crazystudio.sportrecorder.ui.diet.record

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class FullScreenPhotoViewerGeometryTest {
    private val size = IntSize(1000, 2000)

    @Test
    fun focalOffset_unspecifiedCentroid_keepsLastOffsetWithoutNaN() {
        // On the pointer-release event Compose's calculateCentroid() returns Offset.Unspecified
        // (NaN, NaN). If that reaches the graphicsLayer translation the image blanks out (black).
        // focalOffset must fall back to the current offset instead of propagating NaN.
        val current = Offset(120f, -50f)
        val result = focalOffset(
            centroid = Offset.Unspecified,
            pan = Offset.Zero,
            from = ZoomState(scale = 2f, offset = current),
            newScale = 2f,
            size = size,
        )
        assertFalse("offset.x must not be NaN", result.x.isNaN())
        assertFalse("offset.y must not be NaN", result.y.isNaN())
        assertEquals(current, result)
    }

    @Test
    fun focalOffset_normalCentroid_isFinite() {
        val result = focalOffset(
            centroid = Offset(500f, 1000f),
            pan = Offset.Zero,
            from = ZoomState(scale = 1f, offset = Offset.Zero),
            newScale = 2f,
            size = size,
        )
        assertFalse(result.x.isNaN())
        assertFalse(result.y.isNaN())
    }
}
