package com.crazystudio.sportrecorder.dao

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.crazystudio.sportrecorder.entity.FoodRecord

@Dao
interface FoodRecordDao {
    @Insert
    suspend fun insert(foodRecord: FoodRecord)

    @Delete
    suspend fun delete(foodRecord: FoodRecord)

    @Query("SELECT * FROM food_record WHERE eat_time_id = :eatTimeId")
    fun liveFoodRecordByEatTimeId(eatTimeId: Int): LiveData<List<FoodRecord>>

    @Query("UPDATE food_record SET eat_time_id = :targetId WHERE eat_time_id = :oldId")
    suspend fun updateEatTimeId(oldId: Int, targetId: Int)
}