package com.crazystudio.sportrecorder

import android.app.Application
import androidx.room.Room
import com.crazystudio.sportrecorder.database.AppDatabase

class SportApplication : Application() {


    override fun onCreate() {
        super.onCreate()
        db = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java, "database-name"
        ).build()
    }

    companion object {
        lateinit var db: AppDatabase
    }
}