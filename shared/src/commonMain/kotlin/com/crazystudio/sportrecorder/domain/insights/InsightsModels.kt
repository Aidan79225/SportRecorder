package com.crazystudio.sportrecorder.domain.insights

/** Time window the stats + photo wall + locations cards summarise. */
enum class Period { WEEK, MONTH }

/** Per-day fasting-adherence outcome shown on the calendar. */
enum class AdherenceState { ON_TARGET, OFF_TARGET, NO_DATA }

/** One cell of the month calendar. [dayStart] is local midnight epoch millis. */
data class DayCell(
    val dayStart: Long,
    val dayOfMonth: Int,
    val state: AdherenceState,
)

/**
 * Eating-pattern stats over the selected [Period].
 * [avgFirstMealMinutes]/[avgLastMealMinutes] are minutes-since-local-midnight, null when no data.
 */
data class InsightsStats(
    val mealCount: Int,
    val avgFirstMealMinutes: Int?,
    val avgLastMealMinutes: Int?,
    val lateNightDays: Int,
)

/** A place the user ate, grouped by rounded coordinates, with how many times. */
data class LocationCount(val lat: Double, val lng: Double, val count: Int)

/** Everything the Insights screen renders. */
data class InsightsResult(
    val calendarDays: List<DayCell>,
    val streak: Int,
    val stats: InsightsStats,
    val photoFileNames: List<String>,
    val locations: List<LocationCount>,
) {
    companion object {
        val EMPTY = InsightsResult(
            calendarDays = emptyList(),
            streak = 0,
            stats = InsightsStats(0, null, null, 0),
            photoFileNames = emptyList(),
            locations = emptyList(),
        )
    }
}
