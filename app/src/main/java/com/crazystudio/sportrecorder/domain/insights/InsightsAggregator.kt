package com.crazystudio.sportrecorder.domain.insights

import com.crazystudio.sportrecorder.domain.model.DietSettings
import com.crazystudio.sportrecorder.domain.model.EatRecord
import java.util.Calendar
import java.util.concurrent.TimeUnit

/** Pure (Android-free) calculator for the Insights screen. */
object InsightsAggregator {

    private const val LATE_NIGHT_HOUR = 22
    private const val MINUTES_PER_HOUR = 60
    private const val WEEK_LOOKBACK_DAYS = 6
    private const val LOCATION_ROUNDING = 1000.0 // ~100m grid for grouping eat locations

    /** Classify one calendar day's meal times against the eating-hours goal. */
    fun adherenceFor(dayMealTimes: List<Long>, eatingHours: Long): AdherenceState {
        if (dayMealTimes.isEmpty()) return AdherenceState.NO_DATA
        val window = (dayMealTimes.max() - dayMealTimes.min())
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

    private fun mealTimesByDay(records: List<EatRecord>): Map<Long, List<Long>> =
        records.groupBy { dayStart(it.time) }
            .mapValues { entry -> entry.value.map { it.time }.sorted() }

    /** Consecutive ON_TARGET days ending at the most recent day; empty today is neutral. */
    fun computeStreak(records: List<EatRecord>, eatingHours: Long, now: Long): Int {
        val byDay = mealTimesByDay(records)

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
        val byDay = mealTimesByDay(records)

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

    /** Inclusive lower bound (local midnight) for the selected period relative to [now]. */
    fun periodStart(now: Long, period: Period): Long {
        val cal = Calendar.getInstance().apply { timeInMillis = dayStart(now) }
        when (period) {
            Period.WEEK -> cal.add(Calendar.DAY_OF_MONTH, -WEEK_LOOKBACK_DAYS)
            Period.MONTH -> cal.set(Calendar.DAY_OF_MONTH, 1)
        }
        return cal.timeInMillis
    }

    /** Stats over [records] (assumed already filtered to the period). */
    fun statsFor(records: List<EatRecord>): InsightsStats {
        val byDay = records.groupBy { dayStart(it.time) }
        val firsts = byDay.values.map { day -> minutesSinceMidnight(day.minOf { it.time }) }
        val lasts = byDay.values.map { day -> minutesSinceMidnight(day.maxOf { it.time }) }
        val lateDays = byDay.values.count { day ->
            day.any { minutesSinceMidnight(it.time) >= LATE_NIGHT_HOUR * MINUTES_PER_HOUR }
        }
        return InsightsStats(
            mealCount = records.size,
            avgFirstMealMinutes = firsts.averageOrNull(),
            avgLastMealMinutes = lasts.averageOrNull(),
            lateNightDays = lateDays,
        )
    }

    /**
     * Build the full Insights result. The adherence calendar reflects [monthAnchor]'s month,
     * while the streak, stats, photo wall, and locations reflect [now] and [period] — the two
     * are intentionally independent (paging the calendar does not move the stats window).
     */
    fun compute(
        records: List<EatRecord>,
        settings: DietSettings,
        now: Long,
        period: Period,
        monthAnchor: Long,
    ): InsightsResult {
        val from = periodStart(now, period)
        val inPeriod = records.filter { it.time in from..now }

        val photoFileNames = inPeriod
            .sortedByDescending { it.time }
            .flatMap { record -> record.photos.map { it.fileName } }

        val locations = inPeriod
            .mapNotNull { it.location }
            .groupBy { (Math.round(it.lat * LOCATION_ROUNDING)) to (Math.round(it.lng * LOCATION_ROUNDING)) }
            .map { (key, points) ->
                LocationCount(
                    lat = key.first / LOCATION_ROUNDING,
                    lng = key.second / LOCATION_ROUNDING,
                    count = points.size,
                )
            }
            .sortedByDescending { it.count }

        return InsightsResult(
            calendarDays = monthCells(records, settings.eatingHours, monthAnchor),
            streak = computeStreak(records, settings.eatingHours, now),
            stats = statsFor(inPeriod),
            photoFileNames = photoFileNames,
            locations = locations,
        )
    }

    private fun minutesSinceMidnight(millis: Long): Int =
        Calendar.getInstance().apply { timeInMillis = millis }
            .let { it.get(Calendar.HOUR_OF_DAY) * MINUTES_PER_HOUR + it.get(Calendar.MINUTE) }

    private fun List<Int>.averageOrNull(): Int? =
        if (isEmpty()) null else (sum().toDouble() / size).toInt()
}
