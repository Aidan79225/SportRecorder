package com.crazystudio.sportrecorder.fake

import com.crazystudio.sportrecorder.domain.model.Venue
import com.crazystudio.sportrecorder.domain.model.VenueName
import com.crazystudio.sportrecorder.domain.repository.VenueRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * In-memory [VenueRepository] following the real one's contract: names are normalized and unique
 * case-insensitively, [rename] merges when the name belongs to another venue, [observeAll] is
 * most-recently-used first.
 *
 * Records are not modelled, so a test states how many point at a venue through [recordCounts]
 * (a merge moves the emptied venue's count to the survivor, as the real one re-points the rows).
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
        val existing = state.value.firstOrNull { VenueName.sameAs(it.name, normalized) }
        if (existing != null) {
            val learned = if (existing.lat == null && lat != null) {
                existing.copy(lat = lat, lng = lng, lastUsedAt = now)
            } else {
                existing.copy(lastUsedAt = now)
            }
            replace(learned)
            return learned
        }
        val created = Venue(id = nextId++, name = normalized, lat = lat, lng = lng, lastUsedAt = now)
        state.value = state.value + created
        return created
    }

    override suspend fun touch(venueId: Int, now: Long) {
        state.value.firstOrNull { it.id == venueId }?.let { replace(it.copy(lastUsedAt = now)) }
    }

    override suspend fun setPosition(venueId: Int, lat: Double?, lng: Double?) {
        state.value.firstOrNull { it.id == venueId }?.let { replace(it.copy(lat = lat, lng = lng)) }
    }

    override suspend fun recordCount(venueId: Int): Int = recordCounts[venueId] ?: 0

    override suspend fun rename(venueId: Int, newName: String, now: Long): Venue {
        val normalized = VenueName.normalize(newName)
        val row = state.value.first { it.id == venueId }
        val target = state.value.firstOrNull { it.id != venueId && VenueName.sameAs(it.name, normalized) }
        if (target == null) {
            val renamed = row.copy(name = normalized, lastUsedAt = now)
            replace(renamed)
            return renamed
        }
        val survivor = target.copy(lat = target.lat ?: row.lat, lng = target.lng ?: row.lng, lastUsedAt = now)
        recordCounts[target.id] = recordCount(target.id) + recordCount(venueId)
        recordCounts.remove(venueId)
        state.value = state.value.filterNot { it.id == venueId }.map { if (it.id == target.id) survivor else it }
        return survivor
    }

    override suspend fun replaceAll(venues: List<Venue>) {
        state.value = venues
        nextId = (venues.maxOfOrNull { it.id } ?: 0) + 1
    }

    private fun replace(venue: Venue) {
        state.value = state.value.map { if (it.id == venue.id) venue else it }
    }
}
