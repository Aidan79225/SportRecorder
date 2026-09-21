# End-to-end / flow test suite — design

- **Date:** 2026-09-21
- **Status:** Approved (design); implemented in the same change
- **Scope:** Flow-level ("headless E2E") coverage of the app's real user journeys, on the test
  layers the existing CI gate can already run. No new CI jobs, no new runtime dependencies.

## Goal

Today's tests are good but **unit-shaped**: pure calculators (`DietWindow`, `ReminderPlanner`,
`InsightsAggregator`), mappers, one DataStore repository, and one-assertion-per-ViewModel tests.
Nothing pins a **journey** — *add a meal → it shows up in the list → the fast clock moves →
reminders re-arm → back it up → wipe → restore*. Those seams between layers are exactly where
regressions hide, and they are currently unguarded.

This suite adds **flow tests**: real use cases + real ViewModels + (where possible) real
repositories, wired the way `AppModule` wires them, driven through a whole user journey and
asserted on the observable state at each step.

## 初衷對照 / North-Star check (required by CLAUDE.md)

Tests don't ship UI, so the usual red flags don't apply directly — but *what we choose to pin*
encodes what we think the app is for.

- **只記錄、不評價** — every assertion here is about *fidelity of the record* (the meal you saved
  is the meal you get back; the photo filename survives a backup round-trip) and *correctness of
  neutral state* (`EATING` / `FASTING` / `SUCCESS`). Nothing asserts a score, a grade, a streak,
  or a "you missed it". `DietPhase.SUCCESS` is pinned as a *transition*, never as a verdict about
  the user.
- **留住每一個美好的當下** — the backup/restore round-trip and the "a failed download must not
  wipe local data" case are the most mission-critical tests in the repo: they protect the user's
  moments from being lost. Prioritised accordingly.
- **記錄變成負擔** — the capture tests deliberately pin that note, photo, and location are all
  *optional* (a bare time-only meal saves fine). If a future change made any of them required,
  these tests fail. That is the intended guard.
- **教練式命令的語氣** — reminder tests assert *when* a reminder fires and, importantly, when it is
  **suppressed** (quiet hours, already-past events, reminders off by default). We pin the
  restraint, not just the nagging.

**Drift to watch:** a flow suite creates pressure to "just add a seam" to production code for
testability. Held to one rule below — no new seams were needed; the two injected-clock seams that
already exist (`DietViewModel`, `BackupService`, `RescheduleRemindersUseCase`,
`SaveEatRecordUseCase`) were sufficient.

## Step 1 — which E2E layer, and why

Three options were on the table.

| Option | What it would give | Can CI run it today? | Verdict |
|---|---|---|---|
| **(a)** Compose Multiplatform UI tests (`runComposeUiTest`) in `:shared` | Real screens + real VMs, no device | **No** — needs new deps (`compose.uiTest`, plus a Skiko/desktop runtime for `jvmTest`); `commonTest` also compiles for `iosSimulatorArm64` in CI, where the UI-test runner story differs | **Rejected for now** |
| **(b)** Android instrumented tests (`androidTest`) with real Room + Compose | True E2E incl. Room SQL, navigation, permissions | **No** — `ci.yml` has no emulator job | **Rejected for now**; proposed below |
| **(c)** Headless flow tests: real use cases + real ViewModels + real DataStore, fakes at the Room seam | Every journey below, deterministic, fast | **Yes** — already covered by `testDebugUnitTest` and `:shared:jvmTest` | **Chosen** |

**Chosen: (c)**, split across the two source sets the gate already builds:

- **`shared/src/commonTest`** (runs in CI as `:shared:jvmTest` **and** `:shared:iosSimulatorArm64Test`)
  — everything Android-free: capture/edit/delete through the use cases, diet-state recomputation,
  reminder re-arming, the whole backup/restore surface. **Bonus: these journeys are thereby proven
  on Kotlin/Native too**, which no existing flow-level test does.
- **`app/src/test`** (runs in CI as `testDebugUnitTest`) — everything that needs `Dispatchers.Main`
  (`viewModelScope`), Turbine, a pinned JVM `TimeZone`, or a real on-disk DataStore: the Home
  screen state machine through `DietViewModel`, the meal editor through `EatTimeEditorViewModel`,
  and settings persistence through the **real** `ReminderPreferencesRepositoryImpl` /
  `DietSettingsRepositoryImpl` over a real DataStore file.

