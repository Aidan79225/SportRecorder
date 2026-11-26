package com.crazystudio.sportrecorder.domain.record

import com.crazystudio.sportrecorder.domain.model.EatRecord
import com.crazystudio.sportrecorder.domain.model.TasteRating
import com.crazystudio.sportrecorder.domain.model.Venue
import kotlin.test.Test
import kotlin.test.assertEquals

/** U+3000, the full-width (ideographic) space a CJK keyboard types between words. */
private val FULL_WIDTH_SPACE = Char(0x3000).toString()

/**
 * The Record tab's search only narrows the list the user already has: every word of the query must
 * occur in the venue's name or address, the dishes or the note, whatever the case and whichever of 臺 and
 * 台 is written; a chosen set of faces keeps only meals rated so; and the order never changes.
 */
class RecordSearchTest {

    private fun venue(id: Int, name: String, address: String? = null) =
        Venue(id = id, name = name, lat = null, lng = null, lastUsedAt = 0L, address = address)

    private fun record(id: Int, venue: Venue?, time: Long = id.toLong(), note: String? = null) =
        EatRecord(id = id, time = time, location = null, note = note, photos = emptyList(), venue = venue)

    private fun idsMatching(records: List<EatRecord>, query: String) =
        RecordSearch.matching(records, query).map { it.id }

    @Test fun blankQuery_keepsEveryRecord() {
        val records = listOf(
            record(3, venue(1, "麥當勞", address = "臺北市信義路")),
            record(2, venue = null, note = "在家吃"),
            record(1, venue(2, "Ichiran")),
        )

        for (blank in listOf("", "   ", FULL_WIDTH_SPACE, " $FULL_WIDTH_SPACE  $FULL_WIDTH_SPACE ")) {
            assertEquals(records, RecordSearch.matching(records, blank), "blank query of ${blank.length} chars")
        }
    }

    @Test fun matchesTheVenueNameIgnoringCase() {
        val starbucks = record(1, venue(1, "Starbucks 信義"))
        val ichiran = record(2, venue(2, "Ichiran"))
        val records = listOf(starbucks, ichiran)

        assertEquals(listOf(starbucks), RecordSearch.matching(records, "starbucks"))
        assertEquals(listOf(starbucks), RecordSearch.matching(records, "STARBUCKS"))
    }

    @Test fun matchesTheAddress() {
        // Neither name mentions the road, so only the address can be what matched.
        val onXinyiRoad = record(1, venue(1, "鼎泰豐", address = "臺北市大安區信義路二段194號"))
        val elsewhere = record(2, venue(2, "路易莎", address = "臺北市中山區南京東路"))

        assertEquals(listOf(onXinyiRoad), RecordSearch.matching(listOf(onXinyiRoad, elsewhere), "信義路"))
    }

    @Test fun everyWordMustMatch() {
        val xinyi = record(1, venue(1, "麥當勞", address = "臺北市信義路"))
        val zhongxiao = record(2, venue(2, "麥當勞", address = "臺北市忠孝東路"))
        val records = listOf(xinyi, zhongxiao)

        assertEquals(records, RecordSearch.matching(records, "麥當勞"))
        assertEquals(listOf(xinyi), RecordSearch.matching(records, "麥當勞 信義"))
        // The words may come in any order.
        assertEquals(listOf(xinyi), RecordSearch.matching(records, "信義 麥當勞"))
        assertEquals(emptyList(), RecordSearch.matching(records, "麥當勞 信義 忠孝"))
    }

    @Test fun aRecordWithoutAVenue_isFoundByItsNoteOrItsDishes_notByAnythingElse() {
        val byNote = record(1, venue = null, note = "麥當勞 信義")
        val byDish = record(3, venue = null).copy(dishes = listOf("滷肉飯", "燙青菜"))
        val withVenue = record(2, venue(1, "麥當勞", address = "臺北市信義路"))
        val records = listOf(byNote, withVenue, byDish)

        assertEquals(listOf(byNote, withVenue), RecordSearch.matching(records, "麥當勞"))
        assertEquals(listOf(byDish), RecordSearch.matching(records, "滷肉飯"))
        assertEquals(listOf(byDish), RecordSearch.matching(records, "青菜"))
        assertEquals(emptyList(), RecordSearch.matching(records, "拉麵"))
    }

    @Test fun wordsMayComeFromDifferentFields() {
        val ramen = record(1, venue(1, "Ichiran", address = "Shibuya"), note = "late night")
            .copy(dishes = listOf("Tonkotsu"))
        val other = record(2, venue(2, "Ichiran", address = "Shinjuku"))

        assertEquals(listOf(ramen), RecordSearch.matching(listOf(ramen, other), "ichiran tonkotsu night"))
        assertEquals(listOf(ramen), RecordSearch.matching(listOf(ramen, other), "SHIBUYA TONKOTSU"))
        assertEquals(emptyList(), RecordSearch.matching(listOf(ramen, other), "shinjuku tonkotsu"))
    }

