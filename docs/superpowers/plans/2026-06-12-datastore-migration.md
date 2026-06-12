# DietPreference → DataStore Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Swap the SharedPreferences backend of `DietSettingsRepositoryImpl` for Jetpack Preferences DataStore, preserving existing users' selection via `SharedPreferencesMigration`, without changing the `DietSettingsRepository` interface, the ViewModels, or any UI.

**Architecture:** Add `datastore-preferences`; a new `DataStoreModule` provides a singleton `DataStore<Preferences>` wired with a `SharedPreferencesMigration("diet_preference")`; `DietSettingsRepositoryImpl` is rewritten to read `dataStore.data` and write via `dataStore.edit {}`; the old `DietPreference` wrapper and its provider are deleted. Same keys (`Constants.DIET_*`) and defaults (16/8) → behavior preserved.

**Tech Stack:** Kotlin, Hilt, Coroutines/Flow, androidx.datastore:datastore-preferences 1.2.1, JUnit4 + `kotlinx-coroutines-test`, detekt (no baseline) + Android Lint (baseline).

**Verification model:** Phase 1 builds green via `.\gradlew.bat assembleDebug :app:check` (incl. a JVM round-trip test). Phase 2 is an emulator over-install check proving the migration preserves an existing selection. CI gate = `assembleDebug testDebugUnitTest :app:detekt :app:lintDebug`.

**Environment:** Build commands run from `C:\Users\Aidan\SportRecorder` in **PowerShell** — set `$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'` before each `.\gradlew.bat` call (generous timeout, ~600000 ms). `gh` authenticated as `Aidan79225`.

**Branch:** `refactor/datastore-migration` (spec already committed here).

**detekt landmines:** `MagicNumber` active in `data/` — the `16L`/`8L` defaults are named consts (`DEFAULT_FASTING_HOURS`/`DEFAULT_EATING_HOURS`), keep them. No wildcard imports (only `java.util.*`); remove now-unused imports (`NoUnusedImports` fails the build).

**Build-green ordering (important):** The DI graph must stay satisfiable after every commit. Order: add dep → add `DataStoreModule` **while keeping** the old `DietPreference` provider (DataStore provided-but-unused, still green) → rewrite the repo to inject `DataStore` (now `DietPreference` provider is unused-but-present, still green) → only then remove the old provider + delete the wrapper. Never leave the repo injecting a type nothing provides.

---

## Phase 1 — Swap the backend

### Task 1.1: Add the `datastore-preferences` dependency

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`

- [ ] **Step 1: Add the version + library to the catalog.** In `gradle/libs.versions.toml`, under `[versions]` add (e.g. after the `coil` line):

```toml
datastore = "1.2.1"
```
Under `[libraries]` add (e.g. after the `coil-compose` line):

```toml
androidx-datastore-preferences = { group = "androidx.datastore", name = "datastore-preferences", version.ref = "datastore" }
```

- [ ] **Step 2: Wire it as an implementation dependency.** In `app/build.gradle.kts`, in the `dependencies { }` block next to `implementation(libs.coil.compose)`, add:

```kotlin
    implementation(libs.androidx.datastore.preferences)
```

- [ ] **Step 3: Verify it resolves.**

Run: `$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'; .\gradlew.bat assembleDebug`
Expected: `BUILD SUCCESSFUL` (dependency downloads; nothing uses it yet).

- [ ] **Step 4: Commit** (trailer `Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>` on every commit):

```bash
git add gradle/libs.versions.toml app/build.gradle.kts
git commit -m "[Build] Add androidx.datastore:datastore-preferences"
```

---

### Task 1.2: Add `DataStoreModule` (keep the old provider for now)

**Files:**
- Create: `app/src/main/java/com/crazystudio/sportrecorder/dagger/DataStoreModule.kt`

- [ ] **Step 1: Create the module** providing a singleton `DataStore<Preferences>` with the SharedPreferences migration:

```kotlin
package com.crazystudio.sportrecorder.dagger

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataStoreModule {
    private const val DIET_PREFERENCES_NAME = "diet_preference"

    @Provides
    @Singleton
    fun provideDietDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            migrations = listOf(SharedPreferencesMigration(context, DIET_PREFERENCES_NAME)),
            produceFile = { context.preferencesDataStoreFile(DIET_PREFERENCES_NAME) },
        )
}
```

