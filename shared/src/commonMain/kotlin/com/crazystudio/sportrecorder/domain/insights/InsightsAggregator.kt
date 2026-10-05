package com.crazystudio.sportrecorder.domain.insights

import com.crazystudio.sportrecorder.domain.diet.DietWindow
import com.crazystudio.sportrecorder.domain.model.DietSettings
import com.crazystudio.sportrecorder.domain.model.EatRecord
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.time.Instant
import kotlin.time.Duration.Companion.hours

/**
 * Pure (multiplatform) calculator for the Insights screen. Day/month math uses kotlinx-datetime
 * with an injected [TimeZone] (default: the system zone), so it is testable on every platform.
 *
 * Meals are bucketed by **eating day**, not calendar date: the same window grouping Home uses
 * ([DietWindow.groupIntoWindows]), keyed by the local date of each window's first meal. A 00:30
 * snack after a 20:00 dinner therefore belongs to the dinner's day, on the calendar, in the
 * stats and on the chart alike — the two screens never disagree about which day a meal was.
 *
 * Everything here describes a rhythm; nothing here scores it. See
 * `docs/superpowers/specs/2026-09-21-insights-improvements-design.md`.
 */
object InsightsAggregator {

    private const val MINUTES_PER_HOUR = 60
    private const val MILLIS_PER_MINUTE = 60_000L
    private const val LOCATION_ROUNDING = 1000.0 // ~100m grid for grouping eat locations

    /** Classify one eating day's meal times against the eating-hours window. */
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

    /**
     * Every record, grouped by eating day (see the class doc). Keys are local midnights; each
     * value is that day's meals, ascending by time. Iteration order follows the days.
     */
    fun mealsByEatingDay(
        records: List<EatRecord>,
        settings: DietSettings,
        timeZone: TimeZone = TimeZone.currentSystemDefault(),
    ): Map<Long, List<EatRecord>> {
        val sorted = records.sortedBy { it.time }
        val byDay = LinkedHashMap<Long, MutableList<EatRecord>>()
        DietWindow.groupIntoWindows(sorted, settings.eatingHours, settings.fastingHours) { it.time }
            .forEach { window ->
                byDay.getOrPut(dayStart(window.first().time, timeZone)) { mutableListOf() }.addAll(window)
            }
        return byDay
    }

    /** One [DayCell] per day of [range]; [now] marks today and dims what is still ahead. */
    fun dayCells(
        range: DayRange,
        byDay: Map<Long, List<EatRecord>>,
        eatingHours: Long,
        now: Long,
        timeZone: TimeZone = TimeZone.currentSystemDefault(),
    ): List<DayCell> {
        val today = dayStart(now, timeZone)
        return range.dayStarts.map { start ->
            DayCell(
                dayStart = start,
                dayOfMonth = dayOfMonth(start, timeZone),
                state = windowStateFor(byDay[start].orEmpty().map { it.time }, eatingHours),
                isToday = start == today,
                isFuture = start > today,
            )
        }
    }

    /** Stats over eating days ([days]: one ascending meal list per day, already scoped to the period). */
    fun statsFor(
        days: Collection<List<EatRecord>>,
        timeZone: TimeZone = TimeZone.currentSystemDefault(),
    ): InsightsStats {
        val spans = days.filter { it.isNotEmpty() }.map { meals -> spanOf(meals, timeZone) }
        // A single-meal day has no measurable window, so it is left out rather than counted as 0.
        val windows = spans.filter { it.mealCount > 1 }.map { it.lastMinutes - it.firstMinutes }
        return InsightsStats(
            mealCount = spans.sumOf { it.mealCount },
            daysWithRecords = spans.size,
            avgFirstMealMinutes = spans.map { it.firstMinutes }.roundedAverageOrNull(),
            avgLastMealMinutes = spans.map { it.lastMinutes }.roundedAverageOrNull(),
            avgWindowMinutes = windows.roundedAverageOrNull(),
        )
    }

