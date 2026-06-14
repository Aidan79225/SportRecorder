# ViewModel Test Suite Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add JVM unit tests for the diet ViewModels, plus the shared test infrastructure (Turbine, a `MainDispatcherRule`, reusable fake repositories) they need.

**Architecture:** Fake the domain repository **interfaces** (the existing DI seam) and run the **real** use cases on top — matching the codebase's existing test style. ViewModels are constructed directly in tests. One small production seam is added to `DietViewModel`: an injectable time source so its wall-clock-dependent output becomes deterministic.

**Tech Stack:** Kotlin, JUnit4, `kotlinx-coroutines-test` (`runTest`, `TestDispatcher`), Turbine (Flow assertions), Hilt (production only — tests bypass it).

**Scope note:** `EatTimeEditorViewModel` is intentionally **out of scope** (depends on Android `Context` + static `PhotoStorage`/`LocationProvider`; needs interface extraction — a later pass). See `docs/superpowers/specs/2026-06-14-viewmodel-test-suite-design.md`.

**Conventions confirmed from the codebase:**
- Existing fakes are hand-written classes implementing the repo interface (see `CreateCustomFastingTypeUseCaseTest`).
- Models: `DietSettings(fastingHours: Long, eatingHours: Long)`, `FastingWindow(fastingHours: Long, eatingHours: Long)`, `EatRecord(id: Int, time: Long, location: GeoPoint?, note: String?, photos: List<EatPhoto>)`.
- `DietWindow.compute(eatTimesAsc, eatingHours, fastingHours, now)` — eating hours first. With one eat at `f`, settings 16/8: `now=f+2h`→EATING(0.25), `now=f+12h`→FASTING(0.75), `now=f+16h`→SUCCESS(1.0), empty list→IDLE.
- CI already runs `testDebugUnitTest` and `:app:detekt`, so new tests + detekt-cleanliness are gated automatically. **No CI file change needed.**
- detekt already tolerates magic numbers / named imports in `src/test` (existing tests use them freely). Follow the existing test style; no special detekt handling.

**Test dispatcher strategy:** `MainDispatcherRule` holds a `StandardTestDispatcher`. Every test runs `runTest(mainRule.testDispatcher.scheduler)` so the test body and `Dispatchers.Main` share one scheduler. StateFlow/Flow emissions are read with **Turbine** (`.test { … }`). `DietViewModel` has an **infinite** per-second ticker, so its tests never call `advanceUntilIdle()` — they `skipItems`/`awaitItem` and drive ticks with `advanceTimeBy(1000)`.

---

## File Structure

```
gradle/libs.versions.toml                                        # + turbine version & library entry
app/build.gradle.kts                                             # + testImplementation(libs.turbine)
app/src/main/.../ui/diet/DietViewModel.kt                        # MODIFY: clock seam (only production change)

app/src/test/.../testutil/MainDispatcherRule.kt                  # NEW
app/src/test/.../fake/FakeEatRecordRepository.kt                 # NEW
app/src/test/.../fake/FakeDietSettingsRepository.kt              # NEW
app/src/test/.../fake/FakeFastingTypeRepository.kt               # NEW

app/src/test/.../ui/diet/record/DietRecordViewModelTest.kt              # NEW
app/src/test/.../ui/diet/create/fasting/CreateFastingTypeViewModelTest.kt  # NEW
app/src/test/.../ui/diet/select/SelectFastingTypeViewModelTest.kt       # NEW
app/src/test/.../ui/diet/DietViewModelTest.kt                          # NEW
```

Base package path: `com/crazystudio/sportrecorder`. Test root: `app/src/test/java/com/crazystudio/sportrecorder`.

---

