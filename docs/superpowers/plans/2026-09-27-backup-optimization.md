# Backup Optimization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Drive backup/restore keeps running when the user leaves the screen, shows real progress (screen + notification), transfers photos in parallel, pages the Drive listing, and restores only after saving a safety snapshot of the current data.

**Architecture:** A `commonMain` `BackupJobRunner` owns an app-scoped coroutine and a `StateFlow<BackupJobState>`; `BackupService`/`BackupStore` report progress through a `BackupProgress` callback. On Android a `BackupForegroundService` mirrors the runner state into a notification, and `GoogleDriveBackupStore` is split into a JVM-testable `DriveRestClient` (paging, cancellable OkHttp calls) plus the store (parallel transfers, skip-local, `mealCount`).

**Tech Stack:** Kotlin Multiplatform, kotlinx.coroutines (`Semaphore`, `NonCancellable`), kotlinx.serialization, OkHttp 4.12 (+ `mockwebserver` test-only), Compose Multiplatform + Compose Resources, Koin 4.1, Android foreground service (`dataSync`).

**Spec:** `docs/superpowers/specs/2026-09-27-backup-optimization-design.md`

## Global Constraints

- Build gate (run before every commit that touches Kotlin): `./gradlew assembleDebug testDebugUnitTest :app:detekt :app:lintDebug :shared:jvmTest` (`JAVA_HOME` must point to the Android Studio JBR). On Windows use `.\gradlew.bat`.
- No new `:shared` (KMP) dependencies. The only new dependency is `com.squareup.okhttp3:mockwebserver:4.12.0`, `testImplementation` in `:app` only.
- detekt: `MagicNumber` is enforced outside `ui/` and tests → name every constant. `TooManyFunctions` threshold 11 per class/file. `MaxLineLength` 120. `LongParameterList` 6 (functions) / 7 (constructors). `ReturnCount` max 2. `TooGenericExceptionCaught` is active → prefer `runCatching`.
- Compose Resources string args are positional (`%1$d`, `%2$s`); both `values/strings.xml` and `values-zh-rTW/strings.xml` in `shared/src/commonMain/composeResources/` must get every new key.
- Copy tone: gentle, reassuring, never a verdict or a nag (see spec 初衷對照). Result notifications are silent and auto-cancel.
- Existing callers `backup()` / `restore(id)` must keep compiling (default `BackupProgress.None`).
- Commit messages end with `Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>`.

---

## File map

**`:shared` commonMain** (`shared/src/commonMain/kotlin/com/crazystudio/sportrecorder/`)
- Create `backup/BackupProgress.kt` — `BackupStep`, `BackupProgress`.
- Create `backup/BackupJobRunner.kt` — `BackupJobKind`, `BackupOutcome`, `BackupJobState`, `BackupJobHost`, `BackupJobRunner`.
- Modify `backup/BackupStore.kt` — `SnapshotInfo.mealCount`, progress-aware signatures.
- Modify `backup/BackupService.kt` — progress, `backupInternal(prune)`, safety snapshot, `NonCancellable` apply.
- Modify `ui/backup/BackupUiState.kt`, `ui/backup/BackupViewModel.kt`, `ui/backup/BackupScreen.kt`.
- Modify `shared/src/commonMain/composeResources/values/strings.xml` + `values-zh-rTW/strings.xml`.

**`:shared` commonTest** (`shared/src/commonTest/kotlin/com/crazystudio/sportrecorder/`)
- Modify `backup/fakes/FakeBackupStore.kt`; create `backup/fakes/FakeBackupJobHost.kt`.
- Modify `backup/BackupServiceTest.kt`, `ui/backup/BackupViewModelTest.kt`; create `backup/BackupJobRunnerTest.kt`.

**`:app`** (`app/src/main/java/com/crazystudio/sportrecorder/`)
- Create `backup/AccessTokenProvider.kt`, `backup/DriveRestClient.kt`, `backup/AndroidBackupJobHost.kt`, `backup/BackupNotifications.kt`, `backup/BackupForegroundService.kt`.
- Modify `backup/GoogleDriveBackupStore.kt`, `backup/GoogleBackupAuth.kt`, `di/AppModule.kt`, `ui/backup/BackupRoute.kt`, `app/src/main/AndroidManifest.xml`, `app/src/main/res/values/strings.xml`, `app/src/main/res/values-zh-rTW/strings.xml`, `app/build.gradle.kts`, `gradle/libs.versions.toml`.
- Tests: create `app/src/test/java/com/crazystudio/sportrecorder/backup/DriveRestClientTest.kt`, `GoogleDriveBackupStoreTest.kt`.

**Docs**: `docs/DEVELOPMENT.md`, `docs/superpowers/specs/2026-06-23-drive-backup-design.md`.

---

### Task 1: Progress model and progress-aware store/service (commonMain)

**Files:**
- Create: `shared/src/commonMain/kotlin/com/crazystudio/sportrecorder/backup/BackupProgress.kt`
- Modify: `shared/src/commonMain/kotlin/com/crazystudio/sportrecorder/backup/BackupStore.kt`
- Modify: `shared/src/commonMain/kotlin/com/crazystudio/sportrecorder/backup/BackupService.kt`
- Modify: `shared/src/commonTest/kotlin/com/crazystudio/sportrecorder/backup/fakes/FakeBackupStore.kt`
- Test: `shared/src/commonTest/kotlin/com/crazystudio/sportrecorder/backup/BackupServiceTest.kt`

**Interfaces:**
- Produces:
  - `enum class BackupStep { Preparing, SafetyBackup, UploadingPhotos, UploadingManifest, Pruning, DownloadingManifest, DownloadingPhotos, Applying }`
  - `fun interface BackupProgress { fun report(step: BackupStep, done: Int, total: Int) }` with `BackupProgress.None`
  - `SnapshotInfo(id, createdAt, appVersionName, sizeBytes, mealCount: Int? = null)`
  - `BackupStore.uploadSnapshot(manifestJson: String, photoFileNames: List<String>, progress: BackupProgress): SnapshotInfo`
  - `BackupStore.downloadPhotos(id: String, photoFileNames: List<String>, progress: BackupProgress)`
  - `BackupService.backup(progress: BackupProgress = BackupProgress.None): SnapshotInfo`
  - `BackupService.restore(snapshotId: String, progress: BackupProgress = BackupProgress.None)` (restore body is finished in Task 2; this task only threads progress through)

- [ ] **Step 1: Create the progress model**

`shared/src/commonMain/kotlin/com/crazystudio/sportrecorder/backup/BackupProgress.kt`:

```kotlin
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
```

- [ ] **Step 2: Extend `SnapshotInfo` and the store interface**

Replace the whole of `BackupStore.kt`:

```kotlin
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
```

- [ ] **Step 3: Update `FakeBackupStore` for the new signatures and progress**

Replace `FakeBackupStore.kt` with:

```kotlin
package com.crazystudio.sportrecorder.backup.fakes

import com.crazystudio.sportrecorder.backup.BackupDocument
import com.crazystudio.sportrecorder.backup.BackupJson
import com.crazystudio.sportrecorder.backup.BackupProgress
import com.crazystudio.sportrecorder.backup.BackupStep
import com.crazystudio.sportrecorder.backup.BackupStore
import com.crazystudio.sportrecorder.backup.SnapshotInfo
import kotlinx.coroutines.CompletableDeferred

/**
 * In-memory [BackupStore]. Records uploads, models incremental photo skip via [existingPhotos],
 * reports progress like the real store, and can simulate a photo-download failure
 * ([failDownloadPhotos]) or hold a download open ([downloadGate]) for cancellation tests.
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

    /** Photo names passed to the most recent downloadPhotos call. */
    var lastDownloadedPhotoNames: List<String> = emptyList()
        private set

    /** Last keepLast passed to prune, or null if never pruned. */
    var pruneKeepLast: Int? = null
        private set

    /** When true, [downloadPhotos] throws (simulates an interrupted restore). */
    var failDownloadPhotos = false

    /** When set, [downloadPhotos] suspends on it first — complete it (or cancel the caller) to continue. */
    var downloadGate: CompletableDeferred<Unit>? = null

    private var nextId = 1

    /** Pre-seed a committed snapshot for the current [account] (added as newest). */
    fun seedSnapshot(info: SnapshotInfo, manifestJson: String) {
        current.snapshots.add(0, info)
        current.manifestsById[info.id] = manifestJson
    }

    override suspend fun listSnapshots(): List<SnapshotInfo> = current.snapshots.toList()

    override suspend fun uploadSnapshot(
        manifestJson: String,
        photoFileNames: List<String>,
        progress: BackupProgress,
    ): SnapshotInfo {
        val state = current
        val newPhotos = photoFileNames.filterNot { it in state.photos }
        progress.report(BackupStep.UploadingPhotos, 0, newPhotos.size)
        newPhotos.forEachIndexed { index, name ->
            state.photos.add(name)
            progress.report(BackupStep.UploadingPhotos, index + 1, newPhotos.size)
        }
        progress.report(BackupStep.UploadingManifest, 0, 1)
        val doc = BackupJson.decodeFromString(BackupDocument.serializer(), manifestJson)
        val info = SnapshotInfo(
            id = "snapshot-${nextId++}",
            createdAt = 0L,
            appVersionName = "",
            sizeBytes = manifestJson.length.toLong(),
            mealCount = doc.meals.size,
        )
        uploads.add(Upload(info, manifestJson, newPhotos))
        state.snapshots.add(0, info)
        state.manifestsById[info.id] = manifestJson
        progress.report(BackupStep.UploadingManifest, 1, 1)
        return info
    }

    override suspend fun downloadManifest(id: String): String =
        current.manifestsById[id] ?: error("no manifest for $id")

    override suspend fun downloadPhotos(id: String, photoFileNames: List<String>, progress: BackupProgress) {
        downloadGate?.await()
        if (failDownloadPhotos) throw IllegalStateException("simulated photo download failure")
        downloadedPhotosFor.add(id)
        lastDownloadedPhotoNames = photoFileNames
        progress.report(BackupStep.DownloadingPhotos, 0, photoFileNames.size)
        photoFileNames.forEachIndexed { index, _ ->
            progress.report(BackupStep.DownloadingPhotos, index + 1, photoFileNames.size)
        }
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
```

- [ ] **Step 4: Write the failing progress test**

Append to `BackupServiceTest.kt` (inside the class), plus add `import com.crazystudio.sportrecorder.backup.fakes.FakeBackupStore` is already present; add `kotlin.test.assertEquals` is present:

```kotlin
    private class RecordingProgress : BackupProgress {
        val reports = mutableListOf<Triple<BackupStep, Int, Int>>()
        override fun report(step: BackupStep, done: Int, total: Int) { reports.add(Triple(step, done, total)) }
        val steps get() = reports.map { it.first }.distinct()
    }

    @Test fun backup_reportsStepsInOrderWithPhotoCounts() = runTest {
        val eat = FakeEatRecordRepository(
            listOf(
                EatRecord(1, 1_700L, null, "n", listOf(EatPhoto(7, "a.webp", 1_701L))),
                EatRecord(2, 1_800L, null, "m", listOf(EatPhoto(8, "b.webp", 1_801L))),
            ),
        )
        val progress = RecordingProgress()
        service(eat, FakeBackupStore()).backup(progress)

        assertEquals(
            listOf(BackupStep.Preparing, BackupStep.UploadingPhotos, BackupStep.UploadingManifest, BackupStep.Pruning),
            progress.steps,
        )
        assertEquals(Triple(BackupStep.UploadingPhotos, 2, 2), progress.reports.last { it.first == BackupStep.UploadingPhotos })
        assertEquals(Triple(BackupStep.UploadingManifest, 1, 1), progress.reports.last { it.first == BackupStep.UploadingManifest })
    }
```

- [ ] **Step 5: Run the test to verify it fails**

Run: `.\gradlew.bat :shared:jvmTest --tests "com.crazystudio.sportrecorder.backup.BackupServiceTest"`
Expected: compile failure — `backup(progress)` has no such parameter / `FakeBackupStore` does not implement the new `BackupStore`.

- [ ] **Step 6: Thread progress through `BackupService`**

