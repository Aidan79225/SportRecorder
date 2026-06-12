# Clean Architecture — fasting-type vertical slice Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move the fasting-type slice onto the Clean Architecture boundary established by the eat-record slice — a `FastingWindow` domain value, a `FastingTypeRepository` (+ impl + mapper), a domain `DefaultFastingWindows` catalog, three use cases, and a `DietSettingsRepository.setSelection` write — then refactor `SelectFastingTypeViewModel` and `CreateFastingTypeViewModel` to depend on use cases instead of `FastingTypeDao` / `AppDatabase` / `DietPreference`.

**Architecture:** Package-based layering inside `:app`: new files under `domain/{model,diet,repository,usecase}` and `data/{mapper,repository}`; the existing `dao/FastingTypeDao`, `entity/FastingType`, `util/DietPreference` stay put as data sources. Hilt `@Binds` wires the new repository. The two ViewModels keep their **exact public method signatures**, so `AppRoot`, the two screens, and the `FastingItem` presentation model are untouched.

**Tech Stack:** Kotlin, Hilt, Room v6, Coroutines/Flow, Compose, JUnit4 + `kotlinx-coroutines-test`, detekt (no baseline) + Android Lint (baseline).

**Verification model:** Each phase builds green via `.\gradlew.bat assembleDebug :app:check`. Phase 3 adds an emulator smoke test. CI gate = `assembleDebug testDebugUnitTest :app:detekt :app:lintDebug`.

**Environment:** Build commands run from `C:\Users\Aidan\SportRecorder` in **PowerShell**. `java` is not on PATH — set `$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'` before each `.\gradlew.bat` call (generous timeout, ~600000 ms). `gh` authenticated as `Aidan79225`.

**Branch:** `refactor/clean-arch-fasting-type` (spec already committed here).

**detekt landmines (apply throughout):**
- `MagicNumber` is active in `domain/` and `data/` (only `**/ui/**` is excluded). Use named `const` for numeric literals except `-1,0,1,2`. The `DefaultFastingWindows` catalog is the one exception: it carries `@Suppress("MagicNumber")` because it's declarative reference data. `RECENT_CUSTOM_LIMIT = 10` is a named const.
- `ReturnCount` max 2 — `CreateCustomFastingTypeUseCase.invoke` has exactly 2 returns.
- `WildcardImport` active (only `java.util.*`) — list imports explicitly; remove now-unused imports (`NoUnusedImports` fails the build).
- Tests under `**/test/**` are excluded from MagicNumber/etc., so they can be terse.

---

## Phase 1 — Scaffold the fasting-type domain + data layer

### Task 1.1: `FastingWindow` domain model

**Files:**
- Create: `app/src/main/java/com/crazystudio/sportrecorder/domain/model/FastingWindow.kt`

- [ ] **Step 1: Create the model:**

```kotlin
package com.crazystudio.sportrecorder.domain.model

/** A fasting/eating window in hours. Reused for built-in defaults, custom types, and the selection. */
data class FastingWindow(val fastingHours: Long, val eatingHours: Long)
```

- [ ] **Step 2: Verify.**

Run: `$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'; .\gradlew.bat :app:detekt`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit** (trailer `Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>` on every commit):

```bash
git add app/src/main/java/com/crazystudio/sportrecorder/domain/model/FastingWindow.kt
git commit -m "[Refactor] Add FastingWindow domain model"
```

---

### Task 1.2: `DefaultFastingWindows` catalog — TDD via consistency test

The built-in default windows become a domain catalog (the single source of truth for the dedup rule). A consistency test locks it against the UI's existing `FastingItem.defaultFastingItems` so the two can never drift.

**Files:**
- Create: `app/src/main/java/com/crazystudio/sportrecorder/domain/diet/DefaultFastingWindows.kt`
- Create: `app/src/test/java/com/crazystudio/sportrecorder/domain/diet/DefaultFastingWindowsConsistencyTest.kt`

- [ ] **Step 1: Write the failing test** (asserts the UI defaults equal the domain catalog, in order):

