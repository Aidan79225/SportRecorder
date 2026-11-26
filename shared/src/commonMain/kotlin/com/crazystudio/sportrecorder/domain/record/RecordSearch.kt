package com.crazystudio.sportrecorder.domain.record

import com.crazystudio.sportrecorder.domain.model.EatRecord
import com.crazystudio.sportrecorder.domain.model.VenueName
import com.crazystudio.sportrecorder.domain.venue.RecordVenueFilter

private const val WORD_SEPARATOR = " "

// 臺 (U+81FA) and 台 (U+53F0) read as one character, yet the same place is written either way: an address
// may carry the first while a person types the second. Comparing folds the first onto the second.
private const val TAI_FORMAL = '臺'
private const val TAI_COMMON = '台'

/**
 * The Record tab's search: which records fit what the user is looking for, by words and by how the meal tasted.
 *
 * The list is only ever narrowed — never reordered, ranked or counted. The user asks a question of their own
 * diary and the answer is the records that fit.
 */
object RecordSearch {

    /**
     * The [records] that fit both questions: the text [query] and the taste [faces].
     *
     * Text: EVERY word of [query] must occur in "<venue name> <venue address> <dishes> <note>", case-insensitively
     * and in any order, so 「麥當勞 信義」 narrows two 麥當勞 branches down to the one on 信義路 and 「滷肉飯」
     * finds the meals that had it. The query is normalized first ([VenueName.normalize]): stray, doubled and
     * full-width (U+3000) spaces never hide a match. 臺 and 台 count as the same character on both sides.
     * A blank query puts no text condition on [records].
     *
     * Taste: when [faces] is non-empty only records rated with one of those levels remain; an unrated record
     * never matches a non-empty set. An empty set puts no condition on [records].
     *
     * The order of [records] is kept.
     */
    fun matching(records: List<EatRecord>, query: String, faces: Set<Int> = emptySet()): List<EatRecord> =
        byFaces(textMatching(records, query), faces)

    /**
     * The [records] that fit the text [query] alone, before any taste filter: the set the venue summary is
     * computed from, so its face spread stays meaningful while faces are chosen.
     */
    fun textMatching(records: List<EatRecord>, query: String): List<EatRecord> {
        val words = wordsOf(query)
        if (words.isEmpty()) return records
        return records.filter { record ->
            val text = fold(haystack(record))
            words.all { word -> text.contains(word, ignoreCase = true) }
        }
    }

    /**
     * Whether the Record tab offers its search field: when some record has a venue, a dish or a note, and for
     * as long as a query is typed or a face is chosen — so deleting the last searchable record mid-search never
     * takes the field (and the only way to clear it) away.
     */
    fun canSearch(records: List<EatRecord>, query: String, faces: Set<Int> = emptySet()): Boolean =
        query.isNotEmpty() || faces.isNotEmpty() || records.any { it.isSearchable() }

    /** Whether any of [records] is rated: the face chips are offered only then, since otherwise they match nothing. */
    fun hasRated(records: List<EatRecord>): Boolean = records.any { it.tasteRating != null }

    private fun byFaces(records: List<EatRecord>, faces: Set<Int>): List<EatRecord> =
        if (faces.isEmpty()) records else records.filter { it.tasteRating in faces }

    private fun EatRecord.isSearchable(): Boolean =
        venue != null || dishes.isNotEmpty() || !note.isNullOrBlank()

    private fun wordsOf(query: String): List<String> =
        fold(VenueName.normalize(query)).split(WORD_SEPARATOR).filter { it.isNotEmpty() }

    private fun haystack(record: EatRecord): String = listOfNotNull(
        record.venue?.let { RecordVenueFilter.queryFor(it) },
        record.dishes.joinToString(WORD_SEPARATOR).ifEmpty { null },
        record.note,
    ).joinToString(WORD_SEPARATOR)

    /** [text] ready to be compared, never to be shown: the one place 臺 and 台 are made the same character. */
    private fun fold(text: String): String = text.replace(TAI_FORMAL, TAI_COMMON)
}
