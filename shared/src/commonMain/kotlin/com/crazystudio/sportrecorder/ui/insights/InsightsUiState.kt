package com.crazystudio.sportrecorder.ui.insights

import com.crazystudio.sportrecorder.domain.insights.InsightsResult
import com.crazystudio.sportrecorder.domain.insights.OnThisDayMemory
import com.crazystudio.sportrecorder.domain.insights.Period
import com.crazystudio.sportrecorder.domain.insights.TastyMemoryPick

/**
 * [isLoaded] is false only for the `stateIn` seed, before the first real computation. The screen
 * draws nothing in that frame: painting the empty state there would greet every returning user
 * with 「這裡還空著」 for a flicker.
 */
data class InsightsUiState(
    val isLoaded: Boolean = false,
    val period: Period = Period.MONTH,
    /** A day inside the shown period (epoch millis); `InsightsRange` turns it into the days shown. */
    val anchor: Long = 0L,
    val result: InsightsResult = InsightsResult.EMPTY,
    /** The same day a year (or up to five) ago, or null when none holds anything. Not scoped by [period]. */
    val onThisDay: OnThisDayMemory? = null,
    /** A meal the user rated well, a month or more back, or null. Not scoped by [period]; never the same day as [onThisDay]. */
    val tastyMemory: TastyMemoryPick? = null,
)
