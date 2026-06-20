package com.crazystudio.sportrecorder.domain.insights

import com.crazystudio.sportrecorder.domain.model.DietSettings
import com.crazystudio.sportrecorder.domain.model.EatRecord
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.math.roundToLong
import kotlin.time.Duration.Companion.hours

/**
 * Pure (multiplatform) calculator for the Insights screen. Day/month math uses kotlinx-datetime
 * with an injected [TimeZone] (default: the system zone), so it is testable on every platform.
 */
object InsightsAggregator {

    private const val LATE_NIGHT_HOUR = 22
    private const val MINUTES_PER_HOUR = 60
    private const val WEEK_LOOKBACK_DAYS = 6
    private const val LOCATION_ROUNDING = 1000.0 // ~100m grid for grouping eat locations

    /** Classify one calendar day's meal times against the eating-hours goal. */
    fun adherenceFor(dayMealTimes: List<Long>, eatingHours: Long): AdherenceState {
        if (dayMealTimes.isEmpty()) return AdherenceState.NO_DATA
        val window = (dayMealTimes.max() - dayMealTimes.min())
        return if (window <= eatingHours.hours.inWholeMilliseconds) {
            AdherenceState.ON_TARGET
        } else {
            AdherenceState.OFF_TARGET
        }
    }

    /** Local midnight (epoch millis) of the day containing [millis]. */
    fun dayStart(millis: Long, timeZone: TimeZone = TimeZone.currentSystemDefault()): Long =
        Instant.fromEpochMilliseconds(millis).toLocalDateTime(timeZone).date
            .atStartOfDayIn(timeZone).toEpochMilliseconds()

    private fun mealTimesByDay(records: List<EatRecord>, timeZone: TimeZone): Map<Long, List<Long>> =
        records.groupBy { dayStart(it.time, timeZone) }
            .mapValues { entry -> entry.value.map { it.time }.sorted() }

    /** Consecutive ON_TARGET days ending at the most recent day; empty today is neutral. */
    fun computeStreak(
        records: List<EatRecord>,
        eatingHours: Long,
        now: Long,
        timeZone: TimeZone = TimeZone.currentSystemDefault(),
    ): Int {
        val byDay = mealTimesByDay(records, timeZone)
        fun key(date: LocalDate) = date.atStartOfDayIn(timeZone).toEpochMilliseconds()

        var date = Instant.fromEpochMilliseconds(now).toLocalDateTime(timeZone).date
        // Skip an empty in-progress today so it does not zero the streak.
        if (byDay[key(date)].isNullOrEmpty()) {
            date = date.minus(1, DateTimeUnit.DAY)
        }

        var streak = 0
        while (adherenceFor(byDay[key(date)].orEmpty(), eatingHours) == AdherenceState.ON_TARGET) {
            streak++
            date = date.minus(1, DateTimeUnit.DAY)
        }
        return streak
    }

    /** One [DayCell] per day of the month containing [monthAnchor]. */
    fun monthCells(
        records: List<EatRecord>,
        eatingHours: Long,
        monthAnchor: Long,
        timeZone: TimeZone = TimeZone.currentSystemDefault(),
    ): List<DayCell> {
        val byDay = mealTimesByDay(records, timeZone)
        val anchor = Instant.fromEpochMilliseconds(monthAnchor).toLocalDateTime(timeZone).date
        val first = LocalDate(anchor.year, anchor.monthNumber, 1)
        val daysInMonth = first.plus(1, DateTimeUnit.MONTH).minus(1, DateTimeUnit.DAY).dayOfMonth
        return (1..daysInMonth).map { day ->
            val start = LocalDate(first.year, first.monthNumber, day)
                .atStartOfDayIn(timeZone).toEpochMilliseconds()
            DayCell(dayStart = start, dayOfMonth = day, state = adherenceFor(byDay[start].orEmpty(), eatingHours))
        }
    }

    /** Inclusive lower bound (local midnight) for the selected period relative to [now]. */
    fun periodStart(now: Long, period: Period, timeZone: TimeZone = TimeZone.currentSystemDefault()): Long {
        val date = Instant.fromEpochMilliseconds(now).toLocalDateTime(timeZone).date
        val start = when (period) {
            Period.WEEK -> date.minus(WEEK_LOOKBACK_DAYS, DateTimeUnit.DAY)
            Period.MONTH -> LocalDate(date.year, date.monthNumber, 1)
        }
        return start.atStartOfDayIn(timeZone).toEpochMilliseconds()
    }

    /** Stats over [records] (assumed already filtered to the period). */
    fun statsFor(records: List<EatRecord>, timeZone: TimeZone = TimeZone.currentSystemDefault()): InsightsStats {
        val byDay = records.groupBy { dayStart(it.time, timeZone) }
        val firsts = byDay.values.map { day -> minutesSinceMidnight(day.minOf { it.time }, timeZone) }
        val lasts = byDay.values.map { day -> minutesSinceMidnight(day.maxOf { it.time }, timeZone) }
        val lateDays = byDay.values.count { day ->
            day.any { minutesSinceMidnight(it.time, timeZone) >= LATE_NIGHT_HOUR * MINUTES_PER_HOUR }
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
        timeZone: TimeZone = TimeZone.currentSystemDefault(),
    ): InsightsResult {
        val from = periodStart(now, period, timeZone)
        val inPeriod = records.filter { it.time in from..now }

        val photoFileNames = inPeriod
            .sortedByDescending { it.time }
            .flatMap { record -> record.photos.map { it.fileName } }

        val locations = inPeriod
            .mapNotNull { it.location }
            .groupBy { (it.lat * LOCATION_ROUNDING).roundToLong() to (it.lng * LOCATION_ROUNDING).roundToLong() }
            .map { (key, points) ->
                LocationCount(
                    lat = key.first / LOCATION_ROUNDING,
                    lng = key.second / LOCATION_ROUNDING,
                    count = points.size,
                )
            }
            .sortedByDescending { it.count }

        return InsightsResult(
            calendarDays = monthCells(records, settings.eatingHours, monthAnchor, timeZone),
            streak = computeStreak(records, settings.eatingHours, now, timeZone),
            stats = statsFor(inPeriod, timeZone),
            photoFileNames = photoFileNames,
            locations = locations,
        )
    }

    private fun minutesSinceMidnight(millis: Long, timeZone: TimeZone): Int =
        Instant.fromEpochMilliseconds(millis).toLocalDateTime(timeZone)
            .let { it.hour * MINUTES_PER_HOUR + it.minute }

    private fun List<Int>.averageOrNull(): Int? =
        if (isEmpty()) null else (sum().toDouble() / size).toInt()
}
