package com.crazystudio.sportrecorder.backup.fakes

import com.crazystudio.sportrecorder.backup.BackupAccount
import com.crazystudio.sportrecorder.backup.BackupAuth
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeBackupAuth(initial: BackupAccount? = null) : BackupAuth {
    val accountState = MutableStateFlow(initial)
    override val account: Flow<BackupAccount?> = accountState
}
