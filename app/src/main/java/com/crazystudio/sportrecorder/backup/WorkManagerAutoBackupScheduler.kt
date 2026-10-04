package com.crazystudio.sportrecorder.backup

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/** Arms the daily job through WorkManager, which keeps it across reboots. */
class WorkManagerAutoBackupScheduler(private val context: Context) : AutoBackupScheduler {

    override fun schedule() {
        val constraints = Constraints.Builder()
            // Charging + unmetered: a backup is never worth someone's data plan or battery.
            .setRequiresCharging(true)
            .setRequiredNetworkType(NetworkType.UNMETERED)
            .build()
        val request = PeriodicWorkRequestBuilder<AutoBackupWorker>(1, TimeUnit.DAYS)
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            // KEEP, so re-arming on every app start does not reset the day's countdown.
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    override fun cancel() {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }

    private companion object {
        const val WORK_NAME = "auto-backup"
    }
}
