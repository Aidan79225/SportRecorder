package com.crazystudio.sportrecorder.domain.venue

import com.crazystudio.sportrecorder.domain.model.EatRecord
import com.crazystudio.sportrecorder.domain.model.TasteRating
import com.crazystudio.sportrecorder.domain.model.Venue

/**
 * What one place has been for the user, repeated back as plain history: how many times, the last time, the dishes
 * they ate there and the faces they gave. No average, no score, no ranking — only what the diary already says.
 */
class VenueSummary private constructor(
    val venue: Venue,
    /** How many meals were recorded there. At least [MIN_VISITS]. */
    val visits: Int,
    /** Epoch millis of the newest meal there. */
    val lastVisit: Long,
    /** Up to [MAX_DISHES] dishes with how many meals had them, most often first, then most recently eaten. */
    val dishes: List<Pair<String, Int>>,
    /** Taste level to meals rated so, highest level first, only levels that occur. Unrated meals are not counted. */
    val tasteCounts: List<Pair<Int, Int>>,
) {
    companion object {
        const val MIN_VISITS = 2
        const val MAX_DISHES = 5

        /**
         * The summary of [textMatched] (the records matching the text [query], before any taste filter, so the
         * face spread stays meaningful), or null unless the user is plainly looking at one place: [query] is not
         * blank, there are at least [MIN_VISITS] records, and all of them share one venue. A single visit has
         * no history to repeat, and a search that mixes places is not about any one of them.
         */
        fun of(textMatched: List<EatRecord>, query: String): VenueSummary? {
            if (query.isBlank() || textMatched.size < MIN_VISITS) return null
            val venue = textMatched.first().venue ?: return null
            if (textMatched.any { it.venue?.id != venue.id }) return null
            return VenueSummary(
                venue = venue,
                visits = textMatched.size,
                lastVisit = textMatched.maxOf { it.time },
                dishes = topDishes(textMatched),
                tasteCounts = tasteCounts(textMatched),
            )
        }

        /** Dishes are the same dish whatever the case; the spelling shown is the most recent one. */
        private fun topDishes(records: List<EatRecord>): List<Pair<String, Int>> {
            val newestFirst = records.sortedByDescending { it.time }
            val byKey = LinkedHashMap<String, Pair<String, Int>>()
            for (record in newestFirst) {
                for (dish in record.dishes) {
                    val key = dish.lowercase()
                    val seen = byKey[key]
                    byKey[key] = (seen?.first ?: dish) to ((seen?.second ?: 0) + 1)
                }
            }
            // Insertion order is newest-first, and the sort is stable, so ties keep the more recent dish first.
            return byKey.values.sortedByDescending { it.second }.take(MAX_DISHES)
        }

        private fun tasteCounts(records: List<EatRecord>): List<Pair<Int, Int>> {
            val counts = records.mapNotNull { TasteRating.sanitize(it.tasteRating) }.groupingBy { it }.eachCount()
            return TasteRating.LEVELS.reversed().mapNotNull { level -> counts[level]?.let { level to it } }
        }
    }
}
