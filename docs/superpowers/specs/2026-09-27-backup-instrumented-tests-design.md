# Backup instrumented tests (local-only) — Design

**Status:** approved 2026-09-27 · **Covers:** PR #67 (issue #64) · **Builds on:** `2026-09-21-e2e-test-suite-design.md` (layer (b), "proposed, not done")

## Goal

Give the backup-optimization work real on-device coverage that the JVM suites structurally cannot
provide: the Compose screen actually rendering progress/dialog/snackbar, the foreground service
actually posting and replacing notifications, and `EatRecordRepositoryImpl.replaceAll` actually
running against Room. Runnable on any attached emulator with one Gradle task; **not** added to CI
(the 2026-09-21 spec's decision stands: no emulator job until its flake rate is known).

## Non-goals

- No CI emulator job, no Robolectric, no new test dependencies.
- No `BackupRoute` / navigation test: `GoogleBackupAuth` is a concrete class injected directly, so
  faking it needs a production interface. Follow-up if wanted.
- No real Drive. `GoogleDriveBackupStore` and `DriveRestClient` keep their JVM MockWebServer tests.
- No production-code change.

## Where and how to run

- Source set: `app/src/androidTest/java/com/crazystudio/sportrecorder/backup/`. The template
  `ExampleInstrumentedTest.kt` is deleted.
- Command: `.\gradlew.bat :app:connectedDebugAndroidTest` (runs on every attached device; pin one with
  `ANDROID_SERIAL=emulator-5556`). Documented in `docs/DEVELOPMENT.md` §5.
- Dependencies already present in `:app`: `androidTestImplementation` of the Compose BOM,
  `ui-test-junit4`, `androidx.test.ext:junit`, `espresso-core`; `debugImplementation` of
  `ui-test-manifest`. `POST_NOTIFICATIONS` is granted via
  `InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(...)`, so
  `androidx.test:rules` is not needed.

## Shared fixtures — `BackupTestFixtures.kt`

- **`GatedBackupStore : BackupStore`** — androidTest's own in-memory store (the commonTest
  `FakeBackupStore` is not on this classpath). Records `uploads` (manifest JSON + uploaded photo
  names), keeps `snapshots` newest-first, `downloadManifest` by id. Hooks: `uploadGate` and
  `downloadGate` (`CompletableDeferred<Unit>?`, awaited at the top of `uploadSnapshot` /
  `downloadPhotos`) to park a job on a step; `failDownloadPhotos: Boolean`. Reports progress like
  the real store (`UploadingPhotos` per photo, `UploadingManifest` 0/1 → 1/1, `DownloadingPhotos`).