### What layer (c) does **not** cover — stated plainly

- **No Compose UI.** No test here clicks a button, types in a `TextField`, scrolls the record
  list, or asserts anything rendered. A screen could be wired to the wrong ViewModel callback, or
  render nothing at all, and this suite stays green.
- **No Room / no SQL.** `EatRecordRepositoryImpl`, `FastingTypeRepositoryImpl`, the DAO queries,
  the `@Transaction` boundaries, and `Migrations` are all faked out. The `ORDER BY time DESC`
  contract is *re-stated* in the fakes, not *verified*. A broken query or a bad migration ships green.
- **No navigation.** `AppRoot` / `Route` / the nav graph are untested.
- **No platform code.** `AlarmReminderScheduler` (AlarmManager), `ReminderNotifier`,
  `AndroidPhotoImporter`, `AndroidLocationProvider`, `GoogleDriveBackupStore`, `GoogleBackupAuth`,
  `BootReceiver` are all behind interfaces and faked. "Reminders are scheduled" here means
  *`ReminderScheduler.schedule()` was called with this list*, not *an alarm fired*.
- **No real cloud.** Backup tests use `FakeBackupStore`; Drive REST, token refresh, and the
  manifest-last commit ordering in `GoogleDriveBackupStore` are not exercised.
- **Account switching is only pinned at the `BackupService` level** — see the matrix note.

### On adding an emulator job (proposal, **not** done here)

Option (b) is the only way to close the Room/SQL, migration, Compose-UI and navigation gaps. It
would mean adding a `connectedDebugAndroidTest` job to `ci.yml` using
`reactivecircus/android-emulator-runner`. **Cost:** roughly +6–12 min wall clock per run, a
KVM-capable runner, and a real flake budget (emulator boot failures are the single most common
cause of red CI in Android repos). Given this repo's CI is currently fast and green on every PR,
the recommendation is: **don't add it as a blocking job now.** If it is added later, make it
`workflow_dispatch` + nightly first, and only promote it to required once its flake rate is known.
A cheaper middle step would be Robolectric for the Room-on-JVM half, which needs no emulator —
also not done here, to keep this change dependency-free.

## Scope

- Flow tests for: **Capture**, **Home / 斷食視窗**, **自訂斷食類型**, **設定**, **提醒**,
  **備份 / 還原**.
- Extending the **existing** fakes (`shared/.../backup/fakes/`, `app/.../fake/`) so they honour
  the contracts their real counterparts document — in particular making the eat-record fakes
  actually **persist** on `save`/`delete` (they previously recorded the call and dropped the data,
  which no journey can be written against).
- Three small new fakes for platform boundaries that had none: `PhotoImporter`, `PhotoFileStore`,
  `LocationProvider`.

## Not in scope

- **`ui/insights` and `domain/insights`** — untouched, by agreement (concurrent work).
- Any production-code change. (None was needed; see below.)
- New CI jobs, new Gradle dependencies, new test frameworks.
- Compose UI tests, instrumented tests, Robolectric, real Room, real Drive.
- Refactoring or re-asserting existing green tests. `DietWindowTest` and `ReminderPlannerTest`
  already cover their calculators exhaustively; the flow tests deliberately do **not** re-test
  pure arithmetic, only the wiring around it.

## Test matrix

Legend — **S** = `shared/src/commonTest` (JVM + iOS sim), **A** = `app/src/test` (JVM).

### Flow 1 · Capture — 新增/編輯/刪除一筆進食紀錄

