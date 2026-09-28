# Test coverage gaps (six items) — Design

**Status:** approved 2026-09-28 · **Builds on:** `2026-09-21-e2e-test-suite-design.md`, `2026-09-27-backup-instrumented-tests-design.md`

## Goal

Close the six coverage gaps ranked in the 2026-09-28 test inventory, in risk order: Room migrations,
Room DAO/repository contracts, the meal editor's Compose UI, the photo bitmap pipeline, the Android
side of reminders, and three small backup holes. Instrumented tests stay local-only (same rule as
the backup instrumented suite); JVM tests join the CI gate.

## Scope and non-goals

- Production changes are limited to two deliberate, tiny ones: `exportSchema = true` on
  `AppDatabase` (plus the committed schema JSON), and — only if the new race test proves it —
  discarding an in-flight snapshot listing when the signed-in account changes in `BackupViewModel`.
- No CI emulator job. No Robolectric. No new dependencies (`room-testing` stays unused: without
  historical schema JSONs its `MigrationTestHelper` cannot open old versions).
- `BootReceiver` is not tested directly: `ACTION_BOOT_COMPLETED` is a protected broadcast the test
  process may not send, and calling `onReceive` by hand makes `goAsync()` return null. Its only logic
  (`rescheduler.reschedule()`) is covered through `ReminderReceiver`, which shares it.
- `AppRoot` navigation and `BackupRoute` end-to-end remain out (need a production interface for
  `GoogleBackupAuth`).

## 1. Room migrations — `RoomMigrationTest` (androidTest)

**Why:** `AppDatabase` is at version 7 with six hand-written migrations and `exportSchema = false`.
No test opens an old database; a broken migration ships green and destroys data on upgrade.

**Mechanism:** build an old-version database by hand with `android.database.sqlite.SQLiteDatabase`
(the DDL Room itself generated at that version, recovered from git history), set
`PRAGMA user_version`, close it, then open it with the real `Room.databaseBuilder(...).addMigrations(
*Migrations.getMigrations())`. Room validates the migrated schema against the entities on open and
throws if they disagree, so "open + query succeeds + old rows intact" is the assertion.

Cases:

| From | Seed | Assert after open at 7 |
| --- | --- | --- |
| v1 (`eat_time(id, time)`) | one `eat_time` row | row present via `flowAllWithPhotos`, `lat/lng/note` null; `fasting_type` and `photo` tables usable (empty); `user_version == 7` |
| v3 (`eat_time`, `fasting_type` w/o `name`, `food_record`) | one row each | `food_record` gone from `sqlite_master`; `fasting_type` row readable with `name == null`; `eat_time` row keeps `time` |
| v4 (`eat_time` + `lat/lng`, `photo`) | one eat_time with a photo | photo relation intact after `note`/`name` columns are added |

Also flip `exportSchema = true` and commit `shared/schemas/…/7.json` so every future schema change
diffs in review and can use `MigrationTestHelper` from 7 onward. A one-line note in `DEVELOPMENT.md`.

## 2. DAO / repository contracts on real Room — `EatRecordRepositoryRoomTest`, `FastingTypeRepositoryRoomTest` (androidTest)

**Why:** the fakes *restate* `ORDER BY time DESC`, the window bounds, photo cascade and transaction
behaviour; nothing verifies them against SQLite.

Shared fixture `RoomHarness` (androidTest `data/`): in-memory `AppDatabase`, real
`EatRecordRepositoryImpl` / `FastingTypeRepositoryImpl`, a recording `PhotoFileStore`, and a
`ThrowingPhotoDao` decorator (delegates to the real `PhotoDao`, throws on the N-th `insert`).

Cases:

- `observeAll` is newest-first; `observeInWindow(after, before)` is ascending and strictly exclusive
  at both bounds.
- `save` insert assigns an id, stamps photos with `createdAt > 0`, and returns the id.
- `save` update removes exactly the given photo rows and calls `photoFileStore.delete` only for them.
- `delete` removes the record, its photo rows, and deletes the files.
- `replaceAll` is transactional: with the decorator throwing on the second photo insert, the call
  throws and the pre-existing rows are untouched (the real rollback the final reviewer asked for).
