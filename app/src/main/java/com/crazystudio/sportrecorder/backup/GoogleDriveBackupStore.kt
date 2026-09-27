package com.crazystudio.sportrecorder.backup

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

private const val MIME_JSON = "application/json"
private const val MIME_WEBP = "image/webp"

private const val KIND = "kind"
private const val KIND_MANIFEST = "manifest"
private const val KIND_PHOTO = "photo"
private const val KEY_SNAPSHOT_ID = "snapshotId"
private const val KEY_CREATED_AT = "createdAt"
private const val KEY_APP_VERSION = "appVersionName"
private const val KEY_MEAL_COUNT = "mealCount"

/** Concurrent photo transfers. Drive tolerates this comfortably; it turns a serial minute into seconds. */
private const val PARALLEL_TRANSFERS = 4

/**
 * Android [BackupStore] backed by Google Drive's `appDataFolder` through [DriveRestClient].
 *
 * Storage model: every manifest and photo is a flat file in `appDataFolder`, tagged through Drive
 * `appProperties`. A manifest carries `kind=manifest` plus the snapshot's id/createdAt/appVersionName
 * /mealCount; a photo carries `kind=photo` and is deduped by file name (photos are immutable and
 * shared across snapshots). The manifest is always uploaded last so it acts as the commit marker.
 *
 * [localPhoto] maps a photo file name to its file on this device (`PhotoStorage.fileFor` in
 * production, a temp dir in tests).
 */
