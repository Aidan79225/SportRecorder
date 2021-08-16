package com.crazystudio.sportrecorder.dao

import androidx.room.Dao
import androidx.room.Insert
import com.crazystudio.sportrecorder.entity.FoodRecord

@Dao
interface FoodRecordDao {
    @Insert
    suspend fun insert(foodRecord: FoodRecord)
}