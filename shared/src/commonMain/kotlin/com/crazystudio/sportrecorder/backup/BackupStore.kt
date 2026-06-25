package com.crazystudio.sportrecorder.backup

/** Metadata describing one stored snapshot. */
data class SnapshotInfo(
    val id: String,
    val createdAt: Long,
    val appVersionName: String,
    val sizeBytes: Long,
)

/**
 * Cloud boundary for snapshots — no Google specifics. The store owns the "manifest-last commit"
 * and incremental-photo-skip behavior; [uploadSnapshot] receives every referenced photo name and
 * decides which are already present.
 */
interface BackupStore {
    /** Committed snapshots, newest-first. */
    suspend fun listSnapshots(): List<SnapshotInfo>

    /** Upload referenced photos (skipping ones already stored), then the manifest last. */
    suspend fun uploadSnapshot(manifestJson: String, photoFileNames: List<String>): SnapshotInfo

    /** The raw manifest JSON for [id]. */
    suspend fun downloadManifest(id: String): String

    /** Download every photo referenced by snapshot [id] into the local photo store. */
    suspend fun downloadPhotos(id: String)

    /** Keep the newest [keepLast] snapshots; delete older ones and orphan photos. */
    suspend fun prune(keepLast: Int)
}
