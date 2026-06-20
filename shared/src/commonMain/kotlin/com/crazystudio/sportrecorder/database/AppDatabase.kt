package com.crazystudio.sportrecorder.database

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import com.crazystudio.sportrecorder.dao.EatTimeDao
import com.crazystudio.sportrecorder.dao.FastingTypeDao
import com.crazystudio.sportrecorder.dao.PhotoDao
import com.crazystudio.sportrecorder.entity.EatTime
import com.crazystudio.sportrecorder.entity.FastingType
import com.crazystudio.sportrecorder.entity.Photo

@Database(entities = [EatTime::class, FastingType::class, Photo::class], version = 7, exportSchema = false)
@ConstructedBy(AppDatabaseConstructor::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun getEatTimeDao(): EatTimeDao
    abstract fun getFastingTypeDao(): FastingTypeDao
    abstract fun getPhotoDao(): PhotoDao
}

// Room's KSP generates the per-platform actual.
@Suppress("NO_ACTUAL_FOR_EXPECT")
expect object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase> {
    override fun initialize(): AppDatabase
}
