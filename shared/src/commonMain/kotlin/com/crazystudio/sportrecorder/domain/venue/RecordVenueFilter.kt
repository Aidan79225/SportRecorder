package com.crazystudio.sportrecorder.domain.venue

import com.crazystudio.sportrecorder.domain.model.EatRecord
import com.crazystudio.sportrecorder.domain.model.Venue
import com.crazystudio.sportrecorder.domain.model.VenueAddress
import com.crazystudio.sportrecorder.domain.model.VenueName

private const val WORD_SEPARATOR = " "

// 臺 (U+81FA) and 台 (U+53F0) read as one character, yet the same place is written either way: an address
// may carry the first while a person types the second. Comparing folds the first onto the second.
private const val TAI_FORMAL = '臺'
private const val TAI_COMMON = '台'

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
     * hide a match. 臺 and 台 count as the same character on both sides, so 「台北」 finds an address
     * written 「臺北市…」 and the reverse. A blank query keeps [records] as given; a record without a
     * venue never matches a non-blank one. The order of [records] is kept.
     */
    fun matching(records: List<EatRecord>, query: String): List<EatRecord> {
        val words = wordsOf(query)
        if (words.isEmpty()) return records
        return records.filter { record -> record.venue?.let { matchesEveryWord(it, words) } == true }
    }

    /**
     * What tapping a card's venue puts in the search field: "<name> <address>", or just the name when
     * there is no address ([VenueAddress.clean]: trimmed, blank is none), spelled as stored — nothing is
     * folded here. It is the text [matching] searches, so the venue always finds its own records, while a
     * branch that shares its name drops out unless its address holds the same words.
     */
    fun queryFor(venue: Venue): String = searchText(venue)

    /**
     * Whether the Record tab offers its search field: when some record has a venue, and for as long as a
     * query is typed — so deleting the last record with a venue mid-search never takes the field (and
     * the only way to clear the query) away.
     */
    fun canSearch(records: List<EatRecord>, query: String): Boolean =
        query.isNotEmpty() || records.any { it.venue != null }

    private fun wordsOf(query: String): List<String> =
        fold(VenueName.normalize(query)).split(WORD_SEPARATOR).filter { it.isNotEmpty() }

    private fun matchesEveryWord(venue: Venue, words: List<String>): Boolean {
        val text = fold(searchText(venue))
        return words.all { word -> text.contains(word, ignoreCase = true) }
    }

    private fun searchText(venue: Venue): String =
        listOfNotNull(venue.name, VenueAddress.clean(venue.address)).joinToString(WORD_SEPARATOR)

    /** [text] ready to be compared, never to be shown: the one place 臺 and 台 are made the same character. */
    private fun fold(text: String): String = text.replace(TAI_FORMAL, TAI_COMMON)
}
