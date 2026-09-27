package com.crazystudio.sportrecorder.ui.backup

import com.crazystudio.sportrecorder.backup.BackupAccount
import com.crazystudio.sportrecorder.backup.BackupJobState
import com.crazystudio.sportrecorder.backup.SnapshotInfo

/**
 * UI state for the backup/restore screen. [job] mirrors the app-scoped runner so progress survives
 * leaving and re-entering the screen; [message] is a transient result the UI shows then clears.
 */
data class BackupUiState(
    val account: BackupAccount? = null,
    val snapshots: List<SnapshotInfo> = emptyList(),
    val job: BackupJobState = BackupJobState.Idle,
    val isLoadingSnapshots: Boolean = false,
    /** Meals currently on this device — the restore dialog says how many get a safety snapshot. */
    val localMealCount: Int = 0,
    val message: BackupMessage? = null,
) {
    val isSignedIn: Boolean get() = account != null
    val isBusy: Boolean get() = job is BackupJobState.Running || isLoadingSnapshots
    /** Newest snapshot's createdAt, or null if none — drives "last backed up …". */
    val lastBackedUpAt: Long? get() = snapshots.maxOfOrNull { it.createdAt }
}

/** Semantic result — the UI maps each to a localized string (the VM holds no user-facing copy). */
enum class BackupMessage { BackupComplete, RestoreComplete, RestoreSchemaTooNew, Cancelled, Failed }
