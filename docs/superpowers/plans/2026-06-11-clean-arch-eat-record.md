# Clean Architecture — eat-record vertical slice Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Introduce a Clean Architecture domain/data boundary for the eat-time/diet slice — pure domain models, repository interfaces + impls, mappers, and use cases — and refactor `DietViewModel`, `EatTimeEditorViewModel`, `DietRecordViewModel` to depend on use cases instead of Room DAOs / `AppDatabase` / `DietPreference` / `Context`.

**Architecture:** Package-based layering inside the single `:app` module: `domain/{model,diet,repository,usecase}` (framework-free) and `data/{mapper,repository}`. Existing `entity/`, `dao/`, `database/`, `util/*` stay put as data sources the impls depend on. Hilt `@Binds` wires interfaces to impls; use cases are `@Inject constructor`. ViewModels/UiState/Compose stop importing `com.crazystudio.sportrecorder.entity.*`.

**Tech Stack:** Kotlin, Hilt, Room v6, Coroutines/Flow, Compose, JUnit4 + `kotlinx-coroutines-test`, detekt (no baseline) + Android Lint (baseline).

**Verification model:** Each phase builds green via `.\gradlew.bat assembleDebug :app:check`. Phase 3 adds an emulator smoke test of Home/Record/Editor. CI gate = `assembleDebug testDebugUnitTest :app:detekt :app:lintDebug`.

**Environment:** Build commands run from `C:\Users\Aidan\SportRecorder` in PowerShell. Per project memory: set `JAVA_HOME` to Android Studio's JBR, use `.\gradlew.bat`, AVD `Pixel_10_Pro`. `gh` authenticated as `Aidan79225`.

**Branch:** `refactor/clean-arch-eat-record` (spec already committed here).

**detekt landmines (apply throughout):**
- `MagicNumber` is active in `domain/` and `data/` (only `**/ui/**` is excluded). Any numeric literal except `-1,0,1,2` must be a named `const` (`ignoreConstantDeclaration: true`). Floats `0f`/`1f`/`1.0f` normalize to `0.0`/`1.0`/`1.0` and ARE ignored.
- `LongParameterList` triggers at 6 params (`functionThreshold: 6`); keep public functions ≤ 5 params (data-class params are ignored).
- `WildcardImport` is active (only `java.util.*` allowed) — list imports explicitly.
- `ReturnCount` max 2; `NestedBlockDepth` threshold 4.
- Tests under `**/test/**` are excluded from MagicNumber/TooGenericException/etc., so they can be terse.

---

## Phase 1 — Scaffold the domain + data layers

### Task 1.1: Add `kotlinx-coroutines-test` dependency

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`

- [ ] **Step 1: Add the library to the version catalog.** In `gradle/libs.versions.toml`, under `[libraries]` after the `kotlinx-coroutines-play-services` line (line 37), add:

```toml
kotlinx-coroutines-test = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-test", version.ref = "coroutines" }
```

- [ ] **Step 2: Wire it as a test dependency.** In `app/build.gradle.kts`, in the `dependencies { }` block next to `testImplementation(libs.junit)` (line 115), add:

```kotlin
    testImplementation(libs.kotlinx.coroutines.test)
```

- [ ] **Step 3: Verify it resolves.**

Run: `.\gradlew.bat help` (forces a config-time catalog/accessor refresh)
Expected: `BUILD SUCCESSFUL`. (No code uses it yet; this just proves the accessor exists.)

- [ ] **Step 4: Commit.**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts
git commit -m "[Build] Add kotlinx-coroutines-test for unit tests"
```
(Use trailer `Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>` on every commit in this plan.)

---

### Task 1.2: Domain models

**Files:**
- Create: `app/src/main/java/com/crazystudio/sportrecorder/domain/model/EatRecord.kt`
- Create: `app/src/main/java/com/crazystudio/sportrecorder/domain/model/DietSettings.kt`

- [ ] **Step 1: Create `EatRecord.kt`** (three related models in one file — `EatRecord` first, satisfying `MatchingDeclarationName`):

```kotlin
package com.crazystudio.sportrecorder.domain.model

/**
 * A single eating record. [id] == 0 means not-yet-persisted.
 * [time] is epoch millis. [photos] is empty when loaded without its relation.
 */
data class EatRecord(
    val id: Int,
    val time: Long,
    val location: GeoPoint?,
    val note: String?,
    val photos: List<EatPhoto>,
)

data class GeoPoint(val lat: Double, val lng: Double)

data class EatPhoto(val id: Int, val fileName: String, val createdAt: Long)
```

- [ ] **Step 2: Create `DietSettings.kt`:**

```kotlin
package com.crazystudio.sportrecorder.domain.model

/** User's intermittent-fasting window settings (hours). */
data class DietSettings(val fastingHours: Long, val eatingHours: Long)
```

- [ ] **Step 3: Verify compile + detekt.**

Run: `.\gradlew.bat :app:detekt`
Expected: `BUILD SUCCESSFUL`, no findings on the new files.

- [ ] **Step 4: Commit.**

```bash
git add app/src/main/java/com/crazystudio/sportrecorder/domain/model/
git commit -m "[Refactor] Add eat-record domain models"
```

---

### Task 1.3: Move `DietWindow` into the domain layer

**Files:**
- Move: `app/src/main/java/com/crazystudio/sportrecorder/ui/diet/DietWindow.kt` → `app/src/main/java/com/crazystudio/sportrecorder/domain/diet/DietWindow.kt`
- Move: `app/src/test/java/com/crazystudio/sportrecorder/ui/diet/DietWindowTest.kt` → `app/src/test/java/com/crazystudio/sportrecorder/domain/diet/DietWindowTest.kt`
- Modify: `app/src/main/java/com/crazystudio/sportrecorder/ui/diet/DietViewModel.kt` (import only)

- [ ] **Step 1: Move both files with git (preserves history):**

```bash
git mv app/src/main/java/com/crazystudio/sportrecorder/ui/diet/DietWindow.kt app/src/main/java/com/crazystudio/sportrecorder/domain/diet/DietWindow.kt
git mv app/src/test/java/com/crazystudio/sportrecorder/ui/diet/DietWindowTest.kt app/src/test/java/com/crazystudio/sportrecorder/domain/diet/DietWindowTest.kt
```

- [ ] **Step 2: Change the package line** in `domain/diet/DietWindow.kt` (line 1) from:

```kotlin
package com.crazystudio.sportrecorder.ui.diet
```
to:
```kotlin
package com.crazystudio.sportrecorder.domain.diet
```
(The body is unchanged. Its only literals are `0f`/`1f`/`0L`, which MagicNumber ignores — no edits needed.)

- [ ] **Step 3: Change the package line** in `domain/diet/DietWindowTest.kt` (line 1) the same way:

```kotlin
package com.crazystudio.sportrecorder.domain.diet
```
(`DietWindow`, `DietPhase` now resolve in-package; no import changes needed in the test.)

- [ ] **Step 4: Update the import in `DietViewModel.kt`.** It currently has no explicit `DietWindow` import (same package). Add one near the other imports (after the `R` import, line 6):

```kotlin
import com.crazystudio.sportrecorder.domain.diet.DietPhase
import com.crazystudio.sportrecorder.domain.diet.DietWindow
```

- [ ] **Step 5: Verify build + the moved test still passes.**

Run: `.\gradlew.bat testDebugUnitTest --tests "*DietWindowTest"`
Expected: `BUILD SUCCESSFUL`, 10 tests pass.

- [ ] **Step 6: Commit.**

```bash
git add -A
git commit -m "[Refactor] Move DietWindow + test into domain/diet"
```

---

### Task 1.4: Extract `DietHistoryCalculator` (pure) — TDD

