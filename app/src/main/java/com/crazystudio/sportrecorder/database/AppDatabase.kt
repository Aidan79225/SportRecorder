package com.crazystudio.sportrecorder.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.crazystudio.sportrecorder.dao.EatTimeDao
import com.crazystudio.sportrecorder.entity.EatTime

@Database(entities = [EatTime::class], version = 1)
abstract class AppDatabase: RoomDatabase() {
    abstract fun eatTimeDao(): EatTimeDao
}