class GoogleDriveBackupStore(
    private val tokens: AccessTokenProvider,
    private val localPhoto: (fileName: String) -> File,
    private val drive: DriveRestClient = DriveRestClient(),
) : BackupStore {

    override suspend fun listSnapshots(): List<SnapshotInfo> {
        val token = tokens.accessToken()
        return drive.listAppDataFiles(token)
            .filter { it.appProperties[KIND] == KIND_MANIFEST }
            .map { file ->
                SnapshotInfo(
                    id = file.appProperties[KEY_SNAPSHOT_ID].orEmpty(),
                    createdAt = file.appProperties[KEY_CREATED_AT]?.toLongOrNull() ?: 0L,
                    appVersionName = file.appProperties[KEY_APP_VERSION].orEmpty(),
                    sizeBytes = file.sizeBytes,
                    mealCount = file.appProperties[KEY_MEAL_COUNT]?.toIntOrNull(),
                )
            }
            .sortedByDescending { it.createdAt }
    }

    override suspend fun uploadSnapshot(
        manifestJson: String,
        photoFileNames: List<String>,
        progress: BackupProgress,
    ): SnapshotInfo {
        val token = tokens.accessToken()
        // Parse first so a malformed manifest fails before any photo is uploaded (no orphans).
        val doc = BackupJson.decodeFromString(BackupDocument.serializer(), manifestJson)
        val existingPhotos = drive.listAppDataFiles(token)
            .filter { it.appProperties[KIND] == KIND_PHOTO }
            .map { it.name }
            .toSet()
        val missing = photoFileNames.filterNot { it in existingPhotos }
        transferAll(missing, BackupStep.UploadingPhotos, progress) { name ->
            drive.uploadMultipart(token, name, MIME_WEBP, mapOf(KIND to KIND_PHOTO), localPhoto(name).readBytes())
        }

        progress.report(BackupStep.UploadingManifest, 0, 1)
        val snapshotId = UUID.randomUUID().toString()
        val manifestBytes = manifestJson.encodeToByteArray()
        val props = mapOf(
            KIND to KIND_MANIFEST,
            KEY_SNAPSHOT_ID to snapshotId,
            KEY_CREATED_AT to doc.createdAt.toString(),
            KEY_APP_VERSION to doc.appVersionName,
            KEY_MEAL_COUNT to doc.meals.size.toString(),
        )
        drive.uploadMultipart(token, "manifest-$snapshotId.json", MIME_JSON, props, manifestBytes)
        progress.report(BackupStep.UploadingManifest, 1, 1)
        return SnapshotInfo(snapshotId, doc.createdAt, doc.appVersionName, manifestBytes.size.toLong(), doc.meals.size)
    }

    override suspend fun downloadManifest(id: String): String {
        val token = tokens.accessToken()
        val manifest = findManifest(drive.listAppDataFiles(token), id)
        return drive.downloadBytes(token, manifest.id).decodeToString()
    }

    override suspend fun downloadPhotos(id: String, photoFileNames: List<String>, progress: BackupProgress) {
        // Guard first: names come from a downloaded manifest; never let one escape the photos dir,
        // not even for the "does it exist locally" probe below.
        photoFileNames.forEach { name ->
            require(!name.contains('/') && !name.contains('\\')) {
                "Snapshot $id has an illegal photo file name: $name"
            }
        }
        // Photos are immutable webp with unique names: if it is here, it is the right bytes.
        val missing = photoFileNames.filterNot { name -> localPhoto(name).let { it.exists() && it.length() > 0 } }
        if (missing.isEmpty()) {
            progress.report(BackupStep.DownloadingPhotos, 0, 0)
            return
        }
        val token = tokens.accessToken()
        val photoIdByName = drive.listAppDataFiles(token)
            .filter { it.appProperties[KIND] == KIND_PHOTO }
            .associate { it.name to it.id }
        transferAll(missing, BackupStep.DownloadingPhotos, progress) { name ->
            val fileId = photoIdByName[name]
                ?: throw IOException("Snapshot $id references a photo missing from Drive: $name")
            localPhoto(name).writeBytes(drive.downloadBytes(token, fileId))
        }
    }

    override suspend fun prune(keepLast: Int) {
        val token = tokens.accessToken()
        val files = drive.listAppDataFiles(token)
        val manifests = files
            .filter { it.appProperties[KIND] == KIND_MANIFEST }
            .sortedByDescending { it.appProperties[KEY_CREATED_AT]?.toLongOrNull() ?: 0L }
        manifests.drop(keepLast).forEach { drive.deleteFile(token, it.id) }
        // Re-reading the kept manifests is cheap (a few KB each) next to photo transfer; the
        // orphan sweep below is what keeps Drive from growing forever, so keep it.
        val keptPhotoNames = manifests.take(keepLast).flatMap { manifest ->
            val doc = BackupJson.decodeFromString(
                BackupDocument.serializer(),
                drive.downloadBytes(token, manifest.id).decodeToString(),
            )
            doc.meals.flatMap { meal -> meal.photos.map { it.fileName } }
        }.toSet()
        files
            .filter { it.appProperties[KIND] == KIND_PHOTO && it.name !in keptPhotoNames }
            .forEach { drive.deleteFile(token, it.id) }
    }

    private fun findManifest(files: List<DriveRestClient.DriveFile>, id: String): DriveRestClient.DriveFile =
        files.firstOrNull {
            it.appProperties[KIND] == KIND_MANIFEST && it.appProperties[KEY_SNAPSHOT_ID] == id
        } ?: throw IOException("No manifest found in Drive for snapshot $id")

    /**
     * Run [action] for every name, at most [PARALLEL_TRANSFERS] at a time, reporting [step] as each
     * completes. Structured concurrency: one failure cancels the siblings and rethrows, so a
     * half-uploaded photo set never gets a manifest.
     */
    private suspend fun transferAll(
        names: List<String>,
        step: BackupStep,
        progress: BackupProgress,
        action: suspend (String) -> Unit,
    ) {
        progress.report(step, 0, names.size)
        if (names.isEmpty()) return
        val gate = Semaphore(PARALLEL_TRANSFERS)
        val done = AtomicInteger(0)
        coroutineScope {
            names.map { name ->
                async(Dispatchers.IO) {
                    gate.withPermit { action(name) }
                    progress.report(step, done.incrementAndGet(), names.size)
                }
            }.awaitAll()
        }
    }
}
