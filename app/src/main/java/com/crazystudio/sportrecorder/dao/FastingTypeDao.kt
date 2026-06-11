package com.crazystudio.sportrecorder.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.crazystudio.sportrecorder.entity.FastingType
import kotlinx.coroutines.flow.Flow

@Dao
interface FastingTypeDao {
    @Insert
    suspend fun insert(fastingType: FastingType)

    @Query("SELECT * FROM ${FastingType.tableName} ORDER BY timestamp DESC LIMIT :limit")
    fun flowLast(limit: Int): Flow<List<FastingType>>

    @Query(
        "SELECT * FROM ${FastingType.tableName} " +
            "WHERE eating_hours = :eatingHours AND fasting_hours = :fastingHours " +
            "ORDER BY timestamp DESC LIMIT 1"
    )
    suspend fun findByHours(fastingHours: Long, eatingHours: Long): List<FastingType>
}