The history aggregation currently buried in `DietViewModel` (`mergeIntervalWithFourHours`, `effectiveProgress`, `computeHistory`) moves to a pure, testable domain object. Test the two non-Calendar functions directly.

**Files:**
- Create: `app/src/main/java/com/crazystudio/sportrecorder/domain/diet/DietHistoryCalculator.kt`
- Create: `app/src/test/java/com/crazystudio/sportrecorder/domain/diet/DietHistoryCalculatorTest.kt`

- [ ] **Step 1: Write the failing test** at `app/src/test/.../domain/diet/DietHistoryCalculatorTest.kt`:

```kotlin
package com.crazystudio.sportrecorder.domain.diet

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.TimeUnit

class DietHistoryCalculatorTest {
    private fun h(n: Long) = TimeUnit.HOURS.toMillis(n)

    @Test fun mergeIntervals_empty_isEmpty() {
        assertEquals(emptyList<Pair<Long, Long>>(), DietHistoryCalculator.mergeIntervals(emptyList()))
    }

    @Test fun mergeIntervals_singleEat_hasFourHourFloor() {
        val result = DietHistoryCalculator.mergeIntervals(listOf(0L))
        assertEquals(listOf(0L to h(4)), result)
    }

    @Test fun mergeIntervals_closeEats_merge() {
        // eats 1h apart stay one interval; end extends to last eat, floored at 4h
        val result = DietHistoryCalculator.mergeIntervals(listOf(0L, h(1)))
        assertEquals(listOf(0L to h(4)), result)
    }

    @Test fun mergeIntervals_gapOverEightHours_splits() {
        val result = DietHistoryCalculator.mergeIntervals(listOf(0L, h(10)))
        assertEquals(listOf(0L to h(4), h(10) to h(14)), result)
    }

    @Test fun fastedRatio_noIntervals_fullyFasted() {
        assertEquals(1.0f, DietHistoryCalculator.fastedRatio(emptyList(), 0L, h(24)), 0.0001f)
    }

    @Test fun fastedRatio_partialEating() {
        // 4h eating inside a 24h day → fasted 20/24
        val ratio = DietHistoryCalculator.fastedRatio(listOf(0L to h(4)), 0L, h(24))
        assertEquals(20f / 24f, ratio, 0.0001f)
    }

    @Test fun fastedRatio_intervalOutsideDay_ignored() {
        val ratio = DietHistoryCalculator.fastedRatio(listOf(h(100) to h(104)), 0L, h(24))
        assertEquals(1.0f, ratio, 0.0001f)
    }
}
```

- [ ] **Step 2: Run it to confirm it fails (unresolved reference).**

Run: `.\gradlew.bat testDebugUnitTest --tests "*DietHistoryCalculatorTest"`
Expected: FAIL — compilation error `Unresolved reference: DietHistoryCalculator`.

- [ ] **Step 3: Implement `DietHistoryCalculator.kt`** (logic copied verbatim from `DietViewModel`, generalized to `List<Long>` + `now` injection; `8`/`4`/`5` are named consts for MagicNumber):

```kotlin
package com.crazystudio.sportrecorder.domain.diet

import java.util.Calendar
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.min

/** Pure (Android-free except java.util.Calendar) 5-day eating/fasting history aggregator. */
object DietHistoryCalculator {
    data class HistoryBar(val dateMillis: Long, val ratio: Float)

    private const val MERGE_GAP_HOURS = 8L
    private const val MIN_INTERVAL_HOURS = 4L
    private const val HISTORY_DAYS = 5

    /**
     * Merge ascending eat timestamps into eating intervals: a new interval starts when the gap
     * from the previous eat exceeds [MERGE_GAP_HOURS]; each interval is at least [MIN_INTERVAL_HOURS] long.
     */
    fun mergeIntervals(eatTimesAsc: List<Long>): List<Pair<Long, Long>> {
        if (eatTimesAsc.isEmpty()) return emptyList()
        val gap = TimeUnit.HOURS.toMillis(MERGE_GAP_HOURS)
        val floor = TimeUnit.HOURS.toMillis(MIN_INTERVAL_HOURS)
        val result = mutableListOf<Pair<Long, Long>>()
        var start = eatTimesAsc[0]
        var end = eatTimesAsc[0]
        eatTimesAsc.forEach { t ->
            if (end + gap > t) {
                end = t
            } else {
                result.add(start to max(end, start + floor))
                start = t
                end = t
            }
        }
        result.add(start to max(end, start + floor))
        return result
    }

    /** Fraction of [dayStart, dayEnd) spent fasting (1.0 == fully fasted). */
    fun fastedRatio(intervals: List<Pair<Long, Long>>, dayStart: Long, dayEnd: Long): Float {
        var eating = 0L
        intervals.forEach {
            val hi = min(dayEnd, it.second)
            val lo = max(dayStart, it.first)
            eating += max(0L, hi - lo)
        }
        return 1.0f - (eating / (dayEnd - dayStart).toFloat())
    }

    /** Five most-recent whole-day bars (oldest first), each a fasting ratio. */
    fun compute(eatTimesAsc: List<Long>, now: Long): List<HistoryBar> {
        val intervals = mergeIntervals(eatTimesAsc)
        return (0 until HISTORY_DAYS).map { i ->
            val dayStart = dayBoundary(now, -i - 1)
            val dayEnd = dayBoundary(now, -i)
            HistoryBar(dateMillis = dayStart, ratio = fastedRatio(intervals, dayStart, dayEnd))
        }.asReversed()
    }

    private fun dayBoundary(now: Long, dayOffset: Int): Long =
        Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.DAY_OF_YEAR, get(Calendar.DAY_OF_YEAR) + dayOffset)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
}
```

- [ ] **Step 4: Run the test to confirm it passes.**

Run: `.\gradlew.bat testDebugUnitTest --tests "*DietHistoryCalculatorTest"`
Expected: PASS (7 tests).

- [ ] **Step 5: Verify detekt is clean on the new files.**

Run: `.\gradlew.bat :app:detekt`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit.**

```bash
git add app/src/main/java/com/crazystudio/sportrecorder/domain/diet/DietHistoryCalculator.kt app/src/test/java/com/crazystudio/sportrecorder/domain/diet/DietHistoryCalculatorTest.kt
git commit -m "[Refactor] Extract DietHistoryCalculator (pure) with tests"
```

---

### Task 1.5: Repository interfaces

**Files:**
- Create: `app/src/main/java/com/crazystudio/sportrecorder/domain/repository/EatRecordRepository.kt`
- Create: `app/src/main/java/com/crazystudio/sportrecorder/domain/repository/DietSettingsRepository.kt`

- [ ] **Step 1: Create `EatRecordRepository.kt`:**

```kotlin
package com.crazystudio.sportrecorder.domain.repository

import com.crazystudio.sportrecorder.domain.model.EatPhoto
import com.crazystudio.sportrecorder.domain.model.EatRecord
import kotlinx.coroutines.flow.Flow

interface EatRecordRepository {
    /** All records, newest first, each with its photos. */
    fun observeAll(): Flow<List<EatRecord>>

    /** Records with `after < time < before`, ascending, WITHOUT photos (history/window use). */
    fun observeInWindow(after: Long, before: Long): Flow<List<EatRecord>>

    suspend fun findById(id: Int): EatRecord?

    /**
     * Insert (record.id == 0) or update (record.id > 0) the row and reconcile photos:
     * insert [newPhotoFileNames], delete [removedPhotos] (rows + files). Returns the record id.
     * The record's own `photos` field is ignored here.
     */
    suspend fun save(
        record: EatRecord,
        newPhotoFileNames: List<String>,
        removedPhotos: List<EatPhoto>,
    ): Int

    /** Delete the record, its photo rows, and its photo files. */
    suspend fun delete(recordId: Int)
}
```

