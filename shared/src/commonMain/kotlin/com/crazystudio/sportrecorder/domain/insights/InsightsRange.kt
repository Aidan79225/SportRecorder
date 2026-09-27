package com.crazystudio.sportrecorder.domain.insights

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.number
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * Turns the screen's one period control (an anchor day + a [Period]) into the days it covers,
 * and pages it. Week = the seven days ending on the anchor; Month = the anchor's calendar month.
 * Paging never goes past today: there is nothing there yet.
 */
object InsightsRange {

    const val WEEK_DAYS = 7

    fun of(anchor: Long, period: Period, timeZone: TimeZone = TimeZone.currentSystemDefault()): DayRange {
        val date = localDate(anchor, timeZone)
        val first = when (period) {
            Period.WEEK -> date.minus(WEEK_DAYS - 1, DateTimeUnit.DAY)
            Period.MONTH -> LocalDate(date.year, date.month, 1)
        }
        val count = when (period) {
            Period.WEEK -> WEEK_DAYS
            Period.MONTH -> first.plus(1, DateTimeUnit.MONTH).minus(1, DateTimeUnit.DAY).day
        }
        val dayStarts = (0 until count).map { offset ->
            first.plus(offset, DateTimeUnit.DAY).atStartOfDayIn(timeZone).toEpochMilliseconds()
        }
        return DayRange(start = dayStarts.first(), endInclusive = dayStarts.last(), dayStarts = dayStarts)
    }

    /** Whether the range anchored at [anchor] holds today — i.e. there is nothing later to page to. */
    fun isCurrent(
        anchor: Long,
        period: Period,
        now: Long,
        timeZone: TimeZone = TimeZone.currentSystemDefault(),
    ): Boolean {
        val date = localDate(anchor, timeZone)
        val today = localDate(now, timeZone)
        return when (period) {
            Period.WEEK -> date == today
            Period.MONTH -> date.year == today.year && date.month == today.month
        }
    }

    /**
     * The anchor after paging [steps] periods (negative = earlier). Forward paging clamps at
     * today: a week never ends after today, and a month never leaves the current one.
     */
    fun shift(
        anchor: Long,
        period: Period,
        steps: Int,
        now: Long,
        timeZone: TimeZone = TimeZone.currentSystemDefault(),
    ): Long {
        val date = localDate(anchor, timeZone)
        val today = localDate(now, timeZone)
        val shifted = when (period) {
            Period.WEEK -> date.plus(steps * WEEK_DAYS, DateTimeUnit.DAY)
            Period.MONTH -> date.plus(steps, DateTimeUnit.MONTH)
        }
        val monthIsAhead = shifted.year > today.year ||
            (shifted.year == today.year && shifted.month.number > today.month.number)
        val result = when {
            period == Period.MONTH && monthIsAhead -> date
            shifted > today -> today
            else -> shifted
        }
        return result.atStartOfDayIn(timeZone).toEpochMilliseconds()
    }

    private fun localDate(millis: Long, timeZone: TimeZone): LocalDate =
        Instant.fromEpochMilliseconds(millis).toLocalDateTime(timeZone).date
}
