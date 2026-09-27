package com.crazystudio.sportrecorder.backup

import android.app.NotificationManager
import android.content.Context
import android.os.SystemClock
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.fail
import org.koin.core.context.GlobalContext
import org.koin.core.context.loadKoinModules
import org.koin.dsl.module

/**
 * In-memory [BackupStore] for instrumented tests (the commonTest FakeBackupStore is not on this
 * classpath). Photo bytes are never read: the real store's I/O is covered by its JVM tests.
 * [uploadGate] / [downloadGate] park a job at the start of the corresponding step so a test can
 * observe the service and notifications mid-flight.
 */
class GatedBackupStore : BackupStore {
    data class Upload(val info: SnapshotInfo, val manifestJson: String, val uploadedPhotos: List<String>)

    val uploads = mutableListOf<Upload>()
    private val snapshots = mutableListOf<SnapshotInfo>() // newest-first
    private val manifestsById = mutableMapOf<String, String>()
    private val photos = mutableSetOf<String>()
    private var nextId = 1

    var uploadGate: CompletableDeferred<Unit>? = null
    var downloadGate: CompletableDeferred<Unit>? = null
    var failDownloadPhotos = false
    var pruneCalls = 0
        private set

    fun seedSnapshot(info: SnapshotInfo, manifestJson: String) {
        snapshots.add(0, info)
        manifestsById[info.id] = manifestJson
    }

    override suspend fun listSnapshots(): List<SnapshotInfo> = snapshots.toList()

    override suspend fun uploadSnapshot(
        manifestJson: String,
        photoFileNames: List<String>,
        progress: BackupProgress,
    ): SnapshotInfo {
        uploadGate?.await()
        val newPhotos = photoFileNames.filterNot { it in photos }
        progress.report(BackupStep.UploadingPhotos, 0, newPhotos.size)
        newPhotos.forEachIndexed { index, name ->
            photos.add(name)
            progress.report(BackupStep.UploadingPhotos, index + 1, newPhotos.size)
        }
        progress.report(BackupStep.UploadingManifest, 0, 1)
        val doc = BackupJson.decodeFromString(BackupDocument.serializer(), manifestJson)
        val info = SnapshotInfo(
            id = "snapshot-${nextId++}",
            createdAt = doc.createdAt,
            appVersionName = doc.appVersionName,
            sizeBytes = manifestJson.length.toLong(),
            mealCount = doc.meals.size,
        )
        uploads.add(Upload(info, manifestJson, newPhotos))
        snapshots.add(0, info)
        manifestsById[info.id] = manifestJson
        progress.report(BackupStep.UploadingManifest, 1, 1)
        return info
    }

    override suspend fun downloadManifest(id: String): String =
        manifestsById[id] ?: error("no manifest for $id")

    override suspend fun downloadPhotos(id: String, photoFileNames: List<String>, progress: BackupProgress) {
        downloadGate?.await()
        if (failDownloadPhotos) throw IllegalStateException("simulated photo download failure")
        progress.report(BackupStep.DownloadingPhotos, 0, photoFileNames.size)
        photoFileNames.forEachIndexed { index, _ ->
            progress.report(BackupStep.DownloadingPhotos, index + 1, photoFileNames.size)
        }
    }

    override suspend fun prune(keepLast: Int) {
        pruneCalls++
        while (snapshots.size > keepLast) snapshots.removeAt(snapshots.size - 1)
    }
}

/** Poll [condition] until true or fail after [timeoutMs] with [what] in the message. */
fun awaitUntil(timeoutMs: Long = 10_000, stepMs: Long = 50, what: String, condition: () -> Boolean) {
    val deadline = SystemClock.uptimeMillis() + timeoutMs
    while (SystemClock.uptimeMillis() < deadline) {
        if (condition()) return
        SystemClock.sleep(stepMs)
    }
    fail("Timed out after ${timeoutMs}ms waiting for: $what")
}

/** The posted notification with [id], or null. */
fun Context.activeNotification(id: Int): StatusBarNotification? =
    getSystemService(NotificationManager::class.java).activeNotifications.firstOrNull { it.id == id }

// --- Koin ---------------------------------------------------------------------------------------

/**
 * Point the app's running Koin graph at [store]: fake cloud, real everything else (real
 * BackupJobRunner, real AndroidBackupJobHost, so a job really starts BackupForegroundService).
 * Re-declaring BackupService and BackupJobRunner drops the cached singles, so every test gets a
 * fresh runner. Never unloaded — unloading would delete the app's own definitions for these keys.
 */
fun loadBackupTestModule(store: BackupStore): BackupJobRunner {
    loadKoinModules(
        module {
            single<BackupStore> { store }
            single<AccessTokenProvider> { AccessTokenProvider { "test-token" } }
            single { BackupService(get(), get(), get(), get(), get(), get(), appVersionName = "test") }
            single { BackupJobRunner(get(), get()) }
        },
    )
    return GlobalContext.get().get<BackupJobRunner>()
}