Notes for the engineer:
- `SharedPreferencesMigration` here is the Preferences-specific factory in package `androidx.datastore.preferences` (returns a `DataMigration<Preferences>` that copies SharedPreferences entries into Preferences keys of the same name/type). Do NOT import the generic `androidx.datastore.migrations.SharedPreferencesMigration`.
- The old `DietPreference` provider in `DatabaseModule` is intentionally still present at this step — the build stays green.

- [ ] **Step 2: Verify build (Hilt graph still valid).**

Run: `$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'; .\gradlew.bat assembleDebug :app:detekt`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit.**

```bash
git add app/src/main/java/com/crazystudio/sportrecorder/dagger/DataStoreModule.kt
git commit -m "[Refactor] Add DataStoreModule (Preferences DataStore + SP migration)"
```

---

### Task 1.3: Rewrite `DietSettingsRepositoryImpl` onto DataStore — TDD

**Files:**
- Modify (full rewrite): `app/src/main/java/com/crazystudio/sportrecorder/data/repository/DietSettingsRepositoryImpl.kt`
- Create: `app/src/test/java/com/crazystudio/sportrecorder/data/repository/DietSettingsRepositoryTest.kt`

- [ ] **Step 1: Write the failing test** — a real temp-file Preferences DataStore (no migration), asserting defaults-when-empty and a write→read round-trip. This will FAIL TO COMPILE first because the repo still takes `DietPreference`, not `DataStore<Preferences>` (that compile failure is the "red"):

```kotlin
package com.crazystudio.sportrecorder.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.crazystudio.sportrecorder.domain.model.DietSettings
import com.crazystudio.sportrecorder.domain.model.FastingWindow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class DietSettingsRepositoryTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun emptyStore_returnsDefaults() = runTest {
        val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
            scope = backgroundScope,
            produceFile = { File(tmp.root, "diet.preferences_pb") },
        )
        val repo = DietSettingsRepositoryImpl(dataStore)
        assertEquals(DietSettings(fastingHours = 16, eatingHours = 8), repo.settings.first())
    }

    @Test
    fun setSelection_roundTrips() = runTest {
        val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
            scope = backgroundScope,
            produceFile = { File(tmp.root, "diet.preferences_pb") },
        )
        val repo = DietSettingsRepositoryImpl(dataStore)
        repo.setSelection(FastingWindow(fastingHours = 20, eatingHours = 4))
        assertEquals(DietSettings(fastingHours = 20, eatingHours = 4), repo.settings.first())
    }
}
```

(`backgroundScope` is a `TestScope` receiver member inside `runTest { }` — no import needed. Each `@get:Rule TemporaryFolder` is fresh per test, so `tmp.root` is a clean dir per test.)

- [ ] **Step 2: Run to confirm it fails.**

Run: `$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'; .\gradlew.bat testDebugUnitTest --tests "*DietSettingsRepositoryTest"`
Expected: FAIL — compilation error: `DietSettingsRepositoryImpl` constructor expects `DietPreference`, got `DataStore<Preferences>`.

- [ ] **Step 3: Rewrite `DietSettingsRepositoryImpl`** to inject `DataStore<Preferences>`:

```kotlin
package com.crazystudio.sportrecorder.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import com.crazystudio.sportrecorder.domain.model.DietSettings
import com.crazystudio.sportrecorder.domain.model.FastingWindow
import com.crazystudio.sportrecorder.domain.repository.DietSettingsRepository
import com.crazystudio.sportrecorder.util.Constants
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject

private const val DEFAULT_FASTING_HOURS = 16L
private const val DEFAULT_EATING_HOURS = 8L

// Key names MUST match the legacy SharedPreferences keys so SharedPreferencesMigration carries values over.
private val FASTING_KEY = longPreferencesKey(Constants.DIET_FASTING_TIME_INTERVAL)
private val EATING_KEY = longPreferencesKey(Constants.DIET_EATING_TIME_INTERVAL)

class DietSettingsRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : DietSettingsRepository {

    override val settings: Flow<DietSettings> = dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { prefs ->
            DietSettings(
                fastingHours = prefs[FASTING_KEY] ?: DEFAULT_FASTING_HOURS,
                eatingHours = prefs[EATING_KEY] ?: DEFAULT_EATING_HOURS,
            )
        }
        .distinctUntilChanged()

    override suspend fun setSelection(window: FastingWindow) {
        dataStore.edit { prefs ->
            prefs[FASTING_KEY] = window.fastingHours
            prefs[EATING_KEY] = window.eatingHours
        }
    }
}
```

