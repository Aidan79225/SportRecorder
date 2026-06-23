# Google Drive Backup — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let a user sign in with Google and back up their data to their own Google Drive (appDataFolder), then restore it after reinstall / new phone.

**Architecture:** Backup logic lives in `commonMain` (`BackupService` + `@Serializable BackupDocument` + `BackupStore`/`BackupAuth` interfaces), reusing the existing repositories. The Google-specific pieces (`GoogleBackupAuth`, `GoogleDriveBackupStore`) are Android `actual`s in `:app`. Recovery snapshot only — restore overwrites local data; no sync.

**Tech Stack:** Kotlin Multiplatform, kotlinx.serialization (JSON), Coroutines/Flow, Room, Koin, Google Identity Services (`play-services-auth`), Drive v3 REST over OkHttp, Compose Multiplatform UI.

**Spec:** `docs/superpowers/specs/2026-06-23-drive-backup-design.md`

## Global Constraints

- Kotlin `2.3.10`, Compose Multiplatform `1.11.1`. Any new KMP dependency must be built with Kotlin ≤ 2.3.10 or the iOS build breaks (klib ABI rule). OkHttp/play-services are Android-only → safe.
- Backup **logic** goes in `commonMain`; **no** JVM-only APIs there (`System.currentTimeMillis`, `Dispatchers.IO`, `java.*`, `String.format`). Use `kotlin.time.Clock`, `Dispatchers.Default`, manual formatting.
- Backup **JSON** uses positional/owned schema; bump `BackupDocument.SCHEMA_VERSION` on any breaking change.
- Cloud scope: `https://www.googleapis.com/auth/drive.appdata` only. Storage: Drive **appDataFolder**.
- Keep the last **3** snapshots. Commit order: photos first, **manifest.json last**. Restore: **download-all-then-swap** (never wipe local before the full snapshot is downloaded + validated).
- The app must stay fully usable local-only; sign-in is optional. No backup-nag notifications.

## Phases & verifiability

- **Phase 1 (Tasks 1–8): commonMain backup engine.** Fully TDD-able with fakes; runnable now via `:shared:jvmTest`. No device/Google needed.
- **Phase 2 (Tasks 9–11): Android Google actuals.** Device + a configured OAuth consent screen (`drive.appdata`, sensitive scope — verification has lead time) required; verified manually on device.
- **Phase 3 (Tasks 12–13): UI + DI wiring.** Verified on device.

Run the full Android gate after each phase: `assembleDebug testDebugUnitTest :app:detekt :app:lintDebug :shared:jvmTest`, plus the iOS compile (`ios-shared` CI) after Phase 1 since it touches `commonMain`.

## File Structure

```
shared/src/commonMain/kotlin/com/crazystudio/sportrecorder/backup/
  BackupDocument.kt        # @Serializable DTOs + SCHEMA_VERSION
  BackupMappers.kt         # domain <-> DTO
  BackupStore.kt           # interface + SnapshotInfo
  BackupAuth.kt            # interface + BackupAccount
  BackupService.kt         # orchestrator: backup()/restore()/listSnapshots()
shared/src/commonTest/kotlin/com/crazystudio/sportrecorder/backup/
  BackupDocumentTest.kt
  BackupMappersTest.kt
  BackupServiceTest.kt
  fakes/FakeBackupStore.kt
shared/src/commonMain/.../domain/repository/   # add restore methods to existing interfaces
shared/src/commonMain/.../data/repository/     # implement restore methods
shared/src/commonMain/.../dao/                 # add deleteAll + bulk insert
app/src/main/java/com/crazystudio/sportrecorder/backup/
  GoogleBackupAuth.kt
  GoogleDriveBackupStore.kt
app/src/main/java/.../ui/settings/             # Backup UI (shared screen + :app host glue)
```

---

## Phase 1 — commonMain backup engine

### Task 1: Apply kotlinx.serialization to :shared

**Files:**
- Modify: `shared/build.gradle.kts` (plugins + commonMain deps)
- Modify: `gradle/libs.versions.toml` (already has `kotlinx-serialization-json` + the plugin alias `kotlin-serialization`; reuse them)

**Interfaces:** Produces: `@Serializable` usable in `:shared` commonMain.

