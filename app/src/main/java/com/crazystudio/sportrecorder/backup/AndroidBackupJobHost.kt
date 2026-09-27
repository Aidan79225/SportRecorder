package com.crazystudio.sportrecorder.backup

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat

private const val TAG = "BackupJobHost"

/**
 * Android [BackupJobHost]: hosts every job in [BackupForegroundService] so it survives the user
 * leaving the screen or turning it off. The service stops itself when it observes the job finish,
 * so [onJobFinished] has nothing to do — stopping it from here would race the result notification.
 */
class AndroidBackupJobHost(private val context: Context) : BackupJobHost {
    override fun onJobStarted() {
        // Only ever called from a foreground tap, so the Android 12+ background-start restriction
        // should not bite; if it ever does, the job still runs in-process — just without the service.
        runCatching {
            ContextCompat.startForegroundService(context, Intent(context, BackupForegroundService::class.java))
        }.onFailure { Log.w(TAG, "could not start the backup foreground service", it) }
    }

    override fun onJobFinished() = Unit
}
