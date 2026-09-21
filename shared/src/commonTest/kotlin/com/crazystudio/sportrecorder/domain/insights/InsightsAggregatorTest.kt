package com.crazystudio.sportrecorder.domain.insights

import com.crazystudio.sportrecorder.domain.model.DietSettings
import com.crazystudio.sportrecorder.domain.model.EatPhoto
import com.crazystudio.sportrecorder.domain.model.EatRecord
import com.crazystudio.sportrecorder.domain.model.GeoPoint
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

// Moved from :app into :shared/commonTest (kotlin.test). Runs on JVM locally and on the
// iosSimulatorArm64 target in CI. Every case pins [zone] so results never depend on the
// machine's default time zone.
class InsightsAggregatorTest {
    private val zone = TimeZone.UTC
    private val eatingHours = 8L
    private val settings = DietSettings(fastingHours = 16, eatingHours = eatingHours)
    private val base = 1_700_000_000_000L
    private fun h(n: Long) = n * 3_600_000L

    /** Epoch millis for a wall-clock moment in [zone]. `month` is 1-based. */
    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int = 0): Long =
        LocalDateTime(year, month, day, hour, minute).toInstant(zone).toEpochMilliseconds()

    private fun rec(time: Long) =
        EatRecord(id = 0, time = time, location = null, note = null, photos = emptyList())

    private fun recFull(time: Long, photos: List<String>, lat: Double?, lng: Double?) =
        EatRecord(
            id = 0,
            time = time,
            note = null,
            location = if (lat != null && lng != null) GeoPoint(lat, lng) else null,
            photos = photos.mapIndexed { i, name -> EatPhoto(id = i, fileName = name, createdAt = time) },
        )

    // --- window state -------------------------------------------------------

    @Test fun windowState_noMeals_isNoRecord() {
        assertEquals(DayWindowState.NO_RECORD, InsightsAggregator.windowStateFor(emptyList(), eatingHours))
    }

    @Test fun windowState_singleMeal_isWithinWindow() {
        assertEquals(DayWindowState.WITHIN_WINDOW, InsightsAggregator.windowStateFor(listOf(base), eatingHours))
    }

    @Test fun windowState_withinGoal_isWithinWindow() {
        val state = InsightsAggregator.windowStateFor(listOf(base, base + h(6)), eatingHours)
        assertEquals(DayWindowState.WITHIN_WINDOW, state)
    }

    @Test fun windowState_exactlyGoal_isWithinWindow() {
        val state = InsightsAggregator.windowStateFor(listOf(base, base + h(8)), eatingHours)
        assertEquals(DayWindowState.WITHIN_WINDOW, state)
    }

    @Test fun windowState_overGoal_isLongerWindow() {
        val state = InsightsAggregator.windowStateFor(listOf(base, base + h(9)), eatingHours)
        assertEquals(DayWindowState.LONGER_WINDOW, state)
    }

    // --- calendar -----------------------------------------------------------

    @Test fun monthCells_lengthMatchesDaysInMonth() {
        // March 2026 has 31 days.
        val now = at(2026, 3, 15, 12)
        val cells = InsightsAggregator.monthCells(emptyList(), eatingHours, now, now, zone)
        assertEquals(31, cells.size)
        assertEquals(1, cells.first().dayOfMonth)
        assertEquals(31, cells.last().dayOfMonth)
    }

    @Test fun monthCells_classifiesEachDay() {
        val records = listOf(
            rec(at(2026, 3, 10, 9)),
            rec(at(2026, 3, 10, 14)), // 5h window -> within
            rec(at(2026, 3, 11, 8)),
            rec(at(2026, 3, 11, 20)), // 12h window -> longer
        )
        val anchor = at(2026, 3, 1, 0)
        val cells = InsightsAggregator.monthCells(records, eatingHours, anchor, anchor, zone)
        assertEquals(DayWindowState.WITHIN_WINDOW, cells[9].state) // day 10
        assertEquals(DayWindowState.LONGER_WINDOW, cells[10].state) // day 11
        assertEquals(DayWindowState.NO_RECORD, cells[0].state) // day 1
    }

    @Test fun monthCells_marksOnlyToday() {
        val now = at(2026, 3, 15, 12)
        val cells = InsightsAggregator.monthCells(emptyList(), eatingHours, now, now, zone)
        assertEquals(listOf(15), cells.filter { it.isToday }.map { it.dayOfMonth })
    }

    @Test fun monthCells_otherMonthHasNoToday() {
        val now = at(2026, 3, 15, 12)
        val anchor = at(2026, 2, 1, 0)
        val cells = InsightsAggregator.monthCells(emptyList(), eatingHours, anchor, now, zone)
        assertTrue(cells.none { it.isToday })
    }

    @Test fun monthSummary_countsRecordedAndWithinWindowDays() {
        val records = listOf(
            rec(at(2026, 3, 10, 9)),
            rec(at(2026, 3, 10, 14)), // within
            rec(at(2026, 3, 11, 8)),
            rec(at(2026, 3, 11, 20)), // longer
            rec(at(2026, 3, 12, 8)), // single meal -> within
        )
        val anchor = at(2026, 3, 1, 0)
        val summary = MonthSummary.of(
            InsightsAggregator.monthCells(records, eatingHours, anchor, anchor, zone)
        )
        assertEquals(3, summary.recordedDays)
        assertEquals(2, summary.withinWindowDays)
    }

    // --- streak -------------------------------------------------------------

    @Test fun streak_countsConsecutiveWithinWindowDays() {
        val records = listOf(
            rec(at(2026, 3, 14, 9)),
            rec(at(2026, 3, 14, 12)),
            rec(at(2026, 3, 15, 9)),
            rec(at(2026, 3, 15, 13)),
        )
        assertEquals(2, InsightsAggregator.computeStreak(records, eatingHours, at(2026, 3, 15, 20), zone))
    }

    @Test fun streak_emptyTodayDoesNotBreak() {
        // 14th is within window; 15th (today) has no meals yet.
        val records = listOf(rec(at(2026, 3, 14, 9)), rec(at(2026, 3, 14, 12)))
        assertEquals(1, InsightsAggregator.computeStreak(records, eatingHours, at(2026, 3, 15, 10), zone))
    }

    @Test fun streak_longerWindowDayBreaks() {
        val records = listOf(
            rec(at(2026, 3, 13, 9)),
            rec(at(2026, 3, 13, 12)),
            rec(at(2026, 3, 14, 8)),
            rec(at(2026, 3, 14, 22)), // 14h window
            rec(at(2026, 3, 15, 9)),
            rec(at(2026, 3, 15, 12)),
        )
        assertEquals(1, InsightsAggregator.computeStreak(records, eatingHours, at(2026, 3, 15, 20), zone))
    }

    @Test fun streak_gapDayBreaks() {
        val records = listOf(
            rec(at(2026, 3, 13, 9)),
            rec(at(2026, 3, 13, 12)),
            rec(at(2026, 3, 15, 9)),
            rec(at(2026, 3, 15, 12)),
        )
        assertEquals(1, InsightsAggregator.computeStreak(records, eatingHours, at(2026, 3, 15, 20), zone))
    }

    // --- stats --------------------------------------------------------------

    @Test fun statsFor_countsAndAverages() {
        val records = listOf(
            rec(at(2026, 3, 10, 8, 0)),
            rec(at(2026, 3, 10, 18, 0)),
            rec(at(2026, 3, 11, 10, 0)),
            rec(at(2026, 3, 11, 20, 0)),
        )
        val stats = InsightsAggregator.statsFor(records, zone)
        assertEquals(4, stats.mealCount)
        assertEquals(2, stats.daysWithRecords)
        assertEquals(9 * 60, stats.avgFirstMealMinutes)
        assertEquals(19 * 60, stats.avgLastMealMinutes)
    }

    @Test fun statsFor_averagesRoundInsteadOfTruncating() {
        // First meals at 08:00 and 08:01 -> 480.5 minutes, which must round to 481 (08:01).
        val records = listOf(
            rec(at(2026, 3, 10, 8, 0)),
            rec(at(2026, 3, 11, 8, 1)),
        )
        assertEquals(481, InsightsAggregator.statsFor(records, zone).avgFirstMealMinutes)
    }

    @Test fun statsFor_avgWindowAveragesFirstToLastSpan() {
        val records = listOf(
            rec(at(2026, 3, 10, 8, 0)),
            rec(at(2026, 3, 10, 14, 0)), // 6h
            rec(at(2026, 3, 11, 9, 0)),
            rec(at(2026, 3, 11, 12, 0)),
            rec(at(2026, 3, 11, 17, 0)), // 8h
        )
        assertEquals(7 * 60, InsightsAggregator.statsFor(records, zone).avgWindowMinutes)
    }

    @Test fun statsFor_avgWindowIgnoresSingleMealDays() {
        val records = listOf(
            rec(at(2026, 3, 10, 8, 0)),
            rec(at(2026, 3, 10, 14, 0)), // 6h
            rec(at(2026, 3, 11, 9, 0)), // single meal, no measurable window
        )
        val stats = InsightsAggregator.statsFor(records, zone)
        assertEquals(6 * 60, stats.avgWindowMinutes)
        assertEquals(2, stats.daysWithRecords)
    }

    @Test fun statsFor_avgWindowNullWhenEveryDayHasOneMeal() {
        val records = listOf(rec(at(2026, 3, 10, 8, 0)), rec(at(2026, 3, 11, 9, 0)))
        assertNull(InsightsAggregator.statsFor(records, zone).avgWindowMinutes)
    }

    @Test fun statsFor_lateHourCountsDistinctDays() {
        val records = listOf(
            rec(at(2026, 3, 10, 23, 0)),
            rec(at(2026, 3, 11, 12, 0)),
            rec(at(2026, 3, 12, 22, 0)),
            rec(at(2026, 3, 12, 22, 30)),
        )
        assertEquals(2, InsightsAggregator.statsFor(records, zone).lateHourDays)
    }

    @Test fun statsFor_empty() {
        val stats = InsightsAggregator.statsFor(emptyList(), zone)
        assertEquals(0, stats.mealCount)
        assertEquals(0, stats.daysWithRecords)
        assertNull(stats.avgFirstMealMinutes)
        assertNull(stats.avgLastMealMinutes)
        assertNull(stats.avgWindowMinutes)
        assertEquals(0, stats.lateHourDays)
    }

    @Test fun periodStart_weekIsSevenDays() {
        val now = at(2026, 3, 15, 12)
        val start = InsightsAggregator.periodStart(now, Period.WEEK, zone)
        assertEquals(at(2026, 3, 9, 0), start)
    }

    @Test fun periodStart_monthIsFirstOfMonth() {
        val now = at(2026, 3, 15, 12)
        assertEquals(at(2026, 3, 1, 0), InsightsAggregator.periodStart(now, Period.MONTH, zone))
    }

    // --- compute ------------------------------------------------------------

    @Test fun compute_assemblesAllCards() {
        val now = at(2026, 2, 15, 21)
        val records = listOf(
            recFull(at(2026, 2, 15, 9), listOf("a.webp"), 25.0, 121.0),
            recFull(at(2026, 2, 15, 13), listOf("b.webp"), 25.0, 121.0),
        )
        val result = InsightsAggregator.compute(records, settings, now, Period.MONTH, now, zone)

        assertTrue(result.hasAnyRecords)
        assertEquals(28, result.calendarDays.size)
        assertEquals(MonthSummary(recordedDays = 1, withinWindowDays = 1), result.monthSummary)
        assertTrue(result.isAnchorCurrentMonth)
        assertEquals(1, result.streak)
        assertEquals(2, result.stats.mealCount)
        assertEquals(4 * 60, result.stats.avgWindowMinutes)
        assertEquals(at(2026, 2, 1, 0), result.periodStart)
        assertEquals(now, result.periodEnd)
        assertEquals(listOf("b.webp", "a.webp"), result.photoFileNames)
        assertEquals(1, result.locations.size)
        assertEquals(2, result.locations.first().count)
    }

    @Test fun compute_weekPeriodFiltersOutOlderRecords() {
        val now = at(2026, 2, 15, 21)
        val records = listOf(
            recFull(at(2026, 2, 15, 9), listOf("recent.webp"), 25.0, 121.0),
            recFull(at(2026, 2, 1, 9), listOf("old.webp"), 25.0, 121.0),
        )
        val result = InsightsAggregator.compute(records, settings, now, Period.WEEK, now, zone)
        assertEquals(1, result.stats.mealCount)
        assertEquals(listOf("recent.webp"), result.photoFileNames)
        assertEquals(1, result.locations.first().count)
        // The calendar still sees every record, not just the period's.
        assertEquals(2, result.monthSummary.recordedDays)
    }

    @Test fun compute_pastMonthAnchorIsNotTheCurrentMonth() {
        val now = at(2026, 2, 15, 21)
        val result = InsightsAggregator.compute(emptyList(), settings, now, Period.MONTH, at(2026, 1, 3, 0), zone)
        assertFalse(result.isAnchorCurrentMonth)
        assertEquals(31, result.calendarDays.size)
    }

    @Test fun compute_empty_returnsNoRecordsWithFullCalendar() {
        val now = at(2026, 2, 15, 21)
        val result = InsightsAggregator.compute(emptyList(), settings, now, Period.MONTH, now, zone)
        assertFalse(result.hasAnyRecords)
        assertEquals(28, result.calendarDays.size)
        assertEquals(MonthSummary.EMPTY, result.monthSummary)
        assertEquals(0, result.streak)
        assertEquals(0, result.stats.mealCount)
        assertEquals(emptyList<String>(), result.photoFileNames)
        assertEquals(emptyList<LocationCount>(), result.locations)
    }
}