- [ ] **Step 1: Add the serialization plugin to `:shared`.** In `shared/build.gradle.kts` `plugins { }`, add:
```kotlin
    alias(libs.plugins.kotlin.serialization)
```
- [ ] **Step 2: Add the JSON dependency to commonMain.** In `commonMain.dependencies { }`:
```kotlin
            implementation(libs.kotlinx.serialization.json)
```
- [ ] **Step 3: Verify it configures + compiles.**

Run: `$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'; .\gradlew.bat :shared:compileKotlinJvm`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**
```bash
git add shared/build.gradle.kts
git commit -m "build(shared): apply kotlinx.serialization for backup DTOs"
```

---

### Task 2: BackupDocument DTOs

**Files:**
- Create: `shared/src/commonMain/kotlin/com/crazystudio/sportrecorder/backup/BackupDocument.kt`
- Test: `shared/src/commonTest/kotlin/com/crazystudio/sportrecorder/backup/BackupDocumentTest.kt`

**Interfaces:**
- Produces: `BackupDocument`, `BackupMeal`, `BackupPhoto`, `BackupGeoPoint`, `BackupFastingType`, `BackupDietSettings`, `BackupReminderPrefs`, `BackupDocument.SCHEMA_VERSION`, and a `BackupJson` (configured `Json`).

- [ ] **Step 1: Write the failing test**
```kotlin
package com.crazystudio.sportrecorder.backup

import kotlin.test.Test
import kotlin.test.assertEquals

class BackupDocumentTest {
    @Test
    fun roundTrips_throughJson() {
        val doc = BackupDocument(
            schemaVersion = BackupDocument.SCHEMA_VERSION,
            createdAt = 1_700_000_000_000,
            appVersionName = "0.6.2",
            meals = listOf(
                BackupMeal(
                    id = 1, time = 1_700_000_000_000, note = "lunch",
                    location = BackupGeoPoint(25.0, 121.5),
                    photos = listOf(BackupPhoto(id = 7, fileName = "a.webp", createdAt = 1_700_000_000_001)),
                ),
            ),
            fastingTypes = listOf(BackupFastingType(16, 8, "normal")),
            dietSettings = BackupDietSettings(16, 8),
            reminderPrefs = BackupReminderPrefs(true, false, 30, true, 1320, 480),
        )
        val json = BackupJson.encodeToString(BackupDocument.serializer(), doc)
        val parsed = BackupJson.decodeFromString(BackupDocument.serializer(), json)
        assertEquals(doc, parsed)
    }
}
```
- [ ] **Step 2: Run it; expect FAIL** (unresolved `BackupDocument`).
Run: `.\gradlew.bat :shared:jvmTest --tests "*BackupDocumentTest*"`
- [ ] **Step 3: Implement `BackupDocument.kt`**
```kotlin
package com.crazystudio.sportrecorder.backup

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

val BackupJson: Json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = false }

@Serializable
data class BackupDocument(
    val schemaVersion: Int,
    val createdAt: Long,
    val appVersionName: String,
    val meals: List<BackupMeal>,
    val fastingTypes: List<BackupFastingType>,
    val dietSettings: BackupDietSettings,
    val reminderPrefs: BackupReminderPrefs,
) {
    companion object { const val SCHEMA_VERSION = 1 }
}

@Serializable
data class BackupMeal(
    val id: Int,
    val time: Long,
    val note: String?,
    val location: BackupGeoPoint?,
    val photos: List<BackupPhoto>,
)

@Serializable data class BackupGeoPoint(val lat: Double, val lng: Double)
@Serializable data class BackupPhoto(val id: Int, val fileName: String, val createdAt: Long)
@Serializable data class BackupFastingType(val fastingHours: Long, val eatingHours: Long, val name: String?)
@Serializable data class BackupDietSettings(val fastingHours: Long, val eatingHours: Long)

@Serializable
data class BackupReminderPrefs(
    val windowClosingEnabled: Boolean,
    val fastCompleteEnabled: Boolean,
    val leadMinutes: Long,
    val quietHoursEnabled: Boolean,
    val quietStartMinutes: Int,
    val quietEndMinutes: Int,
)
```
- [ ] **Step 4: Run the test; expect PASS.**
- [ ] **Step 5: Commit** — `git commit -m "feat(backup): versioned BackupDocument DTOs"`

---

### Task 3: domain <-> DTO mappers

