package com.crazystudio.sportrecorder.fake

import com.crazystudio.sportrecorder.domain.model.Venue
import com.crazystudio.sportrecorder.domain.repository.VenueRepository

/**
 * [venues], raced by another writer at the moment of a write: [beforeEdit] runs once, as the first
 * [edit] through this one begins, and [beforeMerge] as the first [merge] does — after every read
 * the caller made, so a venue it checked may be gone by its own write. The glued-name repair at app
 * start is such a writer, in the background, while an editor is open.
 */
class RacedVenueWrites(
    private val venues: FakeVenueRepository,
    private var beforeEdit: (suspend FakeVenueRepository.() -> Unit)? = null,
    private var beforeMerge: (suspend FakeVenueRepository.() -> Unit)? = null,
) : VenueRepository by venues {

    override suspend fun edit(venueId: Int, name: String, address: String?, now: Long): Venue? {
        beforeEdit?.let { race ->
            beforeEdit = null
            venues.race()
        }
        return venues.edit(venueId, name, address, now)
    }

    override suspend fun merge(fromId: Int, intoId: Int, now: Long): Venue? {
        beforeMerge?.let { race ->
            beforeMerge = null
            venues.race()
        }
        return venues.merge(fromId, intoId, now)
    }
}
