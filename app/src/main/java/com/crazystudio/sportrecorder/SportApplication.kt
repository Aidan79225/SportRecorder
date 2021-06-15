package com.crazystudio.sportrecorder

import android.app.Application
import androidx.room.Room
import com.crazystudio.sportrecorder.database.AppDatabase
import com.crazystudio.sportrecorder.database.Migrations
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class SportApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        application = this
        db = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java, "database-name"
        ).apply {
            Migrations.getMigrations().forEach { addMigrations(it) }
        }.build()
    }

    companion object {
        lateinit var db: AppDatabase
        lateinit var application: Application
    }
}