- [ ] **Step 2: Create `DietSettingsRepository.kt`:**

```kotlin
package com.crazystudio.sportrecorder.domain.repository

import com.crazystudio.sportrecorder.domain.model.DietSettings
import kotlinx.coroutines.flow.Flow

interface DietSettingsRepository {
    /** Emits current settings and re-emits on every change. */
    val settings: Flow<DietSettings>
}
```

- [ ] **Step 3: Verify.**

Run: `.\gradlew.bat :app:detekt`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit.**

```bash
git add app/src/main/java/com/crazystudio/sportrecorder/domain/repository/
git commit -m "[Refactor] Add EatRecord + DietSettings repository interfaces"
```

---

### Task 1.6: Entity↔domain mappers — TDD

**Files:**
- Create: `app/src/main/java/com/crazystudio/sportrecorder/data/mapper/EatRecordMappers.kt`
- Create: `app/src/test/java/com/crazystudio/sportrecorder/data/mapper/EatRecordMappersTest.kt`

- [ ] **Step 1: Write the failing test:**

```kotlin
package com.crazystudio.sportrecorder.data.mapper

import com.crazystudio.sportrecorder.domain.model.EatPhoto
import com.crazystudio.sportrecorder.domain.model.GeoPoint
import com.crazystudio.sportrecorder.entity.EatTime
import com.crazystudio.sportrecorder.entity.EatTimeWithPhotos
import com.crazystudio.sportrecorder.entity.Photo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EatRecordMappersTest {
    @Test fun withPhotos_mapsAllFields() {
        val entity = EatTimeWithPhotos(
            eatTime = EatTime(id = 1, time = 100L, lat = 1.5, lng = 2.5, note = "hi"),
            photos = listOf(Photo(id = 2, eatTimeId = 1, fileName = "a.webp", createdAt = 50L)),
        )
        val record = entity.toDomain()
        assertEquals(1, record.id)
        assertEquals(100L, record.time)
        assertEquals(GeoPoint(1.5, 2.5), record.location)
        assertEquals("hi", record.note)
        assertEquals(listOf(EatPhoto(id = 2, fileName = "a.webp", createdAt = 50L)), record.photos)
    }

    @Test fun nullLatLng_mapsToNullLocation() {
        val record = EatTime(id = 3, time = 0L, lat = null, lng = null, note = null).toDomain()
        assertNull(record.location)
        assertEquals(emptyList<EatPhoto>(), record.photos)
    }
}
```

- [ ] **Step 2: Run to confirm it fails.**

Run: `.\gradlew.bat testDebugUnitTest --tests "*EatRecordMappersTest"`
Expected: FAIL — `Unresolved reference: toDomain`.

- [ ] **Step 3: Implement `EatRecordMappers.kt`:**

```kotlin
package com.crazystudio.sportrecorder.data.mapper

import com.crazystudio.sportrecorder.domain.model.EatPhoto
import com.crazystudio.sportrecorder.domain.model.EatRecord
import com.crazystudio.sportrecorder.domain.model.GeoPoint
import com.crazystudio.sportrecorder.entity.EatTime
import com.crazystudio.sportrecorder.entity.EatTimeWithPhotos
import com.crazystudio.sportrecorder.entity.Photo

fun Photo.toDomain(): EatPhoto = EatPhoto(id = id, fileName = fileName, createdAt = createdAt)

fun EatTime.toDomain(photos: List<EatPhoto> = emptyList()): EatRecord = EatRecord(
    id = id,
    time = time,
    location = if (lat != null && lng != null) GeoPoint(lat, lng) else null,
    note = note,
    photos = photos,
)

fun EatTimeWithPhotos.toDomain(): EatRecord = eatTime.toDomain(photos.map { it.toDomain() })
```

- [ ] **Step 4: Run to confirm it passes.**

Run: `.\gradlew.bat testDebugUnitTest --tests "*EatRecordMappersTest"`
Expected: PASS (2 tests).

- [ ] **Step 5: Commit.**

```bash
git add app/src/main/java/com/crazystudio/sportrecorder/data/mapper/EatRecordMappers.kt app/src/test/java/com/crazystudio/sportrecorder/data/mapper/EatRecordMappersTest.kt
git commit -m "[Refactor] Add entity<->domain mappers with tests"
```

---

### Task 1.7: Add `deleteById` to `EatTimeDao`

The repository deletes by id (it no longer has an `EatTime` entity in hand).

**Files:**
- Modify: `app/src/main/java/com/crazystudio/sportrecorder/dao/EatTimeDao.kt`

- [ ] **Step 1: Add a `deleteById` query.** After the existing `delete(eatTime: EatTime)` function (line 52), add:

```kotlin
    @Query("DELETE FROM ${EatTime.tableName} WHERE id = :id")
    suspend fun deleteById(id: Int)
```

- [ ] **Step 2: Verify Room codegen + build.**

Run: `.\gradlew.bat assembleDebug`
Expected: `BUILD SUCCESSFUL` (KSP regenerates the DAO impl).

- [ ] **Step 3: Commit.**

```bash
git add app/src/main/java/com/crazystudio/sportrecorder/dao/EatTimeDao.kt
git commit -m "[Refactor] EatTimeDao.deleteById for repository delete"
```

---

### Task 1.8: `EatRecordRepositoryImpl`

**Files:**
- Create: `app/src/main/java/com/crazystudio/sportrecorder/data/repository/EatRecordRepositoryImpl.kt`

- [ ] **Step 1: Implement it.** Logic relocated verbatim from `EatTimeEditorViewModel.save()` and `DietRecordViewModel.deleteEatTime()`:

```kotlin
package com.crazystudio.sportrecorder.data.repository

import android.content.Context
import androidx.room.withTransaction
import com.crazystudio.sportrecorder.dao.EatTimeDao
import com.crazystudio.sportrecorder.dao.PhotoDao
import com.crazystudio.sportrecorder.data.mapper.toDomain
import com.crazystudio.sportrecorder.database.AppDatabase
import com.crazystudio.sportrecorder.domain.model.EatPhoto
import com.crazystudio.sportrecorder.domain.model.EatRecord
import com.crazystudio.sportrecorder.domain.repository.EatRecordRepository
import com.crazystudio.sportrecorder.entity.EatTime
import com.crazystudio.sportrecorder.entity.Photo
import com.crazystudio.sportrecorder.util.PhotoStorage
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class EatRecordRepositoryImpl @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val appDatabase: AppDatabase,
    private val eatTimeDao: EatTimeDao,
    private val photoDao: PhotoDao,
) : EatRecordRepository {

    override fun observeAll(): Flow<List<EatRecord>> =
        eatTimeDao.flowAllWithPhotos().map { list -> list.map { it.toDomain() } }

    override fun observeInWindow(after: Long, before: Long): Flow<List<EatRecord>> =
        eatTimeDao.flowByTimeInterval(before = before, after = after)
            .map { list -> list.map { it.toDomain() } }

    override suspend fun findById(id: Int): EatRecord? =
        eatTimeDao.findWithPhotosById(id)?.toDomain()

    override suspend fun save(
        record: EatRecord,
        newPhotoFileNames: List<String>,
        removedPhotos: List<EatPhoto>,
    ): Int {
        val recordId = appDatabase.withTransaction {
            val id = if (record.id > 0) {
                eatTimeDao.update(
                    EatTime(
                        id = record.id,
                        time = record.time,
                        lat = record.location?.lat,
                        lng = record.location?.lng,
                        note = record.note,
                    ),
                )
                removedPhotos.forEach {
                    photoDao.delete(
                        Photo(id = it.id, eatTimeId = record.id, fileName = it.fileName, createdAt = it.createdAt),
                    )
                }
                record.id
            } else {
                eatTimeDao.insert(
                    EatTime(
                        time = record.time,
                        lat = record.location?.lat,
                        lng = record.location?.lng,
                        note = record.note,
                    ),
                ).toInt()
            }
            val now = System.currentTimeMillis()
            newPhotoFileNames.forEach { name ->
                photoDao.insert(Photo(eatTimeId = id, fileName = name, createdAt = now))
            }
            id
        }
        // File deletes happen only after the DB transaction succeeds (non-transactional).
        removedPhotos.forEach { PhotoStorage.deleteByName(appContext, it.fileName) }
        return recordId
    }

    override suspend fun delete(recordId: Int) {
        val photos = photoDao.findByEatTimeId(recordId)
        photos.forEach { PhotoStorage.deleteByName(appContext, it.fileName) }
        appDatabase.withTransaction {
            photoDao.deleteByEatTimeId(recordId)
            eatTimeDao.deleteById(recordId)
        }
    }
}
```

