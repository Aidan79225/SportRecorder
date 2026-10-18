package com.crazystudio.sportrecorder.fake

import com.crazystudio.sportrecorder.domain.model.Venue
import com.crazystudio.sportrecorder.domain.model.VenueName
import com.crazystudio.sportrecorder.domain.repository.VenueRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * In-memory [VenueRepository] following the real one's contract: names are normalized but not
 * unique, [findOrCreate] picks the most recently used same-name venue, [setPosition] clears the
 * address, [observeAll] is most-recently-used first, and [replaceAll] assigns fresh ids.
 *
 * Records are not modelled, so a test states how many point at a venue through [recordCounts]
 * (a merge moves the emptied venue's count to the survivor, as the real one re-points the rows; a
 * delete drops it, as the real one clears the rows' venue).
 */
class FakeVenueRepository(initial: List<Venue> = emptyList()) : VenueRepository {
    private val state = MutableStateFlow(initial)
    private var nextId: Int = (initial.maxOfOrNull { it.id } ?: 0) + 1

    val recordCounts = mutableMapOf<Int, Int>()

    /** Venues in insertion order, for direct assertions. */
    val stored: List<Venue> get() = state.value

    override fun observeAll(): Flow<List<Venue>> = state.map { venues -> venues.sortedByDescending { it.lastUsedAt } }

    override suspend fun findOrCreate(name: String, lat: Double?, lng: Double?, now: Long): Venue {
        val normalized = VenueName.normalize(name)
        val existing = state.value.filter { VenueName.sameAs(it.name, normalized) }.maxByOrNull { it.lastUsedAt }
        if (existing != null) {
            val learned = if (existing.lat == null && lat != null) {
                existing.copy(lat = lat, lng = lng, address = null, lastUsedAt = now)
            } else {
                existing.copy(lastUsedAt = now)
            }
            replace(learned)
            return learned
        }
        return create(normalized, lat, lng, now)
    }

    override suspend fun create(name: String, lat: Double?, lng: Double?, now: Long): Venue {
        val created = Venue(id = nextId++, name = VenueName.normalize(name), lat = lat, lng = lng, lastUsedAt = now)
        state.value = state.value + created
        return created
    }

    override suspend fun touch(venueId: Int, now: Long) {
        find(venueId)?.let { replace(it.copy(lastUsedAt = now)) }
    }

    override suspend fun setPosition(venueId: Int, lat: Double?, lng: Double?) {
        find(venueId)?.takeIf { it.lat != lat || it.lng != lng }
            ?.let { replace(it.copy(lat = lat, lng = lng, address = null)) }
    }

    override suspend fun setAddressIfUnchanged(venueId: Int, lat: Double, lng: Double, address: String): Boolean {
        val venue = find(venueId)?.takeIf { it.lat == lat && it.lng == lng && it.address == null }
        venue?.let { replace(it.copy(address = address)) }
        return venue != null
    }

    override suspend fun edit(venueId: Int, name: String, address: String?, now: Long): Venue {
        val normalized = VenueName.normalize(name)
        require(normalized.isNotEmpty()) { "a venue needs a name" }
        val edited = state.value.first { it.id == venueId }.copy(
            name = normalized,
            address = address?.trim()?.ifEmpty { null },
            lastUsedAt = now,
        )
        replace(edited)
        return edited
    }

    override suspend fun merge(fromId: Int, intoId: Int, now: Long): Venue {
        require(fromId != intoId) { "cannot merge venue $fromId into itself" }
        val from = state.value.first { it.id == fromId }
        val into = state.value.first { it.id == intoId }
        val survivor = if (into.lat == null && from.lat != null) {
            into.copy(lat = from.lat, lng = from.lng, address = null, lastUsedAt = now)
        } else {
            into.copy(lastUsedAt = now)
        }
        recordCounts[intoId] = recordCount(intoId) + recordCount(fromId)
        recordCounts.remove(fromId)
        state.value = state.value.filterNot { it.id == fromId }.map { if (it.id == intoId) survivor else it }
        return survivor
    }

    override suspend fun delete(venueId: Int) {
        recordCounts.remove(venueId)
        state.value = state.value.filterNot { it.id == venueId }
    }

    override suspend fun recordCount(venueId: Int): Int = recordCounts[venueId] ?: 0

    override suspend fun replaceAll(venues: List<Venue>): List<Venue> {
        val inserted = venues.map { it.copy(id = nextId++, name = VenueName.normalize(it.name)) }
        state.value = inserted
        return inserted
    }

    private fun find(venueId: Int): Venue? = state.value.firstOrNull { it.id == venueId }

    private fun replace(venue: Venue) {
        state.value = state.value.map { if (it.id == venue.id) venue else it }
    }
}