| # | Case | Where | Pins |
|---|---|---|---|
| 1.1 | Empty state | S `CaptureFlowTest` | `observeAll()` starts empty; no reschedule |
| 1.2 | Happy path: save time + note + location + photo | S | record appears with all four fields intact; photo filename stored |
| 1.3 | Bare meal — time only, no note/photo/location | S | saves fine (**optional-by-design guard**) |
| 1.4 | Two meals | S | list is **newest-first** (the DAO's `ORDER BY time DESC` contract) |
| 1.5 | Edit: change note + time, add one photo, remove another | S | id stable; note/time updated; photo set reconciled |
| 1.6 | Edit a record that no longer exists | S | `findById` → null; no crash |
| 1.7 | **Failure:** future time | S | `save` returns false, list unchanged, **no** reschedule |
| 1.8 | Boundary: `time == now` | S | accepted (strict `>` guard) |
| 1.9 | Delete | S | removed from list; reschedule fired |
| 1.10 | Delete an unknown id | S | list unchanged; reschedule still fired (idempotent) |
| 1.11 | Every accepted mutation re-arms reminders | S | reschedule count tracks save+delete exactly |
| 1.12 | Editor VM, create mode: note + captured photo + location + picked date/time → save | A `EatTimeEditorFlowTest` | VM → use case → repository, full path |
| 1.13 | Editor VM, edit mode: `SavedStateHandle("eatTimeId")` seeds the form | A | existing note/photos/location loaded into `uiState` |
| 1.14 | Editor VM: location unavailable | A | `LocationStatus.UNAVAILABLE`, saves with `location == null` |
| 1.15 | Editor VM: photo import fails (importer returns null) | A | no pending photo added, no crash |
| 1.16 | Editor VM: remove a pending photo | A | dropped from `uiState.pendingPhotos` |
| 1.17 | Editor VM: **failure** — future time | A | `save()` false; nothing persisted |

*Not pinned:* `onCleared()` cleanup of orphan photo files (`onCleared` is `protected`, not callable
from a test), and the `Dispatchers.Default` file delete inside `removePendingPhoto` (runs off the
virtual clock — only the state change is asserted).

### Flow 2 · Home / 斷食視窗 — DietWindow-driven state

| # | Case | Where | Pins |
|---|---|---|---|
| 2.1 | No records → IDLE | S `DietStateFlowTest` / A `DietHomeFlowTest` | IDLE phase, `00:00:00`, no fast labels |
| 2.2 | Meal added while observing → EATING | S + A | the flow **re-emits** on repository change |
| 2.3 | Clock advances → FASTING | A | ticker recomputation flips the phase live |
| 2.4 | Clock advances past target → SUCCESS | A | `progress == 100f` |
| 2.5 | Cross-midnight window (22:00 meal, UTC pinned) | A | `windowEnd`/`fastTargetAt` land on **TOMORROW** (`RelativeDay`) |
| 2.6 | Second meal during the window | S | window merges; fast clock moves to the later meal (no 1h grace) |
| 2.7 | Meal beyond the merge tolerance | S | a **new** window opens |
| 2.8 | Settings changed 16:8 → 20:4 while observing | S + A | snapshot re-emits; `fastingLabel` and phase follow |
| 2.9 | Empty → populated → emptied again | S | returns to IDLE |

*Not pinned:* `ObserveDietStateUseCase`'s ±day window bounds (`after`/`before`), because both fakes
intentionally ignore them — that filtering is a Room query, out of scope here.

### Flow 3 · 自訂斷食類型 — create / select / apply

| # | Case | Where | Pins |
|---|---|---|---|
| 3.1 | Create a genuinely new window | S `CustomFastingTypeFlowTest` | returns true; observable in the list |
| 3.2 | Name carried through | S | optional name survives to `CustomFastingType.name` |
| 3.3 | **Rejected:** duplicates a built-in (16/8) | S | returns false; nothing added |
| 3.4 | **Rejected:** duplicates an existing custom | S | returns false; list unchanged |
| 3.5 | Newest-first ordering after two creates | S | matches the `ORDER BY timestamp DESC` contract |
| 3.6 | Blank name normalised to null | A `CreateFastingTypeViewModel` (existing test) + S | `""` → `null` |
| 3.7 | Select a custom type → applied to settings | S | `DietSettings` updated; reminders re-armed |
| 3.8 | Select a built-in type | S | same path works for defaults |
| 3.9 | Selection then Home state | S | the new hours immediately change the computed phase |

### Flow 4 · 設定 — persistence + reschedule

| # | Case | Where | Pins |
|---|---|---|---|
| 4.1 | Fasting hours persist to a **real DataStore file** | A `SettingsPersistenceFlowTest` | a *fresh* repo instance over the same file reads them back |
| 4.2 | Every reminder preference persists | A | all six `ReminderPrefs` fields round-trip |
| 4.3 | Changing fasting hours re-arms reminders | A | scheduler receives a plan computed from the **new** hours |
| 4.4 | Changing a preference re-arms reminders | A | ditto, from the new prefs |
| 4.5 | Empty store → documented defaults | A | 16:8, reminders **off** (opt-in by design) |
| 4.6 | VM: toggles reach the repository and reschedule | A `SettingsViewModelFlowTest` | one reschedule per toggle |
| 4.7 | VM: lead-minutes clamped to [5, 120] | A | repeated deltas saturate, don't overflow |

### Flow 5 · 提醒 — ReminderPlanner through the real use case

All cases below are **timezone-independent** by construction, because
`RescheduleRemindersUseCase` calls `ReminderPlanner.plan(...)` without a `timeZone` argument (it
uses the system zone). Quiet-hours cases therefore use the two degenerate ranges that behave
identically in every zone: `[0, 1440)` = always quiet, `[0, 0)` = never quiet. Per-zone
time-of-day behaviour is already covered by `ReminderPlannerTest` with an injected `TimeZone.UTC`.

| # | Case | Where | Pins |
|---|---|---|---|
| 5.1 | Both reminders off (the default) | S `ReminderFlowTest` | empty plan — no unsolicited nagging |
| 5.2 | Only window-closing on | S | one reminder at `windowEnd − leadMinutes` |
| 5.3 | Only fast-complete on | S | one reminder at `fastTargetAt` |
| 5.4 | Both on | S | exactly two, one of each type |
| 5.5 | Custom lead minutes | S | trigger moves with the lead |
| 5.6 | Window-closing while FASTING | S | dropped (not applicable outside the eating window) |
| 5.7 | Window-closing trigger already past | S | dropped (never schedules backwards) |
| 5.8 | Fast already complete | S | dropped |
| 5.9 | Quiet hours covering the whole day | S | fast-complete **suppressed**; window-closing untouched |
| 5.10 | Degenerate empty quiet range | S | nothing suppressed |
| 5.11 | No records at all | S | empty plan |
| 5.12 | Saving a meal re-arms with the **new** target | S | real `SaveEatRecordUseCase` → real `RescheduleRemindersUseCase` |
| 5.13 | Deleting the only meal re-arms to empty | S | plan collapses |
| 5.14 | Changing the fasting window re-arms | S | real `SaveFastingSelectionUseCase` → new target |
| 5.15 | Fast target crossing midnight | S | planned normally (quiet hours off) |

### Flow 6 · 備份 / 還原 round-trip

| # | Case | Where | Pins |
|---|---|---|---|
| 6.1 | **Full round-trip:** meals + notes + geo + photo filenames + custom types + diet settings + all reminder prefs → backup → wipe → restore | S `BackupRestoreFlowTest` | every field comes back; **this is the mission-critical test** |
| 6.2 | Restore over a device that already has *different* data | S | local data fully replaced, not merged |
| 6.3 | Empty backup → restore | S | restores to an empty (but valid) state |
| 6.4 | Incremental photo upload | S | a second backup re-uploads only genuinely new photos |
| 6.5 | Retention | S | `prune(KEEP_LAST)` called; only the newest 3 survive |
| 6.6 | **Failure:** schema too new | S | `BackupSchemaTooNewException`; local data **untouched**; no reschedule |
| 6.7 | **Failure:** photo download dies mid-restore | S | throws; local meals/types/settings/prefs **all** untouched; no reschedule |
| 6.8 | Restore re-arms reminders | S | exactly one reschedule on success |
| 6.9 | **Account switch:** back up as A, switch to B, list/restore | S | B sees only B's snapshots; A's snapshot id is unreadable as B; restoring B's replaces A's restored data |

*Note on 6.9:* `BackupService` itself is account-agnostic — accounts live in `GoogleBackupAuth` /
`GoogleDriveBackupStore` (Android-only, not reachable from `commonTest`). What 6.9 actually pins
is that **`BackupService` holds no cross-account state**: it re-reads `listSnapshots()` /
`downloadManifest()` on every call and caches nothing, so swapping the store's account swaps the
visible snapshots completely. The Drive-side account handling remains untested.

## Infrastructure changes (test-only)

### Extended existing fakes

| Fake | Change | Why safe |
|---|---|---|
| `shared/.../backup/fakes/FakeEatRecordRepository` | `save()` now really inserts (id `0` → next id) / updates, reconciles photos, and keeps the list **newest-first**; `delete()` really removes | `BackupServiceTest` never calls `save`/`delete` on it |
| `shared/.../backup/fakes/FakeFastingTypeRepository` | `add()` now **prepends** (newest-first, matching `ORDER BY timestamp DESC`) | `BackupServiceTest` never calls `add` |
| `shared/.../backup/fakes/FakeRepositories.kt` | `FakeReminderScheduler` added alongside the existing `FakeRemindersRescheduler` | new type |
| `shared/.../backup/fakes/FakeBackupStore` | snapshots/manifests/photos partitioned per `account` (default account preserves old behaviour); snapshot ids stay globally unique | existing tests never set `account` |
| `app/.../fake/FakeEatRecordRepository` | `save()` really persists; `delete()` really removes **and** still records `deletedIds` | `DietRecordViewModelTest` starts from an empty repo; `SaveEatRecordUseCaseTest` uses its own private fake |
| `app/.../fake/FakeFastingTypeRepository` | `add()` also updates the observable list (still records `added`/`addedNames`) | existing assertions are on `added`, unchanged |

### New fakes (platform boundaries that had none)

- `app/.../fake/FakePhotoImporter` — returns a canned filename per import, or `null` to model a
  failed import; records what it was asked to import.
- `app/.../fake/FakePhotoFileStore` — records deleted filenames.
- `app/.../fake/FakeLocationProvider` — returns a canned `GeoPoint` or `null`.

## Production-code seams

**None.** The clock seams already in the codebase were sufficient:

- `DietViewModel(observeDietState, now: () -> Long)` — already present (secondary constructor for DI).
- `SaveEatRecordUseCase(..., now: Long = Clock.System…)` — already a parameter.
- `RescheduleRemindersUseCase(..., now: () -> Long)` — already a primary-constructor seam.
- `BackupService(..., now: () -> Long = …)` — already a default lambda.

`EatTimeEditorViewModel` seeds `currentMillis` from `Clock.System.now()` with no seam, but the
tests drive it deterministically through the existing `updateDate()` / `updateTime()` API instead
of adding one — which is also closer to what the real UI does.

## Testing

- The suite is verified by the existing gate, unchanged:
  `./gradlew assembleDebug testDebugUnitTest :app:detekt :app:lintDebug :shared:jvmTest`,
  plus `:shared:iosSimulatorArm64Test` on the macOS job.
- New `:app` test sources must pass `:app:detekt` (120-col lines, no wildcard imports, the
  configured import ordering). `:shared` has no detekt plugin.
- Determinism rules followed throughout:
  - `commonTest` stays Android-free — no `java.*`, no `System.currentTimeMillis`, no
    `Dispatchers.IO`, no `String.format`.
  - Every clock is injected; no test reads the wall clock.
  - Timezone-sensitive assertions either pin `java.util.TimeZone` to UTC (`:app` only, as
    `DietViewModelTest` already does) or use the degenerate quiet-hours ranges described above.

## File map

```
docs/superpowers/specs/2026-09-21-e2e-test-suite-design.md          # this spec

shared/src/commonTest/.../backup/fakes/FakeRepositories.kt          # extended + FakeReminderScheduler
shared/src/commonTest/.../backup/fakes/FakeBackupStore.kt           # per-account partitioning
shared/src/commonTest/.../flow/CaptureFlowTest.kt                   # new
shared/src/commonTest/.../flow/DietStateFlowTest.kt                 # new
shared/src/commonTest/.../flow/CustomFastingTypeFlowTest.kt         # new
shared/src/commonTest/.../flow/ReminderFlowTest.kt                  # new
shared/src/commonTest/.../flow/BackupRestoreFlowTest.kt             # new

app/src/test/.../fake/FakeEatRecordRepository.kt                    # now persists
app/src/test/.../fake/FakeFastingTypeRepository.kt                  # add() updates the list
app/src/test/.../fake/FakeLocationProvider.kt                       # new
app/src/test/.../fake/FakePhotoFileStore.kt                         # new
app/src/test/.../fake/FakePhotoImporter.kt                          # new
app/src/test/.../flow/DietHomeFlowTest.kt                           # new
app/src/test/.../flow/EatTimeEditorFlowTest.kt                      # new
app/src/test/.../flow/SettingsPersistenceFlowTest.kt                # new
app/src/test/.../flow/SettingsViewModelFlowTest.kt                  # new
```