**Files:**
- Create: `shared/src/commonMain/kotlin/com/crazystudio/sportrecorder/backup/BackupMappers.kt`
- Test: `shared/src/commonTest/kotlin/com/crazystudio/sportrecorder/backup/BackupMappersTest.kt`

**Interfaces:**
- Consumes: domain `EatRecord`, `EatPhoto`, `GeoPoint`, `CustomFastingType`, `DietSettings`, `ReminderPrefs`; DTOs from Task 2.
- Produces: `EatRecord.toBackup()`, `BackupMeal.toDomain()`, `CustomFastingType.toBackup()`/`BackupFastingType.toDomain()`, `DietSettings.toBackup()`/`BackupDietSettings.toDomain()`, `ReminderPrefs.toBackup()`/`BackupReminderPrefs.toDomain()`.

- [ ] **Step 1: Write the failing test** (round-trip each mapper)
```kotlin
package com.crazystudio.sportrecorder.backup

import com.crazystudio.sportrecorder.domain.model.EatPhoto
import com.crazystudio.sportrecorder.domain.model.EatRecord
import com.crazystudio.sportrecorder.domain.model.GeoPoint
import kotlin.test.Test
import kotlin.test.assertEquals

class BackupMappersTest {
    @Test fun meal_roundTrips() {
        val r = EatRecord(1, 1_700L, GeoPoint(25.0, 121.5), "n",
            listOf(EatPhoto(7, "a.webp", 1_701L)))
        assertEquals(r, r.toBackup().toDomain())
    }
}
```
- [ ] **Step 2: Run; expect FAIL.**
- [ ] **Step 3: Implement `BackupMappers.kt`**
```kotlin
package com.crazystudio.sportrecorder.backup

import com.crazystudio.sportrecorder.domain.model.CustomFastingType
import com.crazystudio.sportrecorder.domain.model.DietSettings
import com.crazystudio.sportrecorder.domain.model.EatPhoto
import com.crazystudio.sportrecorder.domain.model.EatRecord
import com.crazystudio.sportrecorder.domain.model.GeoPoint
import com.crazystudio.sportrecorder.domain.reminder.ReminderPrefs

fun EatRecord.toBackup() = BackupMeal(id, time, note,
    location?.let { BackupGeoPoint(it.lat, it.lng) },
    photos.map { BackupPhoto(it.id, it.fileName, it.createdAt) })

fun BackupMeal.toDomain() = EatRecord(id, time,
    location?.let { GeoPoint(it.lat, it.lng) }, note,
    photos.map { EatPhoto(it.id, it.fileName, it.createdAt) })

fun CustomFastingType.toBackup() = BackupFastingType(fastingHours, eatingHours, name)
fun BackupFastingType.toDomain() = CustomFastingType(fastingHours, eatingHours, name)
fun DietSettings.toBackup() = BackupDietSettings(fastingHours, eatingHours)
fun BackupDietSettings.toDomain() = DietSettings(fastingHours, eatingHours)

fun ReminderPrefs.toBackup() = BackupReminderPrefs(
    windowClosingEnabled, fastCompleteEnabled, leadMinutes, quietHoursEnabled, quietStartMinutes, quietEndMinutes)
fun BackupReminderPrefs.toDomain() = ReminderPrefs(
    windowClosingEnabled, fastCompleteEnabled, leadMinutes, quietHoursEnabled, quietStartMinutes, quietEndMinutes)
```
- [ ] **Step 4: Run; expect PASS.**
- [ ] **Step 5: Commit** — `git commit -m "feat(backup): domain<->DTO mappers"`

---

### Task 4: Repository restore support (interfaces + fakes)

Restore must clear + bulk-insert. Add methods to the existing repository interfaces and to the test fakes now (impls in Task 8). DietSettings/ReminderPrefs already have setters, so only meals + custom types need new methods.

**Files:**
- Modify: `.../domain/repository/EatRecordRepository.kt`, `.../domain/repository/FastingTypeRepository.kt`
- Modify: `app/src/test/java/.../fake/FakeEatRecordRepository.kt`, `FakeFastingTypeRepository.kt`

**Interfaces:**
- Produces: `EatRecordRepository.replaceAll(records: List<EatRecord>)` — deletes all meals+photo rows, inserts the given records with their photos (preserving ids/timestamps), leaving photo **files** untouched (caller manages files). `FastingTypeRepository.replaceAllCustom(types: List<CustomFastingType>)` — deletes all custom types, inserts the given ones.