```kotlin
package com.crazystudio.sportrecorder.domain.diet

import com.crazystudio.sportrecorder.domain.model.FastingWindow
import com.crazystudio.sportrecorder.ui.diet.select.FastingItem
import org.junit.Assert.assertEquals
import org.junit.Test

class DefaultFastingWindowsConsistencyTest {
    @Test fun uiDefaults_matchDomainCatalog_inOrder() {
        val fromUi = FastingItem.defaultFastingItems.map { FastingWindow(it.fastingHours, it.eatingHours) }
        assertEquals(DefaultFastingWindows.all, fromUi)
    }
}
```

- [ ] **Step 2: Run it to confirm it fails** (unresolved `DefaultFastingWindows`).

Run: `$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'; .\gradlew.bat testDebugUnitTest --tests "*DefaultFastingWindowsConsistencyTest"`
Expected: FAIL — `Unresolved reference: DefaultFastingWindows`.

- [ ] **Step 3: Create the catalog** with values matching `FastingItem.defaultFastingItems` (14:10, 16:8, 20:4, 23:1, 47:1, in that order):

```kotlin
package com.crazystudio.sportrecorder.domain.diet

import com.crazystudio.sportrecorder.domain.model.FastingWindow

/** Built-in fasting windows. Single source of truth for the dedup rule in CreateCustomFastingTypeUseCase. */
@Suppress("MagicNumber") // declarative reference data; naming each hour as a const adds no clarity
object DefaultFastingWindows {
    val all: List<FastingWindow> = listOf(
        FastingWindow(14, 10),
        FastingWindow(16, 8),
        FastingWindow(20, 4),
        FastingWindow(23, 1),
        FastingWindow(47, 1),
    )
}
```

- [ ] **Step 4: Run to confirm it passes.**

Run: `$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'; .\gradlew.bat testDebugUnitTest --tests "*DefaultFastingWindowsConsistencyTest"`
Expected: PASS.

- [ ] **Step 5: Verify detekt** (the `@Suppress` must satisfy MagicNumber).

Run: `$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'; .\gradlew.bat :app:detekt`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit.**

```bash
git add app/src/main/java/com/crazystudio/sportrecorder/domain/diet/DefaultFastingWindows.kt app/src/test/java/com/crazystudio/sportrecorder/domain/diet/DefaultFastingWindowsConsistencyTest.kt
git commit -m "[Refactor] Add DefaultFastingWindows catalog + consistency test"
```

---

### Task 1.3: `FastingType` → `FastingWindow` mapper — TDD

**Files:**
- Create: `app/src/main/java/com/crazystudio/sportrecorder/data/mapper/FastingTypeMappers.kt`
- Create: `app/src/test/java/com/crazystudio/sportrecorder/data/mapper/FastingTypeMappersTest.kt`

- [ ] **Step 1: Write the failing test:**

```kotlin
package com.crazystudio.sportrecorder.data.mapper

import com.crazystudio.sportrecorder.domain.model.FastingWindow
import com.crazystudio.sportrecorder.entity.FastingType
import org.junit.Assert.assertEquals
import org.junit.Test

class FastingTypeMappersTest {
    @Test fun toDomain_mapsHours() {
        val window = FastingType(id = 1, fastingHours = 18, eatingHours = 6, timestamp = 123L).toDomain()
        assertEquals(FastingWindow(18, 6), window)
    }
}
```

- [ ] **Step 2: Run to confirm it fails** (`Unresolved reference: toDomain`).

Run: `$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'; .\gradlew.bat testDebugUnitTest --tests "*FastingTypeMappersTest"`
Expected: FAIL.

- [ ] **Step 3: Implement the mapper:**

```kotlin
package com.crazystudio.sportrecorder.data.mapper

import com.crazystudio.sportrecorder.domain.model.FastingWindow
import com.crazystudio.sportrecorder.entity.FastingType

fun FastingType.toDomain(): FastingWindow = FastingWindow(fastingHours = fastingHours, eatingHours = eatingHours)
```

- [ ] **Step 4: Run to confirm it passes.**

Run: `$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'; .\gradlew.bat testDebugUnitTest --tests "*FastingTypeMappersTest"`
Expected: PASS.

- [ ] **Step 5: Commit.**

```bash
git add app/src/main/java/com/crazystudio/sportrecorder/data/mapper/FastingTypeMappers.kt app/src/test/java/com/crazystudio/sportrecorder/data/mapper/FastingTypeMappersTest.kt
git commit -m "[Refactor] Add FastingType->FastingWindow mapper with test"
```

---

