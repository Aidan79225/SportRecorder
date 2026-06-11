package com.crazystudio.sportrecorder.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

@Suppress("LongParameterList")
@Composable
fun CircleProgress(
    progress: Float, // 0..100
    modifier: Modifier = Modifier,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    gradientStart: Color = MaterialTheme.colorScheme.primaryContainer,
    gradientEnd: Color = MaterialTheme.colorScheme.primary,
    tipColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    val strokePx = with(LocalDensity.current) { 20.dp.toPx() }
    Canvas(modifier.aspectRatio(1f)) {
        val w = size.minDimension
        val center = Offset(w / 2f, w / 2f)
        val radius = w / 2f - strokePx
        val brush = Brush.sweepGradient(0f to gradientStart, 1f to gradientEnd, center = center)
        val stroke = Stroke(width = strokePx)
        if (progress > 99f) {
            drawCircle(brush = brush, radius = radius, center = center, style = stroke)
        } else {
            val sweep = 360f * progress / 100f
            drawCircle(color = trackColor, radius = radius, center = center, style = stroke)
            val inset = strokePx
            drawArc(
                brush = brush,
                startAngle = -90f,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = Size(w - 2 * inset, w - 2 * inset),
                style = stroke,
            )
            val rEnd = (w - strokePx * 2) / 2f
            val endRad = Math.toRadians((sweep - 90.0))
            val endX = (w / 2f + rEnd * cos(endRad)).toFloat()
            val endY = (w / 2f + rEnd * sin(endRad)).toFloat()
            drawCircle(brush = brush, radius = strokePx / 2f, center = Offset(endX, endY))
            drawCircle(color = gradientStart, radius = strokePx / 2f, center = Offset(w / 2f, w / 2f - rEnd))
            drawCircle(color = tipColor, radius = strokePx / 3.25f, center = Offset(endX, endY))
        }
    }
}

@Preview
@Composable
@Suppress("UnusedPrivateMember") // @Preview entry point used by the IDE preview tooling
private fun CircleProgressPreview() {
    CircleProgress(progress = 70f, modifier = Modifier.size(240.dp))
}
