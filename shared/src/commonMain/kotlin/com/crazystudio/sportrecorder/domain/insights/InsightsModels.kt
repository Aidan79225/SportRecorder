package com.crazystudio.sportrecorder.domain.insights

/** Time window the stats + photo wall + locations cards summarise. */
enum class Period { WEEK, MONTH }

/**
 * How one calendar day's eating window compares with the user's eating-hours setting.
 *
 * Deliberately descriptive rather than evaluative (see
 * `docs/superpowers/specs/2026-09-21-insights-improvements-design.md`): the screen reflects a
 * rhythm back to the user, it does not grade the day.
 */
enum class DayWindowState { WITHIN_WINDOW, LONGER_WINDOW, NO_RECORD }

/** One cell of the month calendar. [dayStart] is local midnight epoch millis. */
data class DayCell(
    val dayStart: Long,
    val dayOfMonth: Int,
    val state: DayWindowState,
    val isToday: Boolean = false,
)

/**
 * Eating-pattern stats over the selected [Period].
 * [avgFirstMealMinutes]/[avgLastMealMinutes] are minutes-since-local-midnight, null when no data.
 * [avgWindowMinutes] is the mean first-to-last-meal span over days with at least two meals,
 * null when there is no such day.
 */
data class InsightsStats(
    val mealCount: Int,
    val daysWithRecords: Int,
    val avgFirstMealMinutes: Int?,
    val avgLastMealMinutes: Int?,
    val avgWindowMinutes: Int?,
    val lateHourDays: Int,
)

/** A place the user ate, grouped by rounded coordinates, with how many times. */
data class LocationCount(val lat: Double, val lng: Double, val count: Int)

/**
 * Neutral summary of the month the calendar is showing: how many days hold a record, and how
 * many of those stayed inside the eating window. A count, not a streak — nothing to break.
 */
data class MonthSummary(val recordedDays: Int, val withinWindowDays: Int) {
    companion object {
        val EMPTY = MonthSummary(recordedDays = 0, withinWindowDays = 0)

        fun of(days: List<DayCell>): MonthSummary = MonthSummary(
            recordedDays = days.count { it.state != DayWindowState.NO_RECORD },
            withinWindowDays = days.count { it.state == DayWindowState.WITHIN_WINDOW },
        )
    }
}

/** Everything the Insights screen renders. */
data class InsightsResult(
    val hasAnyRecords: Boolean,
    val calendarDays: List<DayCell>,
    val monthSummary: MonthSummary,
    val isAnchorCurrentMonth: Boolean,
    val streak: Int,
    val periodStart: Long,
    val periodEnd: Long,
    val stats: InsightsStats,
    val photoFileNames: List<String>,
    val locations: List<LocationCount>,
) {
    companion object {
        val EMPTY = InsightsResult(
            hasAnyRecords = false,
            calendarDays = emptyList(),
            monthSummary = MonthSummary.EMPTY,
            isAnchorCurrentMonth = true,
            streak = 0,
            periodStart = 0L,
            periodEnd = 0L,
            stats = InsightsStats(
                mealCount = 0,
                daysWithRecords = 0,
                avgFirstMealMinutes = null,
                avgLastMealMinutes = null,
                avgWindowMinutes = null,
                lateHourDays = 0,
            ),
            photoFileNames = emptyList(),
            locations = emptyList(),
        )
    }
}