### Task 1.4: `FastingTypeRepository` interface

**Files:**
- Create: `app/src/main/java/com/crazystudio/sportrecorder/domain/repository/FastingTypeRepository.kt`

- [ ] **Step 1: Create the interface:**

```kotlin
package com.crazystudio.sportrecorder.domain.repository

import com.crazystudio.sportrecorder.domain.model.FastingWindow
import kotlinx.coroutines.flow.Flow

interface FastingTypeRepository {
    /** Recently-created custom windows (capped), in stored (newest-first) order. */
    fun observeRecentCustomWindows(): Flow<List<FastingWindow>>

    /** True if a custom window with these exact hours already exists. */
    suspend fun exists(window: FastingWindow): Boolean

    /** Persist a new custom window (stamped with the current time). */
    suspend fun add(window: FastingWindow)
}
```

- [ ] **Step 2: Verify + commit.**

Run: `$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'; .\gradlew.bat :app:detekt`
Expected: `BUILD SUCCESSFUL`.

```bash
git add app/src/main/java/com/crazystudio/sportrecorder/domain/repository/FastingTypeRepository.kt
git commit -m "[Refactor] Add FastingTypeRepository interface"
```

---

### Task 1.5: `FastingTypeRepositoryImpl`

**Files:**
- Create: `app/src/main/java/com/crazystudio/sportrecorder/data/repository/FastingTypeRepositoryImpl.kt`

- [ ] **Step 1: Implement it** (logic relocated from the two ViewModels: `flowLast(10)`, `findByHours`, `insert`):

```kotlin
package com.crazystudio.sportrecorder.data.repository

import com.crazystudio.sportrecorder.dao.FastingTypeDao
import com.crazystudio.sportrecorder.data.mapper.toDomain
import com.crazystudio.sportrecorder.domain.model.FastingWindow
import com.crazystudio.sportrecorder.domain.repository.FastingTypeRepository
import com.crazystudio.sportrecorder.entity.FastingType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

private const val RECENT_CUSTOM_LIMIT = 10

class FastingTypeRepositoryImpl @Inject constructor(
    private val fastingTypeDao: FastingTypeDao,
) : FastingTypeRepository {

    override fun observeRecentCustomWindows(): Flow<List<FastingWindow>> =
        fastingTypeDao.flowLast(RECENT_CUSTOM_LIMIT).map { list -> list.map { it.toDomain() } }

    override suspend fun exists(window: FastingWindow): Boolean =
        fastingTypeDao.findByHours(window.fastingHours, window.eatingHours).isNotEmpty()

    override suspend fun add(window: FastingWindow) {
        fastingTypeDao.insert(
            FastingType(
                fastingHours = window.fastingHours,
                eatingHours = window.eatingHours,
                timestamp = System.currentTimeMillis(),
            ),
        )
    }
}
```

- [ ] **Step 2: Verify build + detekt + commit.**

Run: `$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'; .\gradlew.bat assembleDebug :app:detekt`
Expected: `BUILD SUCCESSFUL`.

```bash
git add app/src/main/java/com/crazystudio/sportrecorder/data/repository/FastingTypeRepositoryImpl.kt
git commit -m "[Refactor] FastingTypeRepositoryImpl over FastingTypeDao"
```

---

### Task 1.6: Fold the prefs write into `DietSettingsRepository`

Add the selection-write method to the existing interface + impl (the `saveSelection` logic relocated from `SelectFastingTypeViewModel`).

**Files:**
- Modify: `app/src/main/java/com/crazystudio/sportrecorder/domain/repository/DietSettingsRepository.kt`
- Modify: `app/src/main/java/com/crazystudio/sportrecorder/data/repository/DietSettingsRepositoryImpl.kt`

- [ ] **Step 1: Add `setSelection` to the interface.** The file currently is:

```kotlin
package com.crazystudio.sportrecorder.domain.repository

import com.crazystudio.sportrecorder.domain.model.DietSettings
import kotlinx.coroutines.flow.Flow

interface DietSettingsRepository {
    /** Emits current settings and re-emits on every change. */
    val settings: Flow<DietSettings>
}
```

Replace its body with (adds the import + the method):

