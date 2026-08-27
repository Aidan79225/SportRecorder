package com.crazystudio.sportrecorder.ui.backup

import com.crazystudio.sportrecorder.backup.BackupAccount
import com.crazystudio.sportrecorder.backup.SnapshotInfo

/** UI state for the backup/restore screen. [message] is a transient result the UI shows then clears. */
data class BackupUiState(
    val account: BackupAccount? = null,
    val snapshots: List<SnapshotInfo> = emptyList(),
    val phase: BackupPhase = BackupPhase.Idle,
    val message: BackupMessage? = null,
    /**
     * Drive turned our access down and only fresh consent gets it back (the grant was revoked, or
     * it lapsed while the screen was open). We show the signed-out view so there's a way back in
     * instead of an error the user can't act on.
     */
    val needsAuthorization: Boolean = false,
) {
    val isSignedIn: Boolean get() = account != null && !needsAuthorization
    val isBusy: Boolean get() = phase != BackupPhase.Idle
    /** Newest snapshot's createdAt, or null if none — drives "last backed up …". */
    val lastBackedUpAt: Long? get() = snapshots.maxOfOrNull { it.createdAt }
}

enum class BackupPhase { Idle, Loading, BackingUp, Restoring }

/** Semantic result — the UI maps each to a localized string (the VM holds no user-facing copy). */
enum class BackupMessage { BackupComplete, RestoreComplete, RestoreSchemaTooNew, SignOutFailed, Failed }