- [ ] **Step 1: Add to `EatRecordRepository`:**
```kotlin
    /** Restore: delete all meals + photo rows, then insert [records] with their photos. Files untouched. */
    suspend fun replaceAll(records: List<EatRecord>)
```
- [ ] **Step 2: Add to `FastingTypeRepository`:**
```kotlin
    /** Restore: delete all custom types, then insert [types]. */
    suspend fun replaceAllCustom(types: List<CustomFastingType>)
```
- [ ] **Step 3: Implement in the fakes** (in-memory list replace), e.g. `FakeEatRecordRepository.replaceAll { state.value = records }`. Mirror for the fasting-type fake.
- [ ] **Step 4: Verify compile** — `.\gradlew.bat :shared:compileKotlinJvm testDebugUnitTest` (the fakes compile; existing tests still pass).
- [ ] **Step 5: Commit** — `git commit -m "feat(backup): add replaceAll restore methods to repos + fakes"`

---

### Task 5: BackupStore + BackupAuth interfaces

**Files:**
- Create: `.../backup/BackupStore.kt`, `.../backup/BackupAuth.kt`
- Create: `shared/src/commonTest/.../backup/fakes/FakeBackupStore.kt`

**Interfaces:**
- Produces:
```kotlin
data class SnapshotInfo(val id: String, val createdAt: Long, val appVersionName: String, val sizeBytes: Long)
interface BackupStore {
    suspend fun listSnapshots(): List<SnapshotInfo>           // newest-first
    suspend fun uploadSnapshot(manifestJson: String, photoFileNames: List<String>): SnapshotInfo
    suspend fun downloadManifest(id: String): String
    suspend fun downloadPhotos(id: String)                    // writes photos into local store
    suspend fun prune(keepLast: Int)
}
data class BackupAccount(val email: String)
interface BackupAuth {
    val account: kotlinx.coroutines.flow.Flow<BackupAccount?>
}
```
- [ ] **Step 1:** Create both interface files (code above; add the `package`/imports).
- [ ] **Step 2:** Create `FakeBackupStore` — an in-memory store recording uploaded manifests + photo names, with controllable `listSnapshots`, and a `failDownloadAfter` switch (for the download-failure test in Task 7).
- [ ] **Step 3: Verify compile** — `.\gradlew.bat :shared:compileTestKotlinJvm`
- [ ] **Step 4: Commit** — `git commit -m "feat(backup): BackupStore + BackupAuth interfaces + fake"`

---

### Task 6: BackupService.backup()

**Files:**
- Create: `.../backup/BackupService.kt`
- Test: `.../backup/BackupServiceTest.kt`

**Interfaces:**
- Consumes: the four repositories, `BackupStore`, `LocalPhotoFiles` (a tiny interface: `fun photoFileNames(): List<String>` — defaulted from records, so no new dep needed; photo file names come from the meals).
- Produces: `class BackupService(eatRepo, fastingRepo, settingsRepo, prefsRepo, store, appVersionName, now: () -> Long = { Clock.System.now().toEpochMilliseconds() })` with `suspend fun backup(): SnapshotInfo`.