```kotlin
package com.crazystudio.sportrecorder.domain.repository

import com.crazystudio.sportrecorder.domain.model.DietSettings
import com.crazystudio.sportrecorder.domain.model.FastingWindow
import kotlinx.coroutines.flow.Flow

interface DietSettingsRepository {
    /** Emits current settings and re-emits on every change. */
    val settings: Flow<DietSettings>

    /** Persist the selected fasting/eating window (triggers a [settings] re-emit). */
    suspend fun setSelection(window: FastingWindow)
}
```

- [ ] **Step 2: Implement `setSelection` in the impl.** In `DietSettingsRepositoryImpl.kt`, add the `FastingWindow` import (next to the existing `DietSettings` import) and add the method after the `settings` property (before the closing brace):

Add import:
```kotlin
import com.crazystudio.sportrecorder.domain.model.FastingWindow
```
Add method (inside the class, after the `settings` val):
```kotlin
    override suspend fun setSelection(window: FastingWindow) {
        dietPreference.preference.edit()
            .putLong(Constants.DIET_FASTING_TIME_INTERVAL, window.fastingHours)
            .putLong(Constants.DIET_EATING_TIME_INTERVAL, window.eatingHours)
            .apply()
    }
```

- [ ] **Step 3: Verify build + detekt.**

Run: `$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'; .\gradlew.bat assembleDebug :app:detekt`
Expected: `BUILD SUCCESSFUL`. (Uses the same plain `edit()...apply()` API the old VM used — any `UseKtx` lint note is a warning, not a build error.)

- [ ] **Step 4: Commit.**

```bash
git add app/src/main/java/com/crazystudio/sportrecorder/domain/repository/DietSettingsRepository.kt app/src/main/java/com/crazystudio/sportrecorder/data/repository/DietSettingsRepositoryImpl.kt
git commit -m "[Refactor] DietSettingsRepository.setSelection (folded prefs write)"
```

---

### Task 1.7: Bind `FastingTypeRepository` + Phase-1 gate

**Files:**
- Modify: `app/src/main/java/com/crazystudio/sportrecorder/dagger/RepositoryModule.kt`

- [ ] **Step 1: Add the `@Binds`.** In `RepositoryModule`, add the two imports and the binding method. After the existing `bindDietSettingsRepository` method, add:

```kotlin
    @Binds
    @Singleton
    abstract fun bindFastingTypeRepository(impl: FastingTypeRepositoryImpl): FastingTypeRepository
```
And add the imports near the others:
```kotlin
import com.crazystudio.sportrecorder.data.repository.FastingTypeRepositoryImpl
import com.crazystudio.sportrecorder.domain.repository.FastingTypeRepository
```

- [ ] **Step 2: Verify Hilt graph compiles.**

Run: `$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'; .\gradlew.bat assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Phase-1 gate — full check.**

Run: `$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'; .\gradlew.bat assembleDebug :app:check`
Expected: `BUILD SUCCESSFUL` — detekt, lint, and all unit tests (including the 2 new tests + the eat-record-slice tests) green.

- [ ] **Step 4: Commit.**

```bash
git add app/src/main/java/com/crazystudio/sportrecorder/dagger/RepositoryModule.kt
git commit -m "[Refactor] RepositoryModule binds FastingTypeRepository"
```

---

## Phase 2 — Use cases

### Task 2.1: Observe + save-selection use cases

**Files:**
- Create: `app/src/main/java/com/crazystudio/sportrecorder/domain/usecase/ObserveCustomFastingTypesUseCase.kt`
- Create: `app/src/main/java/com/crazystudio/sportrecorder/domain/usecase/SaveFastingSelectionUseCase.kt`

- [ ] **Step 1: `ObserveCustomFastingTypesUseCase.kt`:**

```kotlin
package com.crazystudio.sportrecorder.domain.usecase

import com.crazystudio.sportrecorder.domain.model.FastingWindow
import com.crazystudio.sportrecorder.domain.repository.FastingTypeRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveCustomFastingTypesUseCase @Inject constructor(
    private val repository: FastingTypeRepository,
) {
    operator fun invoke(): Flow<List<FastingWindow>> = repository.observeRecentCustomWindows()
}
```

- [ ] **Step 2: `SaveFastingSelectionUseCase.kt`:**

