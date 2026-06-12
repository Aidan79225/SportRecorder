package com.crazystudio.sportrecorder.dagger

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataStoreModule {
    // Must match the legacy SharedPreferences file name so SharedPreferencesMigration finds it.
    private const val DIET_PREFERENCES_NAME = "diet_preference"

    @Provides
    @Singleton
    fun provideDietDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            migrations = listOf(SharedPreferencesMigration(context, DIET_PREFERENCES_NAME)),
            produceFile = { context.preferencesDataStoreFile(DIET_PREFERENCES_NAME) },
        )
}
