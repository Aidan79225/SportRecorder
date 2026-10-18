package com.crazystudio.sportrecorder.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.crazystudio.sportrecorder.entity.VenueEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface VenueDao {
    /** Most recently used first — the picker's order. */
    @Query("SELECT * FROM ${VenueEntity.tableName} ORDER BY last_used_at DESC")
    fun flowAll(): Flow<List<VenueEntity>>

    /** One-shot [flowAll], usable inside a write transaction. */
    @Query("SELECT * FROM ${VenueEntity.tableName} ORDER BY last_used_at DESC")
    suspend fun getAll(): List<VenueEntity>

    @Query("SELECT * FROM ${VenueEntity.tableName} WHERE id = :id LIMIT 1")
    suspend fun findById(id: Int): VenueEntity?

    @Insert
    suspend fun insert(venue: VenueEntity): Long

    @Update
    suspend fun update(venue: VenueEntity)

    /*
     * Targeted writes: each sets only the columns it owns, so an interleaved write to another
     * column of the same venue is never undone by a stale whole-row rewrite.
     */

    /** Returns the number of rows written (0 when the venue is gone). */
    @Query(
        "UPDATE ${VenueEntity.tableName} SET name = :name, address = :address, last_used_at = :now " +
            "WHERE id = :id",
    )
    suspend fun setNameAndAddress(id: Int, name: String, address: String?, now: Long): Int

    /**
     * Moves the venue and clears its address (it described the old position). Re-applying the same
     * position is not a move, so it writes nothing and keeps the address.
     */
    @Query(
        "UPDATE ${VenueEntity.tableName} SET lat = :lat, lng = :lng, address = NULL " +
            "WHERE id = :id AND (lat IS NOT :lat OR lng IS NOT :lng)",
    )
    suspend fun setPosition(id: Int, lat: Double?, lng: Double?)

    @Query("UPDATE ${VenueEntity.tableName} SET last_used_at = :now WHERE id = :id")
    suspend fun setLastUsedAt(id: Int, now: Long)

    /**
     * Writes a looked-up [address] only while the venue is still at the looked-up position and has
     * no address — so a slow lookup never overwrites an edit or describes a stale position.
     * Returns the number of rows written (0 or 1).
     */
    @Query(
        "UPDATE ${VenueEntity.tableName} SET address = :address " +
            "WHERE id = :id AND lat = :lat AND lng = :lng AND address IS NULL",
    )
    suspend fun setAddressIfUnchanged(id: Int, lat: Double, lng: Double, address: String): Int

    @Query("DELETE FROM ${VenueEntity.tableName} WHERE id = :id")
    suspend fun deleteById(id: Int)

    @Query("DELETE FROM ${VenueEntity.tableName}")
    suspend fun deleteAll()

    /** How many records a merge would move — shown in the confirmation before it happens. */
    @Query("SELECT COUNT(*) FROM eat_time WHERE venue_id = :venueId")
    suspend fun countRecords(venueId: Int): Int

    @Query("UPDATE eat_time SET venue_id = :toId WHERE venue_id = :fromId")
    suspend fun repointRecords(fromId: Int, toId: Int)

    /** Deleting a venue never deletes a meal: its records just lose their venue. */
    @Query("UPDATE eat_time SET venue_id = NULL WHERE venue_id = :venueId")
    suspend fun clearRecordsVenue(venueId: Int)
}
