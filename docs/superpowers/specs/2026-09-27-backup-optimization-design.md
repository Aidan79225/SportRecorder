# Backup optimization — Design

**Status:** approved 2026-09-27 · **Issue:** #64 · **Builds on:** `2026-06-23-drive-backup-design.md`

## Goal

Make a Drive backup/restore something the user can start and then stop worrying about:
it keeps running when they leave the screen or the screen turns off, it shows how far along it
is, it is noticeably faster, and restoring never feels like it might destroy what is on the phone.

Issue #64 lists five points. The mapping:

| # | Issue | This design |
| --- | --- | --- |
| 1 | 備份跑很久 | Parallel photo transfer; skip photos already on the device; fewer redundant Drive calls |
| 2 | 要有進度條 | Step + `done/total` progress, in the screen and in a notification |
| 3 | 螢幕黑掉 / 誤操作就失敗 | Job runs in an app-scoped runner, hosted by an Android foreground service |
| 4 | 大量紀錄要不要分頁 | Drive listing follows `nextPageToken`; the manifest itself needs no paging |
| 5 | 還原會不會覆蓋 | Restore first uploads a safety snapshot of the current data, then replaces; the confirm dialog says so with counts |

## Non-goals

- No scheduled / automatic backup (unchanged from v1).
- No surviving process death: a job is user-initiated and takes minutes; if the OS kills the
  process the user taps again. WorkManager was considered and rejected as over-engineering for a
  one-shot manual job (new dependency, worker factory, progress squeezed through `Data`).
- No merge-on-restore. Records have no stable identity across devices, so merging would guess by
  time + note and either duplicate or drop moments. Overwrite-with-a-safety-net is the honest model.
- No iOS implementation. The runner and progress model live in `commonMain` so iOS can reuse them;
  the host (foreground service) is Android-only behind an interface.

## Root cause of point 3

`BackupViewModel` launches backup/restore in `viewModelScope`. Leaving the screen clears the
ViewModel and cancels the coroutine mid-transfer. Manifest-last commit keeps Drive consistent, so
nothing is corrupted — the backup simply never completes, and nothing tells the user. On top of
that, once the app is backgrounded Android may freeze it, stalling the network.

## Architecture

```
commonMain                                   :app (Android)
────────────────────────────────────────     ─────────────────────────────────────
BackupJobRunner  ──state──▶ BackupViewModel  BackupRoute (observes VM as today)
   │  (app-scoped scope, one job at a time)
   │  starts/stops ──▶ BackupJobHost (iface) ◀── AndroidBackupJobHost
   │                                              └─ starts/stops BackupForegroundService
   ▼                                                 └─ mirrors runner state → notification
BackupService.backup(progress) / restore(id, progress)
   ▼
BackupStore (progress-aware)  ◀── GoogleDriveBackupStore (parallel, paged, skip-local)
```

### `BackupJobRunner` (commonMain, new)

Single instance, owns a `CoroutineScope(SupervisorJob() + Dispatchers.Default)` that outlives any
screen. Exposes:

```kotlin
val state: StateFlow<BackupJobState>
fun startBackup(): Boolean            // false if a job is already running
fun startRestore(snapshotId: String): Boolean
fun cancel()
fun acknowledgeFinished()             // Finished → Idle once the UI has shown the result
```

```kotlin
sealed interface BackupJobState {
    data object Idle : BackupJobState
    data class Running(val kind: BackupJobKind, val step: BackupStep, val done: Int, val total: Int) : BackupJobState
    data class Finished(val kind: BackupJobKind, val outcome: BackupOutcome) : BackupJobState
}
enum class BackupJobKind { Backup, Restore }
enum class BackupStep {
    Preparing, SafetyBackup, UploadingPhotos, UploadingManifest, Pruning,
    DownloadingManifest, DownloadingPhotos, Applying,
}
enum class BackupOutcome { Completed, Cancelled, SchemaTooNew, Failed }
```

Rules:

- Only one job at a time. `start*` returns `false` while `Running`; the UI already disables the
  buttons, this is the guard behind it.
- `cancel()` cancels the job's coroutine. `BackupService` makes the restore's *apply* step
  non-cancellable (`withContext(NonCancellable)`), so cancel is safe at every point: a cancelled
  backup leaves no manifest (manifest-last), a cancelled restore leaves local data untouched.
- The runner calls `BackupJobHost.onJobStarted()` before launching and `onJobFinished()` in a
  `finally`, so the foreground service is always released.