- **`loadBackupTestModule(store): BackupJobRunner`** — calls
  `loadKoinModules(module { … }, allowOverride = true)` on the app's already-started global Koin,
  overriding `single<BackupStore>` and, defensively, `single<AccessTokenProvider>` (returns
  `"test-token"`) — nothing in `AppModule` currently binds or resolves `AccessTokenProvider`, so
  this override is currently unused — and
  **re-declaring** `single { BackupService(...) }` and `single { BackupJobRunner(get(), get()) }` so
  each test gets fresh instances (overriding a definition discards the previously cached single).
  `BackupJobHost` is left as the real `AndroidBackupJobHost` so a job really starts the service.
  Modules are never unloaded (unloading would remove the app's own definitions for those keys).
  Returns `GlobalContext.get().get<BackupJobRunner>()`.
- **`awaitUntil(timeoutMs = 10_000, stepMs = 50) { condition }`** — polling helper for notification
  and runner-state assertions; fails with a descriptive message on timeout.
- **`activeNotification(id): StatusBarNotification?`** — reads `NotificationManager.activeNotifications`.

## Layer 1 — `BackupScreenTest` (Compose UI)

`createComposeRule()`; content = `SportRecorderTheme { BackupScreen(state, …) }` with counting
callbacks. Expected copy comes from Compose Resources via `getString(Res.string.…, args)` inside
`runBlocking`, so the tests hold in either locale.

| Case | State | Assertions |
| --- | --- | --- |
| Running upload | `Running(Backup, UploadingPhotos, 12, 48)` | node with `ProgressBarRangeInfo.current == 0.25f`; caption `backup_step_uploading_photos(12, 48)`; 「取消」 enabled; click → `onCancel` called once |
| Applying | `Running(Restore, Applying, 0, 0)` | 「取消」 `assertIsNotEnabled` |
| Indeterminate | `Running(Backup, Preparing, 0, 0)` | progress node has `ProgressBarRangeInfo.Indeterminate` |
| Dialog: fresh device | `localMealCount = 0`, snapshot `mealCount = 7` | click row → `backup_restore_confirm_message_fresh(date)` shown; click 「還原」 → `onRestore` called with that snapshot |
| Dialog: counted | `localMealCount = 3`, `mealCount = 7` | `backup_restore_confirm_message_counted(3, date, 7)` |
| Dialog: legacy | `localMealCount = 3`, `mealCount = null` | `backup_restore_confirm_message(3, date)` |
| Snackbar | `message = Cancelled` | `backup_msg_cancelled` visible; `onConsumeMessage` called once after it is shown (`waitUntil`) |

The date string is produced by the same `yyyy-MM-dd HH:mm` formatting the screen uses; the test
computes it from `snapshot.createdAt` with `java.time` in the device zone.

## Layer 2 — `BackupForegroundServiceTest` (Koin override + real runner + real service)

`@Before`: grant `POST_NOTIFICATIONS`, `NotificationManagerCompat.cancelAll()`, load the test module.
`@After`: `runner.cancel()`, wait for `Finished` or `Idle`, `cancelAll()`.
No data is seeded: a zero-meal backup still uploads a manifest, and the gate parks the job before
the upload, which is all these cases need. `MainActivity` is launched with `ActivityScenario` and
kept resumed so `startForegroundService` is allowed (Android 12+ forbids it from the background).

| Case | Steps | Assertions |
| --- | --- | --- |
| Backup completes with notifications | `store.uploadGate = CompletableDeferred()`; `runner.startBackup()` | `awaitUntil` id 3001 present with `FLAG_ONGOING_EVENT`; then `uploadGate.complete(Unit)`; `awaitUntil` id 3002 present (no `FLAG_ONGOING_EVENT`, `FLAG_AUTO_CANCEL` set) and 3001 absent; `runner.state.value == Finished(Backup, Completed)` |
| Cancel through the service action | park on `uploadGate`; `context.startService(Intent(context, BackupForegroundService::class.java).setAction(ACTION_CANCEL))` | `awaitUntil` `Finished(Backup, Cancelled)`; 3002's `EXTRA_TITLE` equals `backup_msg_cancelled`; 3001 absent |
| Second start refused while running | park on `uploadGate`; `startBackup()` again | returns `false`; only one 3001 |

The result-notification title is read from `notification.extras.getCharSequence(Notification.EXTRA_TITLE)`.

## Layer 3 — `BackupRoundTripRoomTest` (real Room, no Koin)

Built by hand per test: `Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()`,
real `EatRecordRepositoryImpl(db, db.getEatTimeDao(), db.getPhotoDao(), recordingPhotoFileStore)`,
real `FastingTypeRepositoryImpl(db, db.getFastingTypeDao())`, real `DietSettingsRepositoryImpl` and
`ReminderPreferencesRepositoryImpl` over a `PreferenceDataStoreFactory.create` DataStore in a unique
temp file under `context.cacheDir`, a counting `RemindersRescheduler`, and a `GatedBackupStore`.
`BackupService(..., appVersionName = "test")`.

| Case | Steps | Assertions |
| --- | --- | --- |
| Round trip | save 2 meals (one with location + a photo name), add a custom fasting type, set window 20/4, set lead 45 → `backup()` → `replaceAll(emptyList())`, `replaceAllCustom(emptyList())` → `restore(info.id)` | `observeAll().first()` equals the originals by (time, note, location, photo file names) in newest-first order; fasting types, settings, prefs restored; `reschedule` called once |
| Safety snapshot first | device has meals A, B; store seeded with a manifest holding meal C → `restore("seed")` | `store.uploads.size == 1` and that manifest's meals are {A, B}; Room now holds only C; `pruneCalls == 0` |
| Failed download leaves Room intact | `store.failDownloadPhotos = true` | `restore` throws; Room still holds A, B; `reschedule` not called |

Seeding a manifest uses `BackupJson.encodeToString(BackupDocument.serializer(), …)` with the
`BackupMeal`/`BackupPhoto` DTOs already in `:shared`.

## Testing the tests

Each class is run on an attached API 35 emulator before commit (`connectedDebugAndroidTest` filtered
with `-Pandroid.testInstrumentationRunnerArguments.class=…`). The full instrumented run is executed
once at the end and its result recorded in the PR. The existing JVM gate must stay green (no main
code changes, but `assembleDebugAndroidTest` must compile under `:app:lintDebug`/detekt — detekt
excludes `androidTest`).

## 初衷對照 / North-Star check

Test-only change; nothing the user sees. The service tests post real notifications on the emulator
and cancel them in `@After`. No drift.

## Docs to update

- `docs/DEVELOPMENT.md` §5: how to run the instrumented suite locally; §6 index row.
