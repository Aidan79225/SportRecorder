package com.crazystudio.sportrecorder.backup.fakes

import com.crazystudio.sportrecorder.backup.AutoBackupPrefs
import com.crazystudio.sportrecorder.backup.AutoBackupScheduler
import com.crazystudio.sportrecorder.domain.repository.AutoBackupPreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** In-memory preferences that really persist for the length of a test. */
class FakeAutoBackupPreferencesRepository(
    initial: AutoBackupPrefs = AutoBackupPrefs(),
) : AutoBackupPreferencesRepository {
    private val state = MutableStateFlow(initial)

    override val prefs: Flow<AutoBackupPrefs> = state

    val current: AutoBackupPrefs get() = state.value

    override suspend fun setEnabled(enabled: Boolean) {
        state.value = state.value.copy(enabled = enabled)
    }

    override suspend fun recordSuccess(at: Long) {
        state.value = state.value.copy(lastSuccessAt = at, lastFailureAt = null, lastFailureNeedsSignIn = false)
    }

    override suspend fun recordFailure(at: Long, needsSignIn: Boolean) {
        state.value = state.value.copy(lastFailureAt = at, lastFailureNeedsSignIn = needsSignIn)
    }
}

/** Records whether the periodic job is currently armed, and how often that changed. */
class FakeAutoBackupScheduler : AutoBackupScheduler {
    var scheduled: Boolean? = null
        private set
    var changes = 0
        private set

    override fun schedule() {
        scheduled = true
        changes++
    }

    override fun cancel() {
        scheduled = false
        changes++
    }
}
