package com.crazystudio.sportrecorder.domain.insights

import com.crazystudio.sportrecorder.domain.model.DietSettings
import com.crazystudio.sportrecorder.domain.model.EatRecord
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.time.Instant
import kotlin.time.Duration.Companion.hours

/**
 * Pure (multiplatform) calculator for the Insights screen. Day/month math uses kotlinx-datetime
 * with an injected [TimeZone] (default: the system zone), so it is testable on every platform.
 *
 * Everything here describes a rhythm; nothing here scores it. See
 * `docs/superpowers/specs/2026-09-21-insights-improvements-design.md`.
 */
object InsightsAggregator {

    /** Meals at or after this local hour make a day count towards [InsightsStats.lateHourDays]. */
    const val LATE_HOUR = 22

    private const val MINUTES_PER_HOUR = 60
    private const val MILLIS_PER_MINUTE = 60_000L
    private const val WEEK_LOOKBACK_DAYS = 6
    private const val LOCATION_ROUNDING = 1000.0 // ~100m grid for grouping eat locations

    /** Classify one calendar day's meal times against the eating-hours window. */
    fun windowStateFor(dayMealTimes: List<Long>, eatingHours: Long): DayWindowState {
        if (dayMealTimes.isEmpty()) return DayWindowState.NO_RECORD
        val window = (dayMealTimes.max() - dayMealTimes.min())
        return if (window <= eatingHours.hours.inWholeMilliseconds) {
            DayWindowState.WITHIN_WINDOW
        } else {
            DayWindowState.LONGER_WINDOW
        }
    }

    /** Local midnight (epoch millis) of the day containing [millis]. */
    fun dayStart(millis: Long, timeZone: TimeZone = TimeZone.currentSystemDefault()): Long =
        Instant.fromEpochMilliseconds(millis).toLocalDateTime(timeZone).date
            .atStartOfDayIn(timeZone).toEpochMilliseconds()

    private fun mealTimesByDay(records: List<EatRecord>, timeZone: TimeZone): Map<Long, List<Long>> =
        records.groupBy { dayStart(it.time, timeZone) }
            .mapValues { entry -> entry.value.map { it.time }.sorted() }

    /** Consecutive within-window days ending at the most recent day; an empty today is neutral. */
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
        while (windowStateFor(byDay[key(date)].orEmpty(), eatingHours) == DayWindowState.WITHIN_WINDOW) {
            streak++
            date = date.minus(1, DateTimeUnit.DAY)
        }
        return streak
    }

    /** One [DayCell] per day of the month containing [monthAnchor]; [now] marks today's cell. */
    fun monthCells(
        records: List<EatRecord>,
        eatingHours: Long,
        monthAnchor: Long,
        now: Long,
        timeZone: TimeZone = TimeZone.currentSystemDefault(),
    ): List<DayCell> {
        val byDay = mealTimesByDay(records, timeZone)
        val today = dayStart(now, timeZone)
        val anchor = Instant.fromEpochMilliseconds(monthAnchor).toLocalDateTime(timeZone).date
        val first = LocalDate(anchor.year, anchor.month, 1)
        val daysInMonth = first.plus(1, DateTimeUnit.MONTH).minus(1, DateTimeUnit.DAY).day
        return (1..daysInMonth).map { day ->
            val start = LocalDate(first.year, first.month, day)
                .atStartOfDayIn(timeZone).toEpochMilliseconds()
            DayCell(
                dayStart = start,
                dayOfMonth = day,
                state = windowStateFor(byDay[start].orEmpty(), eatingHours),
                isToday = start == today,
            )
        }
    }

    /** Inclusive lower bound (local midnight) for the selected period relative to [now]. */
    fun periodStart(now: Long, period: Period, timeZone: TimeZone = TimeZone.currentSystemDefault()): Long {
        val date = Instant.fromEpochMilliseconds(now).toLocalDateTime(timeZone).date
        val start = when (period) {
            Period.WEEK -> date.minus(WEEK_LOOKBACK_DAYS, DateTimeUnit.DAY)
            Period.MONTH -> LocalDate(date.year, date.month, 1)
        }
        return start.atStartOfDayIn(timeZone).toEpochMilliseconds()
    }

    /** Stats over [records] (assumed already filtered to the period). */
    fun statsFor(records: List<EatRecord>, timeZone: TimeZone = TimeZone.currentSystemDefault()): InsightsStats {
        val byDay = records.groupBy { dayStart(it.time, timeZone) }
        val firsts = byDay.values.map { day -> minutesSinceMidnight(day.minOf { it.time }, timeZone) }
        val lasts = byDay.values.map { day -> minutesSinceMidnight(day.maxOf { it.time }, timeZone) }
        // A single-meal day has no measurable window, so it is left out rather than counted as 0.
        val windows = byDay.values.filter { it.size > 1 }
            .map { day -> ((day.maxOf { it.time } - day.minOf { it.time }) / MILLIS_PER_MINUTE).toInt() }
        val lateDays = byDay.values.count { day ->
            day.any { minutesSinceMidnight(it.time, timeZone) >= LATE_HOUR * MINUTES_PER_HOUR }
        }
        return InsightsStats(
            mealCount = records.size,
            daysWithRecords = byDay.size,
            avgFirstMealMinutes = firsts.roundedAverageOrNull(),
            avgLastMealMinutes = lasts.roundedAverageOrNull(),
            avgWindowMinutes = windows.roundedAverageOrNull(),
            lateHourDays = lateDays,
        )
    }

    /**
     * Build the full Insights result. The calendar reflects [monthAnchor]'s month, while the
     * stats, photo wall and locations reflect [now] and [period] — the two are intentionally
     * independent (paging the calendar does not move the stats window).
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

        val calendarDays = monthCells(records, settings.eatingHours, monthAnchor, now, timeZone)
        val anchorDate = Instant.fromEpochMilliseconds(monthAnchor).toLocalDateTime(timeZone).date
        val todayDate = Instant.fromEpochMilliseconds(now).toLocalDateTime(timeZone).date

        return InsightsResult(
            hasAnyRecords = records.isNotEmpty(),
            calendarDays = calendarDays,
            monthSummary = MonthSummary.of(calendarDays),
            isAnchorCurrentMonth = anchorDate.year == todayDate.year && anchorDate.month == todayDate.month,
            streak = computeStreak(records, settings.eatingHours, now, timeZone),
            periodStart = from,
            periodEnd = now,
            stats = statsFor(inPeriod, timeZone),
            photoFileNames = photoFileNames,
            locations = locations,
        )
    }

    private fun minutesSinceMidnight(millis: Long, timeZone: TimeZone): Int =
        Instant.fromEpochMilliseconds(millis).toLocalDateTime(timeZone)
            .let { it.hour * MINUTES_PER_HOUR + it.minute }

    private fun List<Int>.roundedAverageOrNull(): Int? =
        if (isEmpty()) null else (sum().toDouble() / size).roundToInt()
}