    @Test fun aWordNeverMatchesAcrossTheSeparatorBetweenFields() {
        // "Ya" ends the venue and "Ra" starts the dish; the joined haystack must not be searched as one run of text.
        val rec = record(1, venue(1, "Ramen Ya")).copy(dishes = listOf("Ra men"))
        assertEquals(emptyList(), RecordSearch.matching(listOf(rec), "yara"))
    }

    @Test fun aBlankNoteAndNoDishesAddNothingToTheHaystack() {
        val rec = record(1, venue(1, "Ichiran"), note = "   ")
        assertEquals(emptyList(), RecordSearch.matching(listOf(rec), "null"))
        assertEquals(listOf(rec), RecordSearch.matching(listOf(rec), "ichiran"))
    }

    @Test fun taiwanVariantsAlsoMatchInDishesAndNotes() {
        val dish = record(1, venue = null).copy(dishes = listOf("臺南擔仔麵"))
        val note = record(2, venue = null, note = "台中太陽餅")

        assertEquals(listOf(dish), RecordSearch.matching(listOf(dish, note), "台南"))
        assertEquals(listOf(note), RecordSearch.matching(listOf(dish, note), "臺中"))
    }

    private fun rated(id: Int, level: Int?, name: String = "Shop $id") =
        record(id, venue(id, name)).copy(tasteRating = level)

    @Test fun tasteFilter_keepsOnlyRecordsRatedInTheSet() {
        val records = listOf(rated(1, 5), rated(2, 3), rated(3, 4), rated(4, 5))

        assertEquals(listOf(1, 4), RecordSearch.matching(records, "", setOf(5)).map { it.id })
        assertEquals(listOf(1, 3, 4), RecordSearch.matching(records, "", setOf(4, 5)).map { it.id })
        assertEquals(records, RecordSearch.matching(records, "", emptySet()))
    }

    @Test fun tasteFilter_neverMatchesAnUnratedRecord() {
        val records = listOf(rated(1, null), rated(2, 5))

        assertEquals(listOf(2), RecordSearch.matching(records, "", setOf(5)).map { it.id })
        assertEquals(emptyList(), RecordSearch.matching(listOf(rated(1, null)), "", TasteRating.LEVELS.toSet()))
    }

    @Test fun tasteFilter_combinesWithTheTextAndKeepsOrder() {
        val records = listOf(
            rated(3, 5, "Ramen A"),
            rated(1, 2, "Ramen B"),
            rated(2, 5, "Sushi C"),
            rated(4, 4, "Ramen D"),
        )

        assertEquals(listOf(3, 4), RecordSearch.matching(records, "ramen", setOf(4, 5)).map { it.id })
        // The text-only view ignores faces: it is what the venue summary reads.
        assertEquals(listOf(3, 1, 4), RecordSearch.textMatching(records, "ramen").map { it.id })
    }

    @Test fun hasRated_isTrueOnlyWhenSomeRecordIsRated() {
        assertEquals(false, RecordSearch.hasRated(emptyList()))
        assertEquals(false, RecordSearch.hasRated(listOf(rated(1, null))))
        assertEquals(true, RecordSearch.hasRated(listOf(rated(1, null), rated(2, 1))))
    }

    @Test fun canSearch_isTrueWhenARecordHasADishOrANote() {
        assertEquals(true, RecordSearch.canSearch(listOf(record(1, venue = null, note = "lunch")), ""))
        assertEquals(true, RecordSearch.canSearch(listOf(record(1, venue = null).copy(dishes = listOf("Rice"))), ""))
        assertEquals(false, RecordSearch.canSearch(listOf(record(1, venue = null, note = "  ")), ""))
    }

    @Test fun canSearch_staysTrueWhileAFaceIsChosen() {
        assertEquals(true, RecordSearch.canSearch(emptyList(), "", setOf(5)))
    }

    @Test fun aVenueWithoutAnAddress_matchesByNameOnly() {
        val noAddress = record(1, venue(1, "Ichiran", address = null))
        val blankAddress = record(2, venue(2, "Ichiran Shibuya", address = "  "))
        val records = listOf(noAddress, blankAddress)

        assertEquals(records, RecordSearch.matching(records, "ichiran"))
        // A missing address stands in for nothing: not even the word "null".
        assertEquals(emptyList(), RecordSearch.matching(records, "null"))
        assertEquals(emptyList(), RecordSearch.matching(records, "ichiran 信義"))
    }