- [ ] **Step 4: Run to confirm the test passes.**

Run: `$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'; .\gradlew.bat testDebugUnitTest --tests "*DietSettingsRepositoryTest"`
Expected: PASS (2 tests).

**Fallback (ONLY if Step 4 fails to RUN — not assert — because the DataStore artifact needs an Android runtime headless):** replace the round-trip test with a pure-mapper test. Add a top-level mapper to `DietSettingsRepositoryImpl.kt`:

```kotlin
internal fun Preferences.toDietSettings(): DietSettings = DietSettings(
    fastingHours = this[FASTING_KEY] ?: DEFAULT_FASTING_HOURS,
    eatingHours = this[EATING_KEY] ?: DEFAULT_EATING_HOURS,
)
```
have `settings`' `.map { it.toDietSettings() }` use it, and test it without a DataStore:

```kotlin
package com.crazystudio.sportrecorder.data.repository

import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.preferencesOf
import com.crazystudio.sportrecorder.domain.model.DietSettings
import com.crazystudio.sportrecorder.util.Constants
import org.junit.Assert.assertEquals
import org.junit.Test

class DietSettingsRepositoryTest {
    @Test fun emptyPrefs_returnDefaults() {
        assertEquals(DietSettings(16, 8), emptyPreferences().toDietSettings())
    }

    @Test fun storedPrefs_mapToSettings() {
        val prefs = preferencesOf(
            longPreferencesKey(Constants.DIET_FASTING_TIME_INTERVAL) to 20L,
            longPreferencesKey(Constants.DIET_EATING_TIME_INTERVAL) to 4L,
        )
        assertEquals(DietSettings(20, 4), prefs.toDietSettings())
    }
}
```
`preferencesOf`/`emptyPreferences` are pure (no Context/file), so this always runs on the JVM. Prefer the round-trip test; use this only if the round-trip can't run.

- [ ] **Step 5: Verify detekt** (the rewritten file must pass — named consts, no wildcard imports).

Run: `$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'; .\gradlew.bat :app:detekt`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit.**

```bash
git add app/src/main/java/com/crazystudio/sportrecorder/data/repository/DietSettingsRepositoryImpl.kt app/src/test/java/com/crazystudio/sportrecorder/data/repository/DietSettingsRepositoryTest.kt
git commit -m "[Refactor] DietSettingsRepositoryImpl on Preferences DataStore + test"
```

---

### Task 1.4: Remove the old `DietPreference` provider + delete the wrapper

**Files:**
- Modify: `app/src/main/java/com/crazystudio/sportrecorder/dagger/DatabaseModule.kt`
- Delete: `app/src/main/java/com/crazystudio/sportrecorder/util/DietPreference.kt`

- [ ] **Step 1: Remove the provider from `DatabaseModule`.** Delete the `provideDietPreference` function (the `@Provides @Singleton fun provideDietPreference(...) { return DietPreference(...) }` block) and the now-unused import `import com.crazystudio.sportrecorder.util.DietPreference`. Leave the Room providers untouched.

- [ ] **Step 2: Delete the wrapper class.**

```bash
git rm app/src/main/java/com/crazystudio/sportrecorder/util/DietPreference.kt
```

- [ ] **Step 3: Confirm no references remain.** Use the Grep tool: pattern `DietPreference|getSharedPreferences`, path `app/src`. Expected: ZERO matches (the repo, the module, and the wrapper are all gone). If any remain, fix before continuing.

- [ ] **Step 4: Phase-1 gate — full check.**

