package com.crazystudio.sportrecorder.backup.fakes

import com.crazystudio.sportrecorder.backup.BackupStore
import com.crazystudio.sportrecorder.backup.SnapshotInfo

/**
 * In-memory [BackupStore]. Records uploads, models incremental photo skip via [existingPhotos],
 * and can simulate a photo-download failure ([failDownloadPhotos]) for the restore-failure test.
 *
 * Snapshots, manifests and already-uploaded photos are partitioned per [account] — the real store
 * is scoped to one Google account's Drive appDataFolder, so switching accounts must reveal a
 * different set of snapshots. Snapshot ids stay globally unique, so another account's id is
 * genuinely unreadable rather than accidentally colliding.
 */
class FakeBackupStore : BackupStore {
    data class Upload(val info: SnapshotInfo, val manifestJson: String, val uploadedPhotos: List<String>)

    private class AccountState {
        val snapshots = mutableListOf<SnapshotInfo>() // newest-first
        val manifestsById = mutableMapOf<String, String>()
        val photos = mutableSetOf<String>()
    }

    /** The signed-in account this store is currently scoped to. Set it to model a switch. */
    var account: String = DEFAULT_ACCOUNT

    private val accounts = mutableMapOf<String, AccountState>()
    private val current: AccountState get() = accounts.getOrPut(account) { AccountState() }

    /** Every uploadSnapshot call, in order, across all accounts. [uploadedPhotos] excludes skips. */
    val uploads = mutableListOf<Upload>()

    /** Photos considered already-present for the current [account] (seed before a test). */
    val existingPhotos: MutableSet<String> get() = current.photos

    /** Snapshot ids whose downloadPhotos was invoked. */
    val downloadedPhotosFor = mutableListOf<String>()

    /** Last keepLast passed to prune, or null if never pruned. */
    var pruneKeepLast: Int? = null
        private set

    /** When true, [downloadPhotos] throws (simulates an interrupted restore). */
    var failDownloadPhotos = false

    private var nextId = 1

    /** Pre-seed a committed snapshot for the current [account] (added as newest). */
    fun seedSnapshot(info: SnapshotInfo, manifestJson: String) {
        current.snapshots.add(0, info)
        current.manifestsById[info.id] = manifestJson
    }

    override suspend fun listSnapshots(): List<SnapshotInfo> = current.snapshots.toList()

    override suspend fun uploadSnapshot(manifestJson: String, photoFileNames: List<String>): SnapshotInfo {
        val state = current
        val newPhotos = photoFileNames.filterNot { it in state.photos }
        state.photos.addAll(newPhotos)
        val info = SnapshotInfo(
            id = "snapshot-${nextId++}",
            createdAt = 0L,
            appVersionName = "",
            sizeBytes = manifestJson.length.toLong(),
        )
        uploads.add(Upload(info, manifestJson, newPhotos))
        state.snapshots.add(0, info)
        state.manifestsById[info.id] = manifestJson
        return info
    }

    override suspend fun downloadManifest(id: String): String =
        current.manifestsById[id] ?: error("no manifest for $id")

    override suspend fun downloadPhotos(id: String) {
        if (failDownloadPhotos) throw IllegalStateException("simulated photo download failure")
        downloadedPhotosFor.add(id)
    }

    override suspend fun prune(keepLast: Int) {
        pruneKeepLast = keepLast
        val state = current
        while (state.snapshots.size > keepLast) state.snapshots.removeAt(state.snapshots.size - 1)
    }

    private companion object {
        const val DEFAULT_ACCOUNT = "primary@example.com"
    }
}
