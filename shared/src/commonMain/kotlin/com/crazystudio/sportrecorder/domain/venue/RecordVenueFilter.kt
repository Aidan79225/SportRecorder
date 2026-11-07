package com.crazystudio.sportrecorder.domain.venue

import com.crazystudio.sportrecorder.domain.model.EatRecord
import com.crazystudio.sportrecorder.domain.model.Venue
import com.crazystudio.sportrecorder.domain.model.VenueName

private const val WORD_SEPARATOR = " "

/**
 * The Record tab's venue search: which records came from the place the user is looking for.
 *
 * Only the venue's name and address are read, and the list is only ever narrowed — never reordered,
 * ranked or counted. The user asks a question of their own diary and the answer is the records that fit.
 */
object RecordVenueFilter {

    /**
     * The [records] whose venue holds EVERY word of [query] in "<name> <address>", case-insensitively
     * and in any order, so 「麥當勞 信義」 narrows two 麥當勞 branches down to the one on 信義路. The query
     * is normalized first ([VenueName.normalize]): stray, doubled and full-width (U+3000) spaces never
     * hide a match. A blank query keeps [records] as given; a record without a venue never matches a
     * non-blank one. The order of [records] is kept.
     */
    fun matching(records: List<EatRecord>, query: String): List<EatRecord> {
        val words = wordsOf(query)
        if (words.isEmpty()) return records
        return records.filter { record -> record.venue?.let { matchesEveryWord(it, words) } == true }
    }

    /**
     * What tapping a card's venue puts in the search field: "<name> <address>", or just the name when
     * there is no (or a blank) address. It is the very text [matching] reads, so the venue always finds
     * its own records, while a branch that shares its name drops out unless its address holds the same
     * words.
     */
    fun queryFor(venue: Venue): String = searchText(venue)

    private fun wordsOf(query: String): List<String> =
        VenueName.normalize(query).split(WORD_SEPARATOR).filter { it.isNotEmpty() }

    private fun matchesEveryWord(venue: Venue, words: List<String>): Boolean {
        val text = searchText(venue)
        return words.all { word -> text.contains(word, ignoreCase = true) }
    }

    private fun searchText(venue: Venue): String =
        listOfNotNull(venue.name, venue.address?.takeIf { it.isNotBlank() }).joinToString(WORD_SEPARATOR)
}
