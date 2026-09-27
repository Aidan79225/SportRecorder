# Backup Instrumented Tests Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Local-only Android instrumented tests that cover what PR #67's JVM suites cannot: the rendered `BackupScreen`, the real `BackupForegroundService` + notifications, and `BackupService` over real Room.

**Architecture:** Everything lives in `app/src/androidTest/java/com/crazystudio/sportrecorder/backup/`. One fixtures file provides an in-memory gated `BackupStore`, a Koin override helper (real runner + real service, fake cloud), and a polling helper. Three test classes, one per layer. No production code changes, no new dependencies.

**Tech Stack:** AndroidJUnit4, Compose `ui-test-junit4` (`createComposeRule`), `ActivityScenario` (from `androidx.test:core`, transitively present), Koin `loadKoinModules`, Room `inMemoryDatabaseBuilder`, DataStore `PreferenceDataStoreFactory`, Compose Resources `getString`.

**Spec:** `docs/superpowers/specs/2026-09-27-backup-instrumented-tests-design.md`

## Global Constraints

- Branch: `claude/backup-optimization-64` (PR #67). Commit messages end with `Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>`.
- No changes under `app/src/main` or `shared/src` (test-only). No new entries in `gradle/libs.versions.toml`.
- Emulator: use `emulator-5556` (AVD `E2E_Api35_B`, API 35, already running). Every Gradle call in PowerShell is preceded by `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"`; set `$env:ANDROID_SERIAL = "emulator-5556"` so `connectedDebugAndroidTest` targets one device. Run a single class with `-Pandroid.testInstrumentationRunnerArguments.class=<fqcn>`. Use the maximum tool timeout (10 min) for instrumented runs.
- Compile check for the test APK: `.\gradlew.bat :app:assembleDebugAndroidTest`. The JVM gate (`assembleDebug testDebugUnitTest :app:detekt :app:lintDebug :shared:jvmTest`) must stay green (detekt excludes `androidTest`; lint may inspect it — fix any lint error in touched files).
- Expected strings come from Compose Resources: `org.jetbrains.compose.resources.getString(Res.string.<key>, args…)` is `suspend`; wrap in `runBlocking`. Accessors are imported one per key from `com.crazystudio.sportrecorder.shared.resources`.
- Test copy assertions must not hard-code a locale.
- Notification ids: progress `BackupNotifications.PROGRESS_ID` (3001), result `BackupNotifications.RESULT_ID` (3002). Cancel action: `BackupForegroundService.ACTION_CANCEL`.

---

## File map

- Delete: `app/src/androidTest/java/com/crazystudio/sportrecorder/ExampleInstrumentedTest.kt`
- Create: `app/src/androidTest/java/com/crazystudio/sportrecorder/backup/BackupTestFixtures.kt` — `GatedBackupStore`, `awaitUntil`, `activeNotification`, `loadBackupTestModule`
- Create: `app/src/androidTest/java/com/crazystudio/sportrecorder/backup/BackupRoundTripRoomTest.kt`
- Create: `app/src/androidTest/java/com/crazystudio/sportrecorder/backup/BackupScreenTest.kt`
- Create: `app/src/androidTest/java/com/crazystudio/sportrecorder/backup/BackupForegroundServiceTest.kt`
- Modify: `docs/DEVELOPMENT.md` (§5 run instructions, §6 index row)

---

### Task 1: Fixtures + Room round-trip test

**Files:**
- Delete: `app/src/androidTest/java/com/crazystudio/sportrecorder/ExampleInstrumentedTest.kt`
- Create: `app/src/androidTest/java/com/crazystudio/sportrecorder/backup/BackupTestFixtures.kt`
- Create: `app/src/androidTest/java/com/crazystudio/sportrecorder/backup/BackupRoundTripRoomTest.kt`

**Interfaces:**
- Consumes (production, unchanged): `BackupStore` (`listSnapshots`, `uploadSnapshot(manifestJson, photoFileNames, progress)`, `downloadManifest(id)`, `downloadPhotos(id, photoFileNames, progress)`, `prune(keepLast)`), `SnapshotInfo(id, createdAt, appVersionName, sizeBytes, mealCount)`, `BackupProgress`, `BackupStep`, `BackupJson`, `BackupDocument`/`BackupMeal`/`BackupPhoto`/`BackupGeoPoint`/`BackupFastingType`/`BackupDietSettings`/`BackupReminderPrefs`, `BackupService(eatRepo, fastingRepo, settingsRepo, prefsRepo, store, rescheduler, appVersionName, now)`, `AppDatabase` (`getEatTimeDao()`, `getFastingTypeDao()`, `getPhotoDao()`), `EatRecordRepositoryImpl(appDatabase, eatTimeDao, photoDao, photoFileStore)`, `FastingTypeRepositoryImpl(appDatabase, fastingTypeDao)`, `DietSettingsRepositoryImpl(dataStore)`, `ReminderPreferencesRepositoryImpl(dataStore)`, `PhotoFileStore { fun delete(fileName) }`, `RemindersRescheduler { suspend fun reschedule() }`, `EatRecord(id, time, location, note, photos)`, `EatPhoto(id, fileName, createdAt)`, `GeoPoint(lat, lng)`, `FastingWindow(fastingHours, eatingHours)`, `CustomFastingType(fastingHours, eatingHours, name)`, `DietSettings(fastingHours, eatingHours)`, `ReminderPrefs`.
- Produces (for Tasks 2–3): `class GatedBackupStore : BackupStore` with `uploads: List<Upload>`, `uploadGate`, `downloadGate`, `failDownloadPhotos`, `pruneCalls`, `seedSnapshot(info, json)`; `fun awaitUntil(timeoutMs: Long = 10_000, stepMs: Long = 50, what: String, condition: () -> Boolean)`; `fun Context.activeNotification(id: Int): StatusBarNotification?`.

- [ ] **Step 1: Delete the template test**

```bash
git rm app/src/androidTest/java/com/crazystudio/sportrecorder/ExampleInstrumentedTest.kt
```

- [ ] **Step 2: Write the fixtures (store + helpers; the Koin helper is added in Task 3)**

`BackupTestFixtures.kt`:

```kotlin
package com.crazystudio.sportrecorder.backup

import android.app.NotificationManager
import android.content.Context
import android.os.SystemClock
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.fail

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
```

- [ ] **Step 3: Write the Room round-trip test**

`BackupRoundTripRoomTest.kt`:

```kotlin
package com.crazystudio.sportrecorder.backup

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.crazystudio.sportrecorder.data.PhotoFileStore
import com.crazystudio.sportrecorder.data.repository.DietSettingsRepositoryImpl
import com.crazystudio.sportrecorder.data.repository.EatRecordRepositoryImpl
import com.crazystudio.sportrecorder.data.repository.FastingTypeRepositoryImpl
import com.crazystudio.sportrecorder.data.repository.ReminderPreferencesRepositoryImpl
import com.crazystudio.sportrecorder.database.AppDatabase
import com.crazystudio.sportrecorder.domain.model.CustomFastingType
import com.crazystudio.sportrecorder.domain.model.DietSettings
import com.crazystudio.sportrecorder.domain.model.EatRecord
import com.crazystudio.sportrecorder.domain.model.FastingWindow
import com.crazystudio.sportrecorder.domain.model.GeoPoint
import com.crazystudio.sportrecorder.domain.reminder.RemindersRescheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/**
 * BackupService over the REAL Room repositories and a REAL DataStore, with only the cloud faked.
 * This is the one place `EatRecordRepositoryImpl.replaceAll` is exercised against SQLite rather
 * than a fake that restates its contract.
 */
@RunWith(AndroidJUnit4::class)
class BackupRoundTripRoomTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var db: AppDatabase
    private lateinit var dataStoreScope: CoroutineScope
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var eatRepo: EatRecordRepositoryImpl
    private lateinit var fastingRepo: FastingTypeRepositoryImpl
    private lateinit var settingsRepo: DietSettingsRepositoryImpl
    private lateinit var prefsRepo: ReminderPreferencesRepositoryImpl
    private val store = GatedBackupStore()
    private var rescheduleCount = 0
    private val deletedPhotos = mutableListOf<String>()

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        dataStoreScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        dataStore = PreferenceDataStoreFactory.create(scope = dataStoreScope) {
            File(context.cacheDir, "backup-test-${UUID.randomUUID()}.preferences_pb")
        }
        val photoFiles = PhotoFileStore { name -> deletedPhotos.add(name) }
        eatRepo = EatRecordRepositoryImpl(db, db.getEatTimeDao(), db.getPhotoDao(), photoFiles)
        fastingRepo = FastingTypeRepositoryImpl(db, db.getFastingTypeDao())
        settingsRepo = DietSettingsRepositoryImpl(dataStore)
        prefsRepo = ReminderPreferencesRepositoryImpl(dataStore)
    }

    @After fun tearDown() {
        db.close()
        dataStoreScope.cancel()
    }

    private fun service() = BackupService(
        eatRepo, fastingRepo, settingsRepo, prefsRepo, store,
        RemindersRescheduler { rescheduleCount++ },
        appVersionName = "test",
    ) { 4_242L }

    private suspend fun seedTwoMeals() {
        eatRepo.save(EatRecord(0, 1_000L, GeoPoint(25.03, 121.56), "lunch", emptyList()), listOf("a.webp"), emptyList())
        eatRepo.save(EatRecord(0, 2_000L, null, "dinner", emptyList()), emptyList(), emptyList())
    }

    private fun List<EatRecord>.shape() = map { Triple(it.time, it.note, it.location) to it.photos.map { p -> p.fileName } }

    @Test fun backup_thenRestore_bringsEverythingBack() = runBlocking {
        seedTwoMeals()
        fastingRepo.add(FastingWindow(18, 6), "my-18-6")
        settingsRepo.setSelection(FastingWindow(20, 4))
        prefsRepo.setLeadMinutes(45)
        val before = eatRepo.observeAll().first()
        val svc = service()

        val info = svc.backup()
        eatRepo.replaceAll(emptyList())
        fastingRepo.replaceAllCustom(emptyList())
        settingsRepo.setSelection(FastingWindow(16, 8))
        prefsRepo.setLeadMinutes(30)
        assertTrue(eatRepo.observeAll().first().isEmpty())

        svc.restore(info.id)

        val after = eatRepo.observeAll().first()
        assertEquals(before.shape(), after.shape()) // newest-first, same content, same photo names
        assertEquals(listOf(CustomFastingType(18, 6, "my-18-6")), fastingRepo.observeRecentCustomTypes().first())
        assertEquals(DietSettings(20, 4), settingsRepo.settings.first())
        assertEquals(45L, prefsRepo.prefs.first().leadMinutes)
        assertEquals(1, rescheduleCount)
        assertEquals(2, info.mealCount)
    }

    @Test fun restore_ontoDeviceWithData_uploadsSafetySnapshotFirst_withoutPrune() = runBlocking {
        seedTwoMeals()
        val seedJson = BackupJson.encodeToString(
            BackupDocument.serializer(),
            BackupDocument(
                schemaVersion = BackupDocument.SCHEMA_VERSION, createdAt = 1L, appVersionName = "seed",
                meals = listOf(BackupMeal(1, 9_000L, "from-cloud", null, emptyList())),
                fastingTypes = emptyList(),
                dietSettings = BackupDietSettings(16, 8),
                reminderPrefs = BackupReminderPrefs(false, false, 30, false, 1320, 480),
            ),
        )
        store.seedSnapshot(SnapshotInfo("seed", 1L, "seed", 1L, 1), seedJson)

        service().restore("seed")

        val safety = store.uploads.single()
        val safetyDoc = BackupJson.decodeFromString(BackupDocument.serializer(), safety.manifestJson)
        assertEquals(listOf("dinner", "lunch"), safetyDoc.meals.map { it.note }) // newest-first, the pre-restore data
        assertEquals(listOf("a.webp"), safety.uploadedPhotos)
        assertEquals(0, store.pruneCalls)
        assertEquals(listOf("from-cloud"), eatRepo.observeAll().first().map { it.note })
    }

    @Test fun restore_whenDownloadFails_leavesRoomUntouched() = runBlocking {
        seedTwoMeals()
        val info = service().backup()
        store.failDownloadPhotos = true
        val before = eatRepo.observeAll().first().shape()
        rescheduleCount = 0

        assertThrows(IllegalStateException::class.java) { runBlocking { service().restore(info.id) } }

        assertEquals(before, eatRepo.observeAll().first().shape())
        assertEquals(0, rescheduleCount)
    }
}
```

Notes for the implementer: `BackupMeal`'s parameter order is `(id, time, note, location, photos)` — check `BackupDocument.kt` and adjust the seed if it differs. `PhotoFileStore` and `RemindersRescheduler` are plain interfaces; if SAM conversion does not compile because they are not `fun interface`s, use `object : PhotoFileStore { override fun delete(fileName: String) { … } }` / `object : RemindersRescheduler { override suspend fun reschedule() { rescheduleCount++ } }`.

- [ ] **Step 4: Compile the test APK**

Run: `.\gradlew.bat :app:assembleDebugAndroidTest`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Run the class on the emulator**

Run (PowerShell): `$env:ANDROID_SERIAL = "emulator-5556"; .\gradlew.bat :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.crazystudio.sportrecorder.backup.BackupRoundTripRoomTest"`
Expected: BUILD SUCCESSFUL, 3 tests passed (report at `app/build/reports/androidTests/connected/debug/index.html`). If a test fails, read the failure from `app/build/outputs/androidTest-results/connected/debug/` and fix the test (production code is out of scope; if the failure reveals a production bug, stop and report it).

- [ ] **Step 6: Commit**

```bash
git add -A app/src/androidTest
git commit -m "test(backup): instrumented fixtures and Room-backed backup/restore round trip

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 2: `BackupScreenTest` (Compose UI)

**Files:**
- Create: `app/src/androidTest/java/com/crazystudio/sportrecorder/backup/BackupScreenTest.kt`

**Interfaces:**
- Consumes: `BackupScreen(state, onSignIn, onSignOut, onBackup, onRestore, onCancel, onConsumeMessage, onBack)`, `BackupUiState(account, snapshots, job, isLoadingSnapshots, localMealCount, message)`, `BackupAccount(email)`, `BackupJobState.Running(kind, step, done, total)`, `BackupMessage`, `SportRecorderTheme` (`com.crazystudio.sportrecorder.ui.theme`), shared strings `backup_step_uploading_photos`, `backup_cancel`, `backup_snapshot_subtitle`, `backup_restore_confirm_message`, `_counted`, `_fresh`, `backup_restore_confirm_button`, `backup_msg_cancelled`.

- [ ] **Step 1: Write the test**

```kotlin
package com.crazystudio.sportrecorder.backup

import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.crazystudio.sportrecorder.shared.resources.Res
import com.crazystudio.sportrecorder.shared.resources.backup_cancel
import com.crazystudio.sportrecorder.shared.resources.backup_msg_cancelled
import com.crazystudio.sportrecorder.shared.resources.backup_restore_confirm_button
import com.crazystudio.sportrecorder.shared.resources.backup_restore_confirm_message
import com.crazystudio.sportrecorder.shared.resources.backup_restore_confirm_message_counted
import com.crazystudio.sportrecorder.shared.resources.backup_restore_confirm_message_fresh
import com.crazystudio.sportrecorder.shared.resources.backup_snapshot_subtitle
import com.crazystudio.sportrecorder.shared.resources.backup_step_uploading_photos
import com.crazystudio.sportrecorder.ui.backup.BackupMessage
import com.crazystudio.sportrecorder.ui.backup.BackupScreen
import com.crazystudio.sportrecorder.ui.backup.BackupUiState
import com.crazystudio.sportrecorder.ui.theme.SportRecorderTheme
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@RunWith(AndroidJUnit4::class)
class BackupScreenTest {
    @get:Rule val compose = createComposeRule()

    private var cancels = 0
    private var restores = mutableListOf<SnapshotInfo>()
    private var consumed = 0

    private fun str(res: StringResource, vararg args: Any): String = runBlocking { getString(res, *args) }

    /** Same "yyyy-MM-dd HH:mm" in the device zone that the screen renders. */
    private fun dateText(epochMillis: Long): String =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()))

    private fun show(state: BackupUiState) {
        compose.setContent {
            SportRecorderTheme {
                BackupScreen(
                    state = state,
                    onSignIn = {}, onSignOut = {}, onBackup = {},
                    onRestore = { restores.add(it) },
                    onCancel = { cancels++ },
                    onConsumeMessage = { consumed++ },
                    onBack = {},
                )
            }
        }
    }

    private val signedIn = BackupUiState(account = BackupAccount("me@example.com"))
    private val snapshot = SnapshotInfo("s1", 1_700_000_000_000L, "0.7.1", 10L, mealCount = 7)

    @Test fun runningUpload_showsFractionCaptionAndEnabledCancel() {
        show(signedIn.copy(job = BackupJobState.Running(BackupJobKind.Backup, BackupStep.UploadingPhotos, 12, 48)))

        compose.onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo(0.25f, 0f..1f))).assertExists()
        compose.onNodeWithText(str(Res.string.backup_step_uploading_photos, 12, 48)).assertExists()
        compose.onNodeWithText(str(Res.string.backup_cancel)).assertIsEnabled().performClick()
        assertEquals(1, cancels)
    }

    @Test fun applying_disablesCancel() {
        show(signedIn.copy(job = BackupJobState.Running(BackupJobKind.Restore, BackupStep.Applying, 0, 0)))
        compose.onNodeWithText(str(Res.string.backup_cancel)).assertIsNotEnabled()
    }

    @Test fun zeroTotal_showsIndeterminateProgress() {
        show(signedIn.copy(job = BackupJobState.Running(BackupJobKind.Backup, BackupStep.Preparing, 0, 0)))
        compose.onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate)).assertExists()
    }

    private fun openDialog(state: BackupUiState) {
        show(state)
        compose.onNodeWithText(str(Res.string.backup_snapshot_subtitle, dateText(snapshot.createdAt), snapshot.appVersionName)).performClick()
    }

    @Test fun restoreDialog_freshDevice_explainsDirectRestore_andConfirms() {
        openDialog(signedIn.copy(snapshots = listOf(snapshot), localMealCount = 0))
        compose.onNodeWithText(str(Res.string.backup_restore_confirm_message_fresh, dateText(snapshot.createdAt))).assertExists()
        compose.onNodeWithText(str(Res.string.backup_restore_confirm_button)).performClick()
        assertEquals(listOf(snapshot), restores)
    }

    @Test fun restoreDialog_withCounts_explainsSafetySnapshot() {
        openDialog(signedIn.copy(snapshots = listOf(snapshot), localMealCount = 3))
        compose.onNodeWithText(str(Res.string.backup_restore_confirm_message_counted, 3, dateText(snapshot.createdAt), 7)).assertExists()
    }

    @Test fun restoreDialog_legacySnapshot_omitsSnapshotCount() {
        openDialog(signedIn.copy(snapshots = listOf(snapshot.copy(mealCount = null)), localMealCount = 3))
        compose.onNodeWithText(str(Res.string.backup_restore_confirm_message, 3, dateText(snapshot.createdAt))).assertExists()
    }

    @Test fun cancelledMessage_showsSnackbar_thenConsumes() {
        show(signedIn.copy(message = BackupMessage.Cancelled))
        compose.onNodeWithText(str(Res.string.backup_msg_cancelled)).assertExists()
        compose.waitUntil(timeoutMillis = 15_000) { consumed == 1 }
    }
}
```

Implementer notes: if `hasProgressBarRangeInfo(ProgressBarRangeInfo(0.25f, 0f..1f))` fails on `steps` (Material3 may report `steps = 0`), construct `ProgressBarRangeInfo(0.25f, 0f..1f, 0)` explicitly. If the snackbar test times out, the screen's `showSnackbar` uses the default `Short` duration (4 s) — keep the 15 s budget and check `compose.mainClock.autoAdvance` is true (default). The subtitle text may be rendered inside a `Column` with `clickable`; if `onNodeWithText(...).performClick()` complains the node is not clickable, use `.onParent().performClick()` or `onNodeWithText(..., useUnmergedTree = true)`.

- [ ] **Step 2: Compile and run on the emulator**

Run: `.\gradlew.bat :app:assembleDebugAndroidTest`, then
`$env:ANDROID_SERIAL = "emulator-5556"; .\gradlew.bat :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.crazystudio.sportrecorder.backup.BackupScreenTest"`
Expected: 7 tests passed.