- [ ] **Step 2: Verify build + detekt.**

Run: `.\gradlew.bat assembleDebug :app:detekt`
Expected: `BUILD SUCCESSFUL`. (No magic-number literals here; `System.currentTimeMillis()` is fine.)

- [ ] **Step 3: Commit.**

```bash
git add app/src/main/java/com/crazystudio/sportrecorder/data/repository/EatRecordRepositoryImpl.kt
git commit -m "[Refactor] EatRecordRepositoryImpl (Room + photo files)"
```

---

### Task 1.9: `DietSettingsRepositoryImpl`

**Files:**
- Create: `app/src/main/java/com/crazystudio/sportrecorder/data/repository/DietSettingsRepositoryImpl.kt`

- [ ] **Step 1: Implement it** (the prefs `callbackFlow` relocated from `DietViewModel.prefsFlow`, now emitting `DietSettings`):

```kotlin
package com.crazystudio.sportrecorder.data.repository

import android.content.SharedPreferences
import com.crazystudio.sportrecorder.domain.model.DietSettings
import com.crazystudio.sportrecorder.domain.repository.DietSettingsRepository
import com.crazystudio.sportrecorder.util.Constants
import com.crazystudio.sportrecorder.util.DietPreference
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject

private const val DEFAULT_FASTING_HOURS = 16L
private const val DEFAULT_EATING_HOURS = 8L

class DietSettingsRepositoryImpl @Inject constructor(
    private val dietPreference: DietPreference,
) : DietSettingsRepository {

    private fun current(): DietSettings = DietSettings(
        fastingHours = dietPreference.preference.getLong(Constants.DIET_FASTING_TIME_INTERVAL, DEFAULT_FASTING_HOURS),
        eatingHours = dietPreference.preference.getLong(Constants.DIET_EATING_TIME_INTERVAL, DEFAULT_EATING_HOURS),
    )

    override val settings: Flow<DietSettings> = callbackFlow {
        trySend(current())
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> trySend(current()) }
        dietPreference.preference.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { dietPreference.preference.unregisterOnSharedPreferenceChangeListener(listener) }
    }
}
```

- [ ] **Step 2: Verify.**

Run: `.\gradlew.bat assembleDebug :app:detekt`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit.**

```bash
git add app/src/main/java/com/crazystudio/sportrecorder/data/repository/DietSettingsRepositoryImpl.kt
git commit -m "[Refactor] DietSettingsRepositoryImpl wrapping SharedPreferences"
```

---

### Task 1.10: Hilt `RepositoryModule` (DI bindings)

**Files:**
- Create: `app/src/main/java/com/crazystudio/sportrecorder/dagger/RepositoryModule.kt`

- [ ] **Step 1: Create the `@Binds` module:**

```kotlin
package com.crazystudio.sportrecorder.dagger

import com.crazystudio.sportrecorder.data.repository.DietSettingsRepositoryImpl
import com.crazystudio.sportrecorder.data.repository.EatRecordRepositoryImpl
import com.crazystudio.sportrecorder.domain.repository.DietSettingsRepository
import com.crazystudio.sportrecorder.domain.repository.EatRecordRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun bindEatRecordRepository(impl: EatRecordRepositoryImpl): EatRecordRepository

    @Binds
    @Singleton
    abstract fun bindDietSettingsRepository(impl: DietSettingsRepositoryImpl): DietSettingsRepository
}
```

