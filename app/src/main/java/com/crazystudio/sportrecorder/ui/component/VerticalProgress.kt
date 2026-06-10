package com.crazystudio.sportrecorder.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.crazystudio.sportrecorder.ui.theme.bg_black2
import com.crazystudio.sportrecorder.ui.theme.dark_green
import com.crazystudio.sportrecorder.ui.theme.light_green

@Composable
fun VerticalProgress(
    progress: Float, // 0f..1f, matching original
    modifier: Modifier = Modifier,
) {
    val strokePx = with(LocalDensity.current) { 20.dp.toPx() }
    Canvas(modifier) {
        val cx = size.width / 2f
        val h = size.height
        val top = strokePx / 2f
        val bottom = h - strokePx / 2f
        drawLine(bg_black2, Offset(cx, top), Offset(cx, bottom), strokeWidth = strokePx)
        drawCircle(bg_black2, radius = strokePx / 2f, center = Offset(cx, bottom))
        drawCircle(bg_black2, radius = strokePx / 2f, center = Offset(cx, top))
        if (progress > 0f) {
            val brush = Brush.verticalGradient(0f to light_green, 1f to dark_green, startY = 0f, endY = h)
            // Original formula: height - (height - widthPx/2) * progress
            val progressTopY = h - (h - strokePx / 2f) * progress
            drawCircle(brush = brush, radius = strokePx / 2f, center = Offset(cx, bottom))
            drawLine(brush = brush, start = Offset(cx, bottom), end = Offset(cx, progressTopY), strokeWidth = strokePx)
            drawCircle(brush = brush, radius = strokePx / 2f, center = Offset(cx, progressTopY))
        }
    }
}
