package com.crazystudio.sportrecorder.domain.insights

import com.crazystudio.sportrecorder.domain.model.DietSettings
import com.crazystudio.sportrecorder.domain.model.EatRecord
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * One meal the user once called tasty, resurfaced. [dayStart] is that meal's eating day, the same mapping
 * [OnThisDay] uses, so the day sheet the card opens agrees with it. The rest is what the card shows.
 */
data class TastyMemoryPick(
    val meal: EatRecord,
    val dayStart: Long,
    val photoFileName: String?,
    val venueName: String?,
    val dishes: List<String>,
    /** The meal's own taste level (4 or 5), repeated back as the face on the card. */
    val level: Int,
)

/**
 * 「還記得這一餐嗎？」 A meal the user rated well, at least [MIN_DAYS_AGO] days back, picked deterministically
 * from the local day so the card is the same all day and changes on the next one.
 *
 * Only the user's own good ratings ever surface: nothing is shown for low or unrated meals, nothing is ranked,
 * and nothing is said when no meal qualifies. A meal needs something to show (a photo, a dish, a venue name or a
 * note); a bare face alone would be a memory of nothing.
 */
object TastyMemory {
    const val MIN_LEVEL = 4
    const val MIN_DAYS_AGO = 30

    fun pick(
        records: List<EatRecord>,
        settings: DietSettings,
        now: Long,
        timeZone: TimeZone = TimeZone.currentSystemDefault(),
    ): TastyMemoryPick? {
        val eligible = records.filter { (it.tasteRating ?: 0) >= MIN_LEVEL && hasSomethingToShow(it) }
        if (eligible.isEmpty()) return null
        val eligibleIds = eligible.map { it.id }.toSet()
        val todayEpochDay = epochDay(Instant.fromEpochMilliseconds(now).toLocalDateTime(timeZone).date)
        // The day mapping comes from the full history: which eating window a meal falls in depends on its neighbours.
        val candidates = InsightsAggregator.mealsByEatingDay(records, settings, timeZone)
            .flatMap { (dayStart, meals) ->
                val dayEpoch = epochDay(Instant.fromEpochMilliseconds(dayStart).toLocalDateTime(timeZone).date)
                if (todayEpochDay - dayEpoch < MIN_DAYS_AGO) {
                    emptyList()
                } else {
                    meals.filter { it.id in eligibleIds }.map { dayStart to it }
                }
            }
            .sortedWith(compareBy({ it.second.time }, { it.second.id }))
        if (candidates.isEmpty()) return null
        val (dayStart, meal) = candidates[todayEpochDay.mod(candidates.size.toLong()).toInt()]
        return TastyMemoryPick(
            meal = meal,
            dayStart = dayStart,
            photoFileName = meal.photos.firstOrNull()?.fileName,
            venueName = meal.venue?.name?.takeIf { it.isNotBlank() },
            dishes = meal.dishes,
            level = meal.tasteRating ?: MIN_LEVEL,
        )
    }

    private fun epochDay(date: LocalDate): Long = date.toEpochDays().toLong()

    private fun hasSomethingToShow(meal: EatRecord): Boolean =
        meal.photos.isNotEmpty() ||
            meal.dishes.isNotEmpty() ||
            !meal.venue?.name.isNullOrBlank() ||
            !meal.note.isNullOrBlank()
}
