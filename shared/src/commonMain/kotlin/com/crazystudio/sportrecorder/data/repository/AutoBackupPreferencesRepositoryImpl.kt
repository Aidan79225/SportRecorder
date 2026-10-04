package com.crazystudio.sportrecorder.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import com.crazystudio.sportrecorder.backup.AutoBackupPrefs
import com.crazystudio.sportrecorder.domain.repository.AutoBackupPreferencesRepository
import com.crazystudio.sportrecorder.util.Constants
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import okio.IOException

private val ENABLED_KEY = booleanPreferencesKey(Constants.AUTO_BACKUP_ENABLED)
private val LAST_SUCCESS_KEY = longPreferencesKey(Constants.AUTO_BACKUP_LAST_SUCCESS_AT)
private val LAST_FAILURE_KEY = longPreferencesKey(Constants.AUTO_BACKUP_LAST_FAILURE_AT)
private val NEEDS_SIGN_IN_KEY = booleanPreferencesKey(Constants.AUTO_BACKUP_LAST_FAILURE_NEEDS_SIGN_IN)

class AutoBackupPreferencesRepositoryImpl constructor(
    private val dataStore: DataStore<Preferences>,
) : AutoBackupPreferencesRepository {

    override val prefs: Flow<AutoBackupPrefs> = dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { p ->
            val defaults = AutoBackupPrefs()
            AutoBackupPrefs(
                enabled = p[ENABLED_KEY] ?: defaults.enabled,
                lastSuccessAt = p[LAST_SUCCESS_KEY],
                lastFailureAt = p[LAST_FAILURE_KEY],
                lastFailureNeedsSignIn = p[NEEDS_SIGN_IN_KEY] ?: defaults.lastFailureNeedsSignIn,
            )
        }
        .distinctUntilChanged()

    override suspend fun setEnabled(enabled: Boolean) {
        dataStore.edit { it[ENABLED_KEY] = enabled }
    }

    override suspend fun recordSuccess(at: Long) {
        dataStore.edit {
            it[LAST_SUCCESS_KEY] = at
            it.remove(LAST_FAILURE_KEY)
            it.remove(NEEDS_SIGN_IN_KEY)
        }
    }

    override suspend fun recordFailure(at: Long, needsSignIn: Boolean) {
        dataStore.edit {
            it[LAST_FAILURE_KEY] = at
            it[NEEDS_SIGN_IN_KEY] = needsSignIn
        }
    }
}