- `replaceAll` never calls `photoFileStore.delete`.
- Fasting types: `observeRecentCustomTypes` newest-first, capped at 10; `exists` matches hours only;
  `replaceAllCustom` swaps the table.

## 3. Meal editor Compose UI — `EatTimeEditorSheetTest` (androidTest)

**Why:** Capture is the core journey and has zero UI coverage; a callback wired to the wrong lambda
would pass every VM test.

Render `EatTimeEditorSheet(state, photoModel = { null }, …)` inside `SportRecorderTheme` with
counting callbacks. Cases: date and time rows show the formatted `dateMillis`; typing in the note
field emits `onNoteChange`; the add/select photo rows call their callbacks; each existing and pending
photo renders a tile whose remove control calls the matching callback with the right photo; location
`LOADING` shows the loading copy, `null` location shows the none copy, a set location shows lat/lng
and the clear control calls `onClearLocation`; the confirm control calls `onConfirm`. Expected copy
from Compose Resources via `getString`.

## 4. Photo pipeline — `BitmapPipelineTest` (androidTest)

**Why:** photos are the record; EXIF rotation and downscale bugs corrupt every capture silently.

`decodeScaleEncode` is `internal` in `:app`, visible to androidTest. Generate JPEGs in `cacheDir`:

- 3000×1500 with EXIF `ORIENTATION_ROTATE_90` → output decodes to 640×1280 (rotated, long edge 1280).
- 800×600, no EXIF → 800×600 unchanged.
- 1500×3000 with `ORIENTATION_ROTATE_180` → 640×1280 (no swap, scaled).
- Output file ends with `.webp`, `BitmapFactory` reports `image/webp`, file lives in the given dir.

## 5. Reminders, Android side — `AlarmReminderSchedulerTest`, `ReminderReceiverTest` (androidTest)

**Why:** everything below `ReminderScheduler.schedule()` is untested; "reminders scheduled" today
means a fake recorded a list.

- Scheduler: after `schedule([WINDOW_CLOSING@t])`, `PendingIntent.getBroadcast(ctx, 1000,
  fireIntent(WINDOW_CLOSING), FLAG_IMMUTABLE or FLAG_NO_CREATE)` is non-null and the
  `FAST_COMPLETE` slot (1001) is null; `schedule([])` cancels both; `schedule` of both types arms both.
  The intent must match the production one (component + action), which the test builds the same way.
- Receiver: Koin-override `RemindersRescheduler` with a recording fake, grant `POST_NOTIFICATIONS`,
  `context.sendBroadcast(explicit ReminderReceiver intent, ACTION_FIRE, EXTRA_TYPE=FAST_COMPLETE)` →
  notification id 2002 appears and the fake's `reschedule()` is awaited; an intent with a bad type
  posts nothing and does not reschedule.

## 6. Backup small holes (commonTest, JVM + iOS)

- **Golden JSON fixture** `BackupDocumentGoldenTest`: a checked-in v1 manifest string decodes to the
  expected document and the expected document encodes back to the same canonical string; the test
  asserts `BackupDocument.SCHEMA_VERSION == 1` so a schema bump forces a fixture update.
- **Cancel during apply**: `FakeEatRecordRepository.replaceAllGate` parks `replaceAll`; the runner is
  cancelled while parked; completing the gate yields `Finished(Restore, Completed)` and the data is
  replaced (proves `NonCancellable`).
- **Account-switch race**: `FakeBackupStore.listGate` parks `listSnapshots`; the account changes
  while parked; the late result must not populate the new account's list. Expected to fail first; fix
  by bumping the ViewModel's list generation on account change.

## Testing the tests

Each androidTest class runs on an attached API 35 emulator before commit; the whole instrumented
suite runs once at the end. The JVM gate runs before every commit that touches `:shared`/`:app` main
or JVM tests, and `:shared:iosSimulatorArm64Test` is covered by CI for the commonTest additions.

## 初衷對照 / North-Star check

Test-only apart from the two seams above. Migration and photo tests directly protect 「留住每一個
美好的當下」 — a lost record or a sideways photo is the failure the mission cannot afford.

## Docs to update

- `docs/DEVELOPMENT.md` §5 test inventory line and §6 index row.
