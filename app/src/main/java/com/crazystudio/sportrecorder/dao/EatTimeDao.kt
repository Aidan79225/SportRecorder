package com.crazystudio.sportrecorder.dao

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.crazystudio.sportrecorder.entity.EatTime

@Dao
interface EatTimeDao {
    @Query("SELECT * FROM ${EatTime.tableName} LIMIT 1")
    fun liveLast(): LiveData<List<EatTime>>

    @Insert
    suspend fun insert(eatTime: EatTime)
}