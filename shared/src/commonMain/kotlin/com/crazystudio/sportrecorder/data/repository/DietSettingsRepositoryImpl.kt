package com.crazystudio.sportrecorder.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import com.crazystudio.sportrecorder.domain.model.DietSettings
import com.crazystudio.sportrecorder.domain.model.FastingWindow
import com.crazystudio.sportrecorder.domain.repository.DietSettingsRepository
import com.crazystudio.sportrecorder.util.Constants
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import okio.IOException

private const val DEFAULT_FASTING_HOURS = 16L
private const val DEFAULT_EATING_HOURS = 8L

// Key names MUST match the legacy SharedPreferences keys so SharedPreferencesMigration carries values over.
private val FASTING_KEY = longPreferencesKey(Constants.DIET_FASTING_TIME_INTERVAL)
private val EATING_KEY = longPreferencesKey(Constants.DIET_EATING_TIME_INTERVAL)

class DietSettingsRepositoryImpl constructor(
    private val dataStore: DataStore<Preferences>,
) : DietSettingsRepository {

    override val settings: Flow<DietSettings> = dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { prefs ->
            DietSettings(
                fastingHours = prefs[FASTING_KEY] ?: DEFAULT_FASTING_HOURS,
                eatingHours = prefs[EATING_KEY] ?: DEFAULT_EATING_HOURS,
            )
        }
        .distinctUntilChanged()

    override suspend fun setSelection(window: FastingWindow) {
        dataStore.edit { prefs ->
            prefs[FASTING_KEY] = window.fastingHours
            prefs[EATING_KEY] = window.eatingHours
        }
    }
}
