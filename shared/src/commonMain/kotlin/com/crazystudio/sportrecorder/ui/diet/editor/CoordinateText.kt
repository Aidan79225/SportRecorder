package com.crazystudio.sportrecorder.ui.diet.editor

import kotlin.math.abs
import kotlin.math.roundToLong

private const val SCALE = 10_000L
private const val DECIMALS = 4

/** A latitude or longitude to four decimals (about 10 m), built by hand: no String.format in common code. */
internal fun coordinateText(value: Double): String {
    val scaled = (value * SCALE).roundToLong()
    val sign = if (scaled < 0) "-" else ""
    val magnitude = abs(scaled)
    return "$sign${magnitude / SCALE}.${(magnitude % SCALE).toString().padStart(DECIMALS, '0')}"
}
