package com.crazystudio.sportrecorder.domain.venue

import com.crazystudio.sportrecorder.domain.model.EatRecord
import com.crazystudio.sportrecorder.domain.model.Venue
import kotlin.test.Test
import kotlin.test.assertEquals

/** U+3000, the full-width (ideographic) space a CJK keyboard types between words. */
private val FULL_WIDTH_SPACE = Char(0x3000).toString()

/**
 * The Record tab's search only narrows the list the user already has: every word of the query must
 * occur in the venue's name or address, whatever the case and whichever of 臺 and 台 is written, and
 * the order never changes.
 */
class RecordVenueFilterTest {

    private fun venue(id: Int, name: String, address: String? = null) =
        Venue(id = id, name = name, lat = null, lng = null, lastUsedAt = 0L, address = address)

    private fun record(id: Int, venue: Venue?, time: Long = id.toLong(), note: String? = null) =
        EatRecord(id = id, time = time, location = null, note = note, photos = emptyList(), venue = venue)

    private fun idsMatching(records: List<EatRecord>, query: String) =
        RecordVenueFilter.matching(records, query).map { it.id }

    @Test fun blankQuery_keepsEveryRecord() {
        val records = listOf(
            record(3, venue(1, "麥當勞", address = "臺北市信義路")),
            record(2, venue = null, note = "在家吃"),
            record(1, venue(2, "Ichiran")),
        )

        for (blank in listOf("", "   ", FULL_WIDTH_SPACE, " $FULL_WIDTH_SPACE  $FULL_WIDTH_SPACE ")) {
            assertEquals(records, RecordVenueFilter.matching(records, blank), "blank query of ${blank.length} chars")
        }
    }

    @Test fun matchesTheVenueNameIgnoringCase() {
        val starbucks = record(1, venue(1, "Starbucks 信義"))
        val ichiran = record(2, venue(2, "Ichiran"))
        val records = listOf(starbucks, ichiran)

        assertEquals(listOf(starbucks), RecordVenueFilter.matching(records, "starbucks"))
        assertEquals(listOf(starbucks), RecordVenueFilter.matching(records, "STARBUCKS"))
    }

    @Test fun matchesTheAddress() {
        // Neither name mentions the road, so only the address can be what matched.
        val onXinyiRoad = record(1, venue(1, "鼎泰豐", address = "臺北市大安區信義路二段194號"))
        val elsewhere = record(2, venue(2, "路易莎", address = "臺北市中山區南京東路"))

        assertEquals(listOf(onXinyiRoad), RecordVenueFilter.matching(listOf(onXinyiRoad, elsewhere), "信義路"))
    }

    @Test fun everyWordMustMatch() {
        val xinyi = record(1, venue(1, "麥當勞", address = "臺北市信義路"))
        val zhongxiao = record(2, venue(2, "麥當勞", address = "臺北市忠孝東路"))
        val records = listOf(xinyi, zhongxiao)

        assertEquals(records, RecordVenueFilter.matching(records, "麥當勞"))
        assertEquals(listOf(xinyi), RecordVenueFilter.matching(records, "麥當勞 信義"))
        // The words may come in any order.
        assertEquals(listOf(xinyi), RecordVenueFilter.matching(records, "信義 麥當勞"))
        assertEquals(emptyList(), RecordVenueFilter.matching(records, "麥當勞 信義 忠孝"))
    }

    @Test fun aRecordWithoutAVenue_neverMatchesANonBlankQuery() {
        // Its note says the very words searched for, but only the venue is ever searched.
        val withoutVenue = record(1, venue = null, note = "麥當勞 信義")
        val withVenue = record(2, venue(1, "麥當勞", address = "臺北市信義路"))

        assertEquals(listOf(withVenue), RecordVenueFilter.matching(listOf(withoutVenue, withVenue), "麥當勞"))
        assertEquals(emptyList(), RecordVenueFilter.matching(listOf(withoutVenue), "麥當勞 信義"))
    }