```kotlin
package com.crazystudio.sportrecorder.domain.usecase

import com.crazystudio.sportrecorder.domain.model.FastingWindow
import com.crazystudio.sportrecorder.domain.repository.DietSettingsRepository
import javax.inject.Inject

class SaveFastingSelectionUseCase @Inject constructor(
    private val dietSettingsRepository: DietSettingsRepository,
) {
    suspend operator fun invoke(window: FastingWindow) = dietSettingsRepository.setSelection(window)
}
```

- [ ] **Step 3: Verify + commit.**

Run: `$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'; .\gradlew.bat :app:detekt assembleDebug`
Expected: `BUILD SUCCESSFUL`.

```bash
git add app/src/main/java/com/crazystudio/sportrecorder/domain/usecase/ObserveCustomFastingTypesUseCase.kt app/src/main/java/com/crazystudio/sportrecorder/domain/usecase/SaveFastingSelectionUseCase.kt
git commit -m "[Refactor] Add observe + save-selection fasting use cases"
```

---

### Task 2.2: `CreateCustomFastingTypeUseCase` — TDD (holds the dedup business rule)

**Files:**
- Create: `app/src/main/java/com/crazystudio/sportrecorder/domain/usecase/CreateCustomFastingTypeUseCase.kt`
- Create: `app/src/test/java/com/crazystudio/sportrecorder/domain/usecase/CreateCustomFastingTypeUseCaseTest.kt`

- [ ] **Step 1: Write the failing test** (hand-written fake repo; `16:8` is a built-in default, `18:6` is not):

```kotlin
package com.crazystudio.sportrecorder.domain.usecase

import com.crazystudio.sportrecorder.domain.model.FastingWindow
import com.crazystudio.sportrecorder.domain.repository.FastingTypeRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeFastingTypeRepository(
    private val existing: Set<FastingWindow> = emptySet(),
) : FastingTypeRepository {
    val added = mutableListOf<FastingWindow>()
    override fun observeRecentCustomWindows(): Flow<List<FastingWindow>> = TODO()
    override suspend fun exists(window: FastingWindow): Boolean = window in existing
    override suspend fun add(window: FastingWindow) {
        added.add(window)
    }
}

class CreateCustomFastingTypeUseCaseTest {
    @Test fun builtInDefault_isRejected() = runTest {
        val repo = FakeFastingTypeRepository()
        val ok = CreateCustomFastingTypeUseCase(repo)(FastingWindow(16, 8))
        assertFalse(ok)
        assertTrue(repo.added.isEmpty())
    }

    @Test fun existingCustom_isRejected() = runTest {
        val repo = FakeFastingTypeRepository(existing = setOf(FastingWindow(18, 6)))
        val ok = CreateCustomFastingTypeUseCase(repo)(FastingWindow(18, 6))
        assertFalse(ok)
        assertTrue(repo.added.isEmpty())
    }

    @Test fun newWindow_isAdded() = runTest {
        val repo = FakeFastingTypeRepository()
        val ok = CreateCustomFastingTypeUseCase(repo)(FastingWindow(18, 6))
        assertTrue(ok)
        assertEquals(listOf(FastingWindow(18, 6)), repo.added)
    }
}
```

- [ ] **Step 2: Run to confirm it fails** (`Unresolved reference: CreateCustomFastingTypeUseCase`).

Run: `$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'; .\gradlew.bat testDebugUnitTest --tests "*CreateCustomFastingTypeUseCaseTest"`
Expected: FAIL.

- [ ] **Step 3: Implement it** (exactly 2 returns for `ReturnCount`):

```kotlin
package com.crazystudio.sportrecorder.domain.usecase

import com.crazystudio.sportrecorder.domain.diet.DefaultFastingWindows
import com.crazystudio.sportrecorder.domain.model.FastingWindow
import com.crazystudio.sportrecorder.domain.repository.FastingTypeRepository
import javax.inject.Inject

class CreateCustomFastingTypeUseCase @Inject constructor(
    private val repository: FastingTypeRepository,
) {
    /** Adds a custom window unless it duplicates a built-in default or an existing custom one. */
    suspend operator fun invoke(window: FastingWindow): Boolean {
        if (window in DefaultFastingWindows.all || repository.exists(window)) return false
        repository.add(window)
        return true
    }
}
```

- [ ] **Step 4: Run to confirm it passes.**

Run: `$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'; .\gradlew.bat testDebugUnitTest --tests "*CreateCustomFastingTypeUseCaseTest"`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit.**