    @Test fun theOrderOfTheInputIsKept() {
        val mcdonalds = venue(1, "麥當勞", address = "臺北市信義路")
        // Neither by time, oldest or newest first, nor by id: only the input order explains the result.
        val records = listOf(
            record(3, mcdonalds, time = 200L),
            record(4, venue(2, "Ichiran"), time = 400L),
            record(1, mcdonalds, time = 100L),
            record(2, mcdonalds, time = 300L),
        )

        assertEquals(listOf(3, 1, 2), idsMatching(records, "麥當勞"))
    }

    @Test fun fullWidthSpacesSeparateWords() {
        val xinyi = record(1, venue(1, "麥當勞", address = "臺北市信義路"))
        val zhongxiao = record(2, venue(2, "麥當勞", address = "臺北市忠孝東路"))
        val records = listOf(xinyi, zhongxiao)

        val withPlainSpace = RecordSearch.matching(records, "麥當勞 信義")
        val withFullWidthSpace = RecordSearch.matching(records, "麥當勞${FULL_WIDTH_SPACE}信義")

        assertEquals(listOf(xinyi), withPlainSpace)
        assertEquals(withPlainSpace, withFullWidthSpace)
        // Padding and doubled spaces, of either width, never turn into an empty word that matches nothing.
        val padded = "${FULL_WIDTH_SPACE}麥當勞 ${FULL_WIDTH_SPACE}信義  "
        assertEquals(listOf(xinyi), RecordSearch.matching(records, padded))
    }

    @Test fun taiwanVariantsMatchEachOther() {
        // 臺 (U+81FA) and 台 (U+53F0) are two characters that read as one: an address may be written with
        // either, and people type either. Each of these records spells it differently.
        val addressWithTai = record(1, venue(1, "鼎泰豐", address = "臺北市大安區信義路二段194號"))
        val addressWithVariant = record(2, venue(2, "路易莎", address = "台北市中山區南京東路"))
        val nameWithTai = record(3, venue(3, "臺灣小吃"))
        val unrelated = record(4, venue(4, "Ichiran"))
        val records = listOf(addressWithTai, addressWithVariant, nameWithTai, unrelated)

        // Whichever way it is typed, an address is found whichever way it was written.
        assertEquals(listOf(addressWithTai, addressWithVariant), RecordSearch.matching(records, "台北"))
        assertEquals(listOf(addressWithTai, addressWithVariant), RecordSearch.matching(records, "臺北"))
        // So is a name.
        assertEquals(listOf(nameWithTai), RecordSearch.matching(records, "台灣"))
        assertEquals(listOf(nameWithTai), RecordSearch.matching(records, "臺灣"))
    }

    @Test fun canSearch_isFalseWhenNoRecordHasAVenueAndNothingIsTyped() {
        assertEquals(false, RecordSearch.canSearch(listOf(record(1, venue = null)), ""))
        assertEquals(false, RecordSearch.canSearch(emptyList(), ""))
    }

    @Test fun canSearch_isTrueWhenARecordHasAVenue() {
        val records = listOf(record(2, venue = null), record(1, venue(1, "Ichiran")))
        assertEquals(true, RecordSearch.canSearch(records, ""))
    }

    @Test fun canSearch_staysTrueWhileAQueryIsTyped_evenIfNoRecordHasAVenueAnymore() {
        assertEquals(true, RecordSearch.canSearch(listOf(record(1, venue = null)), "shop"))
        assertEquals(true, RecordSearch.canSearch(emptyList(), "shop"))
    }

    /**
     * A rating alone neither changes what a text query matches nor reorders anything: a rated and an unrated
     * copy of the same list match the same records in the same order for every query, and the words of a
     * rating are not something to search for.
     */
    @Test fun aRatingChangesNeitherWhatMatchesNorTheOrder() {
        val mcdonalds = venue(1, "麥當勞", address = "臺北市信義路")
        val unrated = listOf(
            record(3, mcdonalds, time = 200L),
            record(4, venue(2, "Ichiran"), time = 400L),
            record(1, mcdonalds, time = 100L),
        )
        val variants = TasteRating.LEVELS.map { level -> unrated.map { it.copy(tasteRating = level) } } +
            listOf(unrated.mapIndexed { index, record -> record.copy(tasteRating = TasteRating.LEVELS[index]) })

        for (query in listOf("", "麥當勞", "ichiran", "信義", "麥當勞 信義")) {
            for (rated in variants) {
                assertEquals(idsMatching(unrated, query), idsMatching(rated, query), "query '$query'")
            }
        }
        for (query in listOf("好吃", "Tasty", "不好吃", "5", "好吃程度")) {
            for (rated in variants) {
                assertEquals(emptyList(), idsMatching(rated, query), "query '$query'")
            }
        }
    }
}
