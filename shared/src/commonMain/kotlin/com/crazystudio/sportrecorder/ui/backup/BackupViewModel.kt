package com.crazystudio.sportrecorder.ui.backup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.crazystudio.sportrecorder.backup.BackupAuth
import com.crazystudio.sportrecorder.backup.BackupAuthorizationRequired
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
            backupAuth.account.collect { account ->
                // Signing out drops the snapshot list too, so a different account never briefly
                // sees the previous one's backups.
                _uiState.update {
                    if (account == null) {
                        it.copy(account = null, snapshots = emptyList())
                    } else {
                        it.copy(account = account)
                    }
                }
            }
        }
    }

    /** Load the snapshot list (restore picker + "last backed up"). Requires being signed in. */
    fun refreshSnapshots() {
        viewModelScope.launch {
            _uiState.update { it.copy(phase = BackupPhase.Loading) }
            runCatching { backupService.listSnapshots() }
                .onSuccess { snapshots -> _uiState.update { it.copy(snapshots = snapshots, phase = BackupPhase.Idle) } }
                .onFailure { error -> onOperationFailure(error) }
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
                .onFailure { error -> onOperationFailure(error) }
        }
    }

    fun restore(snapshotId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(phase = BackupPhase.Restoring, message = null) }
            runCatching { backupService.restore(snapshotId) }
                .onSuccess {
                    _uiState.update { state ->
                        state.copy(phase = BackupPhase.Idle, message = BackupMessage.RestoreComplete)
                    }
                }
                .onFailure { error ->
                    if (error is BackupSchemaTooNewException) {
                        _uiState.update { state ->
                            state.copy(phase = BackupPhase.Idle, message = BackupMessage.RestoreSchemaTooNew)
                        }
                    } else {
                        onOperationFailure(error)
                    }
                }
        }
    }

    /** Clear the transient result message after the UI has shown it. */
    fun consumeMessage() = _uiState.update { it.copy(message = null) }

    /** Surface a failure that originated outside a VM operation (e.g. sign-in in the :app layer). */
    fun reportFailure() = _uiState.update { it.copy(phase = BackupPhase.Idle, message = BackupMessage.Failed) }

    /** The :app layer got consent back — drop the "needs authorization" state and carry on. */
    fun onAuthorized() = _uiState.update { it.copy(needsAuthorization = false) }

    /**
     * A lapsed grant isn't an error the user can do anything about, so instead of "something went
     * wrong" we fall back to the signed-out view, where "Sign in with Google" re-runs consent.
     */
    private fun onOperationFailure(error: Throwable) {
        if (error is BackupAuthorizationRequired) {
            _uiState.update {
                it.copy(phase = BackupPhase.Idle, needsAuthorization = true, snapshots = emptyList())
            }
        } else {
            _uiState.update { it.copy(phase = BackupPhase.Idle, message = BackupMessage.Failed) }
        }
    }
}
