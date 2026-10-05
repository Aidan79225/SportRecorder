package com.crazystudio.sportrecorder.backup.fakes

import com.crazystudio.sportrecorder.domain.model.Venue
import com.crazystudio.sportrecorder.domain.model.VenueName
import com.crazystudio.sportrecorder.domain.repository.VenueRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * In-memory [VenueRepository] following the contracts `VenueRepositoryImpl` documents: names are
 * normalized and unique case-insensitively, [observeAll] is most-recently-used first, and
 * [replaceAll] **assigns fresh ids** — the property a restore depends on.
 *
 * Ids only ever grow, even across [replaceAll] (like Room's AUTOINCREMENT), so a venue that is
 * backed up, wiped and restored comes back with a different id; a test that re-attaches by id
 * instead of by name would therefore fail.
 */
class FakeVenueRepository(initial: List<Venue> = emptyList()) : VenueRepository {
    private val state = MutableStateFlow(initial)
    private var nextId: Int = (initial.maxOfOrNull { it.id } ?: 0) + 1

    /** Venues in insertion order, for direct assertions. */
    val stored: List<Venue> get() = state.value

    override fun observeAll(): Flow<List<Venue>> =
        state.map { venues -> venues.sortedByDescending { it.lastUsedAt } }

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

    override suspend fun recordCount(venueId: Int): Int = 0

    override suspend fun rename(venueId: Int, newName: String, now: Long): Venue {
        val normalized = VenueName.normalize(newName)
        val renamed = state.value.first { it.id == venueId }.copy(name = normalized, lastUsedAt = now)
        replace(renamed)
        return renamed
    }

    override suspend fun replaceAll(venues: List<Venue>) {
        state.value = venues.map { it.copy(id = nextId++, name = VenueName.normalize(it.name)) }
    }

    private fun replace(venue: Venue) {
        state.value = state.value.map { if (it.id == venue.id) venue else it }
    }
}