- [ ] **Step 3: Commit**

```bash
git add app/src/androidTest/java/com/crazystudio/sportrecorder/backup/BackupScreenTest.kt
git commit -m "test(backup): Compose UI tests for progress, cancel, restore dialog and snackbar

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 3: Koin override helper + `BackupForegroundServiceTest`

**Files:**
- Modify: `app/src/androidTest/java/com/crazystudio/sportrecorder/backup/BackupTestFixtures.kt` (add `loadBackupTestModule`)
- Create: `app/src/androidTest/java/com/crazystudio/sportrecorder/backup/BackupForegroundServiceTest.kt`

**Interfaces:**
- Consumes: `BackupJobRunner` (`state`, `startBackup()`, `cancel()`), `BackupJobState`, `BackupJobKind`, `BackupOutcome`, `BackupService`, `BackupStore`, `AccessTokenProvider`, `BackupForegroundService.ACTION_CANCEL`, `BackupNotifications.PROGRESS_ID/RESULT_ID`, `MainActivity`, Koin `GlobalContext`, `loadKoinModules`.
- Produces: `fun loadBackupTestModule(store: BackupStore): BackupJobRunner`.

- [ ] **Step 1: Add the Koin helper to the fixtures file**

Append to `BackupTestFixtures.kt` (add the imports `com.crazystudio.sportrecorder.BuildConfig` is NOT needed; use the ones below):

```kotlin
// --- Koin ---------------------------------------------------------------------------------------

