package com.crazystudio.sportrecorder.backup

/**
 * Arms or disarms the periodic background backup. The *when* is a platform concern (Android uses
 * WorkManager, which also owns the charging / unmetered constraints and surviving a reboot), so
 * commonMain only ever says whether it should be armed at all.
 */
interface AutoBackupScheduler {
    fun schedule()

    fun cancel()
}
