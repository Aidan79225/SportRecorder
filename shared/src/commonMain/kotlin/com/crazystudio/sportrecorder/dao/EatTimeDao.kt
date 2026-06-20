package com.crazystudio.sportrecorder.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.crazystudio.sportrecorder.entity.EatTime
import kotlinx.coroutines.flow.Flow

@Dao
@Suppress("TooManyFunctions") // cohesive Room DAO: each function is a distinct query/mutation
interface EatTimeDao {
    @Query("SELECT * FROM ${EatTime.tableName} ORDER BY time DESC")
    fun flowAll(): Flow<List<EatTime>>

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

    @androidx.room.Transaction
    @Query("SELECT * FROM ${EatTime.tableName} ORDER BY time DESC")
    fun flowAllWithPhotos(): kotlinx.coroutines.flow.Flow<List<com.crazystudio.sportrecorder.entity.EatTimeWithPhotos>>

    @Insert
    suspend fun insert(eatTime: EatTime): Long

    @Update
    suspend fun update(eatTime: EatTime)

    @Transaction
    @Query("SELECT * FROM ${EatTime.tableName} WHERE id = :id LIMIT 1")
    suspend fun findWithPhotosById(id: Int): com.crazystudio.sportrecorder.entity.EatTimeWithPhotos?

    @Query("DELETE FROM ${EatTime.tableName} WHERE id = :id")
    suspend fun deleteById(id: Int)
}