/**
 * Point the app's running Koin graph at [store]: fake cloud, real everything else (real
 * BackupJobRunner, real AndroidBackupJobHost, so a job really starts BackupForegroundService).
 * Re-declaring BackupService and BackupJobRunner drops the cached singles, so every test gets a
 * fresh runner. Never unloaded — unloading would delete the app's own definitions for these keys.
 */
fun loadBackupTestModule(store: BackupStore): BackupJobRunner {
    org.koin.core.context.loadKoinModules(
        org.koin.dsl.module {
            single<BackupStore> { store }
            single<AccessTokenProvider> { AccessTokenProvider { "test-token" } }
            single { BackupService(get(), get(), get(), get(), get(), get(), appVersionName = "test") }
            single { BackupJobRunner(get(), get()) }
        },
    )
    return org.koin.core.context.GlobalContext.get().get<BackupJobRunner>()
}
```

(Convert the fully-qualified names to imports.) If `loadKoinModules` in Koin 4.1 does not override by default, use `GlobalContext.get().loadModules(listOf(module), allowOverride = true)` instead.

- [ ] **Step 2: Write the service test**

```kotlin
package com.crazystudio.sportrecorder.backup

import android.Manifest
import android.app.Notification
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.crazystudio.sportrecorder.MainActivity
import com.crazystudio.sportrecorder.shared.resources.Res
import com.crazystudio.sportrecorder.shared.resources.backup_msg_cancelled
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.getString
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The real BackupJobRunner + AndroidBackupJobHost + BackupForegroundService + BackupNotifications,
 * with only the cloud faked. MainActivity is kept in the foreground so `startForegroundService`
 * is allowed (Android 12+ forbids it from the background).
 */
