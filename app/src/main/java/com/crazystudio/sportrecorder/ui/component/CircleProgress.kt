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
import androidx.compose.ui.graphics.drawscope.rotate
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
    gradientStart: Color = MaterialTheme.colorScheme.primaryContainer, // dark
    gradientEnd: Color = MaterialTheme.colorScheme.primary, // bright
    tipColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    val strokePx = with(LocalDensity.current) { 20.dp.toPx() }
    Canvas(modifier.aspectRatio(1f)) {
        val w = size.minDimension
        val center = Offset(w / 2f, w / 2f)
        val radius = w / 2f - strokePx
        val stroke = Stroke(width = strokePx)
        val fraction = (progress / 100f).coerceIn(0f, 1f)
        if (progress > 99f) {
            // Full ring: dark at the 12 o'clock start, brightest just before it closes.
            rotate(degrees = -90f, pivot = center) {
                val brush = Brush.sweepGradient(0f to gradientStart, 1f to gradientEnd, center = center)
                drawCircle(brush = brush, radius = radius, center = center, style = stroke)
            }
        } else {
            drawCircle(color = trackColor, radius = radius, center = center, style = stroke)
            val inset = strokePx
            rotate(degrees = -90f, pivot = center) {
                // Bright stop placed at the progress fraction so the tip is the brightest point.
                val brush = Brush.sweepGradient(
                    0f to gradientStart,
                    fraction.coerceAtLeast(0.001f) to gradientEnd,
                    center = center,
                )
                drawArc(
                    brush = brush,
                    startAngle = 0f, // rotate(-90) already moved the start to 12 o'clock
                    sweepAngle = 360f * fraction,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = Size(w - 2 * inset, w - 2 * inset),
                    style = stroke,
                )
            }
            // Start dot (dark) at 12 o'clock; tip dot (bright + inner tipColor) at the arc end.
            val rEnd = (w - strokePx * 2) / 2f
            val endRad = Math.toRadians(360.0 * fraction - 90.0)
            val endX = (w / 2f + rEnd * cos(endRad)).toFloat()
            val endY = (w / 2f + rEnd * sin(endRad)).toFloat()
            drawCircle(color = gradientStart, radius = strokePx / 2f, center = Offset(w / 2f, w / 2f - rEnd))
            drawCircle(color = gradientEnd, radius = strokePx / 2f, center = Offset(endX, endY))
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