    /** One [DayBand] per day of [range] — empty days included, so rows never shift. */
    fun bandsFor(
        range: DayRange,
        byDay: Map<Long, List<EatRecord>>,
        timeZone: TimeZone = TimeZone.currentSystemDefault(),
    ): List<DayBand> = range.dayStarts.map { start ->
        val meals = byDay[start].orEmpty()
        val span = if (meals.isEmpty()) null else spanOf(meals, timeZone)
        DayBand(
            dayStart = start,
            dayOfMonth = dayOfMonth(start, timeZone),
            firstMinutes = span?.firstMinutes ?: 0,
            lastMinutes = span?.lastMinutes ?: 0,
            mealCount = meals.size,
        )
    }

    /**
     * Build the full Insights result for the period anchored at [anchor]. Every card reads the
     * same [DayRange]; days after today are excluded from the stats, chart, photos and places
     * (a record dated in the future is left for the calendar to show, quietly).
     */
    fun compute(
        records: List<EatRecord>,
        settings: DietSettings,
        now: Long,
        period: Period,
        anchor: Long,
        timeZone: TimeZone = TimeZone.currentSystemDefault(),
    ): InsightsResult {
        val range = InsightsRange.of(anchor, period, timeZone)
        val today = dayStart(now, timeZone)
        val byDay = mealsByEatingDay(records, settings, timeZone)
        val inRange = byDay.filterKeys { it in range && it <= today }
        val meals = inRange.values.flatten()

        val photoFileNames = meals
            .sortedByDescending { it.time }
            .flatMap { record -> record.photos.map { it.fileName } }

        // A record whose venue knows where it is becomes (part of) a named marker at the VENUE's
        // position. Every other record — no venue, or a venue with no position yet — stays the
        // anonymous point at its own coordinates it has always been: nothing leaves the map.
        val (atVenues, elsewhere) = meals.partition { it.venue?.lat != null && it.venue.lng != null }

        val venueLocations = atVenues
            .groupBy { it.venue!!.id }
            .map { (_, visits) ->
                val venue = visits.first().venue!!
                LocationCount(lat = venue.lat!!, lng = venue.lng!!, count = visits.size, name = venue.name)
            }

        val pointLocations = elsewhere
            .mapNotNull { it.location }
            .groupBy { (it.lat * LOCATION_ROUNDING).roundToLong() to (it.lng * LOCATION_ROUNDING).roundToLong() }
            .map { (key, points) ->
                LocationCount(
                    lat = key.first / LOCATION_ROUNDING,
                    lng = key.second / LOCATION_ROUNDING,
                    count = points.size,
                )
            }

        val locations = (venueLocations + pointLocations).sortedByDescending { it.count }

        val calendarDays = dayCells(range, byDay, settings.eatingHours, now, timeZone)

        return InsightsResult(
            hasAnyRecords = records.isNotEmpty(),
            range = range,
            calendarDays = calendarDays,
            summary = PeriodSummary.of(calendarDays),
            isCurrentPeriod = InsightsRange.isCurrent(anchor, period, now, timeZone),
            stats = statsFor(inRange.values, timeZone),
            bands = bandsFor(range, inRange, timeZone),
            photoFileNames = photoFileNames,
            locations = locations,
        )
    }

    private class DaySpan(val firstMinutes: Int, val lastMinutes: Int, val mealCount: Int)

    /**
     * First/last meal of one eating day as minutes from that day's midnight. The first meal reads
     * the local clock; the last adds the real elapsed span, so a window that crosses midnight
     * runs past 24 h instead of wrapping to the next morning.
     */
    private fun spanOf(mealsAsc: List<EatRecord>, timeZone: TimeZone): DaySpan {
        val first = mealsAsc.first().time
        val last = mealsAsc.last().time
        val firstMinutes = Instant.fromEpochMilliseconds(first).toLocalDateTime(timeZone)
            .let { it.hour * MINUTES_PER_HOUR + it.minute }
        return DaySpan(
            firstMinutes = firstMinutes,
            lastMinutes = firstMinutes + ((last - first) / MILLIS_PER_MINUTE).toInt(),
            mealCount = mealsAsc.size,
        )
    }

    private fun dayOfMonth(dayStart: Long, timeZone: TimeZone): Int =
        Instant.fromEpochMilliseconds(dayStart).toLocalDateTime(timeZone).day

    private fun List<Int>.roundedAverageOrNull(): Int? =
        if (isEmpty()) null else (sum().toDouble() / size).roundToInt()
}