@RunWith(AndroidJUnit4::class)
class BackupForegroundServiceTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var scenario: ActivityScenario<MainActivity>
    private lateinit var store: GatedBackupStore
    private lateinit var runner: BackupJobRunner

    @Before fun setUp() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            InstrumentationRegistry.getInstrumentation().uiAutomation
                .grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        }
        NotificationManagerCompat.from(context).cancelAll()
        scenario = ActivityScenario.launch(MainActivity::class.java)
        store = GatedBackupStore()
        runner = loadBackupTestModule(store)
    }

    @After fun tearDown() {
        runner.cancel()
        store.uploadGate?.complete(Unit)
        awaitUntil(what = "runner to settle") { runner.state.value !is BackupJobState.Running }
        NotificationManagerCompat.from(context).cancelAll()
        scenario.close()
    }

    private fun progressCard() = context.activeNotification(BackupNotifications.PROGRESS_ID)
    private fun resultCard() = context.activeNotification(BackupNotifications.RESULT_ID)

    @Test fun backup_postsOngoingProgress_thenAutoCancelResult_andStopsForeground() {
        store.uploadGate = CompletableDeferred()

        assertTrue(runner.startBackup())
        awaitUntil(what = "progress notification") { progressCard() != null }
        val progress = progressCard()!!.notification
        assertTrue(progress.flags and Notification.FLAG_ONGOING_EVENT != 0)

        store.uploadGate!!.complete(Unit)
        awaitUntil(what = "result notification") { resultCard() != null }
        awaitUntil(what = "progress card removed") { progressCard() == null }
        val result = resultCard()!!.notification
        assertFalse(result.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertTrue(result.flags and Notification.FLAG_AUTO_CANCEL != 0)
        assertEquals(BackupJobState.Finished(BackupJobKind.Backup, BackupOutcome.Completed), runner.state.value)
    }

    @Test fun cancelAction_cancelsJob_andShowsCancelledResult() {
        store.uploadGate = CompletableDeferred()
        runner.startBackup()
        awaitUntil(what = "progress notification") { progressCard() != null }

        context.startService(Intent(context, BackupForegroundService::class.java).setAction(BackupForegroundService.ACTION_CANCEL))

        awaitUntil(what = "cancelled outcome") {
            runner.state.value == BackupJobState.Finished(BackupJobKind.Backup, BackupOutcome.Cancelled)
        }
        awaitUntil(what = "result notification") { resultCard() != null }
        val title = resultCard()!!.notification.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        assertEquals(runBlocking { getString(Res.string.backup_msg_cancelled) }, title)
        awaitUntil(what = "progress card removed") { progressCard() == null }
    }

    @Test fun secondStart_isRefusedWhileRunning() {
        store.uploadGate = CompletableDeferred()
        assertTrue(runner.startBackup())
        awaitUntil(what = "progress notification") { progressCard() != null }

        assertFalse(runner.startBackup())
        assertNotNull(progressCard())
        assertNull(resultCard())
    }
}
```

Implementer notes: the runner's `Running(Preparing)` is set synchronously in `startBackup()`, and `BackupService.backup` reads repositories before calling `uploadSnapshot`, so the job parks at the gate a few ms later — the `awaitUntil` on the progress card covers that. If `startForegroundService` is still refused (look for `ForegroundServiceStartNotAllowedException` in `adb logcat -s BackupJobHost`), make sure `scenario` is RESUMED before `startBackup()` (`scenario.moveToState(Lifecycle.State.RESUMED)`). If the emulator's notification listener lags, raise `awaitUntil` timeouts to 20 s rather than adding sleeps.

- [ ] **Step 3: Compile and run on the emulator**

Run: `.\gradlew.bat :app:assembleDebugAndroidTest`, then
`$env:ANDROID_SERIAL = "emulator-5556"; .\gradlew.bat :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.crazystudio.sportrecorder.backup.BackupForegroundServiceTest"`
Expected: 3 tests passed. Then run the class a second time to check for order/flake sensitivity.

