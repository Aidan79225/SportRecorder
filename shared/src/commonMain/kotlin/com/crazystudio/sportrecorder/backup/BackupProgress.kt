package com.crazystudio.sportrecorder.backup

/** The coarse stage a backup/restore job is in. The UI maps each to a caption. */
enum class BackupStep {
    Preparing,
    SafetyBackup,
    UploadingPhotos,
    UploadingManifest,
    Pruning,
    DownloadingManifest,
    DownloadingPhotos,
    Applying,
}

/**
 * Progress sink for one backup or restore job. [done]/[total] count files inside the current
 * [step]; `total == 0` means "no countable work in this step" (show indeterminate).
 * Implementations must tolerate calls from any thread.
 */
fun interface BackupProgress {
    fun report(step: BackupStep, done: Int, total: Int)

    companion object {
        /** Discards every report. Default for callers that don't show progress (tests, flows). */
        val None: BackupProgress = BackupProgress { _, _, _ -> }
    }
}