- [ ] **Step 2: Verify Hilt graph compiles** (the impls' `@Inject` constructors resolve from the existing `DatabaseModule` providers for `AppDatabase`/`EatTimeDao`/`PhotoDao`/`DietPreference` + `@ApplicationContext`).

Run: `.\gradlew.bat assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Phase-1 gate — full check.**

Run: `.\gradlew.bat assembleDebug :app:check`
Expected: `BUILD SUCCESSFUL` — detekt, lint, and all unit tests (including the 3 new + existing `DietWindowTest`) green.

- [ ] **Step 4: Commit.**

```bash
git add app/src/main/java/com/crazystudio/sportrecorder/dagger/RepositoryModule.kt
git commit -m "[Refactor] RepositoryModule binds repositories"
```

---

## Phase 2 — Use cases

All five are thin `@Inject constructor` classes. Each in its own file under `domain/usecase/`.

### Task 2.1: Read/observe use cases

**Files:**
- Create: `app/src/main/java/com/crazystudio/sportrecorder/domain/usecase/ObserveEatRecordsUseCase.kt`
- Create: `app/src/main/java/com/crazystudio/sportrecorder/domain/usecase/LoadEatRecordUseCase.kt`
- Create: `app/src/main/java/com/crazystudio/sportrecorder/domain/usecase/DeleteEatRecordUseCase.kt`

- [ ] **Step 1: `ObserveEatRecordsUseCase.kt`:**

```kotlin
package com.crazystudio.sportrecorder.domain.usecase

import com.crazystudio.sportrecorder.domain.model.EatRecord
import com.crazystudio.sportrecorder.domain.repository.EatRecordRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveEatRecordsUseCase @Inject constructor(
    private val repository: EatRecordRepository,
) {
    operator fun invoke(): Flow<List<EatRecord>> = repository.observeAll()
}
```

- [ ] **Step 2: `LoadEatRecordUseCase.kt`:**

```kotlin
package com.crazystudio.sportrecorder.domain.usecase

import com.crazystudio.sportrecorder.domain.model.EatRecord
import com.crazystudio.sportrecorder.domain.repository.EatRecordRepository
import javax.inject.Inject

class LoadEatRecordUseCase @Inject constructor(
    private val repository: EatRecordRepository,
) {
    suspend operator fun invoke(id: Int): EatRecord? = repository.findById(id)
}
```

- [ ] **Step 3: `DeleteEatRecordUseCase.kt`:**

```kotlin
package com.crazystudio.sportrecorder.domain.usecase

import com.crazystudio.sportrecorder.domain.repository.EatRecordRepository
import javax.inject.Inject

class DeleteEatRecordUseCase @Inject constructor(
    private val repository: EatRecordRepository,
) {
    suspend operator fun invoke(recordId: Int) = repository.delete(recordId)
}
```

- [ ] **Step 4: Verify + commit.**

Run: `.\gradlew.bat :app:detekt assembleDebug`
Expected: `BUILD SUCCESSFUL`.

```bash
git add app/src/main/java/com/crazystudio/sportrecorder/domain/usecase/ObserveEatRecordsUseCase.kt app/src/main/java/com/crazystudio/sportrecorder/domain/usecase/LoadEatRecordUseCase.kt app/src/main/java/com/crazystudio/sportrecorder/domain/usecase/DeleteEatRecordUseCase.kt
git commit -m "[Refactor] Add observe/load/delete eat-record use cases"
```

---

### Task 2.2: `SaveEatRecordUseCase` — TDD (holds the future-time business rule)

**Files:**
- Create: `app/src/main/java/com/crazystudio/sportrecorder/domain/usecase/SaveEatRecordUseCase.kt`
- Create: `app/src/test/java/com/crazystudio/sportrecorder/domain/usecase/SaveEatRecordUseCaseTest.kt`

- [ ] **Step 1: Write the failing test** (hand-written fake repo; `TODO()` for unused members — tests are detekt-excluded):

```kotlin
package com.crazystudio.sportrecorder.domain.usecase

import com.crazystudio.sportrecorder.domain.model.EatPhoto
import com.crazystudio.sportrecorder.domain.model.EatRecord
import com.crazystudio.sportrecorder.domain.repository.EatRecordRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeEatRecordRepository : EatRecordRepository {
    var savedRecord: EatRecord? = null
    var saveCount = 0
    override fun observeAll(): Flow<List<EatRecord>> = TODO()
    override fun observeInWindow(after: Long, before: Long): Flow<List<EatRecord>> = TODO()
    override suspend fun findById(id: Int): EatRecord? = TODO()
    override suspend fun save(
        record: EatRecord,
        newPhotoFileNames: List<String>,
        removedPhotos: List<EatPhoto>,
    ): Int {
        savedRecord = record
        saveCount++
        return record.id
    }
    override suspend fun delete(recordId: Int) = TODO()
}

private fun record(time: Long) = EatRecord(id = 0, time = time, location = null, note = null, photos = emptyList())

class SaveEatRecordUseCaseTest {
    @Test fun futureTime_isRejected_andRepoNotCalled() = runTest {
        val repo = FakeEatRecordRepository()
        val useCase = SaveEatRecordUseCase(repo)
        val ok = useCase(record(time = 2_000L), emptyList(), emptyList(), now = 1_000L)
        assertFalse(ok)
        assertEquals(0, repo.saveCount)
        assertNull(repo.savedRecord)
    }

    @Test fun validTime_isSaved() = runTest {
        val repo = FakeEatRecordRepository()
        val useCase = SaveEatRecordUseCase(repo)
        val ok = useCase(record(time = 500L), listOf("a.webp"), emptyList(), now = 1_000L)
        assertTrue(ok)
        assertEquals(1, repo.saveCount)
        assertEquals(500L, repo.savedRecord?.time)
    }
}
```

- [ ] **Step 2: Run to confirm it fails.**

Run: `.\gradlew.bat testDebugUnitTest --tests "*SaveEatRecordUseCaseTest"`
Expected: FAIL — `Unresolved reference: SaveEatRecordUseCase`.

- [ ] **Step 3: Implement it:**

```kotlin
package com.crazystudio.sportrecorder.domain.usecase

import com.crazystudio.sportrecorder.domain.model.EatPhoto
import com.crazystudio.sportrecorder.domain.model.EatRecord
import com.crazystudio.sportrecorder.domain.repository.EatRecordRepository
import javax.inject.Inject

class SaveEatRecordUseCase @Inject constructor(
    private val repository: EatRecordRepository,
) {
    /** Returns false (without saving) if [EatRecord.time] is in the future. */
    suspend operator fun invoke(
        record: EatRecord,
        newPhotoFileNames: List<String>,
        removedPhotos: List<EatPhoto>,
        now: Long = System.currentTimeMillis(),
    ): Boolean {
        if (record.time > now) return false
        repository.save(record, newPhotoFileNames, removedPhotos)
        return true
    }
}
```

- [ ] **Step 4: Run to confirm it passes.**

Run: `.\gradlew.bat testDebugUnitTest --tests "*SaveEatRecordUseCaseTest"`
Expected: PASS (2 tests).

- [ ] **Step 5: Commit.**

```bash
git add app/src/main/java/com/crazystudio/sportrecorder/domain/usecase/SaveEatRecordUseCase.kt app/src/test/java/com/crazystudio/sportrecorder/domain/usecase/SaveEatRecordUseCaseTest.kt
git commit -m "[Refactor] SaveEatRecordUseCase with future-time rule + tests"
```

---

### Task 2.3: `ObserveDietStateUseCase`

Combines the eat-time window flow + settings flow and computes history. Owns the 8-day/16:00 window-bounds calendar math (relocated from `DietViewModel.historyFlow`).

**Files:**
- Create: `app/src/main/java/com/crazystudio/sportrecorder/domain/usecase/ObserveDietStateUseCase.kt`

- [ ] **Step 1: Implement it** (`8`/`16` are named consts for MagicNumber; `+1`/`0` are ignored):

```kotlin
package com.crazystudio.sportrecorder.domain.usecase

import com.crazystudio.sportrecorder.domain.diet.DietHistoryCalculator
import com.crazystudio.sportrecorder.domain.model.DietSettings
import com.crazystudio.sportrecorder.domain.repository.DietSettingsRepository
import com.crazystudio.sportrecorder.domain.repository.EatRecordRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.util.Calendar
import javax.inject.Inject

class ObserveDietStateUseCase @Inject constructor(
    private val eatRecordRepository: EatRecordRepository,
    private val dietSettingsRepository: DietSettingsRepository,
) {
    data class DietSnapshot(
        val eatTimesAsc: List<Long>,
        val settings: DietSettings,
        val history: List<DietHistoryCalculator.HistoryBar>,
    )

    /** [now] anchors the (fixed-at-subscription) history window; bars/window are computed against it. */
    operator fun invoke(now: Long): Flow<DietSnapshot> {
        val before = dayStart(now, DAY_OFFSET_TOMORROW)
        val after = windowStart(now)
        return combine(
            eatRecordRepository.observeInWindow(after = after, before = before),
            dietSettingsRepository.settings,
        ) { records, settings ->
            val eatTimesAsc = records.map { it.time }
            DietSnapshot(
                eatTimesAsc = eatTimesAsc,
                settings = settings,
                history = DietHistoryCalculator.compute(eatTimesAsc, now),
            )
        }
    }

    private fun dayStart(now: Long, dayOffset: Int): Long =
        Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.DAY_OF_YEAR, get(Calendar.DAY_OF_YEAR) + dayOffset)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    private fun windowStart(now: Long): Long =
        Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.DAY_OF_YEAR, get(Calendar.DAY_OF_YEAR) - HISTORY_WINDOW_DAYS)
            set(Calendar.HOUR_OF_DAY, WINDOW_START_HOUR)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    private companion object {
        const val DAY_OFFSET_TOMORROW = 1
        const val HISTORY_WINDOW_DAYS = 8
        const val WINDOW_START_HOUR = 16
    }
}
```

Note: the original `historyFlow()` used `before` = today's `DAY_OF_YEAR + 1` at the *current* time-of-day (it did not zero the clock for `before`). Zeroing to 00:00 of tomorrow is an equivalent upper bound — every record with `time < now` is still `< before`, since tomorrow-00:00 > now. Behavior for the window query is preserved.

- [ ] **Step 2: Verify + commit.**

Run: `.\gradlew.bat :app:detekt assembleDebug`
Expected: `BUILD SUCCESSFUL`.

```bash
git add app/src/main/java/com/crazystudio/sportrecorder/domain/usecase/ObserveDietStateUseCase.kt
git commit -m "[Refactor] ObserveDietStateUseCase (window + settings + history)"
```

- [ ] **Step 3: Phase-2 gate.**

Run: `.\gradlew.bat assembleDebug :app:check`
Expected: `BUILD SUCCESSFUL`.

---

## Phase 3 — Refactor ViewModels + UI to the new boundary

After each task in this phase, `EatTimeDao`/`PhotoDao`/`AppDatabase`/`DietPreference` injections disappear from the touched ViewModel, and the screens stop importing `entity.*`.

### Task 3.1: `DietRecordViewModel` + `RecordScreen` + AppRoot wiring

**Files:**
- Modify: `app/src/main/java/com/crazystudio/sportrecorder/ui/diet/record/DietRecordViewModel.kt`
- Modify: `app/src/main/java/com/crazystudio/sportrecorder/ui/diet/record/RecordScreen.kt`
- Modify: `app/src/main/java/com/crazystudio/sportrecorder/ui/AppRoot.kt`

- [ ] **Step 1: Rewrite `DietRecordViewModel.kt`** to depend on use cases:

```kotlin
package com.crazystudio.sportrecorder.ui.diet.record

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.crazystudio.sportrecorder.domain.model.EatRecord
import com.crazystudio.sportrecorder.domain.usecase.DeleteEatRecordUseCase
import com.crazystudio.sportrecorder.domain.usecase.ObserveEatRecordsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DietRecordViewModel @Inject constructor(
    observeEatRecords: ObserveEatRecordsUseCase,
    private val deleteEatRecord: DeleteEatRecordUseCase,
) : ViewModel() {

    val records: StateFlow<List<EatRecord>> = observeEatRecords()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun deleteRecord(record: EatRecord) {
        viewModelScope.launch { deleteEatRecord(record.id) }
    }
}
```

- [ ] **Step 2: Update `RecordScreen.kt`** to use domain types. Replace the entity imports (lines 44-46) — remove `EatTime`, `EatTimeWithPhotos`; keep `PhotoStorage`; add the domain import:

```kotlin
import com.crazystudio.sportrecorder.domain.model.EatRecord
import com.crazystudio.sportrecorder.util.PhotoStorage
```

Then update signatures and field access:
  - `RecordScreen(records: List<EatRecord>, onDelete: (EatRecord), onEditRecord: (Int), ...)`.
  - `var recordToDelete by remember { mutableStateOf<EatRecord?>(null) }`.
  - `items(records, key = { it.id })` — `EatRecord` has `id` directly (no `.eatTime`).
  - In the `items` block: `RecordCard(record = record, onLongClick = { recordToDelete = record }, ...)`.
  - The delete dialog `recordToDelete?.let { record -> ... onDelete(record) ... }`.
  - `RecordCard(record: EatRecord, ...)`: replace `val eatTime = record.eatTime` with direct access — use `record.time`, `record.note`, `record.location`, `record.photos`. Replace the location block:

```kotlin
        // Location
        record.location?.let { loc ->
            Text(
                text = "📍 ${String.format(Locale.ROOT, "%.4f, %.4f", loc.lat, loc.lng)}",
                fontSize = 12.sp,
                color = colorScheme.onSurfaceVariant,
            )
        }