- [ ] **Step 4: Commit**

```bash
git add app/src/androidTest/java/com/crazystudio/sportrecorder/backup/BackupTestFixtures.kt app/src/androidTest/java/com/crazystudio/sportrecorder/backup/BackupForegroundServiceTest.kt
git commit -m "test(backup): instrumented foreground-service and notification tests over a faked cloud

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 4: Full instrumented run, JVM gate, docs, push

**Files:**
- Modify: `docs/DEVELOPMENT.md` (§5 and §6)

- [ ] **Step 1: Full instrumented run on one device**

Run: `$env:ANDROID_SERIAL = "emulator-5556"; .\gradlew.bat :app:connectedDebugAndroidTest`
Expected: BUILD SUCCESSFUL, 13 tests (3 + 7 + 3), 0 failures. Record the summary line for the PR.

- [ ] **Step 2: JVM gate still green**

Run: `.\gradlew.bat assembleDebug testDebugUnitTest :app:detekt :app:lintDebug :shared:jvmTest`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Docs**

In `docs/DEVELOPMENT.md` §5, after the bullet 「測試現況:…」, add:

```markdown
- **Instrumented tests(本機、不進 CI)**:`app/src/androidTest/.../backup/` 用真的 Room、真的
  `BackupForegroundService` 與通知、真的 `BackupScreen`,只假造雲端。跑法:先開一台模擬器,然後
  `$env:ANDROID_SERIAL="emulator-5556"; .\gradlew.bat :app:connectedDebugAndroidTest`
  (單一類別加 `-Pandroid.testInstrumentationRunnerArguments.class=<fqcn>`)。報告在
  `app/build/reports/androidTests/connected/debug/index.html`。
