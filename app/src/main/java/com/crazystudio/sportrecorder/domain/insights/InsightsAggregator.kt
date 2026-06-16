package com.crazystudio.sportrecorder.domain.insights

import com.crazystudio.sportrecorder.domain.model.EatRecord
import java.util.Calendar
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

    /** Local midnight (epoch millis) of the day containing [millis]. */
    internal fun dayStart(millis: Long): Long =
        Calendar.getInstance().apply {
            timeInMillis = millis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    /** Consecutive ON_TARGET days ending at the most recent day; empty today is neutral. */
    fun computeStreak(records: List<EatRecord>, eatingHours: Long, now: Long): Int {
        val byDay: Map<Long, List<Long>> = records
            .groupBy { dayStart(it.time) }
            .mapValues { entry -> entry.value.map { it.time }.sorted() }

        val cal = Calendar.getInstance().apply { timeInMillis = dayStart(now) }
        // Skip an empty in-progress today so it does not zero the streak.
        if (byDay[cal.timeInMillis].isNullOrEmpty()) {
            cal.add(Calendar.DAY_OF_MONTH, -1)
        }

        var streak = 0
        while (adherenceFor(byDay[cal.timeInMillis].orEmpty(), eatingHours) == AdherenceState.ON_TARGET) {
            streak++
            cal.add(Calendar.DAY_OF_MONTH, -1)
        }
        return streak
    }

    /** One [DayCell] per day of the month containing [monthAnchor]. */
    fun monthCells(records: List<EatRecord>, eatingHours: Long, monthAnchor: Long): List<DayCell> {
        val byDay: Map<Long, List<Long>> = records
            .groupBy { dayStart(it.time) }
            .mapValues { entry -> entry.value.map { it.time }.sorted() }

        val cal = Calendar.getInstance().apply {
            timeInMillis = monthAnchor
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        return (1..daysInMonth).map { day ->
            val start = cal.timeInMillis
            val state = adherenceFor(byDay[start].orEmpty(), eatingHours)
            cal.add(Calendar.DAY_OF_MONTH, 1)
            DayCell(dayStart = start, dayOfMonth = day, state = state)
        }
    }
}