- [ ] **Step 1: Write the failing test** — backup builds a document from the repos and uploads it (manifest + the meals' photo names):
```kotlin
@Test fun backup_uploadsManifestWithAllDataAndPhotoNames() = runTest {
    val eat = FakeEatRecordRepository(listOf(
        EatRecord(1, 1_700L, null, "n", listOf(EatPhoto(7, "a.webp", 1_701L)))))
    val store = FakeBackupStore()
    val svc = BackupService(eat, FakeFastingTypeRepository(), FakeDietSettingsRepository(),
        FakeReminderPreferencesRepository(), store, appVersionName = "0.6.2") { 9_999L }
    svc.backup()
    val doc = BackupJson.decodeFromString(BackupDocument.serializer(), store.lastManifest!!)
    assertEquals(1, doc.meals.size)
    assertEquals("0.6.2", doc.appVersionName)
    assertEquals(listOf("a.webp"), store.lastUploadedPhotoNames)
}
```
- [ ] **Step 2: Run; expect FAIL.**
- [ ] **Step 3: Implement `backup()`** — read `eatRepo.observeAll().first()`, `fastingRepo.observeRecentCustomTypes().first()`, `settingsRepo.settings.first()`, `prefsRepo.prefs.first()`; map to `BackupDocument` (createdAt = now()); collect photo names = `meals.flatMap { it.photos.map { p -> p.fileName } }.distinct()`; `store.uploadSnapshot(json, photoNames)`; `store.prune(KEEP_LAST = 3)`; return the SnapshotInfo. Use `kotlinx.coroutines.flow.first`, `kotlin.time.Clock`.
- [ ] **Step 4: Run; expect PASS.**
- [ ] **Step 5: Commit** — `git commit -m "feat(backup): BackupService.backup()"`

---

### Task 7: BackupService.restore() + listSnapshots() + round-trip

**Files:** Modify `BackupService.kt`; extend `BackupServiceTest.kt`.

**Interfaces:** Produces `suspend fun restore(snapshotId: String)`, `suspend fun listSnapshots(): List<SnapshotInfo>`. Restore must: download manifest → validate `schemaVersion <= SCHEMA_VERSION` (else throw `BackupSchemaTooNewException`) → `store.downloadPhotos(id)` → only then `eatRepo.replaceAll(...)` + `fastingRepo.replaceAllCustom(...)` + settings/prefs setters → `rescheduleReminders.reschedule()`.

- [ ] **Step 1: Write failing tests:**
  - `restore_replacesLocalDataFromSnapshot` (round-trip: backup() then restore() yields identical repo contents).
  - `restore_refusesNewerSchema` (manifest schemaVersion = SCHEMA_VERSION+1 → throws `BackupSchemaTooNewException`).
  - `restore_doesNotWipeLocal_whenDownloadFails` (FakeBackupStore.failDownloadAfter set → restore throws AND eatRepo still has original data).
- [ ] **Step 2: Run; expect FAIL.**
- [ ] **Step 3: Implement** restore in the documented order; add `class BackupSchemaTooNewException(val found: Int) : Exception()`; inject `RemindersRescheduler` into `BackupService`. Download photos BEFORE any `replaceAll`.
- [ ] **Step 4: Run; expect PASS.**
- [ ] **Step 5: Run the full Phase-1 gate** — `.\gradlew.bat :shared:jvmTest testDebugUnitTest :app:detekt :app:lintDebug` → all green.
- [ ] **Step 6: Commit** — `git commit -m "feat(backup): BackupService.restore() + listSnapshots()"`

---

### Task 8: Implement replaceAll in the real repositories + DAOs

**Files:**
- Modify: `.../dao/EatTimeDao.kt`, `.../dao/PhotoDao.kt`, `.../dao/FastingTypeDao.kt` (add `@Query("DELETE FROM ...")` deleteAll + `@Insert` bulk where missing)
- Modify: `.../data/repository/EatRecordRepositoryImpl.kt`, `FastingTypeRepositoryImpl.kt`

**Interfaces:** Consumes Task 4 interface methods. Implement `replaceAll`/`replaceAllCustom` inside `appDatabase.useWriterConnection { immediateTransaction { ... } }` (same pattern as existing `save`/`delete`): delete all rows, then insert each record (`eatTimeDao.insert` returning id, then `photoDao.insert` for each photo with its original `createdAt`). **Note:** restored ids may differ from backup ids — that's acceptable (ids are local). Photo *files* are downloaded separately by the store; this only touches DB rows.

- [ ] **Step 1:** Add DAO methods (deleteAll for eat_time, photo, fasting_type; ensure insert exists).
- [ ] **Step 2:** Implement `replaceAll`/`replaceAllCustom` in the impls (transactional).
- [ ] **Step 3: Verify** — `.\gradlew.bat :app:assembleDebug :shared:jvmTest` green. (Impl is exercised on-device in Phase 3; the service logic is already unit-tested via fakes.)
- [ ] **Step 4: Commit** — `git commit -m "feat(backup): implement replaceAll in Room repos"`
- [ ] **Step 5: Trigger iOS verification** — this phase touched `commonMain`; run `ios-shared` (push a branch / it runs per-PR) and confirm green before merging.

---

## Phase 2 — Android Google actuals (device + OAuth required)

> Prerequisite: configure the OAuth consent screen with the `drive.appdata` scope (sensitive — needs Google verification; has lead time) and create an OAuth client for the app's package + signing-cert SHA-1. These tasks are verified **manually on a device**, not by unit tests.

### Task 9: Dependencies, permission, OAuth client

**Files:** `gradle/libs.versions.toml`, `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`.
- [ ] Add deps: `play-services-auth` (Authorization API) and `okhttp` (Drive REST). Add `<uses-permission android:name="android.permission.INTERNET" />`.
- [ ] Add the OAuth web/Android client config per Google setup (SHA-1 of the new upload/debug signing certs).
- [ ] Verify `:app:assembleDebug` green. Commit.

### Task 10: GoogleBackupAuth

**Files:** Create `app/src/main/java/.../backup/GoogleBackupAuth.kt` implementing `BackupAuth` + a way to obtain a `drive.appdata` access token.
- [ ] Implement sign-in (Google Identity) + `AuthorizationClient` requesting `Scope("https://www.googleapis.com/auth/drive.appdata")` → access token; expose `account: Flow<BackupAccount?>` and `suspend fun accessToken(): String`.
- [ ] **Manual verify on device:** sign in, confirm account email appears, token obtained. Commit.

### Task 11: GoogleDriveBackupStore

**Files:** Create `app/src/main/java/.../backup/GoogleDriveBackupStore.kt` implementing `BackupStore` via Drive v3 REST + OkHttp, using `GoogleBackupAuth.accessToken()` and `PhotoStorage` for photo file IO.

Each snapshot = a folder-less set of files in appDataFolder tagged with a snapshot id via `appProperties` (e.g. `appProperties.snapshotId`, `appProperties.kind=manifest|photo`). Endpoints (all with header `Authorization: Bearer <token>`):
- list: `GET https://www.googleapis.com/drive/v3/files?spaces=appDataFolder&fields=files(id,name,size,modifiedTime,appProperties)`
- upload (multipart): `POST https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart` — metadata `{name, parents:["appDataFolder"], appProperties:{...}}` + media part.
- download: `GET https://www.googleapis.com/drive/v3/files/{id}?alt=media`
- delete: `DELETE https://www.googleapis.com/drive/v3/files/{id}`

- [ ] Implement `uploadSnapshot` (photos first, skip a photo whose `fileName` already exists in appData; manifest last), `listSnapshots` (group by `snapshotId`, read manifest metadata), `downloadManifest`, `downloadPhotos` (write via `PhotoStorage.fileFor`), `prune` (delete files of snapshots beyond the newest 3, then orphan photos).
- [ ] **Manual verify on device:** back up, inspect appDataFolder via the Drive API explorer; reinstall; restore; confirm meals + photos return. Commit.

---

## Phase 3 — UI + DI wiring

### Task 12: Koin wiring

**Files:** `app/src/main/java/.../di/AppModule.kt`.
- [ ] `single<BackupAuth> { GoogleBackupAuth(androidContext()) }`, `single<BackupStore> { GoogleDriveBackupStore(get(), androidContext()) }`, `single { BackupService(get(), get(), get(), get(), get(), get(), appVersionName = BuildConfig.VERSION_NAME) }`. Add a `BackupViewModel` (commonMain) exposing account state + `backup()`/`restore()`/`listSnapshots()` with progress/result state; register via `viewModel { }`. Verify `:app:assembleDebug`. Commit.

### Task 13: Backup section in Settings

**Files:** Create a shared `BackupScreen`/section (commonMain, like other screens) + an `:app` host for the Google sign-in launcher; link it from `SettingsScreen` (a "Backup & restore" row → new destination in `AppRoot`).
- [ ] Shared UI: signed-out (Sign in with Google), signed-in (account email, "Back up now", "Last backed up …", "Restore" → snapshot list + confirm dialog "This replaces the data on this device"), progress indicator, friendly error messages (map the Task 7/10 exceptions). Copy gentle, both locales via `Res.string`.
- [ ] `:app` host provides the sign-in launcher callback (like the existing camera/location launchers).
- [ ] **Manual verify on device:** full flow end-to-end (sign in → back up → reinstall → restore). Run the full gate. Commit.

---

## Out of scope (separate future plans)
- iOS `BackupAuth`/`BackupStore` actuals (+ possible shared Ktor Drive client).
- Automatic/scheduled backup (gentle, optional).
- Docs: privacy-policy update (separate blog repo) + Play data-safety form — track outside this code plan.
