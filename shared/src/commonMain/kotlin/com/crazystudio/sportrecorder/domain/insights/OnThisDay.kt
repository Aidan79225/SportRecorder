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
    /** The day's first photo, or null when nothing was photographed. */
    val photoFileName: String?,
    /** The day's first non-blank note, or null. */
    val note: String?,
)

/**
 * 「去年的今天」— the same calendar day, [yearsAgo] years back.
 *
 * Returns null whenever that day holds nothing. That is the whole discipline of this feature: a
 * memory when there is one, and silence otherwise. 「去年今天你沒記錄」 would be a reproach, not
 * a memory, and this never compares the two years to each other.
 *
 * Days are the same **eating days** the rest of Insights uses
 * ([InsightsAggregator.mealsByEatingDay]), so a 00:30 snack belongs to the dinner it followed here
 * too — the card and the sheet it opens never disagree about which day a meal was.
 */
object OnThisDay {

    fun find(
        records: List<EatRecord>,
        settings: DietSettings,
        now: Long,
        yearsAgo: Int = 1,
        timeZone: TimeZone = TimeZone.currentSystemDefault(),
    ): OnThisDayMemory? {
        if (yearsAgo < 1) return null
        // Feb 29 has no counterpart in a common year; kotlinx-datetime clamps to Feb 28, the
        // closest thing to "the same day" that exists.
        val then = Instant.fromEpochMilliseconds(now).toLocalDateTime(timeZone).date
            .minus(yearsAgo, DateTimeUnit.YEAR)
        val dayStart = then.atStartOfDayIn(timeZone).toEpochMilliseconds()
        val meals = InsightsAggregator.mealsByEatingDay(records, settings, timeZone)[dayStart]
        if (meals.isNullOrEmpty()) return null
        return OnThisDayMemory(
            dayStart = dayStart,
            photoFileName = meals.firstNotNullOfOrNull { it.photos.firstOrNull()?.fileName },
            note = meals.firstNotNullOfOrNull { meal -> meal.note?.takeIf { it.isNotBlank() } },
        )
    }
}
