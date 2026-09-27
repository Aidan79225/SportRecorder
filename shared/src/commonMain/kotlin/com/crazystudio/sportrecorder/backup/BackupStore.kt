package com.crazystudio.sportrecorder.backup

/** Metadata describing one stored snapshot. [mealCount] is null for snapshots written before it was recorded. */
data class SnapshotInfo(
    val id: String,
    val createdAt: Long,
    val appVersionName: String,
    val sizeBytes: Long,
    val mealCount: Int? = null,
)

/**
 * Cloud boundary for snapshots — no Google specifics. The store owns the "manifest-last commit"
 * and incremental-photo-skip behavior; [uploadSnapshot] receives every referenced photo name and
 * decides which are already present. Every transfer reports through [BackupProgress].
 */
interface BackupStore {
    /** Committed snapshots, newest-first. */
    suspend fun listSnapshots(): List<SnapshotInfo>

    /**
     * Upload referenced photos (skipping ones already stored), then the manifest last.
     * Reports [BackupStep.UploadingPhotos] per photo and [BackupStep.UploadingManifest] 0/1 → 1/1.
     */
    suspend fun uploadSnapshot(
        manifestJson: String,
        photoFileNames: List<String>,
        progress: BackupProgress,
    ): SnapshotInfo

    /** The raw manifest JSON for [id]. */
    suspend fun downloadManifest(id: String): String

    /**
     * Download the photos [photoFileNames] referenced by snapshot [id] into the local photo store,
     * skipping any already present locally. Reports [BackupStep.DownloadingPhotos].
     */
    suspend fun downloadPhotos(id: String, photoFileNames: List<String>, progress: BackupProgress)

    /** Keep the newest [keepLast] snapshots; delete older ones and orphan photos. */
    suspend fun prune(keepLast: Int)
}
