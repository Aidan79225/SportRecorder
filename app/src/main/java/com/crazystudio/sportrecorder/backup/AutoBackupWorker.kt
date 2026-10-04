package com.crazystudio.sportrecorder.backup

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.crazystudio.sportrecorder.domain.repository.AutoBackupPreferencesRepository
import kotlinx.coroutines.flow.first
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.time.Clock

/**
 * The daily backup, run by WorkManager when the phone is charging on an unmetered network.
 *
 * It never interrupts anyone: a failure is written to [AutoBackupPreferencesRepository] and read
 * back by the Settings screen the next time the user looks. No notification, no sign-in prompt —
 * a backup that failed last night is not worth waking someone for.
 */
class AutoBackupWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params), KoinComponent {

    private val prefsRepo: AutoBackupPreferencesRepository by inject()
    private val backupService: BackupService by inject()
    private val auth: BackupAuth by inject()
    private val runner: BackupJobRunner by inject()

    override suspend fun doWork(): Result {
        val prefs = prefsRepo.prefs.first()
        val signedIn = auth.account.first() != null
        val now = Clock.System.now().toEpochMilliseconds()
        return when {
            // Turned off, signed out, or already backed up today — nothing to retry.
            !AutoBackup.shouldRun(prefs, signedIn = signedIn, now = now) -> Result.success()
            // Never run alongside a backup or restore the user started: a snapshot taken halfway
            // through a restore would record data that was never really on the device.
            runner.state.value is BackupJobState.Running -> Result.retry()
            else -> attemptBackup()
        }
    }

    private suspend fun attemptBackup(): Result =
        runCatching { backupService.backup(BackupProgress.None) }.fold(
            onSuccess = {
                prefsRepo.recordSuccess(Clock.System.now().toEpochMilliseconds())
                Result.success()
            },
            onFailure = {
                // Signed out mid-flight is the one failure the user has to act on; everything else
                // (no network, Drive hiccup) is worth another go on the next run.
                val stillSignedIn = auth.account.first() != null
                prefsRepo.recordFailure(
                    at = Clock.System.now().toEpochMilliseconds(),
                    needsSignIn = !stillSignedIn,
                )
                Result.retry()
            },
        )
}
