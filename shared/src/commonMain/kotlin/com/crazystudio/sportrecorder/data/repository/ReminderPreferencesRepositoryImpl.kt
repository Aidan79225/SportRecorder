package com.crazystudio.sportrecorder.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import com.crazystudio.sportrecorder.domain.reminder.ReminderPrefs
import com.crazystudio.sportrecorder.domain.repository.ReminderPreferencesRepository
import com.crazystudio.sportrecorder.util.Constants
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import okio.IOException

private val WINDOW_CLOSING_KEY = booleanPreferencesKey(Constants.REMINDER_WINDOW_CLOSING_ENABLED)
private val FAST_COMPLETE_KEY = booleanPreferencesKey(Constants.REMINDER_FAST_COMPLETE_ENABLED)
private val LEAD_MINUTES_KEY = longPreferencesKey(Constants.REMINDER_LEAD_MINUTES)
private val QUIET_ENABLED_KEY = booleanPreferencesKey(Constants.REMINDER_QUIET_ENABLED)
private val QUIET_START_KEY = intPreferencesKey(Constants.REMINDER_QUIET_START_MINUTES)
private val QUIET_END_KEY = intPreferencesKey(Constants.REMINDER_QUIET_END_MINUTES)

class ReminderPreferencesRepositoryImpl constructor(
    private val dataStore: DataStore<Preferences>,
) : ReminderPreferencesRepository {

    override val prefs: Flow<ReminderPrefs> = dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { p ->
            val defaults = ReminderPrefs()
            ReminderPrefs(
                windowClosingEnabled = p[WINDOW_CLOSING_KEY] ?: defaults.windowClosingEnabled,
                fastCompleteEnabled = p[FAST_COMPLETE_KEY] ?: defaults.fastCompleteEnabled,
                leadMinutes = p[LEAD_MINUTES_KEY] ?: defaults.leadMinutes,
                quietHoursEnabled = p[QUIET_ENABLED_KEY] ?: defaults.quietHoursEnabled,
                quietStartMinutes = p[QUIET_START_KEY] ?: defaults.quietStartMinutes,
                quietEndMinutes = p[QUIET_END_KEY] ?: defaults.quietEndMinutes,
            )
        }
        .distinctUntilChanged()

    override suspend fun setWindowClosingEnabled(enabled: Boolean) {
        dataStore.edit { it[WINDOW_CLOSING_KEY] = enabled }
    }

    override suspend fun setFastCompleteEnabled(enabled: Boolean) {
        dataStore.edit { it[FAST_COMPLETE_KEY] = enabled }
    }

    override suspend fun setLeadMinutes(minutes: Long) {
        dataStore.edit { it[LEAD_MINUTES_KEY] = minutes }
    }

    override suspend fun setQuietHoursEnabled(enabled: Boolean) {
        dataStore.edit { it[QUIET_ENABLED_KEY] = enabled }
    }

    override suspend fun setQuietHours(startMinutes: Int, endMinutes: Int) {
        dataStore.edit {
            it[QUIET_START_KEY] = startMinutes
            it[QUIET_END_KEY] = endMinutes
        }
    }
}
