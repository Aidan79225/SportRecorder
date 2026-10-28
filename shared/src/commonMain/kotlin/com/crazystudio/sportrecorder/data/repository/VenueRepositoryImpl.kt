package com.crazystudio.sportrecorder.data.repository

import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import com.crazystudio.sportrecorder.dao.VenueDao
import com.crazystudio.sportrecorder.database.AppDatabase
import com.crazystudio.sportrecorder.domain.model.Venue
import com.crazystudio.sportrecorder.domain.model.VenueName
import com.crazystudio.sportrecorder.domain.repository.VenueRepository
import com.crazystudio.sportrecorder.entity.VenueEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class VenueRepositoryImpl(
    private val appDatabase: AppDatabase,
    private val venueDao: VenueDao,
) : VenueRepository {

    override fun observeAll(): Flow<List<Venue>> =
        venueDao.flowAll().map { rows -> rows.map { it.toDomain() } }

    override suspend fun findOrCreate(name: String, lat: Double?, lng: Double?, now: Long, address: String?): Venue {
        val normalized = VenueName.normalize(name)
        val given = cleaned(address)
        // One transaction: names are no longer unique in SQL, so two racing calls must not both
        // miss and create two venues for one name.
        return inTransaction {
            val existing = mostRecentlyUsedNamed(normalized)
            if (existing == null) {
                insertNew(normalized, lat, lng, now, given)
            } else {
                // Teach it a position only if it has none; never overwrite one it already knows.
                // Gaining a position is a position change, so any address goes with it and the given
                // one (if any) comes with the new position. Otherwise it only fills an empty address.
                val learned = if (existing.lat == null && lat != null) {
                    existing.copy(lat = lat, lng = lng, address = given, lastUsedAt = now)
                } else {
                    existing.copy(address = existing.address ?: given, lastUsedAt = now)
                }
                venueDao.update(learned)
                learned.toDomain()
            }
        }
    }

    override suspend fun create(name: String, lat: Double?, lng: Double?, now: Long, address: String?): Venue =
        insertNew(VenueName.normalize(name), lat, lng, now, cleaned(address))

    override suspend fun touch(venueId: Int, now: Long) = venueDao.setLastUsedAt(id = venueId, now = now)

    override suspend fun setPosition(venueId: Int, lat: Double?, lng: Double?, address: String?) =
        venueDao.setPosition(id = venueId, lat = lat, lng = lng, address = cleaned(address))

    override suspend fun setAddressIfUnchanged(venueId: Int, lat: Double, lng: Double, address: String): Boolean =
        venueDao.setAddressIfUnchanged(id = venueId, lat = lat, lng = lng, address = address) > 0

    override suspend fun setAddressIfEmpty(venueId: Int, address: String): Boolean {
        val cleaned = cleaned(address) ?: return false
        return venueDao.setAddressIfEmpty(id = venueId, address = cleaned) > 0
    }

    override suspend fun edit(venueId: Int, name: String, address: String?, now: Long): Venue {
        val normalized = VenueName.normalize(name)
        require(normalized.isNotEmpty()) { "a venue needs a name" }
        venueDao.setNameAndAddress(
            id = venueId,
            name = normalized,
            address = cleaned(address),
            now = now,
        )
        return venueDao.findById(venueId)?.toDomain() ?: error("venue $venueId is gone")
    }

    override suspend fun merge(fromId: Int, intoId: Int, now: Long): Venue {
        require(fromId != intoId) { "cannot merge venue $fromId into itself" }
        // One transaction, so a kill midway can never leave a stale empty venue or a survivor
        // missing its coordinates.
        return inTransaction {
            val from = venueDao.findById(fromId) ?: error("venue $fromId is gone")
            val into = venueDao.findById(intoId) ?: error("venue $intoId is gone")
            venueDao.repointRecords(fromId = fromId, toId = intoId)
            venueDao.deleteById(fromId)
            // Adopting a position is a position change, so the survivor's address goes with it.
            val survivor = if (into.lat == null && from.lat != null) {
                into.copy(lat = from.lat, lng = from.lng, address = null, lastUsedAt = now)
            } else {
                into.copy(lastUsedAt = now)
            }
            venueDao.update(survivor)
            survivor.toDomain()
        }
    }

    override suspend fun delete(venueId: Int) {
        inTransaction {
            venueDao.clearRecordsVenue(venueId)
            venueDao.deleteById(venueId)
        }
    }

    override suspend fun recordCount(venueId: Int): Int = venueDao.countRecords(venueId)

    override suspend fun replaceAll(venues: List<Venue>): List<Venue> =
        inTransaction {
            venueDao.deleteAll()
            venues.map { venue ->
                val row = VenueEntity(
                    name = VenueName.normalize(venue.name),
                    lat = venue.lat,
                    lng = venue.lng,
                    lastUsedAt = venue.lastUsedAt,
                    address = venue.address,
                )
                row.copy(id = venueDao.insert(row).toInt()).toDomain()
            }
        }

    private suspend fun insertNew(
        normalized: String,
        lat: Double?,
        lng: Double?,
        now: Long,
        address: String? = null,
    ): Venue {
        val row = VenueEntity(name = normalized, lat = lat, lng = lng, lastUsedAt = now, address = address)
        return row.copy(id = venueDao.insert(row).toInt()).toDomain()
    }

    /** An address as stored: trimmed, and blank is none. */
    private fun cleaned(address: String?): String? = address?.trim()?.ifEmpty { null }

    /**
     * Of the venues whose name is the same as [normalized] by [VenueName], the most recently used.
     * Compared in Kotlin, not SQL: SQLite's NOCASE folds ASCII only, so "Café" and "CAFÉ" would
     * otherwise look like different names.
     */
    private suspend fun mostRecentlyUsedNamed(normalized: String): VenueEntity? =
        venueDao.getAll().firstOrNull { VenueName.sameAs(it.name, normalized) }

    private suspend fun <T> inTransaction(block: suspend () -> T): T =
        appDatabase.useWriterConnection { transactor ->
            transactor.immediateTransaction { block() }
        }

    private fun VenueEntity.toDomain() =
        Venue(id = id, name = name, lat = lat, lng = lng, lastUsedAt = lastUsedAt, address = address)
}
