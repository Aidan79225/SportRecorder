package com.crazystudio.sportrecorder.ui.backup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.crazystudio.sportrecorder.backup.BackupAuth
import com.crazystudio.sportrecorder.backup.BackupJobKind
import com.crazystudio.sportrecorder.backup.BackupJobRunner
import com.crazystudio.sportrecorder.backup.BackupJobState
import com.crazystudio.sportrecorder.backup.BackupOutcome
import com.crazystudio.sportrecorder.backup.BackupService
import com.crazystudio.sportrecorder.domain.usecase.ObserveEatRecordsUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Drives the backup/restore screen. Observes sign-in state via [BackupAuth], mirrors the
 * app-scoped [BackupJobRunner] (so a job keeps going when this VM is cleared), and loads the
 * snapshot list through [BackupService]. Android auth (consent, sign-out) is the :app layer's
 * concern, so this VM stays platform-free and testable.
 */
class BackupViewModel(
    private val runner: BackupJobRunner,
    private val backupService: BackupService,
    backupAuth: BackupAuth,
    observeEatRecords: ObserveEatRecordsUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(BackupUiState())
    val uiState: StateFlow<BackupUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            backupAuth.account.collect { account ->
                _uiState.update { state ->
                    // Snapshots belong to the account that listed them: drop them whenever the
                    // account changes (sign-out, or switching to another Google account) so the
                    // restore list / "last backed up" never shows the previous account's data.
                    if (state.account?.email == account?.email) {
                        state.copy(account = account)
                    } else {
                        state.copy(account = account, snapshots = emptyList())
                    }
                }
            }
        }
        viewModelScope.launch {
            runner.state.collect { job ->
                _uiState.update { it.copy(job = job) }
                if (job is BackupJobState.Finished) onFinished(job)
            }
        }
        viewModelScope.launch {
            observeEatRecords().collect { meals -> _uiState.update { it.copy(localMealCount = meals.size) } }
        }
    }

    /** Load the snapshot list (restore picker + "last backed up"). Requires being signed in. */
    fun refreshSnapshots() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingSnapshots = true) }
            runCatching { backupService.listSnapshots() }
                .onSuccess { snapshots -> _uiState.update { it.copy(snapshots = snapshots, isLoadingSnapshots = false) } }
                .onFailure { _uiState.update { it.copy(isLoadingSnapshots = false, message = BackupMessage.Failed) } }
        }
    }

    fun backup() {
        _uiState.update { it.copy(message = null) }
        runner.startBackup()
    }

    fun restore(snapshotId: String) {
        _uiState.update { it.copy(message = null) }
        runner.startRestore(snapshotId)
    }

    fun cancel() = runner.cancel()

    /** Clear the transient result message after the UI has shown it, and let the runner go idle. */
    fun consumeMessage() {
        _uiState.update { it.copy(message = null) }
        runner.acknowledgeFinished()
    }

    /** Surface a failure that originated outside a VM operation (e.g. sign-in in the :app layer). */
    fun reportFailure() = _uiState.update { it.copy(message = BackupMessage.Failed) }

    private suspend fun onFinished(job: BackupJobState.Finished) {
        val message = when (job.outcome) {
            BackupOutcome.Completed ->
                if (job.kind == BackupJobKind.Backup) BackupMessage.BackupComplete else BackupMessage.RestoreComplete
            BackupOutcome.Cancelled -> BackupMessage.Cancelled
            BackupOutcome.SchemaTooNew -> BackupMessage.RestoreSchemaTooNew
            BackupOutcome.Failed -> BackupMessage.Failed
        }
        if (job.outcome == BackupOutcome.Completed) {
            val snapshots = runCatching { backupService.listSnapshots() }.getOrDefault(_uiState.value.snapshots)
            _uiState.update { it.copy(snapshots = snapshots) }
        }
        _uiState.update { it.copy(message = message) }
    }
}
