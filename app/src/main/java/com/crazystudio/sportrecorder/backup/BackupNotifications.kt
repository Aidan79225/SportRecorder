package com.crazystudio.sportrecorder.backup

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.crazystudio.sportrecorder.MainActivity
import com.crazystudio.sportrecorder.R
import com.crazystudio.sportrecorder.shared.resources.Res
import com.crazystudio.sportrecorder.shared.resources.backup_cancel
import com.crazystudio.sportrecorder.shared.resources.backup_msg_backup_complete
import com.crazystudio.sportrecorder.shared.resources.backup_msg_cancelled
import com.crazystudio.sportrecorder.shared.resources.backup_msg_failed
import com.crazystudio.sportrecorder.shared.resources.backup_msg_restore_complete
import com.crazystudio.sportrecorder.shared.resources.backup_msg_safety_backup_failed
import com.crazystudio.sportrecorder.shared.resources.backup_msg_schema_too_new
import com.crazystudio.sportrecorder.shared.resources.backup_step_applying
import com.crazystudio.sportrecorder.shared.resources.backup_step_downloading_manifest
import com.crazystudio.sportrecorder.shared.resources.backup_step_downloading_photos
import com.crazystudio.sportrecorder.shared.resources.backup_step_preparing
import com.crazystudio.sportrecorder.shared.resources.backup_step_pruning
import com.crazystudio.sportrecorder.shared.resources.backup_step_safety_backup
import com.crazystudio.sportrecorder.shared.resources.backup_step_uploading_manifest
import com.crazystudio.sportrecorder.shared.resources.backup_step_uploading_photos
import org.jetbrains.compose.resources.getString

private const val CHANNEL_ID = "backup_progress"

/**
 * Builds the two notifications the backup job uses: an ongoing progress card while it runs and a
 * silent, auto-cancel result afterwards. Copy comes from the shared Compose resources so the
 * screen and the notification always say the same thing. Deliberately not a reminder: it exists
 * only for a job the user started, and never asks them to back up.
 */
class BackupNotifications(private val context: Context) {

    // Built once: progress re-posts on every step/photo, and these never change.
    private val openAppIntent: PendingIntent by lazy {
        val intent = Intent(context, MainActivity::class.java).apply {
            // SINGLE_TOP: bring the running MainActivity forward instead of recreating it.
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        PendingIntent.getActivity(
            context,
            REQUEST_OPEN,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private val cancelIntent: PendingIntent by lazy {
        val intent = Intent(context, BackupForegroundService::class.java)
            .setAction(BackupForegroundService.ACTION_CANCEL)
        PendingIntent.getService(
            context,
            REQUEST_CANCEL,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.backup_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
    }

    /**
     * The first foreground card, built synchronously (no shared-resource lookups) so the service can
     * call startForeground promptly. Indeterminate, no caption, no action: the observer replaces it
     * with the full card on its first emission. [kind] null → generic "backing up" title.
     */
    fun initial(kind: BackupJobKind?): Notification =
        progressBuilder(kind)
            .setProgress(0, 0, true)
            .build()

    /** Ongoing progress for a running job, with a cancel action except while applying (not cancellable). */
    suspend fun progress(state: BackupJobState.Running): Notification {
        val builder = progressBuilder(state.kind)
            .setContentText(stepCaption(state))
            .setProgress(state.total, state.done, state.total == 0)
        if (state.step != BackupStep.Applying) {
            builder.addAction(0, getString(Res.string.backup_cancel), cancelIntent)
        }
        return builder.build()
    }

    /** One-shot result. Silent, auto-cancel; tapping opens the app. */
    suspend fun result(state: BackupJobState.Finished): Notification {
        val text = when (state.outcome) {
            BackupOutcome.Completed ->
                if (state.kind == BackupJobKind.Backup) {
                    Res.string.backup_msg_backup_complete
                } else {
                    Res.string.backup_msg_restore_complete
                }
            BackupOutcome.Cancelled -> Res.string.backup_msg_cancelled
            BackupOutcome.SchemaTooNew -> Res.string.backup_msg_schema_too_new
            BackupOutcome.SafetyBackupFailed -> Res.string.backup_msg_safety_backup_failed
            BackupOutcome.Failed -> Res.string.backup_msg_failed
        }
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_baseline_download_24)
            .setContentTitle(getString(text))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(openAppIntent)
            .build()
    }

    /** The screen already showed the outcome as a snackbar; drop the duplicate. */
    fun cancelResult() = NotificationManagerCompat.from(context).cancel(RESULT_ID)

    private fun progressBuilder(kind: BackupJobKind?): NotificationCompat.Builder {
        val title = when (kind) {
            BackupJobKind.Restore -> R.string.backup_notif_restoring
            else -> R.string.backup_notif_backing_up
        }
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_baseline_download_24)
            .setContentTitle(context.getString(title))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(openAppIntent)
    }

    private suspend fun stepCaption(state: BackupJobState.Running): String = when (state.step) {
        BackupStep.Preparing -> getString(Res.string.backup_step_preparing)
        BackupStep.SafetyBackup -> getString(Res.string.backup_step_safety_backup)
        BackupStep.UploadingPhotos -> getString(Res.string.backup_step_uploading_photos, state.done, state.total)
        BackupStep.UploadingManifest -> getString(Res.string.backup_step_uploading_manifest)
        BackupStep.Pruning -> getString(Res.string.backup_step_pruning)
        BackupStep.DownloadingManifest -> getString(Res.string.backup_step_downloading_manifest)
        BackupStep.DownloadingPhotos -> getString(Res.string.backup_step_downloading_photos, state.done, state.total)
        BackupStep.Applying -> getString(Res.string.backup_step_applying)
    }

    companion object {
        const val PROGRESS_ID = 3001
        const val RESULT_ID = 3002
        private const val REQUEST_OPEN = 30
        private const val REQUEST_CANCEL = 31
    }
}