- `Finished` stays until `acknowledgeFinished()`, so a screen opened after the job ended still
  sees the outcome once.

### `BackupJobHost` (commonMain interface, Android actual)

```kotlin
interface BackupJobHost { fun onJobStarted(); fun onJobFinished() }
```

`AndroidBackupJobHost` starts `BackupForegroundService` (`startForegroundService`) and stops it.
iOS / tests use a no-op host.

### Progress plumbing

`BackupService` and `BackupStore` gain a `BackupProgress` reporter parameter:

```kotlin
fun interface BackupProgress { fun report(step: BackupStep, done: Int, total: Int) }
```

- `total` for photo steps is the number of photos that will actually be transferred (after
  dedup / skip-local). `done` counts completed files. The manifest step reports `0/1 → 1/1`.
- `BackupStore` changes:
  - `uploadSnapshot(manifestJson, photoFileNames, progress)`
  - `downloadPhotos(id, photoFileNames, progress)` — names come from the service, which already
    parsed the manifest; the store no longer re-lists and re-downloads the manifest.
  - `listSnapshots()` / `downloadManifest()` / `prune()` unchanged in shape.
- `BackupService.backup(progress)` and `restore(id, progress)`; a default no-op reporter keeps
  existing tests and callers compiling.

### `SnapshotInfo.mealCount`

`SnapshotInfo` gains `val mealCount: Int?`. The Drive store writes `mealCount` into the manifest's
`appProperties` on upload and reads it back on list. Snapshots created before this change have
`null` and the UI omits the count for them.

## Speed

Where the time goes today: every photo is one sequential HTTP round-trip; upload, prune, manifest
download and photo download each re-list `appDataFolder`; restore re-downloads the manifest.

Changes in `GoogleDriveBackupStore`:

1. **Parallel transfer** — photo uploads and downloads run through `coroutineScope { … async … }`
   on `Dispatchers.IO` bounded by a `Semaphore(PARALLEL_TRANSFERS = 4)`. Each completion bumps a
   shared counter and reports progress. The manifest upload still waits for all photos (commit
   marker semantics unchanged).
2. **Skip photos already on the device** during restore: if `PhotoStorage.fileFor(name)` exists
   and is non-empty, don't download it. Photos are immutable webp with unique names, so presence
   is sufficient. This is the win for "restore onto the same phone".
3. **One listing per operation** — `downloadPhotos` receives the file names and lists once (to map
   name → Drive id) instead of listing twice and fetching the manifest again.
4. Prune is left as is (it downloads ≤ 3 small manifests); measured against photo transfer it is
   noise. Note in code so nobody "optimizes" it into skipping the orphan sweep.

## Drive listing paging

`listFiles` loops on `nextPageToken` until absent, keeping `pageSize=1000`. Unit-tested with
`okhttp3:mockwebserver` (test-only dependency in `:app`) — two pages, then assert the merged list.
This also removes the prune-orphan hazard the existing comment warns about.

## Restore with a safety snapshot

`BackupService.restore(snapshotId, progress)`:

1. `Preparing` — `downloadManifest`, decode, refuse newer schema (unchanged).
2. `SafetyBackup` — if the device has ≥ 1 meal, run the backup routine **without prune**
   (`KEEP_LAST` pruning here could delete the very snapshot being restored if it were the oldest
   of three). The next regular backup prunes as usual, so at most four snapshots exist briefly.
   With zero local meals (fresh install) this step is skipped and reported as `0/0`.
3. `DownloadingPhotos` — parallel, skip-local, cancellable.
4. `Applying` — `NonCancellable`: `replaceAll`, settings, prefs, reschedule (unchanged).

Backup is refactored into `backupInternal(prune: Boolean, progress)` so both paths share it.

## UI

**Backup screen (commonMain)**

- `BackupUiState` gains `job: BackupJobState` and `isLoadingSnapshots: Boolean` (together
  replacing `phase`); `isBusy` = job is `Running` or the list is loading.
- While running: `LinearProgressIndicator(progress = done / total)` (indeterminate when
  `total == 0`) plus a caption per step, e.g. 「上傳照片 12 / 48」, 「先備份目前的紀錄…」,
  「下載照片 3 / 48」, 「套用到這台裝置…」. A **取消** text button, disabled during `Applying`.
- Outcome → the existing snackbar messages, plus a new `Cancelled` message:
  「已取消，什麼都沒有改變。」
