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

    /** Case-insensitive, because uniqueness ignores case (see VenueName). */
    @Query("SELECT * FROM ${VenueEntity.tableName} WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun findByName(name: String): VenueEntity?

    @Query("SELECT * FROM ${VenueEntity.tableName} WHERE id = :id LIMIT 1")
    suspend fun findById(id: Int): VenueEntity?

    @Insert
    suspend fun insert(venue: VenueEntity): Long

    @Update
    suspend fun update(venue: VenueEntity)

    @Query("DELETE FROM ${VenueEntity.tableName} WHERE id = :id")
    suspend fun deleteById(id: Int)

    @Query("DELETE FROM ${VenueEntity.tableName}")
    suspend fun deleteAll()

    /** How many records a merge would move — shown in the confirmation before it happens. */
    @Query("SELECT COUNT(*) FROM eat_time WHERE venue_id = :venueId")
    suspend fun countRecords(venueId: Int): Int

    @Query("UPDATE eat_time SET venue_id = :toId WHERE venue_id = :fromId")
    suspend fun repointRecords(fromId: Int, toId: Int)
}
