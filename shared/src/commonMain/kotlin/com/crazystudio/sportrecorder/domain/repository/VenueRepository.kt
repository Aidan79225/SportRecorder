package com.crazystudio.sportrecorder.domain.repository

import com.crazystudio.sportrecorder.domain.model.Venue
import kotlinx.coroutines.flow.Flow

/**
 * Venues by **id**: names are not unique (branches of a chain share one, #89). Whether two names are
 * the same name is `VenueName.sameAs`.
 */
interface VenueRepository {
    /** Every venue, most recently used first — the order the picker shows. */
    fun observeAll(): Flow<List<Venue>>

    /**
     * For typed names: the most recently used venue with this name (compared by `VenueName.sameAs`),
     * else a new one. [lat]/[lng] are only adopted when that venue has no position yet: a later
     * mention without a fix must never erase one the venue already learned.
     */
    suspend fun findOrCreate(name: String, lat: Double?, lng: Double?, now: Long): Venue

    /** Always a new venue, even if the name exists (a new branch). */
    suspend fun create(name: String, lat: Double?, lng: Double?, now: Long): Venue

    suspend fun touch(venueId: Int, now: Long)

    /** Moves the venue **and** clears its address: the old address described the old position. */
    suspend fun setPosition(venueId: Int, lat: Double?, lng: Double?)

    /**
     * Writes [address] only if the venue is still at ([lat], [lng]) and its address is still null —
     * a lookup result never overwrites an edit or describes a stale position. True if written.
     */
    suspend fun setAddressIfUnchanged(venueId: Int, lat: Double, lng: Double, address: String): Boolean

    /**
     * Saves name and address as given: the name normalized, the address trimmed (blank → null).
     * Never merges. A name that is blank once normalized is refused (throws).
     */
    suspend fun edit(venueId: Int, name: String, address: String?, now: Long): Venue

    /**
     * Re-points [fromId]'s records to [intoId] and deletes [fromId], in one transaction. The
     * survivor keeps its own name, address and position, but adopts [fromId]'s position if it has
     * none — and then loses its address, as any position change does. Returns the survivor.
     */
    suspend fun merge(fromId: Int, intoId: Int, now: Long): Venue

    /** Clears the venue on its records and deletes it, in one transaction. Records are never deleted. */
    suspend fun delete(venueId: Int)

    /** How many records point at this venue — the number a merge or delete confirmation shows. */
    suspend fun recordCount(venueId: Int): Int

    /**
     * Restore: drop every venue and insert [venues] in order. Ids are assigned fresh; returns the
     * inserted venues in the same order, so the caller can map old keys to new ids.
     */
    suspend fun replaceAll(venues: List<Venue>): List<Venue>
}