- Restore confirm dialog copy becomes:
  「會先把這台裝置目前的 %1$d 筆紀錄備份一份，再用 %2$s 的備份取代。之後隨時可以還回來。」
  When the snapshot has a `mealCount`, the second sentence reads 「…的備份（%3$d 筆）取代。」
  The dialog needs the local meal count: `BackupViewModel` exposes `localMealCount` from
  `EatRecordRepository.observeAll()`.

**ViewModel**

`BackupViewModel` no longer launches work. It collects `BackupJobRunner.state` into `uiState`,
maps `Finished` to a `BackupMessage`, refreshes the snapshot list after a completed job, and
calls `acknowledgeFinished()` when the message is consumed. `backup()` / `restore()` / `cancel()`
delegate to the runner.

**Android**

- `BackupForegroundService` (`foregroundServiceType="dataSync"`, not exported). On start it
  posts the progress notification, then collects `BackupJobRunner.state`:
  - `Running` → `setProgress(total, done, total == 0)`, `setOngoing(true)`, caption per step,
    a 「取消」 action (PendingIntent → service → `runner.cancel()`).
  - `Finished` → replaces it with a silent, auto-cancel result notification (「已備份」 /
    「已還原」 / 「已取消」 / 「發生了一點問題，請再試一次。」), then `stopSelf()`.
  - Tapping either notification opens the app.
- Channel `backup_progress`, `IMPORTANCE_LOW` (no sound, no heads-up). This is *not* a reminder
  channel; it exists only while the user's own job runs.
- `BackupRoute`: when it observes `Finished` while resumed, it cancels the result notification
  (the user already saw the snackbar). Before starting a job on API 33+, if `POST_NOTIFICATIONS`
  is not granted, request it once (same launcher pattern as `SettingsRoute`); the job starts
  regardless of the answer.
- Manifest: `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_DATA_SYNC`, the `<service>` entry.
  Android 14+ caps `dataSync` at 6 h per day — far above any backup.

## Error handling

- Any exception inside a job → `Finished(kind, Failed)` (or `SchemaTooNew`), logged with the
  Drive error body as today. `CancellationException` → `Finished(kind, Cancelled)`.
- A photo transfer failure cancels the sibling transfers (structured concurrency) and fails the
  job; no manifest is written.
- Token expiry mid-job surfaces as `Failed`; the route's `authorizedThen` still fronts every
  start so the consent sheet appears before a job, never inside one.

## Testing

`:shared` (commonTest, fakes as today):

- `BackupServiceTest` — progress reports arrive in step order with correct totals; restore with
  local meals uploads a safety snapshot first **and does not prune**; restore on an empty device
  skips it; cancellation before `Applying` leaves local data intact.
- `BackupJobRunnerTest` — second `start*` while running returns false; state goes
  Idle → Running → Finished → (acknowledge) Idle; host `onJobStarted`/`onJobFinished` are paired
  on success, failure and cancel; cancel produces `Cancelled`.
- `BackupViewModelTest` — mirrors runner state into `uiState`, maps outcomes to messages,
  refreshes snapshots after completion; `localMealCount` follows the repository.
- `FakeBackupStore` updated for the new signatures and `mealCount`.

`:app` (unit, MockWebServer): listing paging; parallel upload issues N requests and the manifest
is last; restore skips photos that exist locally.

Manual (device): back up with ~50 photos, press home mid-way, screen off, confirm the
notification progresses and the backup completes; restore onto the same phone and confirm the
safety snapshot appears in the list.

## 初衷對照 / North-Star check

Every change removes friction or fear; none adds a verdict.

- 記錄變成負擔? No — capture is untouched; backup gets *less* attention-demanding.
- 評價使用者? No.
- 外在壓力? The progress notification only exists while a job the user started is running and
  disappears when it ends. **Drift to avoid:** the result notification must never grow into
  「你已經 N 天沒備份」. It states an outcome once and is auto-cancel.
- 語氣: 「已取消，什麼都沒有改變。」 and 「之後隨時可以還回來。」 are reassurance, not
  instruction. Keep copy in that register.
- The safety snapshot serves 「留住每一個美好的當下」 directly: restore can no longer lose a
  moment.

## Docs to update

- `docs/DEVELOPMENT.md` §4 — drop 「Drive 檔案列表未分頁」 from known trade-offs; note the runner.
- `2026-06-23-drive-backup-design.md` — mark "Progress UI + cancel" as delivered here.
