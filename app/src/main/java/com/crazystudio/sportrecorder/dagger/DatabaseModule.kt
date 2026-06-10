package com.crazystudio.sportrecorder.dagger

import android.content.Context
import androidx.room.Room
import com.crazystudio.sportrecorder.dao.EatTimeDao
import com.crazystudio.sportrecorder.dao.FastingTypeDao
import com.crazystudio.sportrecorder.dao.FoodRecordDao
import com.crazystudio.sportrecorder.dao.PhotoDao
import com.crazystudio.sportrecorder.database.AppDatabase
import com.crazystudio.sportrecorder.database.Migrations
import com.crazystudio.sportrecorder.util.DietPreference
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext applicationContext: Context): AppDatabase {
        return Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java, "database-name"
        ).apply {
            Migrations.getMigrations().forEach { addMigrations(it) }
        }.build()
    }

    @Provides
    @Singleton
    fun provideDietPreference(@ApplicationContext applicationContext: Context): DietPreference {
        return DietPreference(applicationContext)
    }

    @Provides
    @Singleton
    fun provideEatTimeDao(appDatabase: AppDatabase): EatTimeDao {
        return appDatabase.getEatTimeDao()
    }

    @Provides
    @Singleton
    fun provideFastingTypeDao(appDatabase: AppDatabase): FastingTypeDao {
        return appDatabase.getFastingTypeDao()
    }

    @Provides
    @Singleton
    fun provideFoodRecordDao(appDatabase: AppDatabase): FoodRecordDao {
        return appDatabase.getFoodRecordDao()
    }

    @Provides
    @Singleton
    fun providePhotoDao(appDatabase: AppDatabase): PhotoDao {
        return appDatabase.getPhotoDao()
    }
}