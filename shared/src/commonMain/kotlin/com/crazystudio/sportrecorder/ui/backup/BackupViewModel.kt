package com.crazystudio.sportrecorder.ui.backup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.crazystudio.sportrecorder.backup.BackupAuth
import com.crazystudio.sportrecorder.backup.BackupSchemaTooNewException
import com.crazystudio.sportrecorder.backup.BackupService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Drives the backup/restore screen. Observes sign-in state via [BackupAuth] and runs the one-shot
 * backup/restore/list operations through [BackupService]. Android auth (consent, sign-out) is the
 * :app layer's concern, so this VM stays platform-free and testable.
 */
class BackupViewModel(
    private val backupService: BackupService,
    backupAuth: BackupAuth,
) : ViewModel() {

    private val _uiState = MutableStateFlow(BackupUiState())
    val uiState: StateFlow<BackupUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            backupAuth.account.collect { account -> _uiState.update { it.copy(account = account) } }
        }
    }

    /** Load the snapshot list (restore picker + "last backed up"). Requires being signed in. */
    fun refreshSnapshots() {
        viewModelScope.launch {
            _uiState.update { it.copy(phase = BackupPhase.Loading) }
            runCatching { backupService.listSnapshots() }
                .onSuccess { snapshots -> _uiState.update { it.copy(snapshots = snapshots, phase = BackupPhase.Idle) } }
                .onFailure { _uiState.update { it.copy(phase = BackupPhase.Idle, message = BackupMessage.Failed) } }
        }
    }

    fun backup() {
        viewModelScope.launch {
            _uiState.update { it.copy(phase = BackupPhase.BackingUp, message = null) }
            runCatching { backupService.backup() }
                .onSuccess {
                    val snapshots = runCatching { backupService.listSnapshots() }
                        .getOrDefault(_uiState.value.snapshots)
                    _uiState.update {
                        it.copy(snapshots = snapshots, phase = BackupPhase.Idle, message = BackupMessage.BackupComplete)
                    }
                }
                .onFailure { _uiState.update { it.copy(phase = BackupPhase.Idle, message = BackupMessage.Failed) } }
        }
    }

    fun restore(snapshotId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(phase = BackupPhase.Restoring, message = null) }
            val result = runCatching { backupService.restore(snapshotId) }
            val message = when {
                result.isSuccess -> BackupMessage.RestoreComplete
                result.exceptionOrNull() is BackupSchemaTooNewException -> BackupMessage.RestoreSchemaTooNew
                else -> BackupMessage.Failed
            }
            _uiState.update { it.copy(phase = BackupPhase.Idle, message = message) }
        }
    }

    /** Clear the transient result message after the UI has shown it. */
    fun consumeMessage() = _uiState.update { it.copy(message = null) }

    /** Surface a failure that originated outside a VM operation (e.g. sign-in in the :app layer). */
    fun reportFailure() = _uiState.update { it.copy(phase = BackupPhase.Idle, message = BackupMessage.Failed) }
}