    @Test fun aVenueWithoutAnAddress_matchesByNameOnly() {
        val noAddress = record(1, venue(1, "Ichiran", address = null))
        val blankAddress = record(2, venue(2, "Ichiran Shibuya", address = "  "))
        val records = listOf(noAddress, blankAddress)

        assertEquals(records, RecordVenueFilter.matching(records, "ichiran"))
        // A missing address stands in for nothing: not even the word "null".
        assertEquals(emptyList(), RecordVenueFilter.matching(records, "null"))
        assertEquals(emptyList(), RecordVenueFilter.matching(records, "ichiran 信義"))
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

        val withPlainSpace = RecordVenueFilter.matching(records, "麥當勞 信義")
        val withFullWidthSpace = RecordVenueFilter.matching(records, "麥當勞${FULL_WIDTH_SPACE}信義")

        assertEquals(listOf(xinyi), withPlainSpace)
        assertEquals(withPlainSpace, withFullWidthSpace)
        // Padding and doubled spaces, of either width, never turn into an empty word that matches nothing.
        val padded = "${FULL_WIDTH_SPACE}麥當勞 ${FULL_WIDTH_SPACE}信義  "
        assertEquals(listOf(xinyi), RecordVenueFilter.matching(records, padded))
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
        assertEquals(listOf(addressWithTai, addressWithVariant), RecordVenueFilter.matching(records, "台北"))
        assertEquals(listOf(addressWithTai, addressWithVariant), RecordVenueFilter.matching(records, "臺北"))
        // So is a name.
        assertEquals(listOf(nameWithTai), RecordVenueFilter.matching(records, "台灣"))
        assertEquals(listOf(nameWithTai), RecordVenueFilter.matching(records, "臺灣"))
    }

    @Test fun queryFor_isTheNameThenTheAddress() {
        val withAddress = venue(1, "麥當勞", address = "臺北市大安區信義路二段194號")

        assertEquals("麥當勞 臺北市大安區信義路二段194號", RecordVenueFilter.queryFor(withAddress))
        for (blank in listOf(null, "", "   ", FULL_WIDTH_SPACE)) {
            assertEquals("Ichiran", RecordVenueFilter.queryFor(venue(2, "Ichiran", address = blank)), "address $blank")
        }
    }

    @Test fun queryFor_keepsTheCharactersAsStored() {
        // Only matching treats 臺 and 台 alike. What goes into the search field is the venue's own text,
        // each part spelled the way it was stored, and it still finds the venue's records.
        val mixed = venue(1, "臺灣小吃", address = "台北市中山區南京東路")
        val records = listOf(record(1, mixed), record(2, venue(2, "Ichiran")))

        assertEquals("臺灣小吃 台北市中山區南京東路", RecordVenueFilter.queryFor(mixed))
        assertEquals(listOf(1), idsMatching(records, RecordVenueFilter.queryFor(mixed)))
    }

    @Test fun queryFor_matchesExactlyThatVenuesRecords_evenWhenABranchSharesItsName() {
        val xinyi = venue(1, "麥當勞", address = "臺北市大安區信義路二段194號")
        val zhongxiao = venue(2, "麥當勞", address = "臺北市大安區忠孝東路四段1號")
        val records = listOf(
            record(4, xinyi),
            record(3, zhongxiao),
            record(2, xinyi),
            record(1, venue(3, "Ichiran")),
        )

        // Typing just the shared name would show both branches; the name-then-address query tells them apart.
        assertEquals(listOf(4, 3, 2), idsMatching(records, "麥當勞"))
        assertEquals(listOf(4, 2), idsMatching(records, RecordVenueFilter.queryFor(xinyi)))
        assertEquals(listOf(3), idsMatching(records, RecordVenueFilter.queryFor(zhongxiao)))
    }

    @Test fun anAddressWithPaddingLeavesNoDoubleSpaceInTheQuery() {
        assertEquals("Ichiran X", RecordVenueFilter.queryFor(venue(1, "Ichiran", address = "  X  ")))
    }

    @Test fun canSearch_isFalseWhenNoRecordHasAVenueAndNothingIsTyped() {
        assertEquals(false, RecordVenueFilter.canSearch(listOf(record(1, venue = null)), ""))
        assertEquals(false, RecordVenueFilter.canSearch(emptyList(), ""))
    }

    @Test fun canSearch_isTrueWhenARecordHasAVenue() {
        val records = listOf(record(2, venue = null), record(1, venue(1, "Ichiran")))
        assertEquals(true, RecordVenueFilter.canSearch(records, ""))
    }

    @Test fun canSearch_staysTrueWhileAQueryIsTyped_evenIfNoRecordHasAVenueAnymore() {
        assertEquals(true, RecordVenueFilter.canSearch(listOf(record(1, venue = null)), "shop"))
        assertEquals(true, RecordVenueFilter.canSearch(emptyList(), "shop"))
    }
}
