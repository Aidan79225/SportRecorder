package com.crazystudio.sportrecorder.backup.fakes

import com.crazystudio.sportrecorder.backup.BackupStore
import com.crazystudio.sportrecorder.backup.SnapshotInfo

/**
 * In-memory [BackupStore]. Records uploads, models incremental photo skip via [existingPhotos],
 * and can simulate a photo-download failure ([failDownloadPhotos]) for the restore-failure test.
 */
class FakeBackupStore : BackupStore {
    data class Upload(val info: SnapshotInfo, val manifestJson: String, val uploadedPhotos: List<String>)

    /** Every uploadSnapshot call, in order. [uploadedPhotos] excludes photos already present. */
    val uploads = mutableListOf<Upload>()

    /** Photos considered already-present in the cloud (seed before a test to exercise skip). */
    val existingPhotos = mutableSetOf<String>()

    /** Snapshot ids whose downloadPhotos was invoked. */
    val downloadedPhotosFor = mutableListOf<String>()

    /** Last keepLast passed to prune, or null if never pruned. */
    var pruneKeepLast: Int? = null
        private set

    /** When true, [downloadPhotos] throws (simulates an interrupted restore). */
    var failDownloadPhotos = false

    private var nextId = 1
    private val snapshots = mutableListOf<SnapshotInfo>() // newest-first
    private val manifestsById = mutableMapOf<String, String>()

    /** Pre-seed a committed snapshot for restore/list tests (added as newest). */
    fun seedSnapshot(info: SnapshotInfo, manifestJson: String) {
        snapshots.add(0, info)
        manifestsById[info.id] = manifestJson
    }

    override suspend fun listSnapshots(): List<SnapshotInfo> = snapshots.toList()

    override suspend fun uploadSnapshot(manifestJson: String, photoFileNames: List<String>): SnapshotInfo {
        val newPhotos = photoFileNames.filterNot { it in existingPhotos }
        existingPhotos.addAll(newPhotos)
        val info = SnapshotInfo(
            id = "snapshot-${nextId++}",
            createdAt = 0L,
            appVersionName = "",
            sizeBytes = manifestJson.length.toLong(),
        )
        uploads.add(Upload(info, manifestJson, newPhotos))
        snapshots.add(0, info)
        manifestsById[info.id] = manifestJson
        return info
    }

    override suspend fun downloadManifest(id: String): String =
        manifestsById[id] ?: error("no manifest for $id")

    override suspend fun downloadPhotos(id: String) {
        if (failDownloadPhotos) throw IllegalStateException("simulated photo download failure")
        downloadedPhotosFor.add(id)
    }

    override suspend fun prune(keepLast: Int) {
        pruneKeepLast = keepLast
        while (snapshots.size > keepLast) snapshots.removeAt(snapshots.size - 1)
    }
}
