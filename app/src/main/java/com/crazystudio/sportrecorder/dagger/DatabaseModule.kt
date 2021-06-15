package com.crazystudio.sportrecorder.dagger

import android.content.Context
import androidx.room.Room
import com.crazystudio.sportrecorder.database.AppDatabase
import com.crazystudio.sportrecorder.database.Migrations
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
class DatabaseModule {
    @Provides
    fun provideAppDatabase(@ApplicationContext applicationContext: Context): AppDatabase {
        return Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java, "database-name"
        ).apply {
            Migrations.getMigrations().forEach { addMigrations(it) }
        }.build()
    }
}