Run: `$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'; .\gradlew.bat assembleDebug :app:check`
Expected: `BUILD SUCCESSFUL` — detekt, lint, and all unit tests (incl. the new `DietSettingsRepositoryTest` + prior-slice tests) green.

- [ ] **Step 5: Commit.**

```bash
git add app/src/main/java/com/crazystudio/sportrecorder/dagger/DatabaseModule.kt
git commit -m "[Refactor] Remove DietPreference (SharedPreferences) wrapper + provider"
```

---

## Phase 2 — Migration verification (emulator over-install)

**Files:** none (verification only).

This proves `SharedPreferencesMigration` carries an existing selection from the old SharedPreferences into DataStore on update. It MUST be an over-install (no uninstall) so the legacy `.xml` is present.

- [ ] **Step 1: Seed a non-default selection on the CURRENT master (SharedPreferences) build.**
  - With an emulator running (`Pixel_10_Pro`), check out master and install it: from `master`, `$env:JAVA_HOME='…jbr'; .\gradlew.bat installDebug`.
  - Launch the app, open the fasting-type Select sheet (tap the `16 : 8` chip on Home), and pick a **non-default** window — e.g. **Expert 20 : 4**. Confirm Home shows `20 : 4`.
  - Return to this branch: `git checkout refactor/datastore-migration`.

- [ ] **Step 2: Over-install the DataStore build (do NOT uninstall).**

Run: `$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'; .\gradlew.bat installDebug`
(`installDebug` updates the existing app in place, preserving its files — including the legacy `diet_preference.xml`.)

- [ ] **Step 3: Launch and confirm the selection survived.** Launch the app. Home must show **`20 : 4`** (migrated from SharedPreferences), NOT the `16 : 8` default. Watch logcat for crashes:
  - `adb logcat -d -t 300 | Select-String -Pattern "FATAL|crazystudio.*Exception"` → expect none.
  If Home shows `16 : 8`, the migration failed — check that the DataStore key names exactly equal the legacy SharedPreferences keys (`Constants.DIET_*`) and that the `SharedPreferencesMigration` name (`"diet_preference"`) matches the legacy file name.

- [ ] **Step 4 (optional sanity):** Fully uninstall + fresh install → Home shows the `16 : 8` default (no legacy file to migrate). Confirms defaults path. Then reinstall normally. No commit (verification only); ensure `git status` is clean.

---

## Self-Review (author check vs. spec)

- **Spec §1 dependency** → Task 1.1 (catalog + build.gradle, pinned 1.2.1). ✔
- **Spec §2 DataStoreModule + migration** → Task 1.2 (`DataStoreModule` with `SharedPreferencesMigration` + `preferencesDataStoreFile`, singleton); old provider removed in Task 1.4. ✔
- **Spec §3 repo rewrite** → Task 1.3 (`dataStore.data` + `.catch(IOException→emptyPreferences)` + `.map` + `.distinctUntilChanged()`; `dataStore.edit {}`; same `Constants` keys + 16/8 defaults). ✔
- **Spec §4 delete DietPreference** → Task 1.4 (delete wrapper + provider + grep clean). ✔
- **Spec §5 testing** → Task 1.3 JVM round-trip test (defaults + round-trip) with the pure-mapper fallback inline; Task 2 emulator over-install migration check. ✔
- **Spec §6 phasing** → Phase 1 swap (green `:app:check`), Phase 2 emulator verification. ✔
- **Placeholder scan:** complete code in every code step; exact commands + expected results. The DataStore version is pinned (1.2.1, confirmed via Google Maven). No TBDs. ✔
- **Type consistency:** `DataStore<Preferences>` is the injected type in the module provider (1.2), the repo constructor (1.3), and the test (1.3). `FASTING_KEY`/`EATING_KEY` (`longPreferencesKey(Constants.DIET_*)`) are identical between the repo and the fallback test. Defaults `16L`/`8L` consistent. `SharedPreferencesMigration` import is the `androidx.datastore.preferences` one (called out explicitly to avoid the generic-package mistake). ✔
- **Build-green ordering:** dep → module (old provider kept) → repo rewrite → remove old provider — each commit leaves the Hilt graph satisfiable. ✔