```
  - Photos: `record.photos` is `List<EatPhoto>`; `photo.fileName` still resolves. The carousel/`onEditRecord(record.id)`/header date use `record.time` and `record.id`.

- [ ] **Step 3: Update `AppRoot.kt`** Record route (lines 121-129):
  - `onDelete = vm::deleteRecord` (was `vm::deleteEatTime`).
  - `records` is now `List<EatRecord>` — no other change; `onEditRecord` already passes an `Int` id.

- [ ] **Step 4: Verify build + detekt + lint.**

Run: `.\gradlew.bat assembleDebug :app:detekt :app:lintDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit.**

```bash
git add app/src/main/java/com/crazystudio/sportrecorder/ui/diet/record/ app/src/main/java/com/crazystudio/sportrecorder/ui/AppRoot.kt
git commit -m "[Refactor] DietRecordViewModel + RecordScreen on domain use cases"
```

---

### Task 3.2: `EatTimeEditorViewModel` + UiState + Sheet + AppRoot wiring

**Files:**
- Modify: `app/src/main/java/com/crazystudio/sportrecorder/ui/diet/editor/EatTimeEditorUiState.kt`
- Modify: `app/src/main/java/com/crazystudio/sportrecorder/ui/diet/editor/EatTimeEditorViewModel.kt`
- Modify: `app/src/main/java/com/crazystudio/sportrecorder/ui/diet/editor/EatTimeEditorSheet.kt`
- Modify: `app/src/main/java/com/crazystudio/sportrecorder/ui/AppRoot.kt`

- [ ] **Step 1: Update `EatTimeEditorUiState.kt`** — swap `Photo` (entity) for `EatPhoto` (domain):

```kotlin
package com.crazystudio.sportrecorder.ui.diet.editor

import com.crazystudio.sportrecorder.domain.model.EatPhoto
import java.util.Calendar

data class EatTimeEditorUiState(
    val date: Calendar,
    val isEditMode: Boolean = false,
    val note: String = "",
    val existingPhotos: List<EatPhoto> = emptyList(), // already-saved photos (edit mode)
    val pendingPhotos: List<String> = emptyList(), // newly captured webp file names
    val location: LatLng? = null,
    val locationStatus: LocationStatus = LocationStatus.IDLE,
) {
    data class LatLng(val lat: Double, val lng: Double)
    enum class LocationStatus { IDLE, LOADING, AVAILABLE, UNAVAILABLE }
}
```

- [ ] **Step 2: Rewrite `EatTimeEditorViewModel.kt`** to use `LoadEatRecordUseCase` + `SaveEatRecordUseCase`, dropping `AppDatabase`/`EatTimeDao`/`PhotoDao`. Photo staging (`PhotoStorage` + `Context`) stays.

