package com.crazystudio.sportrecorder.fake

import com.crazystudio.sportrecorder.domain.model.Venue
import com.crazystudio.sportrecorder.domain.repository.VenueRepository

/**
 * [venues], raced by another writer at the moment of a write: [beforeInsert] runs once, as the first
 * [findOrCreate] or [create] through this one begins, [beforeEdit] as the first [edit] does, and
 * [beforeMerge] as the first [merge] does — after every read the caller made, so a venue it checked may
 * be gone, or one it looked for and did not find may be there, by its own write. The glued-name repair at
 * app start is such a writer, in the background, while an editor is open.
 */
class RacedVenueWrites(
    private val venues: FakeVenueRepository,
    private var beforeInsert: (suspend FakeVenueRepository.() -> Unit)? = null,
    private var beforeEdit: (suspend FakeVenueRepository.() -> Unit)? = null,
    private var beforeMerge: (suspend FakeVenueRepository.() -> Unit)? = null,
) : VenueRepository by venues {

    override suspend fun findOrCreate(name: String, lat: Double?, lng: Double?, now: Long, address: String?): Venue {
        raceBeforeInsert()
        return venues.findOrCreate(name, lat, lng, now, address)
    }

    override suspend fun create(name: String, lat: Double?, lng: Double?, now: Long, address: String?): Venue {
        raceBeforeInsert()
        return venues.create(name, lat, lng, now, address)
    }

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

    private suspend fun raceBeforeInsert() {
        beforeInsert?.let { race ->
            beforeInsert = null
            venues.race()
        }
    }
}
