package com.crazystudio.sportrecorder.domain.repository

import com.crazystudio.sportrecorder.backup.AutoBackupPrefs
import kotlinx.coroutines.flow.Flow

interface AutoBackupPreferencesRepository {
    /** Emits current preferences and re-emits on every change. */
    val prefs: Flow<AutoBackupPrefs>

    suspend fun setEnabled(enabled: Boolean)

    /** Clears any recorded failure: the last word is that a backup went through. */
    suspend fun recordSuccess(at: Long)

    suspend fun recordFailure(at: Long, needsSignIn: Boolean)
}
