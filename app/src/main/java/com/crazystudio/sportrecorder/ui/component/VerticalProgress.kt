package com.crazystudio.sportrecorder.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

@Composable
fun VerticalProgress(
    progress: Float, // 0f..1f
    modifier: Modifier = Modifier,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    gradientTop: Color = MaterialTheme.colorScheme.primary,
    gradientBottom: Color = MaterialTheme.colorScheme.primaryContainer,
) {
    val strokePx = with(LocalDensity.current) { 20.dp.toPx() }
    Canvas(modifier) {
        val cx = size.width / 2f
        val h = size.height
        val top = strokePx / 2f
        val bottom = h - strokePx / 2f
        drawLine(trackColor, Offset(cx, top), Offset(cx, bottom), strokeWidth = strokePx)
        drawCircle(trackColor, radius = strokePx / 2f, center = Offset(cx, bottom))
        drawCircle(trackColor, radius = strokePx / 2f, center = Offset(cx, top))
        if (progress > 0f) {
            val brush = Brush.verticalGradient(0f to gradientTop, 1f to gradientBottom, startY = 0f, endY = h)
            val progressTopY = h - (h - strokePx / 2f) * progress
            drawCircle(brush = brush, radius = strokePx / 2f, center = Offset(cx, bottom))
            drawLine(brush = brush, start = Offset(cx, bottom), end = Offset(cx, progressTopY), strokeWidth = strokePx)
            drawCircle(brush = brush, radius = strokePx / 2f, center = Offset(cx, progressTopY))
        }
    }
}
