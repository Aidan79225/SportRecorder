package com.crazystudio.sportrecorder.backup

import android.annotation.SuppressLint
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.koin.android.ext.android.inject

/**
 * Keeps the process alive while a [BackupJobRunner] job runs and mirrors its state into a
 * progress notification. It owns no job logic: the runner is the source of truth, this service
 * just observes it and stops itself when the job is over (or was already over when it started).
 */
class BackupForegroundService : Service() {

    private val runner: BackupJobRunner by inject()
    private val notifications by lazy { BackupNotifications(this) }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var observing: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL) {
            runner.cancel()
            return START_NOT_STICKY
        }
        notifications.ensureChannel()
        // startForeground must happen promptly after startForegroundService; the first card is the
        // cheap generic one, the collector below replaces it with real progress.
        val initial = runBlocking { notifications.progress(runner.state.value as? BackupJobState.Running) }
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        } else {
            0
        }
        ServiceCompat.startForeground(this, BackupNotifications.PROGRESS_ID, initial, type)
        if (observing == null) observing = scope.launch { observeRunner() }
        return START_NOT_STICKY
    }

    // FGS notifications are shown by the system; if the user denied POST_NOTIFICATIONS the call is
    // a silent no-op, which is the intended behavior (the job runs anyway).
    @SuppressLint("MissingPermission")
    private suspend fun observeRunner() {
        runner.state.collect { state ->
            when (state) {
                is BackupJobState.Running ->
                    NotificationManagerCompat.from(this).notify(
                        BackupNotifications.PROGRESS_ID,
                        notifications.progress(state),
                    )
                is BackupJobState.Finished -> {
                    NotificationManagerCompat.from(this).notify(
                        BackupNotifications.RESULT_ID,
                        notifications.result(state),
                    )
                    finish()
                }
                BackupJobState.Idle -> finish() // started after the job was acknowledged: nothing to show
            }
        }
    }

    private fun finish() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_CANCEL = "com.crazystudio.sportrecorder.backup.CANCEL"
    }
}
