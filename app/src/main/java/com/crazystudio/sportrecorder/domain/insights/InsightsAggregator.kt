package com.crazystudio.sportrecorder.domain.insights

import java.util.concurrent.TimeUnit

/** Pure (Android-free) calculator for the Insights screen. */
object InsightsAggregator {

    /** Classify one calendar day's ascending meal times against the eating-hours goal. */
    fun adherenceFor(dayMealTimesAsc: List<Long>, eatingHours: Long): AdherenceState {
        if (dayMealTimesAsc.isEmpty()) return AdherenceState.NO_DATA
        val window = dayMealTimesAsc.last() - dayMealTimesAsc.first()
        return if (window <= TimeUnit.HOURS.toMillis(eatingHours)) {
            AdherenceState.ON_TARGET
        } else {
            AdherenceState.OFF_TARGET
        }
    }
}