## Task 1: Test infrastructure — Turbine, MainDispatcherRule, shared fakes

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`
- Create: `app/src/test/java/com/crazystudio/sportrecorder/testutil/MainDispatcherRule.kt`
- Create: `app/src/test/java/com/crazystudio/sportrecorder/fake/FakeEatRecordRepository.kt`
- Create: `app/src/test/java/com/crazystudio/sportrecorder/fake/FakeDietSettingsRepository.kt`
- Create: `app/src/test/java/com/crazystudio/sportrecorder/fake/FakeFastingTypeRepository.kt`

- [ ] **Step 1: Add the Turbine version + library to the version catalog**

In `gradle/libs.versions.toml`, under `[versions]` add (after `detekt = "1.23.8"`):

```toml
turbine = "1.2.1"
```

Under `[libraries]` add (after the `detekt-formatting` line):

```toml
turbine = { group = "app.cash.turbine", name = "turbine", version.ref = "turbine" }
```

- [ ] **Step 2: Add the Turbine test dependency**

In `app/build.gradle.kts`, in the `dependencies { }` block, next to the other `testImplementation` lines (near `testImplementation(libs.kotlinx.coroutines.test)`), add:

```kotlin
    testImplementation(libs.turbine)
```

- [ ] **Step 3: Create the MainDispatcherRule**

Create `app/src/test/java/com/crazystudio/sportrecorder/testutil/MainDispatcherRule.kt`:

```kotlin
package com.crazystudio.sportrecorder.testutil

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * Swaps [Dispatchers.Main] for a [TestDispatcher] for the duration of a test, so
 * `viewModelScope` work runs on the test scheduler. Run the test body with
 * `runTest(rule.testDispatcher.scheduler)` so both share one virtual clock.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    val testDispatcher: TestDispatcher = StandardTestDispatcher(),
) : TestWatcher() {
    override fun starting(description: Description) {
        Dispatchers.setMain(testDispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}
```

- [ ] **Step 4: Create FakeEatRecordRepository**

Create `app/src/test/java/com/crazystudio/sportrecorder/fake/FakeEatRecordRepository.kt`:

```kotlin
package com.crazystudio.sportrecorder.fake

import com.crazystudio.sportrecorder.domain.model.EatPhoto
import com.crazystudio.sportrecorder.domain.model.EatRecord
import com.crazystudio.sportrecorder.domain.repository.EatRecordRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * In-memory fake. [observeAll] and [observeInWindow] both surface the same backing
 * list (window filtering is exercised in use-case tests, not here). [delete] is recorded.
 */
class FakeEatRecordRepository(
    initial: List<EatRecord> = emptyList(),
) : EatRecordRepository {
    private val state = MutableStateFlow(initial)
    val deletedIds = mutableListOf<Int>()

    fun setRecords(records: List<EatRecord>) {
        state.value = records
    }

    override fun observeAll(): Flow<List<EatRecord>> = state

    override fun observeInWindow(after: Long, before: Long): Flow<List<EatRecord>> = state

    override suspend fun findById(id: Int): EatRecord? = state.value.firstOrNull { it.id == id }

    override suspend fun save(
        record: EatRecord,
        newPhotoFileNames: List<String>,
        removedPhotos: List<EatPhoto>,
    ): Int = record.id

    override suspend fun delete(recordId: Int) {
        deletedIds.add(recordId)
    }
}
```

- [ ] **Step 5: Create FakeDietSettingsRepository**

Create `app/src/test/java/com/crazystudio/sportrecorder/fake/FakeDietSettingsRepository.kt`:

```kotlin
package com.crazystudio.sportrecorder.fake

import com.crazystudio.sportrecorder.domain.model.DietSettings
import com.crazystudio.sportrecorder.domain.model.FastingWindow
import com.crazystudio.sportrecorder.domain.repository.DietSettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeDietSettingsRepository(
    initial: DietSettings = DietSettings(fastingHours = 16, eatingHours = 8),
) : DietSettingsRepository {
    private val state = MutableStateFlow(initial)

    override val settings: Flow<DietSettings> = state

    override suspend fun setSelection(window: FastingWindow) {
        state.value = DietSettings(
            fastingHours = window.fastingHours,
            eatingHours = window.eatingHours,
        )
    }
}
```

- [ ] **Step 6: Create FakeFastingTypeRepository**

Create `app/src/test/java/com/crazystudio/sportrecorder/fake/FakeFastingTypeRepository.kt`:

```kotlin
package com.crazystudio.sportrecorder.fake

import com.crazystudio.sportrecorder.domain.model.FastingWindow
import com.crazystudio.sportrecorder.domain.repository.FastingTypeRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * In-memory fake. [observeRecentCustomWindows] surfaces the backing list (newest-first
 * is the caller's responsibility). [exists] is seeded; [add] is recorded.
 */
class FakeFastingTypeRepository(
    initial: List<FastingWindow> = emptyList(),
    private val existing: Set<FastingWindow> = emptySet(),
) : FastingTypeRepository {
    private val state = MutableStateFlow(initial)
    val added = mutableListOf<FastingWindow>()

    fun setWindows(windows: List<FastingWindow>) {
        state.value = windows
    }

    override fun observeRecentCustomWindows(): Flow<List<FastingWindow>> = state

    override suspend fun exists(window: FastingWindow): Boolean =
        window in existing || window in added

    override suspend fun add(window: FastingWindow) {
        added.add(window)
    }
}
```

- [ ] **Step 7: Verify the project still compiles and all existing tests pass**

Run: `./gradlew testDebugUnitTest`
Expected: BUILD SUCCESSFUL. Existing tests still pass; the new infra files compile but add no tests yet.

- [ ] **Step 8: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts app/src/test/java/com/crazystudio/sportrecorder/testutil app/src/test/java/com/crazystudio/sportrecorder/fake
git commit -m "[Test] Add Turbine, MainDispatcherRule, and shared fake repositories"
```

---

## Task 2: DietRecordViewModelTest

**Files:**
- Create: `app/src/test/java/com/crazystudio/sportrecorder/ui/diet/record/DietRecordViewModelTest.kt`
- Under test: `app/src/main/java/com/crazystudio/sportrecorder/ui/diet/record/DietRecordViewModel.kt` (no change)

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/crazystudio/sportrecorder/ui/diet/record/DietRecordViewModelTest.kt`:

```kotlin
package com.crazystudio.sportrecorder.ui.diet.record

import app.cash.turbine.test
import com.crazystudio.sportrecorder.domain.model.EatRecord
import com.crazystudio.sportrecorder.domain.usecase.DeleteEatRecordUseCase
import com.crazystudio.sportrecorder.domain.usecase.ObserveEatRecordsUseCase
import com.crazystudio.sportrecorder.fake.FakeEatRecordRepository
import com.crazystudio.sportrecorder.testutil.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DietRecordViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private fun record(id: Int, time: Long) =
        EatRecord(id = id, time = time, location = null, note = null, photos = emptyList())

    private fun viewModel(repo: FakeEatRecordRepository) = DietRecordViewModel(
        observeEatRecords = ObserveEatRecordsUseCase(repo),
        deleteEatRecord = DeleteEatRecordUseCase(repo),
    )

    @Test
    fun records_reflectRepositoryEmissions() = runTest(mainRule.testDispatcher.scheduler) {
        val repo = FakeEatRecordRepository(initial = listOf(record(1, 1_000L)))
        val vm = viewModel(repo)

        vm.records.test {
            assertEquals(emptyList<EatRecord>(), awaitItem()) // StateFlow initial value
            assertEquals(listOf(record(1, 1_000L)), awaitItem())

            repo.setRecords(listOf(record(1, 1_000L), record(2, 2_000L)))
            assertEquals(listOf(record(1, 1_000L), record(2, 2_000L)), awaitItem())

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun deleteRecord_deletesById() = runTest(mainRule.testDispatcher.scheduler) {
        val repo = FakeEatRecordRepository()
        val vm = viewModel(repo)

        vm.deleteRecord(record(7, 1_000L))
        advanceUntilIdle()

        assertEquals(listOf(7), repo.deletedIds)
    }
}
```

- [ ] **Step 2: Run the test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.crazystudio.sportrecorder.ui.diet.record.DietRecordViewModelTest"`
Expected: PASS (the VM already supports this behavior; no production change needed).

> Note: this VM needs no production change, so there is no red-then-green cycle — the test documents existing behavior. If `records_reflectRepositoryEmissions` flakes on the initial item, it is because the `WhileSubscribed` upstream produced the seeded value before the empty default was observed; that is acceptable — adjust by removing the first `awaitItem()` assertion only if the run proves it. Run it before assuming.

- [ ] **Step 3: Commit**

```bash
git add app/src/test/java/com/crazystudio/sportrecorder/ui/diet/record/DietRecordViewModelTest.kt
git commit -m "[Test] DietRecordViewModel: records flow + delete-by-id"
```

---

## Task 3: CreateFastingTypeViewModelTest

**Files:**
- Create: `app/src/test/java/com/crazystudio/sportrecorder/ui/diet/create/fasting/CreateFastingTypeViewModelTest.kt`
- Under test: `app/src/main/java/com/crazystudio/sportrecorder/ui/diet/create/fasting/CreateFastingTypeViewModel.kt` (no change)

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/crazystudio/sportrecorder/ui/diet/create/fasting/CreateFastingTypeViewModelTest.kt`:

```kotlin
package com.crazystudio.sportrecorder.ui.diet.create.fasting

import com.crazystudio.sportrecorder.domain.model.FastingWindow
import com.crazystudio.sportrecorder.domain.usecase.CreateCustomFastingTypeUseCase
import com.crazystudio.sportrecorder.fake.FakeFastingTypeRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CreateFastingTypeViewModelTest {

    private fun viewModel(repo: FakeFastingTypeRepository) =
        CreateFastingTypeViewModel(CreateCustomFastingTypeUseCase(repo))

    @Test
    fun newWindow_isCreated() = runTest {
        val repo = FakeFastingTypeRepository()
        val vm = viewModel(repo)

        val ok = vm.createCustomFastingType(fastingHours = 18, eatingHours = 6)

        assertTrue(ok)
        assertEquals(listOf(FastingWindow(18, 6)), repo.added)
    }

    @Test
    fun builtInDefault_isRejected() = runTest {
        val repo = FakeFastingTypeRepository()
        val vm = viewModel(repo)

        val ok = vm.createCustomFastingType(fastingHours = 16, eatingHours = 8)

        assertFalse(ok)
        assertTrue(repo.added.isEmpty())
    }

    @Test
    fun existingCustom_isRejected() = runTest {
        val repo = FakeFastingTypeRepository(existing = setOf(FastingWindow(18, 6)))
        val vm = viewModel(repo)

        val ok = vm.createCustomFastingType(fastingHours = 18, eatingHours = 6)

        assertFalse(ok)
        assertTrue(repo.added.isEmpty())
    }
}
```

- [ ] **Step 2: Run the test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.crazystudio.sportrecorder.ui.diet.create.fasting.CreateFastingTypeViewModelTest"`
Expected: PASS. (16/8 is a built-in default — `CreateCustomFastingTypeUseCase` rejects it; `FakeFastingTypeRepository.exists` also treats already-added windows as existing.)

- [ ] **Step 3: Commit**

```bash
git add app/src/test/java/com/crazystudio/sportrecorder/ui/diet/create/fasting/CreateFastingTypeViewModelTest.kt
git commit -m "[Test] CreateFastingTypeViewModel: new / default-rejected / duplicate-rejected"
```

---

## Task 4: SelectFastingTypeViewModelTest

**Files:**
- Create: `app/src/test/java/com/crazystudio/sportrecorder/ui/diet/select/SelectFastingTypeViewModelTest.kt`
- Under test: `app/src/main/java/com/crazystudio/sportrecorder/ui/diet/select/SelectFastingTypeViewModel.kt` (no change)

Reminder of the production mapping under test:
```kotlin
val fastingItemFlow = observeCustomFastingTypes().map { windows ->
    FastingItem.defaultFastingItems +
        windows.map { FastingItem.CustomFastingItem(it.fastingHours, it.eatingHours) }.reversed()
}
```
So with repo windows `[A, B]` (newest-first), the tail is `[B, A]` mapped to `CustomFastingItem`.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/crazystudio/sportrecorder/ui/diet/select/SelectFastingTypeViewModelTest.kt`:

```kotlin
package com.crazystudio.sportrecorder.ui.diet.select

import app.cash.turbine.test
import com.crazystudio.sportrecorder.domain.model.FastingWindow
import com.crazystudio.sportrecorder.domain.usecase.ObserveCustomFastingTypesUseCase
import com.crazystudio.sportrecorder.domain.usecase.SaveFastingSelectionUseCase
import com.crazystudio.sportrecorder.fake.FakeDietSettingsRepository
import com.crazystudio.sportrecorder.fake.FakeFastingTypeRepository
import com.crazystudio.sportrecorder.testutil.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SelectFastingTypeViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private fun viewModel(
        typeRepo: FakeFastingTypeRepository,
        settingsRepo: FakeDietSettingsRepository,
    ) = SelectFastingTypeViewModel(
        observeCustomFastingTypes = ObserveCustomFastingTypesUseCase(typeRepo),
        saveFastingSelection = SaveFastingSelectionUseCase(settingsRepo),
    )

    @Test
    fun fastingItemFlow_emptyCustom_isDefaultsOnly() = runTest(mainRule.testDispatcher.scheduler) {
        val vm = viewModel(FakeFastingTypeRepository(), FakeDietSettingsRepository())

        assertEquals(FastingItem.defaultFastingItems, vm.fastingItemFlow.first())
    }

    @Test
    fun fastingItemFlow_customWindows_appendedReversed() = runTest(mainRule.testDispatcher.scheduler) {
        // Repository order is newest-first [20/4, 18/6]; display tail is reversed [18/6, 20/4].
        val typeRepo = FakeFastingTypeRepository(
            initial = listOf(FastingWindow(20, 4), FastingWindow(18, 6)),
        )
        val vm = viewModel(typeRepo, FakeDietSettingsRepository())

        val expected = FastingItem.defaultFastingItems + listOf(
            FastingItem.CustomFastingItem(18, 6),
            FastingItem.CustomFastingItem(20, 4),
        )
        assertEquals(expected, vm.fastingItemFlow.first())
    }

    @Test
    fun saveSelection_persistsToSettings() = runTest(mainRule.testDispatcher.scheduler) {
        val settingsRepo = FakeDietSettingsRepository()
        val vm = viewModel(FakeFastingTypeRepository(), settingsRepo)

        vm.saveSelection(fastingHours = 20, eatingHours = 4)
        advanceUntilIdle()

        settingsRepo.settings.test {
            assertEquals(
                com.crazystudio.sportrecorder.domain.model.DietSettings(fastingHours = 20, eatingHours = 4),
                awaitItem(),
            )
            cancelAndIgnoreRemainingEvents()
        }
    }
}
```

- [ ] **Step 2: Run the test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.crazystudio.sportrecorder.ui.diet.select.SelectFastingTypeViewModelTest"`
Expected: PASS.

- [ ] **Step 3: Commit**

```bash
git add app/src/test/java/com/crazystudio/sportrecorder/ui/diet/select/SelectFastingTypeViewModelTest.kt
git commit -m "[Test] SelectFastingTypeViewModel: defaults+reversed custom mapping; saveSelection persists"
```

---

## Task 5: DietViewModel clock seam + DietViewModelTest

This is the only task that changes production code. The test is written first against the **new** primary constructor (so it fails to compile), then the seam is added.

**Files:**
- Modify: `app/src/main/java/com/crazystudio/sportrecorder/ui/diet/DietViewModel.kt`
- Create: `app/src/test/java/com/crazystudio/sportrecorder/ui/diet/DietViewModelTest.kt`

- [ ] **Step 1: Write the failing test (compiles against the not-yet-existing constructor)**

Create `app/src/test/java/com/crazystudio/sportrecorder/ui/diet/DietViewModelTest.kt`:

```kotlin
package com.crazystudio.sportrecorder.ui.diet

import app.cash.turbine.test
import com.crazystudio.sportrecorder.R
import com.crazystudio.sportrecorder.domain.model.DietSettings
import com.crazystudio.sportrecorder.domain.model.EatRecord
import com.crazystudio.sportrecorder.domain.usecase.ObserveDietStateUseCase
import com.crazystudio.sportrecorder.fake.FakeDietSettingsRepository
import com.crazystudio.sportrecorder.fake.FakeEatRecordRepository
import com.crazystudio.sportrecorder.testutil.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalCoroutinesApi::class)
class DietViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private val f = 1_700_000_000_000L
    private fun h(n: Long) = TimeUnit.HOURS.toMillis(n)
    private fun eat(time: Long) =
        EatRecord(id = 1, time = time, location = null, note = null, photos = emptyList())

    private lateinit var defaultTz: TimeZone
    private lateinit var defaultLocale: Locale

    @Before
    fun pinTimeZoneAndLocale() {
        // Calendar (use case) and SimpleDateFormat (VM) read the JVM defaults; pin them
        // so time/elapsed strings are deterministic regardless of the CI machine.
        defaultTz = TimeZone.getDefault()
        defaultLocale = Locale.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        Locale.setDefault(Locale.US)
    }

    @After
    fun restoreTimeZoneAndLocale() {
        TimeZone.setDefault(defaultTz)
        Locale.setDefault(defaultLocale)
    }

    private fun viewModel(
        eats: List<EatRecord>,
        settings: DietSettings = DietSettings(fastingHours = 16, eatingHours = 8),
        now: () -> Long,
    ): DietViewModel {
        val eatRepo = FakeEatRecordRepository(initial = eats)
        val settingsRepo = FakeDietSettingsRepository(settings)
        return DietViewModel(ObserveDietStateUseCase(eatRepo, settingsRepo), now)
    }

    @Test
    fun fasting_mapsStatusProgressLabelAndElapsed() = runTest(mainRule.testDispatcher.scheduler) {
        val vm = viewModel(eats = listOf(eat(f))) { f + h(12) }

        vm.uiState.test {
            skipItems(1) // initial DietUiState() default
            val s = awaitItem()
            assertEquals(R.string.diet_status_fasting, s.statusTextRes)
            assertEquals(R.drawable.ic_baseline_no_food_24, s.statusIcon)
            assertEquals(R.string.diet_fasting_time, s.promptTextRes)
            assertEquals(75f, s.progress, 0.01f)
            assertEquals("16 : 8", s.fastingLabel)
            assertEquals("12:00:00", s.elapsedText)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun eating_mapsStatusAndPrompt() = runTest(mainRule.testDispatcher.scheduler) {
        val vm = viewModel(eats = listOf(eat(f))) { f + h(2) }

        vm.uiState.test {
            skipItems(1)
            val s = awaitItem()
            assertEquals(R.string.diet_status_eating, s.statusTextRes)
            assertEquals(R.drawable.ic_baseline_fastfood_24, s.statusIcon)
            assertEquals(R.string.diet_remaining_time, s.promptTextRes)
            assertEquals(25f, s.progress, 0.01f)
            assertEquals("06:00:00", s.elapsedText) // windowEnd - now = 6h
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun success_mapsStatusAndFullProgress() = runTest(mainRule.testDispatcher.scheduler) {
        val vm = viewModel(eats = listOf(eat(f))) { f + h(16) }

        vm.uiState.test {
            skipItems(1)
            val s = awaitItem()
            assertEquals(R.string.diet_status_success, s.statusTextRes)
            assertEquals(R.drawable.ic_baseline_no_food_24, s.statusIcon)
            assertEquals(100f, s.progress, 0.01f)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun idle_whenNoRecords() = runTest(mainRule.testDispatcher.scheduler) {
        val vm = viewModel(eats = emptyList()) { f }

        vm.uiState.test {
            skipItems(1)
            val s = awaitItem()
            assertEquals(R.string.diet_status_idle, s.statusTextRes)
            assertEquals(R.string.diet_no_record, s.promptTextRes)
            assertEquals("00:00:00", s.elapsedText)
            assertEquals(null, s.fastStart)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun ticker_recomputesEverySecond() = runTest(mainRule.testDispatcher.scheduler) {
        var nowMs = f + h(12)
        val vm = viewModel(eats = listOf(eat(f))) { nowMs }

        vm.uiState.test {
            skipItems(1) // initial default
            assertEquals("12:00:00", awaitItem().elapsedText)

            nowMs += TimeUnit.SECONDS.toMillis(1)
            advanceTimeBy(TimeUnit.SECONDS.toMillis(1))

            assertEquals("12:00:01", awaitItem().elapsedText)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
```

- [ ] **Step 2: Run the test to verify it fails to compile**

Run: `./gradlew testDebugUnitTest --tests "com.crazystudio.sportrecorder.ui.diet.DietViewModelTest"`
Expected: FAIL — compilation error, `DietViewModel` has no constructor taking `(ObserveDietStateUseCase, () -> Long)`.

- [ ] **Step 3: Add the clock seam to DietViewModel**

In `app/src/main/java/com/crazystudio/sportrecorder/ui/diet/DietViewModel.kt`:

Change the class declaration and constructor (lines 24–27) from:

```kotlin
@HiltViewModel
class DietViewModel @Inject constructor(
    observeDietState: ObserveDietStateUseCase,
) : ViewModel() {
```

to:

```kotlin
@HiltViewModel
class DietViewModel(
    observeDietState: ObserveDietStateUseCase,
    private val now: () -> Long,
) : ViewModel() {

    @Inject
    constructor(observeDietState: ObserveDietStateUseCase) :
        this(observeDietState, System::currentTimeMillis)
```

Then replace the two `System.currentTimeMillis()` calls with `now()`:

- In the `tickerFlow` body, change `emit(System.currentTimeMillis())` to `emit(now())`.
- In the `uiState` initializer, change `combine(observeDietState(System.currentTimeMillis()), tickerFlow)` to `combine(observeDietState(now()), tickerFlow)`.

(No imports change — `javax.inject.Inject` is already imported.)

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.crazystudio.sportrecorder.ui.diet.DietViewModelTest"`
Expected: PASS — all five tests green.

- [ ] **Step 5: Verify Hilt still compiles the app (the @Inject constructor is what Hilt uses)**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL — Hilt resolves `DietViewModel` via its `@Inject` secondary constructor.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/crazystudio/sportrecorder/ui/diet/DietViewModel.kt app/src/test/java/com/crazystudio/sportrecorder/ui/diet/DietViewModelTest.kt
git commit -m "[Test] DietViewModel: inject clock seam; phase/progress/elapsed + ticker tests"
```

---

## Task 6: Full verification gate

**Files:** none (verification only)

- [ ] **Step 1: Run the full unit-test + detekt gate exactly as CI does**

Run: `./gradlew testDebugUnitTest :app:detekt`
Expected: BUILD SUCCESSFUL. All existing + new tests pass; detekt reports no issues on the new test/production code.

- [ ] **Step 2: If detekt flags anything in the new files, fix it and re-run**

Typical fixes: remove any wildcard import, split a method over the `LongMethod` threshold, or satisfy `ReturnCount`. Re-run `./gradlew :app:detekt` until clean. (Do not add detekt suppressions unless a rule is genuinely inapplicable — match how existing tests pass the gate.)

- [ ] **Step 3: Final full build (mirrors the CI command)**

Run: `./gradlew assembleDebug testDebugUnitTest :app:detekt :app:lintDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit any fixes from Step 2 (skip if none)**

```bash
git add -A
git commit -m "[Test] Satisfy detekt on new ViewModel tests"
```

---

## Self-review checklist (completed during planning)

- **Spec coverage:** Turbine + MainDispatcherRule + shared fakes (Task 1); DietRecordViewModel (Task 2); CreateFastingTypeViewModel (Task 3); SelectFastingTypeViewModel (Task 4); DietViewModel clock seam + tests (Task 5); `./gradlew test` + detekt gate (Task 6). `EatTimeEditorViewModel` explicitly out of scope. CI already runs the gate → no workflow change. All spec sections mapped.
- **Type consistency:** `DietViewModel(observeDietState, now)` primary constructor used identically in Task 5 test and production. Fake method signatures match the interfaces (`observeInWindow(after, before)`, `save(record, newPhotoFileNames, removedPhotos): Int`, `setSelection(window)`, `exists`/`add`/`observeRecentCustomWindows`). Model constructors match (`EatRecord` 5 args, `DietSettings`/`FastingWindow` 2 `Long` args).
- **No placeholders:** every code and command step is concrete.
