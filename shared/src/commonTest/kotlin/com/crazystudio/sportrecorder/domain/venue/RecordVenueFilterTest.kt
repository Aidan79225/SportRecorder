package com.crazystudio.sportrecorder.domain.venue

import com.crazystudio.sportrecorder.domain.model.EatRecord
import com.crazystudio.sportrecorder.domain.model.Venue
import com.crazystudio.sportrecorder.domain.record.RecordSearch
import kotlin.test.Test
import kotlin.test.assertEquals

/** U+3000, the full-width (ideographic) space a CJK keyboard types between words. */
private val FULL_WIDTH_SPACE = Char(0x3000).toString()

/** Tapping a record's venue searches for exactly that place: its name, then its address as stored. */
class RecordVenueFilterTest {

    private fun venue(id: Int, name: String, address: String? = null) =
        Venue(id = id, name = name, lat = null, lng = null, lastUsedAt = 0L, address = address)

    private fun record(id: Int, venue: Venue?, time: Long = id.toLong(), note: String? = null) =
        EatRecord(id = id, time = time, location = null, note = note, photos = emptyList(), venue = venue)

    private fun idsMatching(records: List<EatRecord>, query: String) =
        RecordSearch.matching(records, query).map { it.id }

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
}