```kotlin
package com.crazystudio.sportrecorder.ui.diet.editor

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.crazystudio.sportrecorder.domain.model.EatPhoto
import com.crazystudio.sportrecorder.domain.model.EatRecord
import com.crazystudio.sportrecorder.domain.model.GeoPoint
import com.crazystudio.sportrecorder.domain.usecase.LoadEatRecordUseCase
import com.crazystudio.sportrecorder.domain.usecase.SaveEatRecordUseCase
import com.crazystudio.sportrecorder.util.LocationProvider
import com.crazystudio.sportrecorder.util.PhotoStorage
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Calendar
import javax.inject.Inject

@HiltViewModel
@Suppress("TooManyFunctions") // cohesive editor VM: one handler per UI interaction
class EatTimeEditorViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val loadEatRecord: LoadEatRecordUseCase,
    private val saveEatRecord: SaveEatRecordUseCase,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    // Route arg: type-safe route field name "eatTimeId". 0 = create, >0 = edit.
    private val eatTimeId: Int = savedStateHandle.get<Int>("eatTimeId") ?: 0
    private val isEditMode: Boolean = eatTimeId > 0

    val currentCalendar: Calendar = Calendar.getInstance()
    private var committed = false
    private val photosToDelete = mutableListOf<EatPhoto>()

    private val _uiState = MutableStateFlow(
        EatTimeEditorUiState(date = currentCalendar.clone() as Calendar, isEditMode = isEditMode),
    )
    val uiState: StateFlow<EatTimeEditorUiState> = _uiState.asStateFlow()

    init {
        if (isEditMode) {
            viewModelScope.launch {
                val record = loadEatRecord(eatTimeId) ?: return@launch
                currentCalendar.timeInMillis = record.time
                val loc = record.location?.let { EatTimeEditorUiState.LatLng(it.lat, it.lng) }
                _uiState.update {
                    it.copy(
                        date = currentCalendar.clone() as Calendar,
                        note = record.note.orEmpty(),
                        existingPhotos = record.photos,
                        location = loc,
                        locationStatus = if (loc != null) {
                            EatTimeEditorUiState.LocationStatus.AVAILABLE
                        } else {
                            EatTimeEditorUiState.LocationStatus.IDLE
                        },
                    )
                }
            }
        }
    }

    fun setNote(value: String) = _uiState.update { it.copy(note = value) }

    fun addCapturedPhoto(tempFile: File) {
        viewModelScope.launch {
            val name = withContext(Dispatchers.IO) { PhotoStorage.convertToWebp(appContext, tempFile) }
            _uiState.update { it.copy(pendingPhotos = it.pendingPhotos + name) }
        }
    }

    fun removePendingPhoto(fileName: String) {
        viewModelScope.launch(Dispatchers.IO) { PhotoStorage.deleteByName(appContext, fileName) }
        _uiState.update { it.copy(pendingPhotos = it.pendingPhotos - fileName) }
    }

    /** Mark an already-saved photo for deletion; actual delete happens on save(). */
    fun removeExistingPhoto(photo: EatPhoto) {
        photosToDelete.add(photo)
        _uiState.update { it.copy(existingPhotos = it.existingPhotos - photo) }
    }

    /** Re-capture (or first capture) the current location. */
    fun requestLocation() {
        _uiState.update { it.copy(locationStatus = EatTimeEditorUiState.LocationStatus.LOADING) }
        viewModelScope.launch {
            val result = LocationProvider.currentLocation(appContext)
            _uiState.update {
                if (result == null) {
                    it.copy(location = null, locationStatus = EatTimeEditorUiState.LocationStatus.UNAVAILABLE)
                } else {
                    it.copy(
                        location = EatTimeEditorUiState.LatLng(result.first, result.second),
                        locationStatus = EatTimeEditorUiState.LocationStatus.AVAILABLE,
                    )
                }
            }
        }
    }

    fun clearLocation() = _uiState.update {
        it.copy(location = null, locationStatus = EatTimeEditorUiState.LocationStatus.IDLE)
    }

    fun locationDenied() = _uiState.update {
        it.copy(locationStatus = EatTimeEditorUiState.LocationStatus.UNAVAILABLE)
    }

    /** Validate + persist via the use case. Returns false if rejected (e.g. future time). */
    suspend fun save(): Boolean {
        val state = _uiState.value
        val record = EatRecord(
            id = eatTimeId,
            time = currentCalendar.timeInMillis,
            location = state.location?.let { GeoPoint(it.lat, it.lng) },
            note = state.note.ifBlank { null },
            photos = emptyList(),
        )
        val ok = saveEatRecord(record, state.pendingPhotos, photosToDelete)
        if (ok) committed = true
        return ok
    }

    fun updateDate(year: Int, month: Int, dayOfMonth: Int) {
        currentCalendar.set(Calendar.YEAR, year)
        currentCalendar.set(Calendar.MONTH, month)
        currentCalendar.set(Calendar.DAY_OF_MONTH, dayOfMonth)
        publishDate()
    }

    fun updateTime(hourOfDay: Int, minute: Int) {
        currentCalendar.set(Calendar.HOUR_OF_DAY, hourOfDay)
        currentCalendar.set(Calendar.MINUTE, minute)
        publishDate()
    }

    private fun publishDate() = _uiState.update { it.copy(date = currentCalendar.clone() as Calendar) }

    override fun onCleared() {
        super.onCleared()
        if (!committed) {
            // Dismissed without saving — delete only the newly-captured (unsaved) files.
            _uiState.value.pendingPhotos.forEach { PhotoStorage.deleteByName(appContext, it) }
        }
    }
}
```

Note: `save()` no longer guards future time inline — `SaveEatRecordUseCase` owns that rule and returns `false`, which (as today) keeps the sheet open.

- [ ] **Step 3: Update `EatTimeEditorSheet.kt`** — the `onRemoveExistingPhoto` param type. Replace the fully-qualified `com.crazystudio.sportrecorder.entity.Photo` on line 46 with the domain type, and add the import (after line 33's `R` import):

```kotlin
import com.crazystudio.sportrecorder.domain.model.EatPhoto
```
Change the parameter (line 46):
```kotlin
    onRemoveExistingPhoto: (EatPhoto) -> Unit,
```
The `items(state.existingPhotos, key = { it.id })` and `photo.fileName` usages are unchanged (`EatPhoto` has `id` + `fileName`).

- [ ] **Step 4: Update `AppRoot.kt`** EatTimeEditor route — no signature change needed: `vm::removeExistingPhoto` now takes `EatPhoto`, matching the sheet's updated param. Confirm `onRemoveExistingPhoto = vm::removeExistingPhoto` still compiles (line 231).

- [ ] **Step 5: Verify build + detekt + lint.**

Run: `.\gradlew.bat assembleDebug :app:detekt :app:lintDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit.**

```bash
git add app/src/main/java/com/crazystudio/sportrecorder/ui/diet/editor/ app/src/main/java/com/crazystudio/sportrecorder/ui/AppRoot.kt
git commit -m "[Refactor] EatTimeEditorViewModel + UiState + Sheet on use cases"
```

---

### Task 3.3: `DietViewModel`

**Files:**
- Modify: `app/src/main/java/com/crazystudio/sportrecorder/ui/diet/DietViewModel.kt`

- [ ] **Step 1: Rewrite `DietViewModel.kt`.** It now injects only `ObserveDietStateUseCase`; the ticker + `R.string` mapping + formatting stay. The history aggregation, prefs `callbackFlow`, and `flowByTimeInterval` glue are gone (moved to the use case / calculator / repo).

```kotlin
package com.crazystudio.sportrecorder.ui.diet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.crazystudio.sportrecorder.R
import com.crazystudio.sportrecorder.domain.diet.DietPhase
import com.crazystudio.sportrecorder.domain.diet.DietWindow
import com.crazystudio.sportrecorder.domain.usecase.ObserveDietStateUseCase
import com.crazystudio.sportrecorder.ui.diet.select.FastingItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltViewModel
class DietViewModel @Inject constructor(
    observeDietState: ObserveDietStateUseCase,
) : ViewModel() {

    /** Per-second ticker so elapsedText / progress recompute every second. */
    private val tickerFlow: Flow<Long> = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(TimeUnit.SECONDS.toMillis(1))
        }
    }

    val uiState: StateFlow<DietUiState> =
        combine(observeDietState(System.currentTimeMillis()), tickerFlow) { snapshot, now ->
            buildState(snapshot, now)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DietUiState())

    private fun buildState(snapshot: ObserveDietStateUseCase.DietSnapshot, now: Long): DietUiState {
        val eatingHours = snapshot.settings.eatingHours
        val fastingHours = snapshot.settings.fastingHours
        val fastingLabel = "%d : %d".format(fastingHours, eatingHours)
        val selectedFastingItem = FastingItem.defaultFastingItems.firstOrNull {
            it.fastingHours == fastingHours && it.eatingHours == eatingHours
        }
        val history = snapshot.history.map { DietUiState.HistoryBar(dateMillis = it.dateMillis, ratio = it.ratio) }

        val s = DietWindow.compute(
            eatTimesAsc = snapshot.eatTimesAsc,
            eatingHours = eatingHours,
            fastingHours = fastingHours,
            now = now,
        )

        val base = DietUiState(
            progress = s.ringProgress * 100f,
            fastingLabel = fastingLabel,
            history = history,
            selectedFastingItem = selectedFastingItem,
            elapsedText = formatElapsed(s.elapsedMillis),
        )

        return when (s.phase) {
            DietPhase.IDLE -> base.copy(
                elapsedText = formatElapsed(0L),
                statusIcon = R.drawable.ic_baseline_no_food_24,
                statusTextRes = R.string.diet_status_fasting,
                promptTextRes = R.string.diet_no_record,
            )
            DietPhase.EATING -> base.copy(
                statusIcon = R.drawable.ic_baseline_fastfood_24,
                statusTextRes = R.string.diet_status_eating,
                promptTextRes = R.string.diet_remaining_time,
                timeInfoRes = R.string.diet_eating_window,
                timeInfoArg1 = hm(s.windowStart),
                timeInfoArg2 = hm(s.windowEnd),
            )
            DietPhase.FASTING -> base.copy(
                statusIcon = R.drawable.ic_baseline_no_food_24,
                statusTextRes = R.string.diet_status_fasting,
                promptTextRes = R.string.diet_fasting_time,
                timeInfoRes = R.string.diet_fast_target,
                timeInfoArg1 = hm(s.fastTargetAt),
            )
            DietPhase.SUCCESS -> base.copy(
                statusIcon = R.drawable.ic_baseline_no_food_24,
                statusTextRes = R.string.diet_status_success,
                promptTextRes = R.string.diet_fasting_time,
                timeInfoRes = R.string.diet_fast_done,
            )
        }
    }

    private fun hm(millis: Long?): String =
        if (millis == null) "" else HM_FORMAT.format(Date(millis))

    private fun formatElapsed(timestamp: Long): String {
        var temp = timestamp
        val hours = TimeUnit.MILLISECONDS.toHours(temp)
        temp -= TimeUnit.HOURS.toMillis(hours)
        val mins = TimeUnit.MILLISECONDS.toMinutes(temp)
        temp -= TimeUnit.MINUTES.toMillis(mins)
        val ses = TimeUnit.MILLISECONDS.toSeconds(temp)
        // Matches R.string.diet_time_format = "%02d:%02d:%02d"
        return "%02d:%02d:%02d".format(hours, mins, ses)
    }

    companion object {
        private val HISTORY_DATE_FORMAT = SimpleDateFormat("MM/dd", Locale.getDefault())
        private val HM_FORMAT = SimpleDateFormat("HH:mm", Locale.getDefault())

        fun formatHistoryDate(dateMillis: Long): String =
            HISTORY_DATE_FORMAT.format(Date(dateMillis))
    }
}
```

Note: `DietViewModel` lives under `ui/`, so MagicNumber is excluded — `100f`/`5000`/`1` literals are fine, matching the original. `HISTORY_DATE_FORMAT` is now `private` (only used via `formatHistoryDate`); `DietScreen` already calls `DietViewModel.formatHistoryDate(...)`.

- [ ] **Step 2: Verify build + detekt + lint.**

Run: `.\gradlew.bat assembleDebug :app:detekt :app:lintDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit.**

