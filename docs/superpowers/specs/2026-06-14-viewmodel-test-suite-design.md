# ViewModel test suite — design

**Date:** 2026-06-14
**Scope:** Pragmatic — test what's reachable now, with one small production seam.

## Goal

Close the ViewModel-testing gap. Domain, mappers, and the DataStore repository
already have unit tests; none of the 5 ViewModels do, and there is no shared
test infrastructure (no `MainDispatcherRule`, no Turbine). Add that
infrastructure and cover the ViewModels that are reachable without invasive
refactoring.

## Non-goals

- **`EatTimeEditorViewModel`** is out of scope. It depends on Android `Context`
  and the static `PhotoStorage` / `LocationProvider` objects, which are not
  injectable. Making it unit-testable requires extracting those behind
  interfaces — deferred to a later "Thorough" pass. This gap is intentional and
  documented here.
- No refactoring of existing passing tests (their inline fakes stay as-is).
- No instrumented (`androidTest`) or Compose-UI tests. JVM unit tests only.

## Existing patterns to follow

- Hand-written `Fake*Repository` implementing the domain repository interface:
  canned data via constructor, captured calls via mutable lists
  (see `CreateCustomFastingTypeUseCaseTest`, `DietSettingsRepositoryTest`).
- Real use cases on top of fake repositories — fake at the repository seam
  (the interfaces already designed for DI), not at the use-case layer
  (use cases are final classes).
- `kotlinx.coroutines.test.runTest`, JUnit4, `org.junit.Assert.*`.

## Infrastructure (new, shared)

1. **Turbine** — add `app.cash.turbine:turbine` to `gradle/libs.versions.toml`
   and as `testImplementation` in `app/build.gradle.kts`. Used for asserting on
   `StateFlow` / `Flow` emissions.

2. **`MainDispatcherRule`** — a JUnit `TestWatcher` that sets `Dispatchers.Main`
   to a `StandardTestDispatcher` in `starting()` and resets it in `finished()`,
   so `viewModelScope.launch { … }` runs under the test scheduler. Location:
   `app/src/test/java/com/crazystudio/sportrecorder/testutil/MainDispatcherRule.kt`.

3. **Shared fakes** — reusable fake repositories in
   `app/src/test/java/com/crazystudio/sportrecorder/fake/`:
   - `FakeEatRecordRepository` — backing `MutableStateFlow<List<EatRecord>>` for
     `observeAll()`; records `delete(id)` calls; `findById` / `save` minimally
     implemented as needed by the VMs under test.
   - `FakeDietSettingsRepository` — backing `MutableStateFlow<DietSettings>` for
     `settings`; `setSelection` updates it.
   - `FakeFastingTypeRepository` — backing `MutableStateFlow<List<FastingWindow>>`
     for `observeRecentCustomWindows()`; captures `add(window)`; `exists()` from
     a seeded set.

   Existing inline per-file fakes are left untouched.

## Production change — `DietViewModel` clock seam

`DietViewModel` calls `System.currentTimeMillis()` inline in two places (the
`ObserveDietStateUseCase` argument and the per-second ticker), so its output
depends on the wall clock. Inject a time source instead.

**Chosen approach (A):** a primary constructor taking `now: () -> Long`, plus a
secondary `@Inject` constructor that delegates with the real clock:

```kotlin
class DietViewModel(
    observeDietState: ObserveDietStateUseCase,
    private val now: () -> Long,
) : ViewModel() {

    @Inject
    constructor(observeDietState: ObserveDietStateUseCase) :
        this(observeDietState, System::currentTimeMillis)

    // ... existing body, with both System.currentTimeMillis() calls replaced by now()
}
```

Hilt uses the `@Inject`-annotated secondary constructor; tests construct the VM
directly via the primary constructor with a fixed/controllable clock. This
touches no Hilt module. (`@HiltViewModel` annotation stays on the class.)

Rejected alternatives: (B) a `@Provides fun (): () -> Long` Hilt binding —
touches a DI module for no benefit; (C) a dedicated `TimeProvider` interface +
binding — most ceremony for the same result.

## Tests per ViewModel

### `DietRecordViewModel`
- `records` StateFlow emits what the repository emits (seed fake → assert via
  Turbine after `runCurrent`/advance).
- `deleteRecord(record)` calls `DeleteEatRecordUseCase` → repository
  `delete(record.id)` with the correct id (advance the dispatcher, then assert
  the fake captured the id).

### `CreateFastingTypeViewModel`
- `createCustomFastingType(f, e)` returns `true` for a genuinely new window.
- Returns `false` for a built-in default (e.g. 16/8) and for an existing custom
  window. Drives the real `CreateCustomFastingTypeUseCase` over the fake repo.

### `SelectFastingTypeViewModel`
- `fastingItemFlow` equals `FastingItem.defaultFastingItems` followed by the
  custom windows mapped to `CustomFastingItem` and **reversed** (newest-first
  source → oldest-first display). Assert for empty custom list and a non-empty
  one.
- `saveSelection(f, e)` persists via `SaveFastingSelectionUseCase` → fake repo
  (requires `MainDispatcherRule`; advance dispatcher then assert).

### `DietViewModel` (fixed clock + pinned `TimeZone`/`Locale`)
- Setup pins `TimeZone.setDefault(...)` and `Locale.setDefault(...)` and restores
  them in teardown, so `Calendar` (in `ObserveDietStateUseCase` / `DietWindow`)
  and the VM's `SimpleDateFormat` produce deterministic output.
- Phase mapping: for IDLE / EATING / FASTING / SUCCESS, the resulting
  `statusIcon`, `statusTextRes`, and `promptTextRes` match expectations. Each
  phase is driven by seeding fake eat times + settings and choosing a fixed
  `now` that lands in that phase (anchored to `DietWindow` semantics).
- `fastingLabel` formats as `"%d : %d"` (e.g. `"16 : 8"`).
- `progress` equals `ringProgress * 100`.
- `elapsedText` formats elapsed millis as `HH:MM:SS` (duration is
  timezone-independent).
- Per-second recomputation: after advancing virtual time by one second, the
  collected state reflects the new `now` (proves the ticker drives
  recomputation). Collected via Turbine; the infinite ticker is bounded by
  Turbine cancelling the flow at the end of the `test {}` block.

## Verification

- `./gradlew test` passes (all existing + new tests).
- Confirm the CI workflow runs the `test` task; add it if missing.
- detekt stays clean on the new test sources (no wildcard imports, etc.).

## File map

```
gradle/libs.versions.toml                         # + turbine version & library
app/build.gradle.kts                              # + testImplementation(turbine)
app/src/main/.../ui/diet/DietViewModel.kt         # clock seam (only prod change)
app/src/test/.../testutil/MainDispatcherRule.kt   # new
app/src/test/.../fake/FakeEatRecordRepository.kt   # new
app/src/test/.../fake/FakeDietSettingsRepository.kt # new
app/src/test/.../fake/FakeFastingTypeRepository.kt  # new
app/src/test/.../ui/diet/DietViewModelTest.kt              # new
app/src/test/.../ui/diet/record/DietRecordViewModelTest.kt # new
app/src/test/.../ui/diet/create/fasting/CreateFastingTypeViewModelTest.kt # new
app/src/test/.../ui/diet/select/SelectFastingTypeViewModelTest.kt # new
```
