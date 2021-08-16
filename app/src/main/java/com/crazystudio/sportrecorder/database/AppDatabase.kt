package com.crazystudio.sportrecorder.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.crazystudio.sportrecorder.dao.EatTimeDao
import com.crazystudio.sportrecorder.dao.FastingTypeDao
import com.crazystudio.sportrecorder.dao.FoodRecordDao
import com.crazystudio.sportrecorder.entity.EatTime
import com.crazystudio.sportrecorder.entity.FastingType
import com.crazystudio.sportrecorder.entity.FoodRecord

@Database(entities = [EatTime::class, FastingType::class, FoodRecord::class], version = 3)
abstract class AppDatabase: RoomDatabase() {
    abstract fun getEatTimeDao(): EatTimeDao
    abstract fun getFastingTypeDao(): FastingTypeDao
    abstract fun getFoodRecordDao(): FoodRecordDao
}