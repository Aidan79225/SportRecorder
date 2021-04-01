package com.crazystudio.sportrecorder.dao

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.crazystudio.sportrecorder.entity.EatTime
import com.crazystudio.sportrecorder.entity.FastingType

@Dao
interface FastingTypeDao {
    @Insert
    suspend fun insert(fastingType: FastingType)

    @Query("SELECT * FROM ${FastingType.tableName} ORDER BY timestamp DESC LIMIT :limit")
    fun liveLast(limit: Int): LiveData<List<FastingType>>

}