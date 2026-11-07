package com.crazystudio.sportrecorder.ui.diet.record

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals

private const val MILLIS_PER_MINUTE = 60_000L

/** The Record card's time line: when the meal started and ended, as clock times, "12:30 – 13:30". */
class RecordTimeFormatTest {
    private val zone = TimeZone.UTC

    private fun at(hour: Int, minute: Int, inZone: TimeZone = zone): Long =
        LocalDateTime(2026, 11, 7, hour, minute).toInstant(inZone).toEpochMilliseconds()

    private fun range(startHour: Int, startMinute: Int, durationMinutes: Int): String {
        val start = at(startHour, startMinute)
        return formatClockRange(start, start + durationMinutes * MILLIS_PER_MINUTE, zone)
    }

    @Test fun aRangeWithinOneDay() {
        assertEquals("12:30 – 13:30", range(startHour = 12, startMinute = 30, durationMinutes = 60))
    }

    @Test fun aRangeAcrossMidnight() {
        assertEquals("23:30 – 00:30", range(startHour = 23, startMinute = 30, durationMinutes = 60))
    }

    @Test fun aNinetyMinuteRange() {
        assertEquals("12:00 – 13:30", range(startHour = 12, startMinute = 0, durationMinutes = 90))
    }

    /** The clock times are the given zone's — not UTC's, not the device's — so one meal reads differently. */
    @Test fun theClockTimesAreThoseOfTheGivenZone() {
        val taipei = TimeZone.of("Asia/Taipei")
        val start = at(hour = 12, minute = 30, inZone = taipei)
        val end = start + 60 * MILLIS_PER_MINUTE
        assertEquals("12:30 – 13:30", formatClockRange(start, end, taipei))
        assertEquals("04:30 – 05:30", formatClockRange(start, end, zone))
    }

    /** The times are wall-clock ones: an hour that spans the spring-forward jump reads as two on the clock. */
    @Test fun aRangeAcrossASpringForwardShowsWallClockTimes() {
        val newYork = TimeZone.of("America/New_York")
        // On 2026-03-08 the clocks jump from 02:00 to 03:00, so one hour after 01:30 is 03:30 on the wall.
        val start = LocalDateTime(2026, 3, 8, 1, 30).toInstant(newYork).toEpochMilliseconds()
        val end = start + 60 * MILLIS_PER_MINUTE
        assertEquals("01:30 – 03:30", formatClockRange(start, end, newYork))
    }
}
