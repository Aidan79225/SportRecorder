package com.crazystudio.sportrecorder.domain.venue

import com.crazystudio.sportrecorder.domain.model.Venue
import com.crazystudio.sportrecorder.domain.model.VenueAddress

/**
 * What tapping a record's venue puts in the Record tab's search field. The matching itself lives in
 * `RecordSearch`, which reads this same text as the venue's part of what it searches.
 */
object RecordVenueFilter {

    /**
     * What tapping a card's venue puts in the search field: "<name> <address>", or just the name when
     * there is no address ([VenueAddress.clean]: trimmed, blank is none), spelled as stored — nothing is
     * folded here. It is the text `RecordSearch` searches for the venue, so the venue always finds its own
     * records, while a branch that shares its name drops out unless its address holds the same words.
     */
    fun queryFor(venue: Venue): String =
        listOfNotNull(venue.name, VenueAddress.clean(venue.address)).joinToString(" ")
}