```

In §6's table, add after the 09-27 備份優化 row:

```markdown
| 09-27 | 備份 instrumented tests(本機) | ✓ | ✓ | 已完成(同 PR #67) |
```

- [ ] **Step 4: Commit and push**

```bash
git add docs/DEVELOPMENT.md
git commit -m "docs: how to run the local backup instrumented tests

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
git push
```

Then add a PR comment on #67 with the instrumented run summary (`gh pr comment 67 --body "..."`) — controller does this step.

---

## Self-review against the spec

- Fixtures (`GatedBackupStore`, `awaitUntil`, `activeNotification`, `loadBackupTestModule`) → Tasks 1 and 3. Layer 1 → Task 2 (7 cases match the spec table). Layer 2 → Task 3 (3 cases match). Layer 3 → Task 1 (3 cases match). Docs → Task 4. No production code, no new dependencies.
- Type consistency: `GatedBackupStore.uploadGate/downloadGate/failDownloadPhotos/pruneCalls/uploads/seedSnapshot` used identically in Tasks 1 and 3; `awaitUntil(timeoutMs, stepMs, what, condition)` called with named `what` everywhere; `loadBackupTestModule(store): BackupJobRunner` defined in Task 3 Step 1 and used in Step 2.
- Known deviation from spec wording: the service test does not seed a meal (a zero-meal backup still uploads a manifest and parks on the gate, which is all the test needs); the spec's layer-2 paragraph is updated in the same commit as the docs if the implementer confirms the run passes without seeding.
