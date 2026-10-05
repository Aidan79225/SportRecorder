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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class VenueRepositoryImpl(
    private val appDatabase: AppDatabase,
    private val venueDao: VenueDao,
) : VenueRepository {

    override fun observeAll(): Flow<List<Venue>> =
        venueDao.flowAll().map { rows -> rows.map { it.toDomain() } }

    override suspend fun findOrCreate(name: String, lat: Double?, lng: Double?, now: Long): Venue {
        val normalized = VenueName.normalize(name)
        val existing = findSameName(normalized)
        if (existing != null) {
            // Teach it a position only if it has none; never overwrite one it already knows.
            val learned = if (existing.lat == null && lat != null) {
                existing.copy(lat = lat, lng = lng, lastUsedAt = now)
            } else {
                existing.copy(lastUsedAt = now)
            }
            venueDao.update(learned)
            return learned.toDomain()
        }
        val id = venueDao.insert(
            VenueEntity(name = normalized, lat = lat, lng = lng, lastUsedAt = now),
        ).toInt()
        return Venue(id = id, name = normalized, lat = lat, lng = lng, lastUsedAt = now)
    }

    override suspend fun touch(venueId: Int, now: Long) {
        val row = venueDao.findById(venueId) ?: return
        venueDao.update(row.copy(lastUsedAt = now))
    }

    override suspend fun setPosition(venueId: Int, lat: Double?, lng: Double?) {
        val row = venueDao.findById(venueId) ?: return
        venueDao.update(row.copy(lat = lat, lng = lng))
    }

    override suspend fun recordCount(venueId: Int): Int = venueDao.countRecords(venueId)

    override suspend fun rename(venueId: Int, newName: String, now: Long): Venue {
        val normalized = VenueName.normalize(newName)
        val row = venueDao.findById(venueId) ?: error("venue $venueId is gone")
        val target = findSameName(normalized)
        if (target == null || target.id == venueId) {
            val renamed = row.copy(name = normalized, lastUsedAt = now)
            venueDao.update(renamed)
            return renamed.toDomain()
        }
        // Merge: the records move to the survivor, then the emptied venue goes. One transaction, so
        // a kill midway can never leave a stale empty venue or a survivor missing its coordinates.
        return appDatabase.useWriterConnection { transactor ->
            transactor.immediateTransaction {
                venueDao.repointRecords(fromId = venueId, toId = target.id)
                venueDao.deleteById(venueId)
                val survivor = target.copy(
                    lat = target.lat ?: row.lat,
                    lng = target.lng ?: row.lng,
                    lastUsedAt = now,
                )
                venueDao.update(survivor)
                survivor.toDomain()
            }
        }
    }

    override suspend fun replaceAll(venues: List<Venue>) {
        appDatabase.useWriterConnection { transactor ->
            transactor.immediateTransaction {
                venueDao.deleteAll()
                venues.forEach { venue ->
                    venueDao.insert(
                        VenueEntity(
                            name = VenueName.normalize(venue.name),
                            lat = venue.lat,
                            lng = venue.lng,
                            lastUsedAt = venue.lastUsedAt,
                        ),
                    )
                }
            }
        }
    }

    /**
     * The venue whose name is the same as [normalized] by [VenueName] — the definition of
     * uniqueness. SQLite's NOCASE folds ASCII only, so a SQL hit is just the fast path; on a miss
     * we reconcile against every venue so "Café" and "CAFÉ" can never become two rows.
     */
    private suspend fun findSameName(normalized: String): VenueEntity? =
        venueDao.findByName(normalized)
            ?: venueDao.flowAll().first().firstOrNull { VenueName.sameAs(it.name, normalized) }

    private fun VenueEntity.toDomain() =
        Venue(id = id, name = name, lat = lat, lng = lng, lastUsedAt = lastUsedAt)
}