```bash
git add app/src/main/java/com/crazystudio/sportrecorder/domain/usecase/CreateCustomFastingTypeUseCase.kt app/src/test/java/com/crazystudio/sportrecorder/domain/usecase/CreateCustomFastingTypeUseCaseTest.kt
git commit -m "[Refactor] CreateCustomFastingTypeUseCase with dedup rule + tests"
```

- [ ] **Step 6: Phase-2 gate.**

Run: `$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'; .\gradlew.bat assembleDebug :app:check`
Expected: `BUILD SUCCESSFUL`.

---

## Phase 3 — Refactor the ViewModels

The public method signatures stay identical, so `AppRoot` and the screens need no changes.

### Task 3.1: `SelectFastingTypeViewModel`

**Files:**
- Modify (full rewrite): `app/src/main/java/com/crazystudio/sportrecorder/ui/diet/select/SelectFastingTypeViewModel.kt`

- [ ] **Step 1: Rewrite the ViewModel** to depend on use cases (drops `AppDatabase` + `DietPreference`):

```kotlin
package com.crazystudio.sportrecorder.ui.diet.select

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.crazystudio.sportrecorder.domain.model.FastingWindow
import com.crazystudio.sportrecorder.domain.usecase.ObserveCustomFastingTypesUseCase
import com.crazystudio.sportrecorder.domain.usecase.SaveFastingSelectionUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SelectFastingTypeViewModel @Inject constructor(
    observeCustomFastingTypes: ObserveCustomFastingTypesUseCase,
    private val saveFastingSelection: SaveFastingSelectionUseCase,
) : ViewModel() {

    val fastingItemFlow: Flow<List<FastingItem>> = observeCustomFastingTypes().map { windows ->
        FastingItem.defaultFastingItems +
            windows.map { FastingItem.CustomFastingItem(it.fastingHours, it.eatingHours) }.reversed()
    }

    fun saveSelection(fastingHours: Long, eatingHours: Long) {
        viewModelScope.launch { saveFastingSelection(FastingWindow(fastingHours, eatingHours)) }
    }
}
```

- [ ] **Step 2: Verify build + detekt + lint.**

Run: `$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'; .\gradlew.bat assembleDebug :app:detekt :app:lintDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit.**

```bash
git add app/src/main/java/com/crazystudio/sportrecorder/ui/diet/select/SelectFastingTypeViewModel.kt
git commit -m "[Refactor] SelectFastingTypeViewModel on fasting use cases"
```

---

### Task 3.2: `CreateFastingTypeViewModel`

**Files:**
- Modify (full rewrite): `app/src/main/java/com/crazystudio/sportrecorder/ui/diet/create/fasting/CreateFastingTypeViewModel.kt`

- [ ] **Step 1: Rewrite the ViewModel** to delegate to the use case (drops `FastingTypeDao` + the inline dedup logic + `Dispatchers.Default`). The injected use case is named `createCustomType` to avoid clashing with the public `createCustomFastingType` method:

```kotlin
package com.crazystudio.sportrecorder.ui.diet.create.fasting

import androidx.lifecycle.ViewModel
import com.crazystudio.sportrecorder.domain.model.FastingWindow
import com.crazystudio.sportrecorder.domain.usecase.CreateCustomFastingTypeUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class CreateFastingTypeViewModel @Inject constructor(
    private val createCustomType: CreateCustomFastingTypeUseCase,
) : ViewModel() {

    suspend fun createCustomFastingType(fastingHours: Long, eatingHours: Long): Boolean =
        createCustomType(FastingWindow(fastingHours, eatingHours))
}
```

- [ ] **Step 2: Verify build + detekt + lint.**

Run: `$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'; .\gradlew.bat assembleDebug :app:detekt :app:lintDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit.**

```bash
git add app/src/main/java/com/crazystudio/sportrecorder/ui/diet/create/fasting/CreateFastingTypeViewModel.kt
git commit -m "[Refactor] CreateFastingTypeViewModel on CreateCustomFastingTypeUseCase"
```

---

### Task 3.3: Phase-3 gate — full check + emulator smoke test

**Files:** none (verification only).

- [ ] **Step 1: Confirm `FastingTypeDao` is now repository-only.**