Replace `BackupService.kt` (restore keeps today's semantics; Task 2 adds the safety snapshot):

```kotlin
package com.crazystudio.sportrecorder.backup

import com.crazystudio.sportrecorder.domain.model.FastingWindow
import com.crazystudio.sportrecorder.domain.reminder.RemindersRescheduler
import com.crazystudio.sportrecorder.domain.repository.DietSettingsRepository
import com.crazystudio.sportrecorder.domain.repository.EatRecordRepository
import com.crazystudio.sportrecorder.domain.repository.FastingTypeRepository
import com.crazystudio.sportrecorder.domain.repository.ReminderPreferencesRepository
import kotlinx.coroutines.flow.first
import kotlin.time.Clock

/**
 * Orchestrates backup. Reads the current data via the repositories, builds a [BackupDocument],
 * and hands the JSON + referenced photo names to the [store]. The store owns the manifest-last
 * commit and incremental photo skip; this service just snapshots and prunes.
 */
class BackupService(
    private val eatRepo: EatRecordRepository,
    private val fastingRepo: FastingTypeRepository,
    private val settingsRepo: DietSettingsRepository,
    private val prefsRepo: ReminderPreferencesRepository,
    private val store: BackupStore,
    private val rescheduler: RemindersRescheduler,
    private val appVersionName: String,
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {
    /** Build a snapshot from current data, upload it, prune to the last [KEEP_LAST]. */
    suspend fun backup(progress: BackupProgress = BackupProgress.None): SnapshotInfo =
        backupInternal(prune = true, progress = progress)

    private suspend fun backupInternal(prune: Boolean, progress: BackupProgress): SnapshotInfo {
        progress.report(BackupStep.Preparing, 0, 0)
        val meals = eatRepo.observeAll().first()
        val fastingTypes = fastingRepo.observeRecentCustomTypes().first()
        val settings = settingsRepo.settings.first()
        val prefs = prefsRepo.prefs.first()

        val doc = BackupDocument(
            schemaVersion = BackupDocument.SCHEMA_VERSION,
            createdAt = now(),
            appVersionName = appVersionName,
            meals = meals.map { it.toBackup() },
            fastingTypes = fastingTypes.map { it.toBackup() },
            dietSettings = settings.toBackup(),
            reminderPrefs = prefs.toBackup(),
        )
        val json = BackupJson.encodeToString(BackupDocument.serializer(), doc)
        val photoNames = meals.flatMap { meal -> meal.photos.map { it.fileName } }.distinct()

        val info = store.uploadSnapshot(json, photoNames, progress)
        if (prune) {
            progress.report(BackupStep.Pruning, 0, 0)
            store.prune(KEEP_LAST)
        }
        return info
    }

    /** Committed snapshots, newest-first. */
    suspend fun listSnapshots(): List<SnapshotInfo> = store.listSnapshots()

    /**
     * Replace local data with snapshot [snapshotId]. Validates the schema, downloads everything,
     * and only then swaps local data — so a failed download or an unreadable schema leaves the
     * device's current data untouched.
     */
    suspend fun restore(snapshotId: String, progress: BackupProgress = BackupProgress.None) {
        progress.report(BackupStep.DownloadingManifest, 0, 0)
        val json = store.downloadManifest(snapshotId)
        val doc = BackupJson.decodeFromString(BackupDocument.serializer(), json)
        if (doc.schemaVersion > BackupDocument.SCHEMA_VERSION) {
            throw BackupSchemaTooNewException(doc.schemaVersion)
        }
        // Download-all-then-swap: a throw here leaves local data intact.
        val photoNames = doc.meals.flatMap { meal -> meal.photos.map { it.fileName } }.distinct()
        store.downloadPhotos(snapshotId, photoNames, progress)

        progress.report(BackupStep.Applying, 0, 0)
        eatRepo.replaceAll(doc.meals.map { it.toDomain() })
        fastingRepo.replaceAllCustom(doc.fastingTypes.map { it.toDomain() })
        settingsRepo.setSelection(
            FastingWindow(doc.dietSettings.fastingHours, doc.dietSettings.eatingHours),
        )
        val prefs = doc.reminderPrefs
        prefsRepo.setWindowClosingEnabled(prefs.windowClosingEnabled)
        prefsRepo.setFastCompleteEnabled(prefs.fastCompleteEnabled)
        prefsRepo.setLeadMinutes(prefs.leadMinutes)
        prefsRepo.setQuietHoursEnabled(prefs.quietHoursEnabled)
        prefsRepo.setQuietHours(prefs.quietStartMinutes, prefs.quietEndMinutes)

        rescheduler.reschedule()
    }

    companion object {
        /** Retain the newest N snapshots so an accidental empty backup can't destroy the only good one. */
        const val KEEP_LAST = 3
    }
}
```

- [ ] **Step 7: Run all shared tests**

Run: `.\gradlew.bat :shared:jvmTest`
Expected: PASS (existing `BackupRestoreFlowTest`, `BackupViewModelTest` compile via defaults; the new test passes). `:app` does not compile yet (`GoogleDriveBackupStore` still has the old signatures) — that is fixed in Task 6; do **not** run the full gate until then.

- [ ] **Step 8: Commit**

```bash
git add shared/src/commonMain/kotlin/com/crazystudio/sportrecorder/backup/BackupProgress.kt shared/src/commonMain/kotlin/com/crazystudio/sportrecorder/backup/BackupStore.kt shared/src/commonMain/kotlin/com/crazystudio/sportrecorder/backup/BackupService.kt shared/src/commonTest/kotlin/com/crazystudio/sportrecorder/backup/fakes/FakeBackupStore.kt shared/src/commonTest/kotlin/com/crazystudio/sportrecorder/backup/BackupServiceTest.kt
git commit -m "feat(backup): report step progress from the store and service

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 2: Restore takes a safety snapshot first and applies non-cancellably

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/crazystudio/sportrecorder/backup/BackupService.kt`
- Test: `shared/src/commonMain/.../BackupServiceTest.kt` → `shared/src/commonTest/kotlin/com/crazystudio/sportrecorder/backup/BackupServiceTest.kt`

**Interfaces:**
- Consumes: Task 1 types.
- Produces: `restore(snapshotId, progress)` now reports `DownloadingManifest → [SafetyBackup] → DownloadingPhotos → Applying`; uploads one un-pruned snapshot when the device has ≥ 1 meal.

- [ ] **Step 1: Write the failing tests**

Add to `BackupServiceTest.kt`. `emptyDocJson()` builds a valid empty manifest:

```kotlin
    private fun emptyDocJson(): String = BackupJson.encodeToString(
        BackupDocument.serializer(),
        BackupDocument(
            schemaVersion = BackupDocument.SCHEMA_VERSION,
            createdAt = 1L,
            appVersionName = "0.6.2",
            meals = emptyList(),
            fastingTypes = emptyList(),
            dietSettings = BackupDietSettings(16, 8),
            reminderPrefs = BackupReminderPrefs(false, false, 30, false, 1320, 480),
        ),
    )

    @Test fun restore_backsUpCurrentDataFirst_withoutPruning() = runTest {
        val store = FakeBackupStore()
        store.seedSnapshot(SnapshotInfo("target", 1L, "0.6.2", 1L), emptyDocJson())
        val eat = FakeEatRecordRepository(
            listOf(
                EatRecord(1, 1_700L, null, "keep-me", listOf(EatPhoto(7, "a.webp", 1_701L))),
                EatRecord(2, 1_800L, null, "me-too", emptyList()),
            ),
        )
        val progress = RecordingProgress()

        service(eat, store).restore("target", progress)

        val safety = store.uploads.single()
        val safetyDoc = BackupJson.decodeFromString(BackupDocument.serializer(), safety.manifestJson)
        assertEquals(listOf("keep-me", "me-too"), safetyDoc.meals.map { it.note }.sorted())
        assertEquals(listOf("a.webp"), safety.uploadedPhotos)
        assertEquals(null, store.pruneKeepLast) // the safety snapshot must never prune the target away
        assertEquals(emptyList(), eat.state.value) // and the restore still applied
        assertEquals(
            listOf(BackupStep.DownloadingManifest, BackupStep.SafetyBackup, BackupStep.DownloadingPhotos, BackupStep.Applying),
            progress.steps,
        )
    }

    @Test fun restore_skipsSafetyBackup_whenDeviceHasNoRecords() = runTest {
        val store = FakeBackupStore()
        store.seedSnapshot(SnapshotInfo("target", 1L, "0.6.2", 1L), emptyDocJson())
        val progress = RecordingProgress()

        service(FakeEatRecordRepository(), store).restore("target", progress)

        assertTrue(store.uploads.isEmpty())
        assertTrue(BackupStep.SafetyBackup !in progress.steps)
    }

    @Test fun restore_passesReferencedPhotoNamesToStore() = runTest {
        val store = FakeBackupStore()
        val info = service(
            FakeEatRecordRepository(listOf(EatRecord(1, 1L, null, null, listOf(EatPhoto(1, "x.webp", 1L))))),
            store,
        ).backup()

        service(FakeEatRecordRepository(), store).restore(info.id)

        assertEquals(listOf("x.webp"), store.lastDownloadedPhotoNames)
    }
```

- [ ] **Step 2: Run to verify they fail**

Run: `.\gradlew.bat :shared:jvmTest --tests "com.crazystudio.sportrecorder.backup.BackupServiceTest"`
Expected: `restore_backsUpCurrentDataFirst_withoutPruning` FAILS (`uploads.single()` on an empty list); `restore_skipsSafetyBackup_whenDeviceHasNoRecords` passes trivially; `restore_passesReferencedPhotoNamesToStore` passes (already done in Task 1) — keep it as a regression guard.

- [ ] **Step 3: Implement the safety snapshot + non-cancellable apply**

In `BackupService.kt`, add imports `kotlinx.coroutines.NonCancellable` and `kotlinx.coroutines.withContext`, and replace `restore` with:

```kotlin
    /**
     * Replace local data with snapshot [snapshotId]. Validates the schema, **backs up the current
     * device data first** (so the restore is reversible), downloads everything, and only then swaps
     * local data — so a failed or cancelled download leaves the device's current data untouched.
     */
    suspend fun restore(snapshotId: String, progress: BackupProgress = BackupProgress.None) {
        progress.report(BackupStep.DownloadingManifest, 0, 0)
        val json = store.downloadManifest(snapshotId)
        val doc = BackupJson.decodeFromString(BackupDocument.serializer(), json)
        if (doc.schemaVersion > BackupDocument.SCHEMA_VERSION) {
            throw BackupSchemaTooNewException(doc.schemaVersion)
        }

        // Safety net: keep what is on the device as its own snapshot before overwriting it.
        // Never prune here — with KEEP_LAST snapshots present, pruning could delete the very
        // snapshot we are about to restore. The next regular backup prunes as usual.
        if (eatRepo.observeAll().first().isNotEmpty()) {
            backupInternal(prune = false) { _, done, total ->
                progress.report(BackupStep.SafetyBackup, done, total)
            }
        }

        // Download-all-then-swap: a throw or cancel here leaves local data intact.
        val photoNames = doc.meals.flatMap { meal -> meal.photos.map { it.fileName } }.distinct()
        store.downloadPhotos(snapshotId, photoNames, progress)

        // Once we start writing, finish: a half-applied restore is the one state we must never leave.
        withContext(NonCancellable) {
            progress.report(BackupStep.Applying, 0, 0)
            apply(doc)
        }
    }

    private suspend fun apply(doc: BackupDocument) {
        eatRepo.replaceAll(doc.meals.map { it.toDomain() })
        fastingRepo.replaceAllCustom(doc.fastingTypes.map { it.toDomain() })
        settingsRepo.setSelection(
            FastingWindow(doc.dietSettings.fastingHours, doc.dietSettings.eatingHours),
        )
        val prefs = doc.reminderPrefs
        prefsRepo.setWindowClosingEnabled(prefs.windowClosingEnabled)
        prefsRepo.setFastCompleteEnabled(prefs.fastCompleteEnabled)
        prefsRepo.setLeadMinutes(prefs.leadMinutes)
        prefsRepo.setQuietHoursEnabled(prefs.quietHoursEnabled)
        prefsRepo.setQuietHours(prefs.quietStartMinutes, prefs.quietEndMinutes)
        rescheduler.reschedule()
    }
```

- [ ] **Step 4: Run shared tests**

Run: `.\gradlew.bat :shared:jvmTest`
Expected: PASS. Check `BackupRestoreFlowTest` in particular: its restore-into-a-device-with-data journeys now also upload a safety snapshot, so any assertion on `store.uploads.size` or `listSnapshots().size` after a restore must be revisited. Read each failing assertion and update the expectation only where the new upload is the cause (e.g. `uploads.size` grows by one per restore onto a non-empty device).

- [ ] **Step 5: Commit**

```bash
git add shared/src/commonMain/kotlin/com/crazystudio/sportrecorder/backup/BackupService.kt shared/src/commonTest
git commit -m "feat(backup): restore saves a safety snapshot first and applies non-cancellably

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 3: `BackupJobRunner` — app-scoped job with state, cancel, host hooks

**Files:**
- Create: `shared/src/commonMain/kotlin/com/crazystudio/sportrecorder/backup/BackupJobRunner.kt`
- Create: `shared/src/commonTest/kotlin/com/crazystudio/sportrecorder/backup/fakes/FakeBackupJobHost.kt`
- Test: `shared/src/commonTest/kotlin/com/crazystudio/sportrecorder/backup/BackupJobRunnerTest.kt`

**Interfaces:**
- Consumes: `BackupService.backup(progress)`, `restore(id, progress)`, `BackupProgress`, `BackupStep`, `BackupSchemaTooNewException`.
- Produces:
  - `enum class BackupJobKind { Backup, Restore }`
  - `enum class BackupOutcome { Completed, Cancelled, SchemaTooNew, Failed }`
  - `sealed interface BackupJobState { Idle; Running(kind, step, done, total); Finished(kind, outcome) }`
  - `interface BackupJobHost { fun onJobStarted(); fun onJobFinished() }` + `BackupJobHost.None`
  - `class BackupJobRunner(service, host, scope = CoroutineScope(SupervisorJob() + Dispatchers.Default))` with `state`, `startBackup(): Boolean`, `startRestore(id): Boolean`, `cancel()`, `acknowledgeFinished()`

- [ ] **Step 1: Write the fake host**

`FakeBackupJobHost.kt`:

```kotlin
package com.crazystudio.sportrecorder.backup.fakes

import com.crazystudio.sportrecorder.backup.BackupJobHost

class FakeBackupJobHost : BackupJobHost {
    var started = 0
        private set
    var finished = 0
        private set
    override fun onJobStarted() { started++ }
    override fun onJobFinished() { finished++ }
}
```

- [ ] **Step 2: Write the failing runner tests**

`BackupJobRunnerTest.kt`:

```kotlin
package com.crazystudio.sportrecorder.backup

import com.crazystudio.sportrecorder.backup.fakes.FakeBackupJobHost
import com.crazystudio.sportrecorder.backup.fakes.FakeBackupStore
import com.crazystudio.sportrecorder.backup.fakes.FakeDietSettingsRepository
import com.crazystudio.sportrecorder.backup.fakes.FakeEatRecordRepository
import com.crazystudio.sportrecorder.backup.fakes.FakeFastingTypeRepository
import com.crazystudio.sportrecorder.backup.fakes.FakeReminderPreferencesRepository
import com.crazystudio.sportrecorder.backup.fakes.FakeRemindersRescheduler
import com.crazystudio.sportrecorder.domain.model.EatPhoto
import com.crazystudio.sportrecorder.domain.model.EatRecord
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class BackupJobRunnerTest {
    private val dispatcher = StandardTestDispatcher()

    private fun service(store: FakeBackupStore, eat: FakeEatRecordRepository = FakeEatRecordRepository()) =
        BackupService(
            eat, FakeFastingTypeRepository(), FakeDietSettingsRepository(),
            FakeReminderPreferencesRepository(), store, FakeRemindersRescheduler(), "0.7.1",
        ) { 1L }

    private fun runner(service: BackupService, host: FakeBackupJobHost = FakeBackupJobHost()) =
        BackupJobRunner(service, host, CoroutineScope(SupervisorJob() + dispatcher))

    private fun emptyDocJson(): String = BackupJson.encodeToString(
        BackupDocument.serializer(),
        BackupDocument(
            schemaVersion = BackupDocument.SCHEMA_VERSION, createdAt = 1L, appVersionName = "0.7.1",
            meals = emptyList(), fastingTypes = emptyList(),
            dietSettings = BackupDietSettings(16, 8),
            reminderPrefs = BackupReminderPrefs(false, false, 30, false, 1320, 480),
        ),
    )

    @Test fun backup_goesIdleRunningFinished_andPairsHostHooks() = runTest(dispatcher) {
        val host = FakeBackupJobHost()
        val eat = FakeEatRecordRepository(listOf(EatRecord(1, 1L, null, "n", listOf(EatPhoto(1, "a.webp", 1L)))))
        val runner = runner(service(FakeBackupStore(), eat), host)
        assertEquals(BackupJobState.Idle, runner.state.value)

        assertTrue(runner.startBackup())
        assertIs<BackupJobState.Running>(runner.state.value)
        assertEquals(1, host.started)

        testScheduler.advanceUntilIdle()
        assertEquals(BackupJobState.Finished(BackupJobKind.Backup, BackupOutcome.Completed), runner.state.value)
        assertEquals(1, host.finished)

        runner.acknowledgeFinished()
        assertEquals(BackupJobState.Idle, runner.state.value)
    }

    @Test fun start_isRefused_whileAnotherJobRuns() = runTest(dispatcher) {
        val store = FakeBackupStore()
        store.seedSnapshot(SnapshotInfo("s", 1L, "0.7.1", 1L), emptyDocJson())
        store.downloadGate = CompletableDeferred()
        val runner = runner(service(store))

        assertTrue(runner.startRestore("s"))
        testScheduler.advanceUntilIdle() // parked on the gate
        assertFalse(runner.startBackup())
        assertFalse(runner.startRestore("s"))

        store.downloadGate!!.complete(Unit)
        testScheduler.advanceUntilIdle()
        assertEquals(BackupJobState.Finished(BackupJobKind.Restore, BackupOutcome.Completed), runner.state.value)
    }

    @Test fun cancel_duringDownload_leavesLocalDataAndReportsCancelled() = runTest(dispatcher) {
        val store = FakeBackupStore()
        store.seedSnapshot(SnapshotInfo("s", 1L, "0.7.1", 1L), emptyDocJson())
        store.downloadGate = CompletableDeferred()
        val eat = FakeEatRecordRepository(listOf(EatRecord(9, 9L, null, "keep", emptyList())))
        val host = FakeBackupJobHost()
        val runner = runner(service(store, eat), host)

        runner.startRestore("s")
        testScheduler.advanceUntilIdle()
        runner.cancel()
        testScheduler.advanceUntilIdle()

        assertEquals(BackupJobState.Finished(BackupJobKind.Restore, BackupOutcome.Cancelled), runner.state.value)
        assertEquals(listOf(9), eat.state.value.map { it.id })
        assertEquals(1, host.finished)
    }

    @Test fun failure_reportsFailed() = runTest(dispatcher) {
        val store = FakeBackupStore()
        store.seedSnapshot(SnapshotInfo("s", 1L, "0.7.1", 1L), emptyDocJson())
        store.failDownloadPhotos = true
        val runner = runner(service(store))

        runner.startRestore("s")
        testScheduler.advanceUntilIdle()

        assertEquals(BackupJobState.Finished(BackupJobKind.Restore, BackupOutcome.Failed), runner.state.value)
    }

    @Test fun newerSchema_reportsSchemaTooNew() = runTest(dispatcher) {
        val store = FakeBackupStore()
        val tooNew = BackupJson.encodeToString(
            BackupDocument.serializer(),
            BackupDocument(
                schemaVersion = BackupDocument.SCHEMA_VERSION + 1, createdAt = 1L, appVersionName = "9",
                meals = emptyList(), fastingTypes = emptyList(),
                dietSettings = BackupDietSettings(16, 8),
                reminderPrefs = BackupReminderPrefs(false, false, 30, false, 1320, 480),
            ),
        )
        store.seedSnapshot(SnapshotInfo("s", 1L, "9", 1L), tooNew)
        val runner = runner(service(store))

        runner.startRestore("s")
        testScheduler.advanceUntilIdle()

        assertEquals(BackupJobState.Finished(BackupJobKind.Restore, BackupOutcome.SchemaTooNew), runner.state.value)
    }

    @Test fun progress_isMirroredIntoRunningState() = runTest(dispatcher) {
        val eat = FakeEatRecordRepository(listOf(EatRecord(1, 1L, null, "n", listOf(EatPhoto(1, "a.webp", 1L)))))
        val runner = runner(service(FakeBackupStore(), eat))
        val seen = mutableListOf<BackupJobState>()
        val collector = kotlinx.coroutines.launch { runner.state.collect { seen.add(it) } }

        runner.startBackup()
        testScheduler.advanceUntilIdle()
        collector.cancel()

        assertTrue(seen.contains(BackupJobState.Running(BackupJobKind.Backup, BackupStep.UploadingPhotos, 1, 1)))
    }
}
```

- [ ] **Step 3: Run to verify they fail**

Run: `.\gradlew.bat :shared:jvmTest --tests "com.crazystudio.sportrecorder.backup.BackupJobRunnerTest"`
Expected: compile failure (`BackupJobRunner` unresolved).

- [ ] **Step 4: Implement the runner**

`BackupJobRunner.kt`:

```kotlin
package com.crazystudio.sportrecorder.backup

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class BackupJobKind { Backup, Restore }

enum class BackupOutcome { Completed, Cancelled, SchemaTooNew, Failed }

/** What the one-and-only backup/restore job is doing right now. */
sealed interface BackupJobState {
    data object Idle : BackupJobState

    data class Running(val kind: BackupJobKind, val step: BackupStep, val done: Int, val total: Int) : BackupJobState

    /** Stays until [BackupJobRunner.acknowledgeFinished], so a screen opened later still sees it once. */
    data class Finished(val kind: BackupJobKind, val outcome: BackupOutcome) : BackupJobState
}

/**
 * Platform hook around a job's lifetime. Android starts a foreground service in [onJobStarted] so
 * the process stays alive with the screen off; [onJobFinished] is always called, even on failure.
 */
interface BackupJobHost {
    fun onJobStarted()
    fun onJobFinished()

    /** No platform hosting (tests, iOS until it has one). */
    object None : BackupJobHost {
        override fun onJobStarted() = Unit
        override fun onJobFinished() = Unit
    }
}

/**
 * Runs backup/restore jobs in an application-scoped coroutine so they outlive the screen that
 * started them (a `viewModelScope` job dies with the ViewModel the moment the user navigates
 * back). One job at a time; progress is mirrored into [state] for any observer.
 */
class BackupJobRunner(
    private val service: BackupService,
    private val host: BackupJobHost,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    private val _state = MutableStateFlow<BackupJobState>(BackupJobState.Idle)
    val state: StateFlow<BackupJobState> = _state.asStateFlow()

    private var job: Job? = null

    /** Start a backup. Returns false (and does nothing) if a job is already running. */
    fun startBackup(): Boolean = start(BackupJobKind.Backup) { progress -> service.backup(progress) }

    /** Start restoring [snapshotId]. Returns false (and does nothing) if a job is already running. */
    fun startRestore(snapshotId: String): Boolean =
        start(BackupJobKind.Restore) { progress -> service.restore(snapshotId, progress) }

    /** Cancel the running job, if any. Safe at every point: the service guards its own apply step. */
    fun cancel() {
        job?.cancel()
    }

    /** The UI has shown the outcome; go back to [BackupJobState.Idle]. */
    fun acknowledgeFinished() {
        if (_state.value is BackupJobState.Finished) _state.value = BackupJobState.Idle
    }

    private fun start(kind: BackupJobKind, work: suspend (BackupProgress) -> Unit): Boolean {
        if (_state.value is BackupJobState.Running) return false
        _state.value = BackupJobState.Running(kind, BackupStep.Preparing, 0, 0)
        host.onJobStarted()
        job = scope.launch {
            val result = runCatching {
                work { step, done, total -> _state.value = BackupJobState.Running(kind, step, done, total) }
            }
            _state.value = BackupJobState.Finished(kind, result.outcome())
            host.onJobFinished()
        }
        return true
    }
}

private fun Result<Unit>.outcome(): BackupOutcome = when (exceptionOrNull()) {
    null -> BackupOutcome.Completed
    is CancellationException -> BackupOutcome.Cancelled
    is BackupSchemaTooNewException -> BackupOutcome.SchemaTooNew
    else -> BackupOutcome.Failed
}
```

- [ ] **Step 5: Run the tests**

Run: `.\gradlew.bat :shared:jvmTest --tests "com.crazystudio.sportrecorder.backup.BackupJobRunnerTest"`
Expected: PASS (6 tests).

- [ ] **Step 6: Commit**

```bash
git add shared/src/commonMain/kotlin/com/crazystudio/sportrecorder/backup/BackupJobRunner.kt shared/src/commonTest/kotlin/com/crazystudio/sportrecorder/backup/fakes/FakeBackupJobHost.kt shared/src/commonTest/kotlin/com/crazystudio/sportrecorder/backup/BackupJobRunnerTest.kt
git commit -m "feat(backup): app-scoped BackupJobRunner with state, cancel and host hooks

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 4: `BackupViewModel` observes the runner; `BackupUiState` carries job + local count

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/crazystudio/sportrecorder/ui/backup/BackupUiState.kt`
- Modify: `shared/src/commonMain/kotlin/com/crazystudio/sportrecorder/ui/backup/BackupViewModel.kt`
- Test: `shared/src/commonTest/kotlin/com/crazystudio/sportrecorder/ui/backup/BackupViewModelTest.kt`

**Interfaces:**
- Consumes: `BackupJobRunner`, `BackupJobState`, `BackupOutcome`, `ObserveEatRecordsUseCase` (`operator fun invoke(): Flow<List<EatRecord>>`, in `domain/usecase`).
- Produces:
  - `BackupUiState(account, snapshots, job: BackupJobState = Idle, isLoadingSnapshots = false, localMealCount = 0, message)`; `isBusy = job is Running || isLoadingSnapshots`
  - `enum class BackupMessage { BackupComplete, RestoreComplete, RestoreSchemaTooNew, Cancelled, Failed }` (`BackupPhase` is deleted)
  - `BackupViewModel(runner: BackupJobRunner, backupService: BackupService, backupAuth: BackupAuth, observeEatRecords: ObserveEatRecordsUseCase)` with `refreshSnapshots()`, `backup()`, `restore(id)`, `cancel()`, `consumeMessage()`, `reportFailure()`

- [ ] **Step 1: Rewrite `BackupViewModelTest` for the runner**

Replace the file:

```kotlin
package com.crazystudio.sportrecorder.ui.backup

import com.crazystudio.sportrecorder.backup.BackupAccount
import com.crazystudio.sportrecorder.backup.BackupDietSettings
import com.crazystudio.sportrecorder.backup.BackupDocument
import com.crazystudio.sportrecorder.backup.BackupJobHost
import com.crazystudio.sportrecorder.backup.BackupJobRunner
import com.crazystudio.sportrecorder.backup.BackupJobState
import com.crazystudio.sportrecorder.backup.BackupJson
import com.crazystudio.sportrecorder.backup.BackupReminderPrefs
import com.crazystudio.sportrecorder.backup.BackupService
import com.crazystudio.sportrecorder.backup.SnapshotInfo
import com.crazystudio.sportrecorder.backup.fakes.FakeBackupAuth
import com.crazystudio.sportrecorder.backup.fakes.FakeBackupStore
import com.crazystudio.sportrecorder.backup.fakes.FakeDietSettingsRepository
import com.crazystudio.sportrecorder.backup.fakes.FakeEatRecordRepository
import com.crazystudio.sportrecorder.backup.fakes.FakeFastingTypeRepository
import com.crazystudio.sportrecorder.backup.fakes.FakeReminderPreferencesRepository
import com.crazystudio.sportrecorder.backup.fakes.FakeRemindersRescheduler
import com.crazystudio.sportrecorder.domain.model.EatPhoto
import com.crazystudio.sportrecorder.domain.model.EatRecord
import com.crazystudio.sportrecorder.domain.usecase.ObserveEatRecordsUseCase
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class BackupViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeTest fun setUp() { Dispatchers.setMain(dispatcher) }
    @AfterTest fun tearDown() { Dispatchers.resetMain() }

    private fun service(store: FakeBackupStore, eat: FakeEatRecordRepository) =
        BackupService(
            eat, FakeFastingTypeRepository(), FakeDietSettingsRepository(),
            FakeReminderPreferencesRepository(), store, FakeRemindersRescheduler(), "0.7.1",
        ) { 1L }

    private fun vm(
        store: FakeBackupStore,
        eat: FakeEatRecordRepository = FakeEatRecordRepository(),
        auth: FakeBackupAuth = FakeBackupAuth(BackupAccount("me@x.com")),
    ): BackupViewModel {
        val svc = service(store, eat)
        val runner = BackupJobRunner(svc, BackupJobHost.None, CoroutineScope(SupervisorJob() + dispatcher))
        return BackupViewModel(runner, svc, auth, ObserveEatRecordsUseCase(eat))
    }

    private fun emptyDocJson(): String = BackupJson.encodeToString(
        BackupDocument.serializer(),
        BackupDocument(
            schemaVersion = BackupDocument.SCHEMA_VERSION, createdAt = 1L, appVersionName = "0.7.1",
            meals = emptyList(), fastingTypes = emptyList(),
            dietSettings = BackupDietSettings(16, 8),
            reminderPrefs = BackupReminderPrefs(false, false, 30, false, 1320, 480),
        ),
    )

    @Test fun backup_setsCompleteMessageAndRefreshesSnapshots() = runTest(dispatcher) {
        val store = FakeBackupStore()
        val eat = FakeEatRecordRepository(listOf(EatRecord(1, 1L, null, "n", listOf(EatPhoto(7, "a.webp", 2L)))))
        val vm = vm(store, eat)

        vm.backup()
        testScheduler.advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(BackupMessage.BackupComplete, state.message)
        assertEquals(1, state.snapshots.size)
        assertIs<BackupJobState.Finished>(state.job)
        assertEquals(false, state.isBusy)
    }

    @Test fun runningJob_isBusy_andMirrorsProgress() = runTest(dispatcher) {
        val store = FakeBackupStore()
        store.seedSnapshot(SnapshotInfo("s", 1L, "0.7.1", 1L), emptyDocJson())
        store.downloadGate = CompletableDeferred()
        val vm = vm(store)

        vm.restore("s")
        testScheduler.advanceUntilIdle()

        val job = vm.uiState.value.job
        assertIs<BackupJobState.Running>(job)
        assertEquals(true, vm.uiState.value.isBusy)

        store.downloadGate!!.complete(Unit)
        testScheduler.advanceUntilIdle()
        assertEquals(BackupMessage.RestoreComplete, vm.uiState.value.message)
    }

    @Test fun cancel_setsCancelledMessage() = runTest(dispatcher) {
        val store = FakeBackupStore()
        store.seedSnapshot(SnapshotInfo("s", 1L, "0.7.1", 1L), emptyDocJson())
        store.downloadGate = CompletableDeferred()
        val vm = vm(store)

        vm.restore("s")
        testScheduler.advanceUntilIdle()
        vm.cancel()
        testScheduler.advanceUntilIdle()

        assertEquals(BackupMessage.Cancelled, vm.uiState.value.message)
    }

    @Test fun consumeMessage_clearsMessage_andReturnsRunnerToIdle() = runTest(dispatcher) {
        val vm = vm(FakeBackupStore())
        vm.backup()
        testScheduler.advanceUntilIdle()

        vm.consumeMessage()
        testScheduler.advanceUntilIdle()

        assertNull(vm.uiState.value.message)
        assertEquals(BackupJobState.Idle, vm.uiState.value.job)
    }

    @Test fun localMealCount_followsRepository() = runTest(dispatcher) {
        val eat = FakeEatRecordRepository(listOf(EatRecord(1, 1L, null, null, emptyList())))
        val vm = vm(FakeBackupStore(), eat)
        testScheduler.advanceUntilIdle()
        assertEquals(1, vm.uiState.value.localMealCount)

        eat.state.value = emptyList()
        testScheduler.advanceUntilIdle()
        assertEquals(0, vm.uiState.value.localMealCount)
    }

    @Test fun account_isReflectedInState() = runTest(dispatcher) {
        val vm = vm(FakeBackupStore())
        testScheduler.advanceUntilIdle()
        assertEquals("me@x.com", vm.uiState.value.account?.email)
    }

    @Test fun accountChange_clearsPreviousAccountsSnapshots() = runTest(dispatcher) {
        val store = FakeBackupStore()
        val auth = FakeBackupAuth(BackupAccount("me@x.com"))
        val vm = vm(store, auth = auth)

        vm.backup()
        testScheduler.advanceUntilIdle()
        assertEquals(1, vm.uiState.value.snapshots.size)

        auth.accountState.value = null // signed out
        testScheduler.advanceUntilIdle()
        assertEquals(emptyList<SnapshotInfo>(), vm.uiState.value.snapshots)

        auth.accountState.value = BackupAccount("someone-else@x.com") // a different account
        testScheduler.advanceUntilIdle()
        assertEquals(emptyList<SnapshotInfo>(), vm.uiState.value.snapshots)
    }

    @Test fun restore_tooNewSchema_setsSchemaMessage() = runTest(dispatcher) {
        val store = FakeBackupStore()
        val tooNew = BackupDocument(
            schemaVersion = BackupDocument.SCHEMA_VERSION + 1,
            createdAt = 1L, appVersionName = "9",
            meals = emptyList(), fastingTypes = emptyList(),
            dietSettings = BackupDietSettings(16, 8),
            reminderPrefs = BackupReminderPrefs(false, false, 30, false, 1320, 480),
        )
        val json = BackupJson.encodeToString(BackupDocument.serializer(), tooNew)
        store.seedSnapshot(SnapshotInfo("s1", 1L, "9", json.length.toLong()), json)
        val vm = vm(store)

        vm.restore("s1")
        testScheduler.advanceUntilIdle()

        assertEquals(BackupMessage.RestoreSchemaTooNew, vm.uiState.value.message)
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `.\gradlew.bat :shared:jvmTest --tests "com.crazystudio.sportrecorder.ui.backup.BackupViewModelTest"`
Expected: compile failure (constructor arity, `job`, `localMealCount`, `BackupMessage.Cancelled`).

- [ ] **Step 3: Rewrite `BackupUiState`**

```kotlin
package com.crazystudio.sportrecorder.ui.backup

import com.crazystudio.sportrecorder.backup.BackupAccount
import com.crazystudio.sportrecorder.backup.BackupJobState
import com.crazystudio.sportrecorder.backup.SnapshotInfo

/**
 * UI state for the backup/restore screen. [job] mirrors the app-scoped runner so progress survives
 * leaving and re-entering the screen; [message] is a transient result the UI shows then clears.
 */
data class BackupUiState(
    val account: BackupAccount? = null,
    val snapshots: List<SnapshotInfo> = emptyList(),
    val job: BackupJobState = BackupJobState.Idle,
    val isLoadingSnapshots: Boolean = false,
    /** Meals currently on this device — the restore dialog says how many get a safety snapshot. */
    val localMealCount: Int = 0,
    val message: BackupMessage? = null,
) {
    val isSignedIn: Boolean get() = account != null
    val isBusy: Boolean get() = job is BackupJobState.Running || isLoadingSnapshots
    /** Newest snapshot's createdAt, or null if none — drives "last backed up …". */
    val lastBackedUpAt: Long? get() = snapshots.maxOfOrNull { it.createdAt }
}

/** Semantic result — the UI maps each to a localized string (the VM holds no user-facing copy). */
enum class BackupMessage { BackupComplete, RestoreComplete, RestoreSchemaTooNew, Cancelled, Failed }
```

- [ ] **Step 4: Rewrite `BackupViewModel`**

```kotlin
package com.crazystudio.sportrecorder.ui.backup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.crazystudio.sportrecorder.backup.BackupAuth
import com.crazystudio.sportrecorder.backup.BackupJobKind
import com.crazystudio.sportrecorder.backup.BackupJobRunner
import com.crazystudio.sportrecorder.backup.BackupJobState
import com.crazystudio.sportrecorder.backup.BackupOutcome
import com.crazystudio.sportrecorder.backup.BackupService
import com.crazystudio.sportrecorder.domain.usecase.ObserveEatRecordsUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Drives the backup/restore screen. Observes sign-in state via [BackupAuth], mirrors the
 * app-scoped [BackupJobRunner] (so a job keeps going when this VM is cleared), and loads the
 * snapshot list through [BackupService]. Android auth (consent, sign-out) is the :app layer's
 * concern, so this VM stays platform-free and testable.
 */
class BackupViewModel(
    private val runner: BackupJobRunner,
    private val backupService: BackupService,
    backupAuth: BackupAuth,
    observeEatRecords: ObserveEatRecordsUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(BackupUiState())
    val uiState: StateFlow<BackupUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            backupAuth.account.collect { account ->
                _uiState.update { state ->
                    // Snapshots belong to the account that listed them: drop them whenever the
                    // account changes (sign-out, or switching to another Google account) so the
                    // restore list / "last backed up" never shows the previous account's data.
                    if (state.account?.email == account?.email) {
                        state.copy(account = account)
                    } else {
                        state.copy(account = account, snapshots = emptyList())
                    }
                }
            }
        }
        viewModelScope.launch {
            runner.state.collect { job ->
                _uiState.update { it.copy(job = job) }
                if (job is BackupJobState.Finished) onFinished(job)
            }
        }
        viewModelScope.launch {
            observeEatRecords().collect { meals -> _uiState.update { it.copy(localMealCount = meals.size) } }
        }
    }

    /** Load the snapshot list (restore picker + "last backed up"). Requires being signed in. */
    fun refreshSnapshots() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingSnapshots = true) }
            runCatching { backupService.listSnapshots() }
                .onSuccess { snapshots -> _uiState.update { it.copy(snapshots = snapshots, isLoadingSnapshots = false) } }
                .onFailure { _uiState.update { it.copy(isLoadingSnapshots = false, message = BackupMessage.Failed) } }
        }
    }

    fun backup() {
        _uiState.update { it.copy(message = null) }
        runner.startBackup()
    }

    fun restore(snapshotId: String) {
        _uiState.update { it.copy(message = null) }
        runner.startRestore(snapshotId)
    }

    fun cancel() = runner.cancel()

    /** Clear the transient result message after the UI has shown it, and let the runner go idle. */
    fun consumeMessage() {
        _uiState.update { it.copy(message = null) }
        runner.acknowledgeFinished()
    }

    /** Surface a failure that originated outside a VM operation (e.g. sign-in in the :app layer). */
    fun reportFailure() = _uiState.update { it.copy(message = BackupMessage.Failed) }

    private suspend fun onFinished(job: BackupJobState.Finished) {
        val message = when (job.outcome) {
            BackupOutcome.Completed ->
                if (job.kind == BackupJobKind.Backup) BackupMessage.BackupComplete else BackupMessage.RestoreComplete
            BackupOutcome.Cancelled -> BackupMessage.Cancelled
            BackupOutcome.SchemaTooNew -> BackupMessage.RestoreSchemaTooNew
            BackupOutcome.Failed -> BackupMessage.Failed
        }
        if (job.outcome == BackupOutcome.Completed) {
            val snapshots = runCatching { backupService.listSnapshots() }.getOrDefault(_uiState.value.snapshots)
            _uiState.update { it.copy(snapshots = snapshots) }
        }
        _uiState.update { it.copy(message = message) }
    }
}
```

- [ ] **Step 5: Check nothing else references `BackupPhase`**

Run: `grep -rn "BackupPhase" shared/src app/src`
Expected: no matches. If any test in `app/src/test` or `shared/src/commonTest/.../flow` matches, replace `phase == BackupPhase.Idle` assertions with `isBusy == false`.

- [ ] **Step 6: Run shared tests**

Run: `.\gradlew.bat :shared:jvmTest`
Expected: PASS. (`BackupScreen` still compiles: it only uses `state.isBusy`, `state.message`, `state.snapshots`, `state.account`.)

- [ ] **Step 7: Commit**

```bash
git add shared/src/commonMain/kotlin/com/crazystudio/sportrecorder/ui/backup/BackupUiState.kt shared/src/commonMain/kotlin/com/crazystudio/sportrecorder/ui/backup/BackupViewModel.kt shared/src/commonTest/kotlin/com/crazystudio/sportrecorder/ui/backup/BackupViewModelTest.kt
git commit -m "feat(backup): ViewModel mirrors the job runner instead of owning the work

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 5: Backup screen — progress bar, cancel, reassuring restore dialog

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/crazystudio/sportrecorder/ui/backup/BackupScreen.kt`
- Modify: `shared/src/commonMain/composeResources/values/strings.xml`
- Modify: `shared/src/commonMain/composeResources/values-zh-rTW/strings.xml`

**Interfaces:**
- Consumes: `BackupUiState.job/localMealCount/isLoadingSnapshots`, `BackupJobState.Running`, `BackupStep`, `SnapshotInfo.mealCount`, `BackupMessage.Cancelled`.
- Produces: `BackupScreen(..., onCancel: () -> Unit, ...)` — one new callback, inserted after `onRestore`.

No unit test covers Compose here (project has no shared UI tests); verification is compile + manual.

- [ ] **Step 1: Add strings (English)**

In `shared/src/commonMain/composeResources/values/strings.xml`, replace the `backup_restore_confirm_message` line and add the new keys right after `backup_msg_failed`:

```xml
    <string name="backup_restore_confirm_message">Your %1$d records on this device are backed up first, then replaced with the backup from %2$s. You can always come back to them.</string>
    <string name="backup_restore_confirm_message_counted">Your %1$d records on this device are backed up first, then replaced with the backup from %2$s (%3$d records). You can always come back to them.</string>
    <string name="backup_restore_confirm_message_fresh">This device has no records yet. The backup from %1$s will be restored here.</string>
```

```xml
    <string name="backup_msg_cancelled">Cancelled. Nothing was changed.</string>
    <string name="backup_step_preparing">Getting ready…</string>
    <string name="backup_step_safety_backup">Backing up what\'s on this device first…</string>
    <string name="backup_step_uploading_photos">Uploading photos %1$d / %2$d</string>
    <string name="backup_step_uploading_manifest">Writing the backup index…</string>
    <string name="backup_step_pruning">Tidying older backups…</string>
    <string name="backup_step_downloading_manifest">Reading the backup…</string>
    <string name="backup_step_downloading_photos">Downloading photos %1$d / %2$d</string>
    <string name="backup_step_applying">Applying to this device…</string>
```

- [ ] **Step 2: Add strings (zh-rTW)**

Same positions in `values-zh-rTW/strings.xml`:

```xml
    <string name="backup_restore_confirm_message">會先把這台裝置目前的 %1$d 筆紀錄備份一份，再用 %2$s 的備份取代。之後隨時可以還回來。</string>
    <string name="backup_restore_confirm_message_counted">會先把這台裝置目前的 %1$d 筆紀錄備份一份，再用 %2$s 的備份（%3$d 筆）取代。之後隨時可以還回來。</string>
    <string name="backup_restore_confirm_message_fresh">這台裝置還沒有任何紀錄，會直接還原 %1$s 的備份。</string>
```

```xml
    <string name="backup_msg_cancelled">已取消，什麼都沒有改變。</string>
    <string name="backup_step_preparing">準備中…</string>
    <string name="backup_step_safety_backup">先備份目前這台裝置的紀錄…</string>
    <string name="backup_step_uploading_photos">上傳照片 %1$d / %2$d</string>
    <string name="backup_step_uploading_manifest">寫入備份清單…</string>
    <string name="backup_step_pruning">整理舊備份…</string>
    <string name="backup_step_downloading_manifest">讀取備份…</string>
    <string name="backup_step_downloading_photos">下載照片 %1$d / %2$d</string>
    <string name="backup_step_applying">套用到這台裝置…</string>
```

- [ ] **Step 3: Update `BackupScreen`**

Changes to `BackupScreen.kt` (keep the rest of the file as is):

1. Imports — add:
```kotlin
import androidx.compose.material3.LinearProgressIndicator
import com.crazystudio.sportrecorder.backup.BackupJobState
import com.crazystudio.sportrecorder.backup.BackupStep
import com.crazystudio.sportrecorder.shared.resources.backup_msg_cancelled
import com.crazystudio.sportrecorder.shared.resources.backup_restore_confirm_message_counted
import com.crazystudio.sportrecorder.shared.resources.backup_restore_confirm_message_fresh
import com.crazystudio.sportrecorder.shared.resources.backup_step_applying
import com.crazystudio.sportrecorder.shared.resources.backup_step_downloading_manifest
import com.crazystudio.sportrecorder.shared.resources.backup_step_downloading_photos
import com.crazystudio.sportrecorder.shared.resources.backup_step_preparing
import com.crazystudio.sportrecorder.shared.resources.backup_step_pruning
import com.crazystudio.sportrecorder.shared.resources.backup_step_safety_backup
import com.crazystudio.sportrecorder.shared.resources.backup_step_uploading_manifest
import com.crazystudio.sportrecorder.shared.resources.backup_step_uploading_photos
```

2. `BackupScreen` signature — add `onCancel: () -> Unit,` after `onRestore`, and pass it to `SignedInContent(..., onCancel = onCancel, ...)`.

3. `messageRes` — add `BackupMessage.Cancelled -> Res.string.backup_msg_cancelled`.

4. `SignedInContent` — add parameter `onCancel: () -> Unit`, and replace the `if (state.isBusy) { CircularProgressIndicator(...) }` block with:
```kotlin
        val job = state.job
        if (job is BackupJobState.Running) {
            JobProgress(job = job, onCancel = onCancel)
        } else if (state.isLoadingSnapshots) {
            CircularProgressIndicator(modifier = Modifier.padding(top = 16.dp).size(28.dp))
        }
```
   and pass `onRestore` / `state.localMealCount` down: `RestoreList(snapshots = state.snapshots, localMealCount = state.localMealCount, enabled = enabled, onRestore = onRestore)`.

5. New composables (place after `SignedInContent`):
```kotlin
@Composable
private fun JobProgress(job: BackupJobState.Running, onCancel: () -> Unit) {
    val colorScheme = MaterialTheme.colorScheme
    Column(modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
        if (job.total > 0) {
            LinearProgressIndicator(
                progress = { job.done.toFloat() / job.total },
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stepCaption(job),
                style = MaterialTheme.typography.bodyMedium,
                color = colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            // Applying is the one step that must finish once started (see BackupService.restore).
            TextButton(onClick = onCancel, enabled = job.step != BackupStep.Applying) {
                Text(stringResource(Res.string.backup_cancel))
            }
        }
    }
}

@Composable
private fun stepCaption(job: BackupJobState.Running): String = when (job.step) {
    BackupStep.Preparing -> stringResource(Res.string.backup_step_preparing)
    BackupStep.SafetyBackup -> stringResource(Res.string.backup_step_safety_backup)
    BackupStep.UploadingPhotos -> stringResource(Res.string.backup_step_uploading_photos, job.done, job.total)
    BackupStep.UploadingManifest -> stringResource(Res.string.backup_step_uploading_manifest)
    BackupStep.Pruning -> stringResource(Res.string.backup_step_pruning)
    BackupStep.DownloadingManifest -> stringResource(Res.string.backup_step_downloading_manifest)
    BackupStep.DownloadingPhotos -> stringResource(Res.string.backup_step_downloading_photos, job.done, job.total)
    BackupStep.Applying -> stringResource(Res.string.backup_step_applying)
}
```

6. `RestoreList` — add `localMealCount: Int` parameter and pass it to `RestoreConfirmDialog(snapshot = snapshot, localMealCount = localMealCount, ...)`.

7. `RestoreConfirmDialog` — add `localMealCount: Int` parameter and replace the `text = { … }` body with:
```kotlin
        text = {
            val date = formatTimestamp(snapshot.createdAt)
            val count = snapshot.mealCount
            Text(
                when {
                    localMealCount == 0 -> stringResource(Res.string.backup_restore_confirm_message_fresh, date)
                    count != null ->
                        stringResource(Res.string.backup_restore_confirm_message_counted, localMealCount, date, count)
                    else -> stringResource(Res.string.backup_restore_confirm_message, localMealCount, date)
                },
            )
        },
```

- [ ] **Step 4: Compile the shared module**

Run: `.\gradlew.bat :shared:compileKotlinJvm :shared:compileDebugKotlinAndroid`
Expected: BUILD SUCCESSFUL. (If `compileKotlinJvm` is not a task name in this project, use `:shared:jvmTest` which compiles first.)

- [ ] **Step 5: Commit**

```bash
git add shared/src/commonMain/kotlin/com/crazystudio/sportrecorder/ui/backup/BackupScreen.kt shared/src/commonMain/composeResources
git commit -m "feat(backup): progress bar, cancel, and a restore dialog that explains the safety snapshot

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 6: `DriveRestClient` — JVM-testable Drive HTTP layer with paging and cancellable calls

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`
- Create: `app/src/main/java/com/crazystudio/sportrecorder/backup/DriveRestClient.kt`
- Test: `app/src/test/java/com/crazystudio/sportrecorder/backup/DriveRestClientTest.kt`

**Interfaces:**
- Produces:
  ```kotlin
  class DriveRestClient(httpClient: OkHttpClient = defaultHttpClient(), apiBaseUrl: String = "https://www.googleapis.com") {
      data class DriveFile(val id: String, val name: String, val sizeBytes: Long, val appProperties: Map<String, String>)
      suspend fun listAppDataFiles(token: String): List<DriveFile>   // follows nextPageToken
      suspend fun uploadMultipart(token: String, name: String, mimeType: String, appProperties: Map<String, String>, bytes: ByteArray)
      suspend fun downloadBytes(token: String, fileId: String): ByteArray
      suspend fun deleteFile(token: String, fileId: String)
  }
  ```
  All calls are `suspend`, use OkHttp `enqueue`, and cancel the in-flight call when the coroutine is cancelled.

- [ ] **Step 1: Add MockWebServer (test-only) and let unit tests see `android.util.Log`**

`gradle/libs.versions.toml`, under `[libraries]` next to `okhttp`:
```toml
okhttp-mockwebserver = { group = "com.squareup.okhttp3", name = "mockwebserver", version.ref = "okhttp" }
```

`app/build.gradle.kts`: add inside the `android { … }` block (after `buildFeatures` or `packaging`, wherever the other android options are):
```kotlin
    testOptions {
        // Drive client tests run on the JVM and touch android.util.Log; stub it instead of crashing.
        unitTests.isReturnDefaultValues = true
    }
```
and in `dependencies { … }` next to the other `testImplementation` lines:
```kotlin
    testImplementation(libs.okhttp.mockwebserver)
```

- [ ] **Step 2: Write the failing paging test**

`app/src/test/java/com/crazystudio/sportrecorder/backup/DriveRestClientTest.kt`:

```kotlin
package com.crazystudio.sportrecorder.backup

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DriveRestClientTest {
    private val server = MockWebServer()
    private lateinit var client: DriveRestClient

    @Before fun setUp() {
        server.start()
        client = DriveRestClient(OkHttpClient(), server.url("/").toString().trimEnd('/'))
    }

    @After fun tearDown() { server.shutdown() }

    @Test fun listAppDataFiles_followsNextPageToken() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"nextPageToken":"page-2","files":[{"id":"1","name":"a.webp","size":"10","appProperties":{"kind":"photo"}}]}""",
            ),
        )
        server.enqueue(
            MockResponse().setBody(
                """{"files":[{"id":"2","name":"manifest-x.json","size":"20","appProperties":{"kind":"manifest","snapshotId":"x"}}]}""",
            ),
        )

        val files = client.listAppDataFiles("tok")

        assertEquals(listOf("1", "2"), files.map { it.id })
        assertEquals(mapOf("kind" to "manifest", "snapshotId" to "x"), files[1].appProperties)
        assertEquals(20L, files[1].sizeBytes)

        val first = server.takeRequest()
        val second = server.takeRequest()
        assertEquals("Bearer tok", first.getHeader("Authorization"))
        assertTrue(first.requestUrl!!.queryParameter("pageToken") == null)
        assertEquals("page-2", second.requestUrl!!.queryParameter("pageToken"))
        assertEquals("appDataFolder", second.requestUrl!!.queryParameter("spaces"))
    }

    @Test fun uploadMultipart_sendsMetadataThenBytes() = runTest {
        server.enqueue(MockResponse().setBody("""{"id":"new"}"""))

        client.uploadMultipart("tok", "a.webp", "image/webp", mapOf("kind" to "photo"), byteArrayOf(1, 2, 3))

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("multipart", request.requestUrl!!.queryParameter("uploadType"))
        val body = request.body.readUtf8()
        assertTrue(body.contains("\"name\":\"a.webp\""))
        assertTrue(body.contains("\"parents\":[\"appDataFolder\"]"))
        assertTrue(body.contains("\"kind\":\"photo\""))
    }

    @Test fun failedResponse_throwsIoExceptionWithDriveReason() = runTest {
        server.enqueue(MockResponse().setResponseCode(403).setBody("""{"error":{"message":"accessNotConfigured"}}"""))

        val error = runCatching { client.downloadBytes("tok", "f1") }.exceptionOrNull()

        assertTrue(error is java.io.IOException)
        assertTrue(error!!.message!!.contains("403"))
        assertTrue(error.message!!.contains("accessNotConfigured"))
    }
}
```

- [ ] **Step 3: Run to verify it fails**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.crazystudio.sportrecorder.backup.DriveRestClientTest"`
Expected: compile failure (`DriveRestClient` unresolved).

- [ ] **Step 4: Implement `DriveRestClient`**

```kotlin
package com.crazystudio.sportrecorder.backup

import android.util.Log
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private const val DEFAULT_API_BASE_URL = "https://www.googleapis.com"
private const val FILES_PATH = "/drive/v3/files"
private const val UPLOAD_PATH = "/upload/drive/v3/files"
private const val PAGE_SIZE = "1000"
private const val LIST_FIELDS = "nextPageToken,files(id,name,size,appProperties)"
private const val AUTH_HEADER = "Authorization"
private const val MIME_JSON_METADATA = "application/json; charset=UTF-8"
private const val TAG = "DriveRestClient"

/** Drive's JSON error body says *why* (accessNotConfigured, insufficientPermissions, quota…). */
private const val ERROR_BODY_LIMIT = 400

private const val CONNECT_TIMEOUT_SECONDS = 30L
private const val IO_TIMEOUT_SECONDS = 120L

/**
 * Thin, suspendable wrapper over the Drive v3 REST endpoints the backup store needs. Knows nothing
 * about snapshots. [apiBaseUrl] is injectable so tests can point it at a MockWebServer; JSON is
 * parsed with kotlinx.serialization (not org.json) so the class runs in plain JVM unit tests.
 *
 * Every call uses OkHttp's async `enqueue` and cancels the HTTP call when the coroutine is
 * cancelled, so a user's "取消" stops the transfer promptly instead of after the current file.
 */
class DriveRestClient(
    private val httpClient: OkHttpClient = defaultHttpClient(),
    private val apiBaseUrl: String = DEFAULT_API_BASE_URL,
) {
    data class DriveFile(
        val id: String,
        val name: String,
        val sizeBytes: Long,
        val appProperties: Map<String, String>,
    )

    /** Every file in appDataFolder, across all pages. */
    suspend fun listAppDataFiles(token: String): List<DriveFile> {
        val all = mutableListOf<DriveFile>()
        var pageToken: String? = null
        do {
            val url = "$apiBaseUrl$FILES_PATH".toHttpUrl().newBuilder()
                .addQueryParameter("spaces", "appDataFolder")
                .addQueryParameter("pageSize", PAGE_SIZE)
                .addQueryParameter("fields", LIST_FIELDS)
                .apply { if (pageToken != null) addQueryParameter("pageToken", pageToken) }
                .build()
            val body = execute(Request.Builder().url(url).header(AUTH_HEADER, "Bearer $token").build(), "Drive list")
                .use { it.body?.string() ?: throw IOException("Drive list returned an empty body") }
            val json = Json.parseToJsonElement(body).jsonObject
            json["files"]?.jsonArray?.forEach { element ->
                val obj = element.jsonObject
                all += DriveFile(
                    id = obj["id"]?.jsonPrimitive?.content ?: throw IOException("Drive file without id"),
                    name = obj["name"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    sizeBytes = obj["size"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 0L,
                    appProperties = obj["appProperties"]?.jsonObject
                        ?.mapValues { (_, value) -> value.jsonPrimitive.content }
                        ?: emptyMap(),
                )
            }
            pageToken = json["nextPageToken"]?.jsonPrimitive?.contentOrNull
        } while (pageToken != null)
        return all
    }

    suspend fun uploadMultipart(
        token: String,
        name: String,
        mimeType: String,
        appProperties: Map<String, String>,
        bytes: ByteArray,
    ) {
        val metadata = buildJsonObject {
            put("name", name)
            putJsonArray("parents") { add("appDataFolder") }
            put("mimeType", mimeType)
            putJsonObject("appProperties") { appProperties.forEach { (key, value) -> put(key, value) } }
        }.toString()
        val body = MultipartBody.Builder()
            .setType("multipart/related".toMediaType())
            .addPart(metadata.toRequestBody(MIME_JSON_METADATA.toMediaType()))
            .addPart(bytes.toRequestBody(mimeType.toMediaType()))
            .build()
        val url = "$apiBaseUrl$UPLOAD_PATH".toHttpUrl().newBuilder()
            .addQueryParameter("uploadType", "multipart")
            .build()
        val request = Request.Builder().url(url).header(AUTH_HEADER, "Bearer $token").post(body).build()
        execute(request, "Drive upload of $name").close()
    }

    suspend fun downloadBytes(token: String, fileId: String): ByteArray {
        val url = "$apiBaseUrl$FILES_PATH/$fileId".toHttpUrl().newBuilder()
            .addQueryParameter("alt", "media")
            .build()
        val request = Request.Builder().url(url).header(AUTH_HEADER, "Bearer $token").build()
        return execute(request, "Drive download").use {
            it.body?.bytes() ?: throw IOException("Drive download returned an empty body")
        }
    }

    suspend fun deleteFile(token: String, fileId: String) {
        val request = Request.Builder()
            .url("$apiBaseUrl$FILES_PATH/$fileId")
            .header(AUTH_HEADER, "Bearer $token")
            .delete()
            .build()
        execute(request, "Drive delete").close()
    }

    /** Run [request]; a non-2xx response is turned into an [IOException] carrying Drive's reason. */
    private suspend fun execute(request: Request, what: String): Response {
        val response = httpClient.newCall(request).await()
        if (!response.isSuccessful) response.fail(what)
        return response
    }
}

private fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
    // OkHttp's 10-second defaults are too tight: a backup uploads every photo as its own request
    // over whatever connection the phone has, so an upload that is merely slow can outlive them.
    .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
    .readTimeout(IO_TIMEOUT_SECONDS, TimeUnit.SECONDS)
    .writeTimeout(IO_TIMEOUT_SECONDS, TimeUnit.SECONDS)
    .build()

/** Suspend on an OkHttp call; cancelling the coroutine cancels the HTTP call. */
private suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
    enqueue(
        object : Callback {
            override fun onResponse(call: Call, response: Response) {
                continuation.resume(response)
            }

            override fun onFailure(call: Call, e: IOException) {
                if (!continuation.isCancelled) continuation.resumeWithException(e)
            }
        },
    )
    continuation.invokeOnCancellation { cancel() }
}

/**
 * Fail a Drive call with the reason Drive actually gave. The HTTP code alone does not say whether
 * the Drive API is disabled for the project, the grant is missing the appdata scope, or the account
 * is out of storage — the JSON error body does, so carry it into the message and the log.
 */
private fun Response.fail(what: String): Nothing {
    val detail = runCatching { body?.string().orEmpty() }
        .getOrDefault("")
        .replace('\n', ' ')
        .trim()
        .take(ERROR_BODY_LIMIT)
    close()
    Log.w(TAG, "$what failed: HTTP $code ${detail.ifBlank { "(no body)" }}")
    throw IOException("$what failed: HTTP $code${if (detail.isBlank()) "" else " — $detail"}")
}
```

- [ ] **Step 5: Run the client tests**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.crazystudio.sportrecorder.backup.DriveRestClientTest"`
Expected: PASS (3 tests). Note: `:app` main sources will not compile yet because `GoogleDriveBackupStore` still implements the old `BackupStore` — if the test task fails on that, temporarily proceed to Task 7 Step 3 first, then come back and run this. Commit both tasks separately regardless.

- [ ] **Step 6: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts app/src/main/java/com/crazystudio/sportrecorder/backup/DriveRestClient.kt app/src/test/java/com/crazystudio/sportrecorder/backup/DriveRestClientTest.kt
git commit -m "feat(backup): extract DriveRestClient with paged listing and cancellable calls

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 7: `GoogleDriveBackupStore` — parallel transfers, skip-local, `mealCount`

**Files:**
- Create: `app/src/main/java/com/crazystudio/sportrecorder/backup/AccessTokenProvider.kt`
- Modify: `app/src/main/java/com/crazystudio/sportrecorder/backup/GoogleBackupAuth.kt` (implement `AccessTokenProvider`)
- Modify: `app/src/main/java/com/crazystudio/sportrecorder/backup/GoogleDriveBackupStore.kt` (rewrite)
- Modify: `app/src/main/java/com/crazystudio/sportrecorder/di/AppModule.kt:92`
- Test: `app/src/test/java/com/crazystudio/sportrecorder/backup/GoogleDriveBackupStoreTest.kt`

**Interfaces:**
- Consumes: `DriveRestClient` (Task 6), `BackupStore`/`BackupProgress`/`SnapshotInfo.mealCount` (Task 1).
- Produces:
  - `fun interface AccessTokenProvider { suspend fun accessToken(): String }`
  - `class GoogleDriveBackupStore(tokens: AccessTokenProvider, localPhoto: (fileName: String) -> File, drive: DriveRestClient = DriveRestClient()) : BackupStore`

- [ ] **Step 1: Token provider + auth conformance**

`AccessTokenProvider.kt`:
```kotlin
package com.crazystudio.sportrecorder.backup

/** Supplies a live Drive access token. Split from [GoogleBackupAuth] so the store is testable without Play services. */
fun interface AccessTokenProvider {
    suspend fun accessToken(): String
}
```

In `GoogleBackupAuth.kt` change the class header to `) : BackupAuth, AccessTokenProvider {` and mark the existing method `override suspend fun accessToken(): String {`.

- [ ] **Step 2: Write the failing store tests**

`GoogleDriveBackupStoreTest.kt`:

```kotlin
package com.crazystudio.sportrecorder.backup

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class GoogleDriveBackupStoreTest {
    @get:Rule val photosDir = TemporaryFolder()

    private val server = MockWebServer()
    private lateinit var store: GoogleDriveBackupStore
    private val reports = mutableListOf<Triple<BackupStep, Int, Int>>()
    private val progress = BackupProgress { step, done, total -> synchronized(reports) { reports.add(Triple(step, done, total)) } }

    @Before fun setUp() {
        server.start()
        val drive = DriveRestClient(OkHttpClient(), server.url("/").toString().trimEnd('/'))
        store = GoogleDriveBackupStore({ "tok" }, { name -> File(photosDir.root, name) }, drive)
    }

    @After fun tearDown() { server.shutdown() }

    private fun manifestJson(photoNames: List<String>): String = BackupJson.encodeToString(
        BackupDocument.serializer(),
        BackupDocument(
            schemaVersion = BackupDocument.SCHEMA_VERSION, createdAt = 42L, appVersionName = "0.7.1",
            meals = photoNames.mapIndexed { i, name ->
                BackupMeal(i + 1, 1_000L + i, null, null, listOf(BackupPhoto(i + 1, name, 1L)))
            },
            fastingTypes = emptyList(),
            dietSettings = BackupDietSettings(16, 8),
            reminderPrefs = BackupReminderPrefs(false, false, 30, false, 1320, 480),
        ),
    )

    private fun drain(): List<RecordedRequest> = buildList {
        while (true) add(server.takeRequest(200, java.util.concurrent.TimeUnit.MILLISECONDS) ?: break)
    }

    @Test fun uploadSnapshot_uploadsOnlyMissingPhotos_manifestLast_withMealCount() = runTest {
        listOf("a.webp", "b.webp", "c.webp").forEach { File(photosDir.root, it).writeBytes(byteArrayOf(1)) }
        server.enqueue(MockResponse().setBody("""{"files":[{"id":"p-a","name":"a.webp","appProperties":{"kind":"photo"}}]}"""))
        repeat(3) { server.enqueue(MockResponse().setBody("""{"id":"x"}""")) }

        val info = store.uploadSnapshot(manifestJson(listOf("a.webp", "b.webp", "c.webp")), listOf("a.webp", "b.webp", "c.webp"), progress)

        val requests = drain()
        assertEquals(4, requests.size) // list + b + c + manifest
        assertEquals("GET", requests[0].method)
        val bodies = requests.drop(1).map { it.body.readUtf8() }
        assertTrue(bodies.last().contains("\"kind\":\"manifest\""))
        assertTrue(bodies.last().contains("\"mealCount\":\"3\""))
        assertEquals(setOf("b.webp", "c.webp"), bodies.dropLast(1).map { body -> Regex("\"name\":\"([^\"]+)\"").find(body)!!.groupValues[1] }.toSet())
        assertEquals(3, info.mealCount)
        assertEquals(42L, info.createdAt)
        assertTrue(reports.contains(Triple(BackupStep.UploadingPhotos, 2, 2)))
        assertTrue(reports.contains(Triple(BackupStep.UploadingManifest, 1, 1)))
    }

    @Test fun downloadPhotos_skipsPhotosAlreadyOnDevice() = runTest {
        File(photosDir.root, "a.webp").writeBytes(byteArrayOf(9))
        server.enqueue(
            MockResponse().setBody(
                """{"files":[{"id":"p-a","name":"a.webp","appProperties":{"kind":"photo"}},{"id":"p-b","name":"b.webp","appProperties":{"kind":"photo"}}]}""",
            ),
        )
        server.enqueue(MockResponse().setBody("BBB"))

        store.downloadPhotos("snap", listOf("a.webp", "b.webp"), progress)

        val requests = drain()
        assertEquals(2, requests.size) // list + b only
        assertEquals("/drive/v3/files/p-b", requests[1].requestUrl!!.encodedPath)
        assertArrayEquals(byteArrayOf(9), File(photosDir.root, "a.webp").readBytes())
        assertEquals("BBB", File(photosDir.root, "b.webp").readText())
        assertTrue(reports.contains(Triple(BackupStep.DownloadingPhotos, 1, 1)))
    }

    @Test fun downloadPhotos_withNothingMissing_makesNoNetworkCall() = runTest {
        File(photosDir.root, "a.webp").writeBytes(byteArrayOf(9))

        store.downloadPhotos("snap", listOf("a.webp"), progress)

        assertTrue(drain().isEmpty())
        assertTrue(reports.contains(Triple(BackupStep.DownloadingPhotos, 0, 0)))
    }

    @Test fun downloadPhotos_rejectsPathTraversalNames() = runTest {
        val error = runCatching { store.downloadPhotos("snap", listOf("../evil.webp"), progress) }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
        assertFalse(File(photosDir.root.parentFile, "evil.webp").exists())
    }

    @Test fun listSnapshots_readsMealCountWhenPresent() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"files":[
                    {"id":"m1","name":"manifest-1.json","size":"5","appProperties":{"kind":"manifest","snapshotId":"s1","createdAt":"10","appVersionName":"0.7.1","mealCount":"12"}},
                    {"id":"m2","name":"manifest-2.json","size":"5","appProperties":{"kind":"manifest","snapshotId":"s2","createdAt":"20","appVersionName":"0.7.0"}}
                ]}""",
            ),
        )

        val snapshots = store.listSnapshots()

        assertEquals(listOf("s2", "s1"), snapshots.map { it.id })
        assertEquals(null, snapshots[0].mealCount)
        assertEquals(12, snapshots[1].mealCount)
    }
}
```

- [ ] **Step 3: Run to verify it fails**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.crazystudio.sportrecorder.backup.GoogleDriveBackupStoreTest"`
Expected: compile failure (constructor shape / old `BackupStore` signatures).

- [ ] **Step 4: Rewrite `GoogleDriveBackupStore`**

```kotlin
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
```

- [ ] **Step 5: Wire the store in Koin**

In `AppModule.kt`, replace line `single<BackupStore> { GoogleDriveBackupStore(get(), androidContext()) }` with:

```kotlin
    single<BackupStore> {
        val context = androidContext()
        GoogleDriveBackupStore(get<GoogleBackupAuth>(), { name -> PhotoStorage.fileFor(context, name) })
    }
```
and add `import com.crazystudio.sportrecorder.util.PhotoStorage` if not present.

- [ ] **Step 6: Run app unit tests + detekt**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.crazystudio.sportrecorder.backup.*" :app:detekt`
Expected: PASS. `GoogleDriveBackupStore` now has 7 functions (under the 11 threshold). If detekt flags `LongParameterList` on `transferAll` (4 params — fine) or `MaxLineLength`, wrap lines.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/crazystudio/sportrecorder/backup/AccessTokenProvider.kt app/src/main/java/com/crazystudio/sportrecorder/backup/GoogleBackupAuth.kt app/src/main/java/com/crazystudio/sportrecorder/backup/GoogleDriveBackupStore.kt app/src/main/java/com/crazystudio/sportrecorder/di/AppModule.kt app/src/test/java/com/crazystudio/sportrecorder/backup/GoogleDriveBackupStoreTest.kt
git commit -m "feat(backup): parallel photo transfer, skip photos already on device, record mealCount

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 8: Android host — foreground service, progress notification, Koin + Route wiring

**Files:**
- Create: `app/src/main/java/com/crazystudio/sportrecorder/backup/BackupNotifications.kt`
- Create: `app/src/main/java/com/crazystudio/sportrecorder/backup/BackupForegroundService.kt`
- Create: `app/src/main/java/com/crazystudio/sportrecorder/backup/AndroidBackupJobHost.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/res/values/strings.xml`, `app/src/main/res/values-zh-rTW/strings.xml`
- Modify: `app/src/main/java/com/crazystudio/sportrecorder/di/AppModule.kt`
- Modify: `app/src/main/java/com/crazystudio/sportrecorder/ui/backup/BackupRoute.kt`

**Interfaces:**
- Consumes: `BackupJobRunner`, `BackupJobState`, `BackupJobKind`, `BackupOutcome`, `BackupStep`, `BackupJobHost`; shared string resources via `org.jetbrains.compose.resources.getString(Res.string.x, args…)` (suspend).
- Produces: `BackupNotifications(context)` with `ensureChannel()`, `PROGRESS_ID`, `RESULT_ID`, `suspend fun progress(state: BackupJobState.Running?): Notification`, `suspend fun result(state: BackupJobState.Finished): Notification`, `fun cancelResult()`; `BackupForegroundService` with `ACTION_CANCEL`; `AndroidBackupJobHost(context) : BackupJobHost`.

No JVM test for this task (Android framework); verification is the build gate plus the manual device checklist in Task 9.

- [ ] **Step 1: Android string resources**

`app/src/main/res/values/strings.xml` — add before `</resources>`:
```xml

    <!-- Drive backup foreground service (progress channel is silent; result is one-shot). -->
    <string name="backup_channel_name">Backup progress</string>
    <string name="backup_notif_backing_up">Backing up</string>
    <string name="backup_notif_restoring">Restoring</string>
```
`app/src/main/res/values-zh-rTW/strings.xml`:
```xml

    <!-- Drive 備份前景服務 -->
    <string name="backup_channel_name">備份進度</string>
    <string name="backup_notif_backing_up">正在備份</string>
    <string name="backup_notif_restoring">正在還原</string>
```

- [ ] **Step 2: Manifest**

In `AndroidManifest.xml` add after the `INTERNET` permission:
```xml
    <!-- Keeps a user-started backup/restore alive with the screen off (progress notification). -->
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />
```
and inside `<application>` after the `BootReceiver` entry:
```xml

        <!-- Hosts a running backup/restore job; started only by the user's own tap. -->
        <service
            android:name=".backup.BackupForegroundService"
            android:exported="false"
            android:foregroundServiceType="dataSync" />
```

- [ ] **Step 3: `BackupNotifications`**

```kotlin
package com.crazystudio.sportrecorder.backup

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.crazystudio.sportrecorder.MainActivity
import com.crazystudio.sportrecorder.R
import com.crazystudio.sportrecorder.shared.resources.Res
import com.crazystudio.sportrecorder.shared.resources.backup_cancel
import com.crazystudio.sportrecorder.shared.resources.backup_msg_backup_complete
import com.crazystudio.sportrecorder.shared.resources.backup_msg_cancelled
import com.crazystudio.sportrecorder.shared.resources.backup_msg_failed
import com.crazystudio.sportrecorder.shared.resources.backup_msg_restore_complete
import com.crazystudio.sportrecorder.shared.resources.backup_msg_schema_too_new
import com.crazystudio.sportrecorder.shared.resources.backup_step_applying
import com.crazystudio.sportrecorder.shared.resources.backup_step_downloading_manifest
import com.crazystudio.sportrecorder.shared.resources.backup_step_downloading_photos
import com.crazystudio.sportrecorder.shared.resources.backup_step_preparing
import com.crazystudio.sportrecorder.shared.resources.backup_step_pruning
import com.crazystudio.sportrecorder.shared.resources.backup_step_safety_backup
import com.crazystudio.sportrecorder.shared.resources.backup_step_uploading_manifest
import com.crazystudio.sportrecorder.shared.resources.backup_step_uploading_photos
import org.jetbrains.compose.resources.getString

private const val CHANNEL_ID = "backup_progress"

/**
 * Builds the two notifications the backup job uses: an ongoing progress card while it runs and a
 * silent, auto-cancel result afterwards. Copy comes from the shared Compose resources so the
 * screen and the notification always say the same thing. Deliberately not a reminder: it exists
 * only for a job the user started, and never asks them to back up.
 */
class BackupNotifications(private val context: Context) {

    fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.backup_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
    }

    /** Ongoing progress. [state] null → the job has not reported yet (indeterminate, generic title). */
    suspend fun progress(state: BackupJobState.Running?): Notification {
        val title = when (state?.kind) {
            BackupJobKind.Restore -> R.string.backup_notif_restoring
            else -> R.string.backup_notif_backing_up
        }
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_baseline_download_24)
            .setContentTitle(context.getString(title))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(openAppIntent())
            .addAction(0, getString(Res.string.backup_cancel), cancelIntent())
        if (state == null) {
            builder.setProgress(0, 0, true)
        } else {
            builder.setContentText(stepCaption(state))
            builder.setProgress(state.total, state.done, state.total == 0)
        }
        return builder.build()
    }

    /** One-shot result. Silent, auto-cancel; tapping opens the app. */
    suspend fun result(state: BackupJobState.Finished): Notification {
        val text = when (state.outcome) {
            BackupOutcome.Completed ->
                if (state.kind == BackupJobKind.Backup) Res.string.backup_msg_backup_complete else Res.string.backup_msg_restore_complete
            BackupOutcome.Cancelled -> Res.string.backup_msg_cancelled
            BackupOutcome.SchemaTooNew -> Res.string.backup_msg_schema_too_new
            BackupOutcome.Failed -> Res.string.backup_msg_failed
        }
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_baseline_download_24)
            .setContentTitle(getString(text))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(openAppIntent())
            .build()
    }

    /** The screen already showed the outcome as a snackbar; drop the duplicate. */
    fun cancelResult() = NotificationManagerCompat.from(context).cancel(RESULT_ID)

    private suspend fun stepCaption(state: BackupJobState.Running): String = when (state.step) {
        BackupStep.Preparing -> getString(Res.string.backup_step_preparing)
        BackupStep.SafetyBackup -> getString(Res.string.backup_step_safety_backup)
        BackupStep.UploadingPhotos -> getString(Res.string.backup_step_uploading_photos, state.done, state.total)
        BackupStep.UploadingManifest -> getString(Res.string.backup_step_uploading_manifest)
        BackupStep.Pruning -> getString(Res.string.backup_step_pruning)
        BackupStep.DownloadingManifest -> getString(Res.string.backup_step_downloading_manifest)
        BackupStep.DownloadingPhotos -> getString(Res.string.backup_step_downloading_photos, state.done, state.total)
        BackupStep.Applying -> getString(Res.string.backup_step_applying)
    }

    private fun openAppIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            REQUEST_OPEN,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun cancelIntent(): PendingIntent {
        val intent = Intent(context, BackupForegroundService::class.java).setAction(BackupForegroundService.ACTION_CANCEL)
        return PendingIntent.getService(
            context,
            REQUEST_CANCEL,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    companion object {
        const val PROGRESS_ID = 3001
        const val RESULT_ID = 3002
        private const val REQUEST_OPEN = 30
        private const val REQUEST_CANCEL = 31
    }
}
```

- [ ] **Step 4: `BackupForegroundService`**

```kotlin
package com.crazystudio.sportrecorder.backup

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.koin.android.ext.android.inject

/**
 * Keeps the process alive while a [BackupJobRunner] job runs and mirrors its state into a
 * progress notification. It owns no job logic: the runner is the source of truth, this service
 * just observes it and stops itself when the job is over (or was already over when it started).
 */
class BackupForegroundService : Service() {

    private val runner: BackupJobRunner by inject()
    private val notifications by lazy { BackupNotifications(this) }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var observing: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL) {
            runner.cancel()
            return START_NOT_STICKY
        }
        notifications.ensureChannel()
        // startForeground must happen promptly after startForegroundService; the first card is the
        // cheap generic one, the collector below replaces it with real progress.
        val initial = runBlocking { notifications.progress(runner.state.value as? BackupJobState.Running) }
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        } else {
            0
        }
        ServiceCompat.startForeground(this, BackupNotifications.PROGRESS_ID, initial, type)
        if (observing == null) observing = scope.launch { observeRunner() }
        return START_NOT_STICKY
    }

    private suspend fun observeRunner() {
        runner.state.collect { state ->
            when (state) {
                is BackupJobState.Running ->
                    NotificationManagerCompat.from(this).notify(BackupNotifications.PROGRESS_ID, notifications.progress(state))
                is BackupJobState.Finished -> {
                    NotificationManagerCompat.from(this).notify(BackupNotifications.RESULT_ID, notifications.result(state))
                    finish()
                }
                BackupJobState.Idle -> finish() // started after the job was acknowledged: nothing to show
            }
        }
    }

    private fun finish() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_CANCEL = "com.crazystudio.sportrecorder.backup.CANCEL"
    }
}
```

Lint note: `NotificationManagerCompat.notify` inside a foreground service does not need the `POST_NOTIFICATIONS` runtime check for the FGS notification itself, but lint's `MissingPermission`/`NotificationPermission` may still flag the `notify` calls. If `:app:lintDebug` fails on them, add `@SuppressLint("MissingPermission")` on `observeRunner()` with a comment: "FGS notifications are shown by the system; if the user denied POST_NOTIFICATIONS the call is a silent no-op, which is the intended behavior (the job runs anyway)."

- [ ] **Step 5: `AndroidBackupJobHost`**

```kotlin
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
```

- [ ] **Step 6: Koin wiring**

In `AppModule.kt`, in the `// Backup` section after the `BackupService` single, add:
```kotlin
    single<BackupJobHost> { AndroidBackupJobHost(androidContext()) }
    single { BackupJobRunner(get(), get()) }
```
and change the ViewModel line to:
```kotlin
    viewModel { BackupViewModel(get(), get(), get(), get()) }
```
Imports to add: `com.crazystudio.sportrecorder.backup.AndroidBackupJobHost`, `com.crazystudio.sportrecorder.backup.BackupJobHost`, `com.crazystudio.sportrecorder.backup.BackupJobRunner`. (`ObserveEatRecordsUseCase` is already a `factory` in this module, so the 4th `get()` resolves.)

- [ ] **Step 7: `BackupRoute` — permission prompt, cancel, result-notification cleanup**

Replace `BackupRoute.kt`:

```kotlin
package com.crazystudio.sportrecorder.ui.backup

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.crazystudio.sportrecorder.backup.BackupAuthorizationRequiredException
import com.crazystudio.sportrecorder.backup.BackupNotifications
import com.crazystudio.sportrecorder.backup.GoogleBackupAuth
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

private const val TAG = "BackupRoute"

/**
 * :app wrapper around the shared [BackupScreen]. Owns the Android-only bits: the Google consent
 * flow (StartIntentSenderForResult for the authorization PendingIntent), the silent account
 * refresh on open, the one-time notification-permission prompt before a job, and dropping the
 * result notification once the screen has shown the same outcome as a snackbar.
 */
@Composable
fun BackupRoute(onBack: () -> Unit) {
    val vm: BackupViewModel = koinViewModel()
    val auth: GoogleBackupAuth = koinInject()
    val state by vm.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val notifications = remember(context) { BackupNotifications(context) }

    val consentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        scope.launch {
            // Surface any failure after consent — otherwise the dialog just closes and nothing
            // happens. A plain user cancel (no result data) stays quiet.
            runCatching { auth.onAuthorizationResult(result.data) }
                .onFailure { if (result.resultCode == Activity.RESULT_OK || result.data != null) vm.reportFailure() }
        }
    }

    // The job runs with or without notification permission; the prompt just lets the user see
    // progress with the screen off. Whatever they answer, the pending action proceeds.
    var pendingJob by remember { mutableStateOf<(() -> Unit)?>(null) }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        pendingJob?.invoke()
        pendingJob = null
    }

    fun withNotificationPermission(action: () -> Unit) {
        val needsPrompt = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        if (needsPrompt) {
            pendingJob = action
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            action()
        }
    }

    // On open, silently populate the signed-in account if consent was already granted.
    LaunchedEffect(Unit) { runCatching { auth.refreshAccount() } }
    // Once signed in, load the snapshot list — keyed on the account itself, so switching
    // accounts (not just signing out and back in) reloads it.
    LaunchedEffect(state.account?.email) { if (state.isSignedIn) vm.refreshSnapshots() }

    // Every Drive call needs a live token, and Google can ask for consent again at any point
    // (the token is short-lived and nothing is persisted). Run every action through here so the
    // consent sheet appears before a job, never inside one.
    fun authorizedThen(action: () -> Unit) {
        scope.launch {
            runCatching { auth.accessToken() } // populates account on success
                .onSuccess { action() }
                .onFailure { e ->
                    if (e is BackupAuthorizationRequiredException) {
                        consentLauncher.launch(IntentSenderRequest.Builder(e.pendingIntent).build())
                    } else {
                        Log.w(TAG, "authorization failed before a backup action", e)
                        vm.reportFailure()
                    }
                }
        }
    }

    BackupScreen(
        state = state,
        onSignIn = { authorizedThen { } },
        onSignOut = { auth.signOut() },
        onBackup = { authorizedThen { withNotificationPermission { vm.backup() } } },
        onRestore = { snapshot -> authorizedThen { withNotificationPermission { vm.restore(snapshot.id) } } },
        onCancel = vm::cancel,
        onConsumeMessage = {
            // The snackbar has been shown for this outcome; the notification would only repeat it.
            notifications.cancelResult()
            vm.consumeMessage()
        },
        onBack = onBack,
    )
}
```

- [ ] **Step 8: Run the full gate**

Run: `.\gradlew.bat assembleDebug testDebugUnitTest :app:detekt :app:lintDebug :shared:jvmTest`
Expected: BUILD SUCCESSFUL. Typical fixes if not:
- lint `ForegroundServiceType`/`ForegroundServicePermission`: confirm both `FOREGROUND_SERVICE*` permissions and `foregroundServiceType="dataSync"` are in the manifest.
- lint `NotificationPermission` on `notify` in the service: see the note in Step 4.
- detekt `TooManyFunctions` in `BackupNotifications` (currently 7): fine; `MagicNumber`: all ids are named constants.

- [ ] **Step 9: Commit**

```bash
git add app/src/main/AndroidManifest.xml app/src/main/res app/src/main/java/com/crazystudio/sportrecorder/backup/BackupNotifications.kt app/src/main/java/com/crazystudio/sportrecorder/backup/BackupForegroundService.kt app/src/main/java/com/crazystudio/sportrecorder/backup/AndroidBackupJobHost.kt app/src/main/java/com/crazystudio/sportrecorder/di/AppModule.kt app/src/main/java/com/crazystudio/sportrecorder/ui/backup/BackupRoute.kt
git commit -m "feat(backup): foreground service with progress notification hosts backup jobs

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 9: Docs, device checklist, PR

**Files:**
- Modify: `docs/DEVELOPMENT.md`
- Modify: `docs/superpowers/specs/2026-06-23-drive-backup-design.md:77`
- Modify: `docs/superpowers/specs/2026-09-27-backup-optimization-design.md` (Status line)

- [ ] **Step 1: Update `DEVELOPMENT.md`**

In §4 「進行中 / 下一步」 item 2 (備份引擎已知取捨), remove the bullet 「Drive 檔案列表未分頁(>1000 檔案有風險,已在程式碼註解)」 and add a new item:

```markdown
5. **備份優化(issue #64)— 分支 `claude/backup-optimization-64`**
   `BackupJobRunner`(commonMain,app 等級 scope)+ Android `BackupForegroundService` 讓備份離開頁面、
   關螢幕也會跑完;進度條與通知;照片並行傳輸、還原跳過本機已有照片;Drive 列表分頁;
   還原前先自動備份一份安全快照。spec:`2026-09-27-backup-optimization-design.md`。
```

In §6 the doc index table, add a row at the bottom:

```markdown
| 09-27 | 備份優化(進度、前景服務、並行、分頁、還原安全快照) | ✓ | ✓ | 實作中(`claude/backup-optimization-64`) |
```

In §2, the `backup/` line for `:shared/commonMain`, append `、\`BackupJobRunner\`、\`BackupProgress\``; in the `:app` line, append `、\`backup/DriveRestClient\`+\`BackupForegroundService\``.

- [ ] **Step 2: Mark the old spec**

In `2026-06-23-drive-backup-design.md` line 77, change `Record "last backed up at". Progress UI + cancel.` to:
```markdown
   - Record "last backed up at". Progress UI + cancel — delivered by `2026-09-27-backup-optimization-design.md`.
```

- [ ] **Step 3: Commit docs**

```bash
git add docs/DEVELOPMENT.md docs/superpowers/specs/2026-06-23-drive-backup-design.md
git commit -m "docs: record backup optimization in development status and the original backup spec

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

- [ ] **Step 4: Manual device checklist (run on the Pixel_10_Pro AVD or a phone; record results in the PR)**

1. Sign in, tap 立即備份 with ≥ 10 photos → progress bar shows 「上傳照片 n / N」, notification shows the same; back out of the screen mid-way → notification keeps progressing → 「已備份。」 result notification appears; reopen the screen → snackbar 「已備份。」, result notification disappears after the snackbar.
2. Start a backup, press 取消 in the notification → screen shows 「已取消，什麼都沒有改變。」, snapshot list unchanged.
3. Restore the newest snapshot onto the same phone → dialog shows 「會先把這台裝置目前的 N 筆紀錄備份一份…（M 筆）」; after restore the list has one extra (safety) snapshot; photos step is near-instant (skipped locally).
4. Screen off during a restore's download → resumes and completes; 「已還原。」 notification.
5. Deny POST_NOTIFICATIONS when prompted → backup still completes (no notification).

- [ ] **Step 5: Open the PR**

```bash
git push -u origin claude/backup-optimization-64
gh pr create --title "feat(backup): progress, foreground job, parallel transfer, paging, safety-snapshot restore (#64)" --body-file - <<'EOF'
Closes #64.

## What
- **Keeps running when you leave the screen or turn it off** — jobs move from `viewModelScope` into an app-scoped `BackupJobRunner`, hosted on Android by `BackupForegroundService` with a progress notification (silent channel, one-shot result, cancel action).
- **Progress** — `BackupProgress` step/done/total from the store up to the screen and the notification.
- **Faster** — photos upload/download 4 at a time; restore skips photos already on the device; one Drive listing per operation.
- **Drive listing pages** through `nextPageToken` (MockWebServer-tested).
- **Restore is reversible** — a safety snapshot of the current device data is uploaded first (never pruned in that step), and the confirm dialog says so with counts.

Spec: `docs/superpowers/specs/2026-09-27-backup-optimization-design.md`
Plan: `docs/superpowers/plans/2026-09-27-backup-optimization.md`

## Tests
- shared: `BackupServiceTest` (progress order, safety snapshot, no-prune), `BackupJobRunnerTest`, `BackupViewModelTest`
- app: `DriveRestClientTest` (paging, multipart, error reason), `GoogleDriveBackupStoreTest` (parallel + manifest-last, skip-local, mealCount)

## Device checklist
- [ ] backup keeps going after leaving the screen; notification progresses; result shows once
- [ ] cancel from notification → 「已取消，什麼都沒有改變。」
- [ ] restore onto same phone → dialog shows counts; safety snapshot appears; photo step near-instant
- [ ] screen off during restore → completes
- [ ] notifications denied → job still completes

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
```

---

## Self-review against the spec

- **Point 3 / runner + FGS** → Tasks 3, 8. **Progress** → Tasks 1, 5, 8. **Speed (parallel, skip-local, one listing)** → Task 7. **Paging** → Task 6. **Safety snapshot + dialog copy + mealCount** → Tasks 2, 5, 7. **Cancel** → Tasks 3, 5, 8 (`NonCancellable` apply in Task 2). **Notification permission prompt** → Task 8. **Result notification cleared by the screen** → Task 8 Step 7. **Docs** → Task 9.
- Type consistency: `BackupStore.uploadSnapshot(manifestJson, photoFileNames, progress)` / `downloadPhotos(id, photoFileNames, progress)` used identically in Tasks 1, 2, 7 and the fake. `BackupViewModel(runner, backupService, backupAuth, observeEatRecords)` matches Task 4 tests and Task 8 Koin. `BackupScreen(..., onRestore, onCancel, onConsumeMessage, onBack)` matches Task 5 and Task 8. `BackupNotifications.PROGRESS_ID/RESULT_ID`, `BackupForegroundService.ACTION_CANCEL` used consistently in Task 8.
- Known gap (accepted in spec): no automated test that `Applying` is non-cancellable; covered by code review of `withContext(NonCancellable)` and the device checklist.
