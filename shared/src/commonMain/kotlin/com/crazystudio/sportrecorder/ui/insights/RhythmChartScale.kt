package com.crazystudio.sportrecorder.ui.insights

import com.crazystudio.sportrecorder.domain.insights.DayBand

/**
 * The x-axis of the rhythm chart, in minutes from each day's midnight. Pure, so it is tested
 * without Compose. The axis is 24 h long, and grows in whole 6 h steps whenever a window ran
 * past midnight — a late night is drawn as a band that keeps going, never wrapped or clipped.
 */
object RhythmChartScale {
    const val MINUTES_PER_HOUR = 60
    const val MINUTES_PER_DAY = 24 * MINUTES_PER_HOUR
    const val TICK_MINUTES = 6 * MINUTES_PER_HOUR

    fun domainEnd(bands: List<DayBand>): Int {
        val furthest = bands.filter { it.mealCount > 0 }.maxOfOrNull { it.lastMinutes } ?: 0
        val end = maxOf(MINUTES_PER_DAY, furthest)
        return ((end + TICK_MINUTES - 1) / TICK_MINUTES) * TICK_MINUTES
    }

    /** Tick positions in minutes, first to last inclusive, evenly spaced every 6 h. */
    fun ticks(domainEnd: Int): List<Int> = (0..domainEnd step TICK_MINUTES).toList()

    /** Fraction of the track width at which [minutes] sits. */
    fun fraction(minutes: Int, domainEnd: Int): Float = minutes.toFloat() / domainEnd
}
