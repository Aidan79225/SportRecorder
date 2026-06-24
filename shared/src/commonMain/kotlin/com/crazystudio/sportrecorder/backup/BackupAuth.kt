package com.crazystudio.sportrecorder.backup

import kotlinx.coroutines.flow.Flow

/** The signed-in Google account, as far as backup cares. */
data class BackupAccount(val email: String)

/** Sign-in state for backup. Token acquisition is a platform concern, not exposed here. */
interface BackupAuth {
    /** Emits the current account, or null when signed out. */
    val account: Flow<BackupAccount?>
}
