package com.crazystudio.sportrecorder.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import com.crazystudio.sportrecorder.entity.Photo

@Dao
interface PhotoDao {
    @Insert
    suspend fun insert(photo: Photo): Long

    @Delete
    suspend fun delete(photo: Photo)

    @Query("SELECT * FROM photo WHERE eat_time_id = :eatTimeId")
    suspend fun findByEatTimeId(eatTimeId: Int): List<Photo>

    @Query("DELETE FROM photo WHERE eat_time_id = :eatTimeId")
    suspend fun deleteByEatTimeId(eatTimeId: Int)

    @Query("DELETE FROM photo")
    suspend fun deleteAll()
}
