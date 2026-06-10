package com.crazystudio.sportrecorder.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.crazystudio.sportrecorder.dao.EatTimeDao
import com.crazystudio.sportrecorder.dao.FastingTypeDao
import com.crazystudio.sportrecorder.dao.FoodRecordDao
import com.crazystudio.sportrecorder.dao.PhotoDao
import com.crazystudio.sportrecorder.entity.EatTime
import com.crazystudio.sportrecorder.entity.FastingType
import com.crazystudio.sportrecorder.entity.FoodRecord
import com.crazystudio.sportrecorder.entity.Photo

@Database(entities = [EatTime::class, FastingType::class, FoodRecord::class, Photo::class], version = 4, exportSchema = false)
abstract class AppDatabase: RoomDatabase() {
    abstract fun getEatTimeDao(): EatTimeDao
    abstract fun getFastingTypeDao(): FastingTypeDao
    abstract fun getFoodRecordDao(): FoodRecordDao
    abstract fun getPhotoDao(): PhotoDao
}