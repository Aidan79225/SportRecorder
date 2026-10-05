package com.crazystudio.sportrecorder.domain.repository

import com.crazystudio.sportrecorder.domain.model.Venue
import kotlinx.coroutines.flow.Flow

interface VenueRepository {
    /** Every venue, most recently used first — the order the picker shows. */
    fun observeAll(): Flow<List<Venue>>

    /**
     * The venue called [name] (compared case-insensitively), created if it is new. [lat]/[lng] are
     * only adopted when the venue has no position yet: a later mention without a fix must never
     * erase one the venue already learned.
     */
    suspend fun findOrCreate(name: String, lat: Double?, lng: Double?, now: Long): Venue

    suspend fun touch(venueId: Int, now: Long)

    suspend fun setPosition(venueId: Int, lat: Double?, lng: Double?)

    /** How many records point at this venue — the number a merge confirmation must show. */
    suspend fun recordCount(venueId: Int): Int

    /**
     * Rename, **merging** when [newName] already belongs to another venue: that venue's records
     * re-point to the survivor and the emptied one is deleted. Returns the surviving venue.
     */
    suspend fun rename(venueId: Int, newName: String, now: Long): Venue

    /** Restore: drop every venue and insert [venues]. Ids are assigned fresh. */
    suspend fun replaceAll(venues: List<Venue>)
}
