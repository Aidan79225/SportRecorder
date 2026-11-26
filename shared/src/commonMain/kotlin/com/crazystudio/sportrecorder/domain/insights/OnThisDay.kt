package com.crazystudio.sportrecorder.domain.insights

import com.crazystudio.sportrecorder.domain.model.DietSettings
import com.crazystudio.sportrecorder.domain.model.EatRecord
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * One past day worth revisiting. [dayStart] is that eating day's local midnight — exactly what
 * `DayRecordsSheet` opens with, so tapping the card shows the whole day with nothing new wired up.
 */
data class OnThisDayMemory(
    val dayStart: Long,
    /** How many years back that day is, 1 … [OnThisDay.MAX_YEARS_BACK]: drives the card's title. */
    val yearsAgo: Int,
    /** The day's first photo, or null when nothing was photographed. */
    val photoFileName: String?,
    /** The day's first non-blank note, or null. */
    val note: String?,
)

/**
 * 「去年的今天」— the same calendar day one year back, or failing that the nearest of the years before it
 * (up to [MAX_YEARS_BACK]) that holds a day.
 *
 * Returns null whenever none of those days holds anything. That is the whole discipline of this feature: a
 * memory when there is one, and silence otherwise. 「去年今天你沒記錄」 would be a reproach, not
 * a memory, and this never compares the two years to each other.
 *
 * Days are the same **eating days** the rest of Insights uses
 * ([InsightsAggregator.mealsByEatingDay]), so a 00:30 snack belongs to the dinner it followed here
 * too — the card and the sheet it opens never disagree about which day a meal was.
 */
object OnThisDay {

    const val MAX_YEARS_BACK = 5

    fun find(
        records: List<EatRecord>,
        settings: DietSettings,
        now: Long,
        timeZone: TimeZone = TimeZone.currentSystemDefault(),
    ): OnThisDayMemory? {
        val today = Instant.fromEpochMilliseconds(now).toLocalDateTime(timeZone).date
        val byDay = InsightsAggregator.mealsByEatingDay(records, settings, timeZone)
        for (yearsAgo in 1..MAX_YEARS_BACK) {
            // Feb 29 has no counterpart in a common year; kotlinx-datetime clamps to Feb 28, the
            // closest thing to "the same day" that exists.
            val dayStart = today.minus(yearsAgo, DateTimeUnit.YEAR).atStartOfDayIn(timeZone).toEpochMilliseconds()
            val meals = byDay[dayStart]
            if (meals.isNullOrEmpty()) continue
            return OnThisDayMemory(
                dayStart = dayStart,
                yearsAgo = yearsAgo,
                photoFileName = meals.firstNotNullOfOrNull { it.photos.firstOrNull()?.fileName },
                note = meals.firstNotNullOfOrNull { meal -> meal.note?.takeIf { it.isNotBlank() } },
            )
        }
        return null
    }
}