Use the Grep tool: pattern `FastingTypeDao`, path `app/src/main`. The only references must be in `dao/FastingTypeDao.kt` (definition), `database/AppDatabase.kt`, `dagger/DatabaseModule.kt` (provider), and `data/repository/FastingTypeRepositoryImpl.kt`. No ViewModel may reference it.
Also Grep `AppDatabase|DietPreference` under `ui/diet/select` and `ui/diet/create` — expected: zero matches.

- [ ] **Step 2: Full gate (mirrors CI).**

Run: `$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'; .\gradlew.bat assembleDebug testDebugUnitTest :app:detekt :app:lintDebug`
Expected: `BUILD SUCCESSFUL` — all unit tests pass (the 3 new fasting tests + eat-record-slice tests + DietWindow).

- [ ] **Step 3: Emulator smoke test** (if an emulator/device is connected; boot `Pixel_10_Pro` otherwise). Install and verify behavior parity:

Run: `$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'; .\gradlew.bat installDebug`
Then verify, comparing against pre-refactor behavior:
  - **Home → tap the fasting-type chip** (e.g. `16 : 8`) → the **Select** sheet opens showing the 5 built-in items plus any custom items.
  - **Tap a different built-in window** (e.g. `20 : 4`) → sheet dismisses, Home's chip + ring update to the new window (proves `SaveFastingSelectionUseCase` → `setSelection` → `settings` re-emit).
  - **Open Select → tap `+`** → the **Create** sheet; enter a brand-new window (e.g. `18 : 6`) → confirm → it appears in the Select list (proves `CreateCustomFastingTypeUseCase` → `add`).
  - **Create the same window again** (e.g. `18 : 6`, or a built-in like `16 : 8`) → it is rejected / not duplicated (dedup rule).

- [ ] **Step 4: Final commit** (only if a smoke-test fix was needed; otherwise skip). Ensure `git status` is clean.

---

## Self-Review (author check vs. spec)

- **Spec §1 domain** → Task 1.1 (`FastingWindow`), Task 1.2 (`DefaultFastingWindows` + `@Suppress("MagicNumber")`), Task 1.4 (`FastingTypeRepository`), Task 1.6 (`DietSettingsRepository.setSelection`). ✔
- **Spec §2 data** → Task 1.3 (`FastingTypeMappers`), Task 1.5 (`FastingTypeRepositoryImpl` + `RECENT_CUSTOM_LIMIT`), Task 1.6 (`DietSettingsRepositoryImpl.setSelection`), Task 1.7 (`RepositoryModule` bind). ✔
- **Spec §3 use cases** → Task 2.1 (`ObserveCustomFastingTypesUseCase`, `SaveFastingSelectionUseCase`), Task 2.2 (`CreateCustomFastingTypeUseCase`, 2 returns). ✔
- **Spec §4 ViewModels** → Task 3.1 (`SelectFastingTypeViewModel`, same `fastingItemFlow`/`saveSelection` signatures), Task 3.2 (`CreateFastingTypeViewModel`, same `createCustomFastingType` signature); Task 3.3 Step 1 verifies `FastingTypeDao` is repository-only and the screens/`AppRoot` are unchanged. ✔
- **Spec §5 testing** → `FastingTypeMappersTest` (1.3), `CreateCustomFastingTypeUseCaseTest` (2.2), `DefaultFastingWindowsConsistencyTest` (1.2). ✔
- **Spec §6 phasing** → Phase 1 scaffold, Phase 2 use cases, Phase 3 VM refactor; each ends green. ✔
- **Placeholder scan:** every code step has complete code; every run step has an exact command + expected result. ✔
- **Type consistency:** `FastingWindow(fastingHours, eatingHours)` identical across model, mapper, repo, use cases, VMs, tests. `observeRecentCustomWindows`/`exists`/`add` identical across interface (1.4), impl (1.5), use case (2.1/2.2), and fake (2.2). `setSelection(window)` identical across interface (1.6), impl (1.6), use case (2.1), VM (3.1). The injected `createCustomType` name in 3.2 deliberately differs from the public `createCustomFastingType` method to avoid resolution ambiguity. ✔
- **detekt landmines:** `@Suppress("MagicNumber")` on the catalog; `RECENT_CUSTOM_LIMIT` const; `CreateCustomFastingTypeUseCase` 2 returns; no wildcard imports. ✔
