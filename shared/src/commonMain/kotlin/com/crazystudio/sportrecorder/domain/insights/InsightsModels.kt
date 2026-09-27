package com.crazystudio.sportrecorder.domain.insights

/**
 * Granularity of the one period control on the Insights screen. It scopes every card: the
 * calendar, the rhythm chart, the stats, the photos and the map all describe the same days.
 */
enum class Period { WEEK, MONTH }

/**
 * How one eating day's window compares with the user's eating-hours setting.
 *
 * Deliberately descriptive rather than evaluative (see
 * `docs/superpowers/specs/2026-09-21-insights-improvements-design.md`): the screen reflects a
 * rhythm back to the user, it does not grade the day.
 */
enum class DayWindowState { WITHIN_WINDOW, LONGER_WINDOW, NO_RECORD }

/** The consecutive local days a period covers. [start] / [endInclusive] are local midnights. */
data class DayRange(val start: Long, val endInclusive: Long, val dayStarts: List<Long>) {
    operator fun contains(dayStart: Long): Boolean = dayStart in start..endInclusive

    companion object {
        val EMPTY = DayRange(start = 0L, endInclusive = 0L, dayStarts = emptyList())
    }
}

/** One cell of the calendar. [dayStart] is local midnight epoch millis. */
data class DayCell(
    val dayStart: Long,
    val dayOfMonth: Int,
    val state: DayWindowState,
    val isToday: Boolean = false,
    /** A day of the current month that has not happened yet — drawn, but quietly. */
    val isFuture: Boolean = false,
)

/**
 * One eating day as a band on the rhythm chart: minutes from that day's local midnight to its
 * first and last meal. [lastMinutes] runs past 24 × 60 when the window crosses midnight — the
 * band simply keeps going, which is what a late night looks like. [mealCount] 0 means an empty
 * row that still holds its place.
 */
data class DayBand(
    val dayStart: Long,
    val dayOfMonth: Int,
    val firstMinutes: Int,
    val lastMinutes: Int,
    val mealCount: Int,
)

/**
 * Eating-pattern stats over the period.
 * [avgFirstMealMinutes]/[avgLastMealMinutes] are minutes since the eating day's local midnight
 * (the last meal can exceed 24 h, see [DayBand]); null when no data.
 * [avgWindowMinutes] is the mean first-to-last-meal span over eating days with at least two
 * meals, null when there is no such day.
 */
data class InsightsStats(
    val mealCount: Int,
    val daysWithRecords: Int,
    val avgFirstMealMinutes: Int?,
    val avgLastMealMinutes: Int?,
    val avgWindowMinutes: Int?,
) {
    companion object {
        val EMPTY = InsightsStats(
            mealCount = 0,
            daysWithRecords = 0,
            avgFirstMealMinutes = null,
            avgLastMealMinutes = null,
            avgWindowMinutes = null,
        )
    }
}

/** A place the user ate, grouped by rounded coordinates, with how many times. */
data class LocationCount(val lat: Double, val lng: Double, val count: Int)

/**
 * Neutral summary of the period the calendar is showing: how many days hold a record, and how
 * many of those stayed inside the eating window. A count, not a run — nothing to break, and
 * nothing to keep up (see the spec's 初衷對照: no pressure mechanics on this page).
 */
data class PeriodSummary(val recordedDays: Int, val withinWindowDays: Int) {
    companion object {
        val EMPTY = PeriodSummary(recordedDays = 0, withinWindowDays = 0)

        fun of(days: List<DayCell>): PeriodSummary = PeriodSummary(
            recordedDays = days.count { it.state != DayWindowState.NO_RECORD },
            withinWindowDays = days.count { it.state == DayWindowState.WITHIN_WINDOW },
        )
    }
}

/** Everything the Insights screen renders, all scoped to the same [range]. */
data class InsightsResult(
    val hasAnyRecords: Boolean,
    val range: DayRange,
    val calendarDays: List<DayCell>,
    val summary: PeriodSummary,
    /** True when the range holds today, so there is nothing further ahead to page to. */
    val isCurrentPeriod: Boolean,
    val stats: InsightsStats,
    val bands: List<DayBand>,
    val photoFileNames: List<String>,
    val locations: List<LocationCount>,
) {
    companion object {
        val EMPTY = InsightsResult(
            hasAnyRecords = false,
            range = DayRange.EMPTY,
            calendarDays = emptyList(),
            summary = PeriodSummary.EMPTY,
            isCurrentPeriod = true,
            stats = InsightsStats.EMPTY,
            bands = emptyList(),
            photoFileNames = emptyList(),
            locations = emptyList(),
        )
    }
}
