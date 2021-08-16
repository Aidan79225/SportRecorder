package com.crazystudio.sportrecorder.dao

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import com.crazystudio.sportrecorder.entity.EatTime
import kotlinx.coroutines.flow.Flow

@Dao
interface EatTimeDao {
    @Query("SELECT * FROM ${EatTime.tableName} ORDER BY time DESC")
    fun liveAll(): LiveData<List<EatTime>>

    @Query("SELECT * FROM ${EatTime.tableName} ORDER BY time DESC LIMIT 1")
    suspend fun findLast(): List<EatTime>

    @Query("SELECT * FROM ${EatTime.tableName} ORDER BY time DESC LIMIT 1")
    fun flowLast(): Flow<List<EatTime>>

    @Query("SELECT * FROM ${EatTime.tableName} WHERE time < :before ORDER BY time DESC LIMIT 1")
    suspend fun findLastByTime(before: Long): List<EatTime>

    @Query("SELECT * FROM ${EatTime.tableName} WHERE time < :before AND time > :after ORDER BY time ASC")
    suspend fun findByTimeInterval(before: Long, after: Long): List<EatTime>

    @Query("SELECT * FROM ${EatTime.tableName} WHERE time < :before AND time > :after ORDER BY time ASC")
    fun flowByTimeInterval(before: Long, after: Long): Flow<List<EatTime>>

    @Insert
    suspend fun insert(eatTime: EatTime): Long

    @Delete
    suspend fun delete(eatTime: EatTime)
}