package com.crazystudio.sportrecorder.domain.insights

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InsightsRangeTest {
    private val zone = TimeZone.UTC

    private fun at(year: Int, month: Int, day: Int, hour: Int = 0, minute: Int = 0): Long =
        LocalDateTime(year, month, day, hour, minute).toInstant(zone).toEpochMilliseconds()

    @Test fun of_week_isTheSevenDaysEndingOnTheAnchor() {
        val range = InsightsRange.of(at(2026, 3, 15, 13), Period.WEEK, zone)
        assertEquals(7, range.dayStarts.size)
        assertEquals(at(2026, 3, 9), range.start)
        assertEquals(at(2026, 3, 15), range.endInclusive)
    }

    @Test fun of_month_isTheAnchorsCalendarMonth() {
        val range = InsightsRange.of(at(2026, 2, 10, 8), Period.MONTH, zone)
        assertEquals(28, range.dayStarts.size)
        assertEquals(at(2026, 2, 1), range.start)
        assertEquals(at(2026, 2, 28), range.endInclusive)
    }

    @Test fun contains_isInclusiveOfBothEnds() {
        val range = InsightsRange.of(at(2026, 3, 15), Period.WEEK, zone)
        assertTrue(at(2026, 3, 9) in range)
        assertTrue(at(2026, 3, 15) in range)
        assertFalse(at(2026, 3, 8) in range)
        assertFalse(at(2026, 3, 16) in range)
    }

    @Test fun isCurrent_weekMeansAnchoredOnToday_monthMeansSameMonth() {
        val now = at(2026, 3, 15, 12)
        assertTrue(InsightsRange.isCurrent(at(2026, 3, 15, 1), Period.WEEK, now, zone))
        assertFalse(InsightsRange.isCurrent(at(2026, 3, 14), Period.WEEK, now, zone))
        assertTrue(InsightsRange.isCurrent(at(2026, 3, 1), Period.MONTH, now, zone))
        assertFalse(InsightsRange.isCurrent(at(2026, 2, 28), Period.MONTH, now, zone))
    }

    @Test fun shift_weekBackMovesSevenDays() {
        val now = at(2026, 3, 15, 12)
        assertEquals(at(2026, 3, 8), InsightsRange.shift(now, Period.WEEK, -1, now, zone))
    }

    @Test fun shift_weekForwardNeverPassesToday() {
        val now = at(2026, 3, 15, 12)
        assertEquals(at(2026, 3, 15), InsightsRange.shift(at(2026, 3, 12), Period.WEEK, 1, now, zone))
        // Already on today: nothing moves, and the anchor is handed back as it was (not re-snapped).
        assertEquals(now, InsightsRange.shift(now, Period.WEEK, 1, now, zone))
    }

    @Test fun shift_monthBackKeepsTheDayOfMonthWhereItCan() {
        val now = at(2026, 3, 31, 12)
        // February has no 31st; kotlinx clamps to the 28th.
        assertEquals(at(2026, 2, 28), InsightsRange.shift(now, Period.MONTH, -1, now, zone))
        assertEquals(at(2025, 12, 31), InsightsRange.shift(now, Period.MONTH, -3, now, zone))
    }

    @Test fun shift_monthForwardStopsAtTheCurrentMonth() {
        val now = at(2026, 3, 15, 12)
        // Already on the current month: stays put, anchor untouched.
        assertEquals(now, InsightsRange.shift(now, Period.MONTH, 1, now, zone))
        // From a past month, landing in the current month clamps the day to today.
        assertEquals(at(2026, 3, 15), InsightsRange.shift(at(2026, 2, 20), Period.MONTH, 1, now, zone))
        // From a past month, landing on an earlier day of the current month keeps that day.
        assertEquals(at(2026, 3, 10), InsightsRange.shift(at(2026, 2, 10), Period.MONTH, 1, now, zone))
    }

    @Test fun shift_returnsALocalMidnight() {
        val now = at(2026, 3, 15, 12)
        assertEquals(at(2026, 3, 8), InsightsRange.shift(at(2026, 3, 15, 23, 59), Period.WEEK, -1, now, zone))
    }
}