```bash
git add app/src/main/java/com/crazystudio/sportrecorder/ui/diet/DietViewModel.kt
git commit -m "[Refactor] DietViewModel on ObserveDietStateUseCase"
```

---

### Task 3.4: Phase-3 gate — full check + emulator smoke test

**Files:** none (verification only).

- [ ] **Step 1: Confirm DAOs are now only used by the repository.**

Run (Grep tool, pattern `EatTimeDao|PhotoDao`, across `app/src/main`): the only non-`dao/`, non-`database/`, non-`dagger/DatabaseModule` references must be in `data/repository/EatRecordRepositoryImpl.kt`. No ViewModel should reference them.
Also Grep `com.crazystudio.sportrecorder.entity` under `ui/` — expected: zero matches (UI now speaks domain types).

- [ ] **Step 2: Full gate (mirrors CI).**

Run: `.\gradlew.bat assembleDebug testDebugUnitTest :app:detekt :app:lintDebug`
Expected: `BUILD SUCCESSFUL` — all unit tests pass (DietWindow + DietHistoryCalculator + mappers + SaveEatRecordUseCase), detekt + lint clean.

- [ ] **Step 3: Install on the emulator and smoke-test behavior parity.**

Run: `.\gradlew.bat installDebug` (ensure the `Pixel_10_Pro` AVD is running).
Manually verify, comparing against pre-refactor behavior:
  - **Home**: ring progress, status icon/text, prompt, elapsed timer ticking each second, time-info line, fasting-type label, and the 5 history bars all render and update.
  - **Record**: cards list newest-first; photo carousel swipes; tap photo → full-screen; long-press → delete dialog → delete removes the card and its files; pencil → opens editor for that record.
  - **Editor (create)**: location permission prompt; date/time pickers; note; add photo (camera) → thumbnail; remove pending photo; CREATE saves and the new record appears on Record/Home; future time is rejected (sheet stays open).
  - **Editor (edit)**: existing note/location/photos prefilled; re-capture/clear location; remove existing photo; add new photo; SAVE persists changes; removed photos' files are gone.

- [ ] **Step 4: Final commit (if any smoke-test fixes were needed; otherwise skip).** Ensure `git status` is clean.

---

## Self-Review (author check vs. spec)

- **Spec §1 package structure** → Tasks 1.2–1.10, 2.1–2.3 create exactly `domain/{model,diet,repository,usecase}` + `data/{mapper,repository}`; `entity/`/`dao/`/`database/`/`util/*` left in place (only `EatTimeDao.deleteById` added, Task 1.7). ✔
- **Spec §2 domain models** → Task 1.2 (`EatRecord`, `EatPhoto`, `GeoPoint`, `DietSettings`), epoch-millis `Long`, `id==0` sentinel. ✔
- **Spec §3 repository interfaces** → Task 1.5; `save` consolidated to `(record, newPhotoFileNames, removedPhotos)` per the spec update (LongParameterList). Impl (Tasks 1.8/1.9) keeps the transaction + file-after-commit ordering and the prefs `callbackFlow`. Photo seam (`convertToWebp`/`fileFor`) untouched in UI. ✔
- **Spec §4 use cases** → Tasks 2.1–2.3; `SaveEatRecordUseCase` holds the future-time rule; `ObserveDietStateUseCase` combines window+settings and runs `DietHistoryCalculator`; the ticker + `R.string` mapping stay in `DietViewModel` (Task 3.3). ✔
- **Spec §5 mappers + DI** → Task 1.6 (`EatRecordMappers`), Task 1.10 (`RepositoryModule` `@Binds`). ✔
- **Spec §6 VM/UI changes** → Tasks 3.1–3.3 drop DAO/DB/prefs injections; UiState/screens move to domain types; Task 3.4 Step 1 verifies DAOs are repository-only. ✔
- **Spec §7 testing** → `kotlinx-coroutines-test` (Task 1.1); the three pure tests (`DietHistoryCalculatorTest` 1.4, `EatRecordMappersTest` 1.6, `SaveEatRecordUseCaseTest` 2.2). No Turbine/VM tests. ✔
- **Spec §8 phasing** → Phase 1 scaffold, Phase 2 use cases, Phase 3 VM/UI — each ends on a green `:app:check`. ✔
- **Placeholder scan:** every code step has complete code; every run step has an exact command + expected result. No TBD/TODO-in-plan. ✔
- **Type consistency:** `save(record, newPhotoFileNames, removedPhotos)` identical across interface (1.5), impl (1.8), use case (2.2), VM call (3.2), and fake (2.2 test). `deleteRecord(EatRecord)` (3.1) vs `DeleteEatRecordUseCase(recordId: Int)` (2.1) — VM passes `record.id`. `existingPhotos: List<EatPhoto>` consistent across UiState (3.2), VM (3.2), Sheet param (3.2). `ObserveDietStateUseCase.DietSnapshot` fields match `DietViewModel.buildState` usage (3.3). ✔
- **detekt landmines:** MagicNumber consts added in `DietHistoryCalculator` (8/4/5), `DietSettingsRepositoryImpl` (16/8), `ObserveDietStateUseCase` (8/16/1); `LongParameterList` avoided via `EatRecord`-based `save`; no wildcard imports. ✔
