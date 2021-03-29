package com.crazystudio.sportrecorder.dao

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.crazystudio.sportrecorder.entity.EatTime

@Dao
interface EatTimeDao {
    @Query("SELECT * FROM ${EatTime.tableName} ORDER BY time DESC LIMIT 1")
    fun liveLast(): LiveData<List<EatTime>>

    @Query("SELECT * FROM ${EatTime.tableName} ORDER BY time DESC LIMIT 1")
    suspend fun findLast(): List<EatTime>

    @Query("SELECT * FROM ${EatTime.tableName} WHERE time < :before ORDER BY time DESC LIMIT 1")
    suspend fun findLastByTime(before: Long): List<EatTime>

    @Insert
    suspend fun insert(eatTime: EatTime)
}