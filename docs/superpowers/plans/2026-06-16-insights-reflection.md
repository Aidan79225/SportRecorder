# 回顧 / Insights tab Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a 回顧 / Insights tab (replacing the placeholder Notifications tab) that reflects the user's eating back to them: a fasting-adherence calendar + streak, eating-pattern stats, a meal photo wall, and an eating-locations list.

**Architecture:** A pure, Android-free `InsightsAggregator` (mirrors `DietWindow`) computes everything from the existing `EatRecord` stream + `DietSettings`. A `@HiltViewModel` combines `ObserveEatRecordsUseCase`, `DietSettingsRepository.settings`, and UI state (selected period + month anchor) into `InsightsUiState`. A Compose `InsightsScreen` renders four cards and reuses the existing `FullScreenPhotoViewer`.

**Tech Stack:** Kotlin, Jetpack Compose (Material 3), Hilt, Kotlin Coroutines/Flow, Room (via existing repos), JUnit4 + Turbine + coroutines-test. minSdk 24 → `java.util.Calendar` / `TimeUnit` for dates (no `java.time`).

**Spec:** `docs/superpowers/specs/2026-06-16-insights-reflection-design.md`

---

## File Structure

**Create (domain — pure, unit-tested):**
- `app/src/main/java/com/crazystudio/sportrecorder/domain/insights/InsightsModels.kt` — enums + data classes (`Period`, `AdherenceState`, `DayCell`, `InsightsStats`, `LocationCount`, `InsightsResult`).
- `app/src/main/java/com/crazystudio/sportrecorder/domain/insights/InsightsAggregator.kt` — pure `compute(...)` + helpers.

**Create (UI):**
- `app/src/main/java/com/crazystudio/sportrecorder/ui/insights/InsightsUiState.kt`
- `app/src/main/java/com/crazystudio/sportrecorder/ui/insights/InsightsViewModel.kt`
- `app/src/main/java/com/crazystudio/sportrecorder/ui/insights/InsightsScreen.kt`
- `app/src/main/res/drawable/ic_baseline_insights_24.xml`

**Create (tests):**
- `app/src/test/java/com/crazystudio/sportrecorder/domain/insights/InsightsAggregatorTest.kt`
- `app/src/test/java/com/crazystudio/sportrecorder/ui/insights/InsightsViewModelTest.kt`

**Modify:**
- `app/src/main/java/com/crazystudio/sportrecorder/ui/nav/Route.kt` — `Notifications` → `Insights`.
- `app/src/main/java/com/crazystudio/sportrecorder/ui/AppRoot.kt` — tab, nav-selection branch, NavHost entry.
- `app/src/main/res/values/strings.xml` + `app/src/main/res/values-zh-rTW/strings.xml` — labels.

**Delete:**
- `app/src/main/java/com/crazystudio/sportrecorder/ui/notifications/NotificationsScreen.kt`

**detekt watch (domain/** is NOT excluded from MagicNumber):** name every numeric literal in the aggregator (constants are provided below). Keep the aggregator file at ≤10 functions (TooManyFunctions threshold is 11). UI files are exempt from MagicNumber.

**Per-commit:** every `git commit` ends with the trailer
`Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>` (shown in commands below).

**Build commands (this machine):** set `JAVA_HOME` first in PowerShell:
`$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'` then `.\gradlew.bat <tasks> --console=plain`.

---

## Task 1: Domain models

**Files:**
- Create: `app/src/main/java/com/crazystudio/sportrecorder/domain/insights/InsightsModels.kt`

- [ ] **Step 1: Create the models file**

```kotlin
package com.crazystudio.sportrecorder.domain.insights

/** Time window the stats + photo wall + locations cards summarise. */
enum class Period { WEEK, MONTH }

/** Per-day fasting-adherence outcome shown on the calendar. */
enum class AdherenceState { ON_TARGET, OFF_TARGET, NO_DATA }

/** One cell of the month calendar. [dayStart] is local midnight epoch millis. */
data class DayCell(
    val dayStart: Long,
    val dayOfMonth: Int,
    val state: AdherenceState,
)

/**
 * Eating-pattern stats over the selected [Period].
 * [avgFirstMealMinutes]/[avgLastMealMinutes] are minutes-since-local-midnight, null when no data.
 */
data class InsightsStats(
    val mealCount: Int,
    val avgFirstMealMinutes: Int?,
    val avgLastMealMinutes: Int?,
    val lateNightDays: Int,
)

/** A place the user ate, grouped by rounded coordinates, with how many times. */
data class LocationCount(val lat: Double, val lng: Double, val count: Int)

/** Everything the Insights screen renders. */
data class InsightsResult(
    val calendarDays: List<DayCell>,
    val streak: Int,
    val stats: InsightsStats,
    val photoFileNames: List<String>,
    val locations: List<LocationCount>,
) {
    companion object {
        val EMPTY = InsightsResult(
            calendarDays = emptyList(),
            streak = 0,
            stats = InsightsStats(0, null, null, 0),
            photoFileNames = emptyList(),
            locations = emptyList(),
        )
    }
}
```

- [ ] **Step 2: Compile**

Run: `$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'; .\gradlew.bat compileDebugKotlin --console=plain`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/crazystudio/sportrecorder/domain/insights/InsightsModels.kt
git commit -m "Add Insights domain models" -m "Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 2: Per-day adherence classification

Pure function over a single day's meal times. ON_TARGET when the eating window (last − first) ≤ the eating-hours goal; NO_DATA when no meals.

**Files:**
- Create: `app/src/main/java/com/crazystudio/sportrecorder/domain/insights/InsightsAggregator.kt`
- Test: `app/src/test/java/com/crazystudio/sportrecorder/domain/insights/InsightsAggregatorTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.crazystudio.sportrecorder.domain.insights

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.TimeUnit

class InsightsAggregatorTest {
    private val eatingHours = 8L
    private fun h(n: Long) = TimeUnit.HOURS.toMillis(n)
    private val base = 1_700_000_000_000L

    @Test fun adherence_noMeals_isNoData() {
        assertEquals(AdherenceState.NO_DATA, InsightsAggregator.adherenceFor(emptyList(), eatingHours))
    }

    @Test fun adherence_singleMeal_isOnTarget() {
        assertEquals(AdherenceState.ON_TARGET, InsightsAggregator.adherenceFor(listOf(base), eatingHours))
    }

    @Test fun adherence_windowWithinGoal_isOnTarget() {
        val state = InsightsAggregator.adherenceFor(listOf(base, base + h(6)), eatingHours)
        assertEquals(AdherenceState.ON_TARGET, state)
    }

    @Test fun adherence_windowExactlyGoal_isOnTarget() {
        val state = InsightsAggregator.adherenceFor(listOf(base, base + h(8)), eatingHours)
        assertEquals(AdherenceState.ON_TARGET, state)
    }

    @Test fun adherence_windowOverGoal_isOffTarget() {
        val state = InsightsAggregator.adherenceFor(listOf(base, base + h(9)), eatingHours)
        assertEquals(AdherenceState.OFF_TARGET, state)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'; .\gradlew.bat testDebugUnitTest --tests "*InsightsAggregatorTest" --console=plain`
Expected: FAIL — `InsightsAggregator` unresolved.

- [ ] **Step 3: Create the aggregator with `adherenceFor`**

```kotlin
package com.crazystudio.sportrecorder.domain.insights

import com.crazystudio.sportrecorder.domain.model.EatRecord
import com.crazystudio.sportrecorder.domain.model.DietSettings
import java.util.Calendar
import java.util.concurrent.TimeUnit

/** Pure (Android-free) calculator for the Insights screen. */
object InsightsAggregator {

    /** Classify one calendar day's ascending meal times against the eating-hours goal. */
    fun adherenceFor(dayMealTimesAsc: List<Long>, eatingHours: Long): AdherenceState {
        if (dayMealTimesAsc.isEmpty()) return AdherenceState.NO_DATA
        val window = dayMealTimesAsc.last() - dayMealTimesAsc.first()
        return if (window <= TimeUnit.HOURS.toMillis(eatingHours)) {
            AdherenceState.ON_TARGET
        } else {
            AdherenceState.OFF_TARGET
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'; .\gradlew.bat testDebugUnitTest --tests "*InsightsAggregatorTest" --console=plain`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/crazystudio/sportrecorder/domain/insights/InsightsAggregator.kt app/src/test/java/com/crazystudio/sportrecorder/domain/insights/InsightsAggregatorTest.kt
git commit -m "Add per-day adherence classification" -m "Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 2 helper note: test date builder

Later tasks construct timestamps at explicit **local** clock times so day-bucketing is timezone-robust. Add this private helper to `InsightsAggregatorTest` now (used by Tasks 3–6):

- [ ] **Step 1: Add the helper to the test class** (place near the top of `InsightsAggregatorTest`)

```kotlin
    /** Local-time epoch millis for the given wall-clock moment. `month` is 0-based. */
    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int = 0): Long =
        Calendar.getInstance().apply {
            clear()
            set(year, month, day, hour, minute)
        }.timeInMillis
```

Add the import `import java.util.Calendar` to the test file.

- [ ] **Step 2: Commit**

```bash
git add app/src/test/java/com/crazystudio/sportrecorder/domain/insights/InsightsAggregatorTest.kt
git commit -m "Add local-time builder to Insights test" -m "Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 3: Month calendar cells

Group all records into local days and produce one `DayCell` per day of the anchored month.

**Files:**
- Modify: `app/src/main/java/com/crazystudio/sportrecorder/domain/insights/InsightsAggregator.kt`
- Test: `app/src/test/java/com/crazystudio/sportrecorder/domain/insights/InsightsAggregatorTest.kt`

- [ ] **Step 1: Write the failing test** (append to `InsightsAggregatorTest`)

```kotlin
    private fun rec(time: Long) =
        EatRecord(id = 0, time = time, location = null, note = null, photos = emptyList())

    @Test fun monthCells_lengthMatchesDaysInMonth() {
        // March 2026 has 31 days.
        val cells = InsightsAggregator.monthCells(emptyList(), eatingHours, at(2026, 2, 15, 12))
        assertEquals(31, cells.size)
        assertEquals(1, cells.first().dayOfMonth)
        assertEquals(31, cells.last().dayOfMonth)
    }

    @Test fun monthCells_classifiesEachDay() {
        val records = listOf(
            rec(at(2026, 2, 10, 9)), rec(at(2026, 2, 10, 14)),   // 5h window -> ON
            rec(at(2026, 2, 11, 8)), rec(at(2026, 2, 11, 20)),   // 12h window -> OFF
        )
        val cells = InsightsAggregator.monthCells(records, eatingHours, at(2026, 2, 1, 0))
        assertEquals(AdherenceState.ON_TARGET, cells[9].state)   // day 10
        assertEquals(AdherenceState.OFF_TARGET, cells[10].state) // day 11
        assertEquals(AdherenceState.NO_DATA, cells[0].state)     // day 1
    }
```

Add imports `import com.crazystudio.sportrecorder.domain.model.EatRecord` to the test file.

- [ ] **Step 2: Run test to verify it fails**

Run: `$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'; .\gradlew.bat testDebugUnitTest --tests "*InsightsAggregatorTest" --console=plain`
Expected: FAIL — `monthCells` unresolved.

- [ ] **Step 3: Add `monthCells` + `dayStart` to the aggregator**

```kotlin
    /** Local midnight (epoch millis) of the day containing [millis]. */
    internal fun dayStart(millis: Long): Long =
        Calendar.getInstance().apply {
            timeInMillis = millis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    /** One [DayCell] per day of the month containing [monthAnchor]. */
    fun monthCells(records: List<EatRecord>, eatingHours: Long, monthAnchor: Long): List<DayCell> {
        val byDay: Map<Long, List<Long>> = records
            .groupBy { dayStart(it.time) }
            .mapValues { entry -> entry.value.map { it.time }.sorted() }

        val cal = Calendar.getInstance().apply {
            timeInMillis = monthAnchor
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        return (1..daysInMonth).map { day ->
            val start = cal.timeInMillis
            val state = adherenceFor(byDay[start].orEmpty(), eatingHours)
            cal.add(Calendar.DAY_OF_MONTH, 1)
            DayCell(dayStart = start, dayOfMonth = day, state = state)
        }
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run: same `--tests "*InsightsAggregatorTest"` command. Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/crazystudio/sportrecorder/domain/insights/InsightsAggregator.kt app/src/test/java/com/crazystudio/sportrecorder/domain/insights/InsightsAggregatorTest.kt
git commit -m "Add month calendar cell computation" -m "Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 4: Adherence streak

Consecutive ON_TARGET days counting back from the most recent day. An empty in-progress *today* is neutral (skipped, doesn't break); any other NO_DATA day or an OFF_TARGET day breaks the streak.

**Files:**
- Modify: `app/src/main/java/com/crazystudio/sportrecorder/domain/insights/InsightsAggregator.kt`
- Test: `app/src/test/java/com/crazystudio/sportrecorder/domain/insights/InsightsAggregatorTest.kt`

- [ ] **Step 1: Write the failing test** (append)

```kotlin
    @Test fun streak_countsConsecutiveOnTargetDays() {
        val records = listOf(
            rec(at(2026, 2, 14, 9)), rec(at(2026, 2, 14, 12)), // ON
            rec(at(2026, 2, 15, 9)), rec(at(2026, 2, 15, 13)), // ON
        )
        val now = at(2026, 2, 15, 20)
        assertEquals(2, InsightsAggregator.computeStreak(records, eatingHours, now))
    }

    @Test fun streak_emptyTodayDoesNotBreak() {
        val records = listOf(
            rec(at(2026, 2, 14, 9)), rec(at(2026, 2, 14, 12)), // ON (yesterday)
        )
        val now = at(2026, 2, 15, 10) // today has no meals yet
        assertEquals(1, InsightsAggregator.computeStreak(records, eatingHours, now))
    }

    @Test fun streak_offTargetBreaks() {
        val records = listOf(
            rec(at(2026, 2, 13, 9)), rec(at(2026, 2, 13, 12)), // ON
            rec(at(2026, 2, 14, 8)), rec(at(2026, 2, 14, 22)), // OFF (14h)
            rec(at(2026, 2, 15, 9)), rec(at(2026, 2, 15, 12)), // ON
        )
        val now = at(2026, 2, 15, 20)
        assertEquals(1, InsightsAggregator.computeStreak(records, eatingHours, now))
    }

    @Test fun streak_gapDayBreaks() {
        val records = listOf(
            rec(at(2026, 2, 13, 9)), rec(at(2026, 2, 13, 12)), // ON
            // 14th: no meals (a gap, and not "today")
            rec(at(2026, 2, 15, 9)), rec(at(2026, 2, 15, 12)), // ON
        )
        val now = at(2026, 2, 15, 20)
        assertEquals(1, InsightsAggregator.computeStreak(records, eatingHours, now))
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: same `--tests "*InsightsAggregatorTest"`. Expected: FAIL — `computeStreak` unresolved.

- [ ] **Step 3: Add `computeStreak`**

```kotlin
    /** Consecutive ON_TARGET days ending at the most recent day; empty today is neutral. */
    fun computeStreak(records: List<EatRecord>, eatingHours: Long, now: Long): Int {
        val byDay: Map<Long, List<Long>> = records
            .groupBy { dayStart(it.time) }
            .mapValues { entry -> entry.value.map { it.time }.sorted() }

        val cal = Calendar.getInstance().apply { timeInMillis = dayStart(now) }
        // Skip an empty in-progress today so it doesn't zero the streak.
        if (byDay[cal.timeInMillis].isNullOrEmpty()) {
            cal.add(Calendar.DAY_OF_MONTH, -1)
        }

        var streak = 0
        while (adherenceFor(byDay[cal.timeInMillis].orEmpty(), eatingHours) == AdherenceState.ON_TARGET) {
            streak++
            cal.add(Calendar.DAY_OF_MONTH, -1)
        }
        return streak
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run: same command. Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/crazystudio/sportrecorder/domain/insights/InsightsAggregator.kt app/src/test/java/com/crazystudio/sportrecorder/domain/insights/InsightsAggregatorTest.kt
git commit -m "Add adherence streak computation" -m "Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 5: Period filtering + stats

`periodStart(now, period)` gives the lower bound (WEEK = last 7 days incl. today; MONTH = start of the current calendar month). `statsFor(records)` summarises records already filtered to the period.

**Files:**
- Modify: `app/src/main/java/com/crazystudio/sportrecorder/domain/insights/InsightsAggregator.kt`
- Test: `app/src/test/java/com/crazystudio/sportrecorder/domain/insights/InsightsAggregatorTest.kt`

- [ ] **Step 1: Write the failing test** (append)

```kotlin
    @Test fun statsFor_countsAndAverages() {
        val records = listOf(
            rec(at(2026, 2, 10, 8, 0)), rec(at(2026, 2, 10, 18, 0)),  // first 08:00 last 18:00
            rec(at(2026, 2, 11, 10, 0)), rec(at(2026, 2, 11, 20, 0)), // first 10:00 last 20:00
        )
        val stats = InsightsAggregator.statsFor(records)
        assertEquals(4, stats.mealCount)
        assertEquals(9 * 60, stats.avgFirstMealMinutes)   // (480 + 600)/2 = 540
        assertEquals(19 * 60, stats.avgLastMealMinutes)   // (1080 + 1200)/2 = 1140
    }

    @Test fun statsFor_lateNightCountsDistinctDays() {
        val records = listOf(
            rec(at(2026, 2, 10, 23, 0)),                 // late
            rec(at(2026, 2, 11, 12, 0)),                 // not late
            rec(at(2026, 2, 12, 22, 0)), rec(at(2026, 2, 12, 22, 30)), // late, one day
        )
        assertEquals(2, InsightsAggregator.statsFor(records).lateNightDays)
    }

    @Test fun statsFor_empty() {
        val stats = InsightsAggregator.statsFor(emptyList())
        assertEquals(0, stats.mealCount)
        assertEquals(null, stats.avgFirstMealMinutes)
        assertEquals(null, stats.avgLastMealMinutes)
        assertEquals(0, stats.lateNightDays)
    }

    @Test fun periodStart_weekIsSevenDays() {
        val now = at(2026, 2, 15, 12)
        val start = InsightsAggregator.periodStart(now, Period.WEEK)
        assertEquals(InsightsAggregator.dayStart(at(2026, 2, 9, 0)), start) // 15 - 6 days
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: same `--tests "*InsightsAggregatorTest"`. Expected: FAIL — `statsFor` / `periodStart` unresolved.

- [ ] **Step 3: Add constants, `periodStart`, and `statsFor`**

Add these constants inside the `object InsightsAggregator` (top of the body):

```kotlin
    private const val LATE_NIGHT_HOUR = 22
    private const val MINUTES_PER_HOUR = 60
    private const val WEEK_LOOKBACK_DAYS = 6
```

Then add the functions:

```kotlin
    /** Inclusive lower bound (local midnight) for the selected period relative to [now]. */
    fun periodStart(now: Long, period: Period): Long {
        val cal = Calendar.getInstance().apply { timeInMillis = dayStart(now) }
        when (period) {
            Period.WEEK -> cal.add(Calendar.DAY_OF_MONTH, -WEEK_LOOKBACK_DAYS)
            Period.MONTH -> cal.set(Calendar.DAY_OF_MONTH, 1)
        }
        return cal.timeInMillis
    }

    /** Stats over [records] (assumed already filtered to the period). */
    fun statsFor(records: List<EatRecord>): InsightsStats {
        val byDay = records.groupBy { dayStart(it.time) }
        val firsts = byDay.values.map { day -> minutesSinceMidnight(day.minOf { it.time }) }
        val lasts = byDay.values.map { day -> minutesSinceMidnight(day.maxOf { it.time }) }
        val lateDays = byDay.values.count { day ->
            day.any { minutesSinceMidnight(it.time) >= LATE_NIGHT_HOUR * MINUTES_PER_HOUR }
        }
        return InsightsStats(
            mealCount = records.size,
            avgFirstMealMinutes = firsts.averageOrNull(),
            avgLastMealMinutes = lasts.averageOrNull(),
            lateNightDays = lateDays,
        )
    }

    private fun minutesSinceMidnight(millis: Long): Int =
        Calendar.getInstance().apply { timeInMillis = millis }
            .let { it.get(Calendar.HOUR_OF_DAY) * MINUTES_PER_HOUR + it.get(Calendar.MINUTE) }

    private fun List<Int>.averageOrNull(): Int? =
        if (isEmpty()) null else (sum().toDouble() / size).toInt()
```

Note: `minutesSinceMidnight` and `averageOrNull` are private helpers. With `compute` (Task 7) the file will hold: `adherenceFor, dayStart, monthCells, computeStreak, periodStart, statsFor, minutesSinceMidnight, averageOrNull, photosFor, locationsFor, compute` = 11 — at the TooManyFunctions threshold. To stay under, in Task 6 fold `photosFor`/`locationsFor` logic inline into `compute` (do NOT add them as separate functions). The plan below reflects that.

- [ ] **Step 4: Run test to verify it passes**

Run: same command. Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/crazystudio/sportrecorder/domain/insights/InsightsAggregator.kt app/src/test/java/com/crazystudio/sportrecorder/domain/insights/InsightsAggregatorTest.kt
git commit -m "Add period filtering and eating-pattern stats" -m "Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 6: Top-level `compute` (assembles everything, incl. photos + locations inline)

**Files:**
- Modify: `app/src/main/java/com/crazystudio/sportrecorder/domain/insights/InsightsAggregator.kt`
- Test: `app/src/test/java/com/crazystudio/sportrecorder/domain/insights/InsightsAggregatorTest.kt`

- [ ] **Step 1: Write the failing test** (append)

```kotlin
    private fun recFull(time: Long, photos: List<String>, lat: Double?, lng: Double?) =
        EatRecord(
            id = 0, time = time, note = null,
            location = if (lat != null && lng != null) GeoPoint(lat, lng) else null,
            photos = photos.mapIndexed { i, name -> EatPhoto(id = i, fileName = name, createdAt = time) },
        )

    @Test fun compute_assemblesAllCards() {
        val settings = DietSettings(fastingHours = 16, eatingHours = 8)
        val now = at(2026, 2, 15, 21)
        val records = listOf(
            recFull(at(2026, 2, 15, 9), listOf("a.webp"), 25.0, 121.0),
            recFull(at(2026, 2, 15, 13), listOf("b.webp"), 25.0, 121.0),
        )
        val result = InsightsAggregator.compute(records, settings, now, Period.MONTH, now)

        assertEquals(28, result.calendarDays.size)            // Feb 2026 = 28 days
        assertEquals(1, result.streak)
        assertEquals(2, result.stats.mealCount)
        assertEquals(listOf("b.webp", "a.webp"), result.photoFileNames) // newest first
        assertEquals(1, result.locations.size)
        assertEquals(2, result.locations.first().count)
    }
```

Add imports `import com.crazystudio.sportrecorder.domain.model.DietSettings`, `import com.crazystudio.sportrecorder.domain.model.GeoPoint`, `import com.crazystudio.sportrecorder.domain.model.EatPhoto` to the test file.

- [ ] **Step 2: Run test to verify it fails**

Run: same `--tests "*InsightsAggregatorTest"`. Expected: FAIL — `compute` unresolved.

- [ ] **Step 3: Add `compute` (photos + locations handled inline to keep function count ≤10... +compute = 9)**

Add the rounding constant with the other constants:

```kotlin
    private const val LOCATION_ROUNDING = 1000.0 // ~100m grid for grouping eat locations
```

Then:

```kotlin
    /** Build the full Insights result. [monthAnchor] selects the calendar month. */
    fun compute(
        records: List<EatRecord>,
        settings: DietSettings,
        now: Long,
        period: Period,
        monthAnchor: Long,
    ): InsightsResult {
        val from = periodStart(now, period)
        val inPeriod = records.filter { it.time in from..now }

        val photoFileNames = inPeriod
            .sortedByDescending { it.time }
            .flatMap { record -> record.photos.map { it.fileName } }

        val locations = inPeriod
            .mapNotNull { it.location }
            .groupBy { (Math.round(it.lat * LOCATION_ROUNDING)) to (Math.round(it.lng * LOCATION_ROUNDING)) }
            .map { (key, points) ->
                LocationCount(
                    lat = key.first / LOCATION_ROUNDING,
                    lng = key.second / LOCATION_ROUNDING,
                    count = points.size,
                )
            }
            .sortedByDescending { it.count }

        return InsightsResult(
            calendarDays = monthCells(records, settings.eatingHours, monthAnchor),
            streak = computeStreak(records, settings.eatingHours, now),
            stats = statsFor(inPeriod),
            photoFileNames = photoFileNames,
            locations = locations,
        )
    }
```

- [ ] **Step 4: Run the full aggregator test + detekt**

Run: `$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'; .\gradlew.bat testDebugUnitTest --tests "*InsightsAggregatorTest" detekt --console=plain`
Expected: PASS, detekt clean (no MagicNumber/TooManyFunctions on the aggregator). If detekt flags a magic number, extract it to a named `private const val`.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/crazystudio/sportrecorder/domain/insights/InsightsAggregator.kt app/src/test/java/com/crazystudio/sportrecorder/domain/insights/InsightsAggregatorTest.kt
git commit -m "Assemble full Insights compute (calendar, streak, stats, photos, locations)" -m "Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 7: UI state + ViewModel

**Files:**
- Create: `app/src/main/java/com/crazystudio/sportrecorder/ui/insights/InsightsUiState.kt`
- Create: `app/src/main/java/com/crazystudio/sportrecorder/ui/insights/InsightsViewModel.kt`
- Test: `app/src/test/java/com/crazystudio/sportrecorder/ui/insights/InsightsViewModelTest.kt`

- [ ] **Step 1: Create the UI state**

```kotlin
package com.crazystudio.sportrecorder.ui.insights

import com.crazystudio.sportrecorder.domain.insights.InsightsResult
import com.crazystudio.sportrecorder.domain.insights.Period

data class InsightsUiState(
    val period: Period = Period.MONTH,
    val monthAnchor: Long = 0L,
    val result: InsightsResult = InsightsResult.EMPTY,
)
```

- [ ] **Step 2: Write the failing ViewModel test**

```kotlin
package com.crazystudio.sportrecorder.ui.insights

import app.cash.turbine.test
import com.crazystudio.sportrecorder.domain.model.EatRecord
import com.crazystudio.sportrecorder.domain.insights.Period
import com.crazystudio.sportrecorder.domain.usecase.ObserveEatRecordsUseCase
import com.crazystudio.sportrecorder.fake.FakeDietSettingsRepository
import com.crazystudio.sportrecorder.fake.FakeEatRecordRepository
import com.crazystudio.sportrecorder.testutil.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalCoroutinesApi::class)
class InsightsViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private val fixedNow = 1_700_000_000_000L
    private fun h(n: Long) = TimeUnit.HOURS.toMillis(n)
    private fun record(id: Int, time: Long) =
        EatRecord(id = id, time = time, location = null, note = null, photos = emptyList())

    private fun viewModel(repo: FakeEatRecordRepository) = InsightsViewModel(
        observeEatRecords = ObserveEatRecordsUseCase(repo),
        dietSettingsRepository = FakeDietSettingsRepository(),
        now = { fixedNow },
    )

    @Test
    fun uiState_reflectsRecordsAndDefaultsToMonth() = runTest(mainRule.testDispatcher.scheduler) {
        val repo = FakeEatRecordRepository(initial = listOf(record(1, fixedNow - h(3)), record(2, fixedNow - h(1))))
        val vm = viewModel(repo)

        vm.uiState.test {
            awaitItem() // StateFlow initial (empty)
            val loaded = awaitItem()
            assertEquals(Period.MONTH, loaded.period)
            assertEquals(2, loaded.result.stats.mealCount)
            assertTrue(loaded.result.calendarDays.isNotEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun setPeriod_updatesState() = runTest(mainRule.testDispatcher.scheduler) {
        val repo = FakeEatRecordRepository()
        val vm = viewModel(repo)

        vm.uiState.test {
            awaitItem()
            vm.setPeriod(Period.WEEK)
            val updated = awaitItem()
            assertEquals(Period.WEEK, updated.period)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'; .\gradlew.bat testDebugUnitTest --tests "*InsightsViewModelTest" --console=plain`
Expected: FAIL — `InsightsViewModel` unresolved.

- [ ] **Step 4: Create the ViewModel**

```kotlin
package com.crazystudio.sportrecorder.ui.insights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.crazystudio.sportrecorder.domain.insights.InsightsAggregator
import com.crazystudio.sportrecorder.domain.insights.Period
import com.crazystudio.sportrecorder.domain.repository.DietSettingsRepository
import com.crazystudio.sportrecorder.domain.usecase.ObserveEatRecordsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.util.Calendar
import javax.inject.Inject

@HiltViewModel
class InsightsViewModel(
    observeEatRecords: ObserveEatRecordsUseCase,
    dietSettingsRepository: DietSettingsRepository,
    private val now: () -> Long,
) : ViewModel() {

    @Inject
    constructor(
        observeEatRecords: ObserveEatRecordsUseCase,
        dietSettingsRepository: DietSettingsRepository,
    ) : this(observeEatRecords, dietSettingsRepository, System::currentTimeMillis)

    private val period = MutableStateFlow(Period.MONTH)
    private val monthAnchor = MutableStateFlow(now())

    val uiState: StateFlow<InsightsUiState> =
        combine(
            observeEatRecords(),
            dietSettingsRepository.settings,
            period,
            monthAnchor,
        ) { records, settings, selectedPeriod, anchor ->
            InsightsUiState(
                period = selectedPeriod,
                monthAnchor = anchor,
                result = InsightsAggregator.compute(records, settings, now(), selectedPeriod, anchor),
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), InsightsUiState())

    fun setPeriod(value: Period) {
        period.value = value
    }

    fun shiftMonth(months: Int) {
        monthAnchor.value = Calendar.getInstance().apply {
            timeInMillis = monthAnchor.value
            add(Calendar.MONTH, months)
        }.timeInMillis
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: same `--tests "*InsightsViewModelTest"` command. Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/crazystudio/sportrecorder/ui/insights/InsightsUiState.kt app/src/main/java/com/crazystudio/sportrecorder/ui/insights/InsightsViewModel.kt app/src/test/java/com/crazystudio/sportrecorder/ui/insights/InsightsViewModelTest.kt
git commit -m "Add InsightsViewModel + UI state" -m "Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 8: String resources + tab icon

**Files:**
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-zh-rTW/strings.xml`
- Create: `app/src/main/res/drawable/ic_baseline_insights_24.xml`

- [ ] **Step 1: Add English strings** (insert before `</resources>` in `values/strings.xml`)

```xml
    <string name="title_insights">Insights</string>
    <string name="insights_card_adherence">Fasting adherence</string>
    <string name="insights_card_stats">Stats</string>
    <string name="insights_card_photos">Meals</string>
    <string name="insights_card_locations">Places</string>
    <string name="insights_streak">%d-day streak</string>
    <string name="insights_period_week">Week</string>
    <string name="insights_period_month">Month</string>
    <string name="insights_stat_meals">Meals</string>
    <string name="insights_stat_first">Avg first meal</string>
    <string name="insights_stat_last">Avg last meal</string>
    <string name="insights_stat_late">Late-night days</string>
    <string name="insights_empty_photos">No meal photos in this period</string>
    <string name="insights_empty_locations">No places recorded in this period</string>
    <string name="insights_value_none" translatable="false">—</string>
    <string name="insights_location_count">📍 %1$.4f, %2$.4f · %3$d</string>
```

- [ ] **Step 2: Add Traditional-Chinese strings** (insert before `</resources>` in `values-zh-rTW/strings.xml`)

```xml
    <string name="title_insights">回顧</string>
    <string name="insights_card_adherence">斷食達成</string>
    <string name="insights_card_stats">統計</string>
    <string name="insights_card_photos">餐點</string>
    <string name="insights_card_locations">地點</string>
    <string name="insights_streak">連續 %d 天</string>
    <string name="insights_period_week">週</string>
    <string name="insights_period_month">月</string>
    <string name="insights_stat_meals">用餐次數</string>
    <string name="insights_stat_first">平均第一餐</string>
    <string name="insights_stat_last">平均最後一餐</string>
    <string name="insights_stat_late">深夜進食天數</string>
    <string name="insights_empty_photos">這段期間沒有餐點照片</string>
    <string name="insights_empty_locations">這段期間沒有地點紀錄</string>
```

(The `insights_value_none` and `insights_location_count` strings are shared from the default file.)

- [ ] **Step 3: Create the tab icon**

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24"
    android:tint="?attr/colorControlNormal">
    <path
        android:fillColor="@android:color/white"
        android:pathData="M5,9.2h3V19H5V9.2zM10.6,5h2.8v14h-2.8V5zM16.2,13H19v6h-2.8V13z" />
</vector>
```

- [ ] **Step 4: Verify resources compile**

Run: `$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'; .\gradlew.bat processDebugResources --console=plain`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/res/values/strings.xml app/src/main/res/values-zh-rTW/strings.xml app/src/main/res/drawable/ic_baseline_insights_24.xml
git commit -m "Add Insights strings (en + zh-rTW) and tab icon" -m "Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 9: InsightsScreen Compose UI

No Compose UI tests are configured in this project, so this task is verified by **compile + manual emulator check** (Task 11), not unit tests. Keep it lean Material 3.

**Files:**
- Create: `app/src/main/java/com/crazystudio/sportrecorder/ui/insights/InsightsScreen.kt`

- [ ] **Step 1: Create the screen with the four cards**

```kotlin
package com.crazystudio.sportrecorder.ui.insights

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.crazystudio.sportrecorder.R
import com.crazystudio.sportrecorder.domain.insights.AdherenceState
import com.crazystudio.sportrecorder.domain.insights.DayCell
import com.crazystudio.sportrecorder.domain.insights.InsightsStats
import com.crazystudio.sportrecorder.domain.insights.LocationCount
import com.crazystudio.sportrecorder.domain.insights.Period
import com.crazystudio.sportrecorder.ui.diet.record.FullScreenPhotoViewer
import com.crazystudio.sportrecorder.util.PhotoStorage
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

private const val WEEK_COLUMNS = 7
private val monthFormat = SimpleDateFormat("yyyy / MM", Locale.getDefault())

@Composable
fun InsightsScreen(
    state: InsightsUiState,
    onSelectPeriod: (Period) -> Unit,
    onShiftMonth: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var fullScreen by remember { mutableStateOf<Pair<List<String>, Int>?>(null) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        PeriodSelector(state.period, onSelectPeriod)
        AdherenceCard(state.result.calendarDays, state.result.streak, state.monthAnchor, onShiftMonth)
        StatsCard(state.result.stats)
        PhotoWallCard(state.result.photoFileNames) { index ->
            fullScreen = state.result.photoFileNames to index
        }
        LocationsCard(state.result.locations)
    }

    fullScreen?.let { (names, index) ->
        FullScreenPhotoViewer(fileNames = names, initialIndex = index, onDismiss = { fullScreen = null })
    }
}

@Composable
private fun PeriodSelector(period: Period, onSelect: (Period) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = period == Period.WEEK,
            onClick = { onSelect(Period.WEEK) },
            label = { Text(stringResource(R.string.insights_period_week)) },
        )
        FilterChip(
            selected = period == Period.MONTH,
            onClick = { onSelect(Period.MONTH) },
            label = { Text(stringResource(R.string.insights_period_month)) },
        )
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

@Composable
private fun AdherenceCard(days: List<DayCell>, streak: Int, monthAnchor: Long, onShiftMonth: (Int) -> Unit) {
    SectionCard(stringResource(R.string.insights_card_adherence)) {
        Text(stringResource(R.string.insights_streak, streak), style = MaterialTheme.typography.bodyLarge)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onShiftMonth(-1) }) {
                Icon(painterResource(R.drawable.ic_arrow_left_24dp), contentDescription = "Previous month")
            }
            Text(
                text = monthFormat.format(monthAnchor),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
            )
            IconButton(onClick = { onShiftMonth(1) }) {
                Icon(painterResource(R.drawable.ic_arrow_right_24dp), contentDescription = "Next month")
            }
        }
        CalendarGrid(days)
    }
}

@Composable
private fun CalendarGrid(days: List<DayCell>) {
    if (days.isEmpty()) return
    val leadingBlanks = remember(days.first().dayStart) {
        Calendar.getInstance().apply { timeInMillis = days.first().dayStart }
            .get(Calendar.DAY_OF_WEEK) - 1 // Sunday = 0 leading blanks
    }
    val cells: List<DayCell?> = List(leadingBlanks) { null } + days
    cells.chunked(WEEK_COLUMNS).forEach { week ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            week.forEach { cell -> DayBox(cell, Modifier.weight(1f)) }
            repeat(WEEK_COLUMNS - week.size) { Box(Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun DayBox(cell: DayCell?, modifier: Modifier) {
    val color = when (cell?.state) {
        AdherenceState.ON_TARGET -> MaterialTheme.colorScheme.primary
        AdherenceState.OFF_TARGET -> MaterialTheme.colorScheme.error
        AdherenceState.NO_DATA -> MaterialTheme.colorScheme.surfaceVariant
        null -> Color.Transparent
    }
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(6.dp))
            .background(color),
        contentAlignment = Alignment.Center,
    ) {
        if (cell != null) {
            Text(
                text = cell.dayOfMonth.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = if (cell.state == AdherenceState.NO_DATA) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onPrimary
                },
            )
        }
    }
}

@Composable
private fun StatsCard(stats: InsightsStats) {
    SectionCard(stringResource(R.string.insights_card_stats)) {
        StatRow(stringResource(R.string.insights_stat_meals), stats.mealCount.toString())
        StatRow(stringResource(R.string.insights_stat_first), formatMinutes(stats.avgFirstMealMinutes))
        StatRow(stringResource(R.string.insights_stat_last), formatMinutes(stats.avgLastMealMinutes))
        StatRow(stringResource(R.string.insights_stat_late), stats.lateNightDays.toString())
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun formatMinutes(minutes: Int?): String {
    if (minutes == null) return stringResource(R.string.insights_value_none)
    return "%02d:%02d".format(minutes / 60, minutes % 60)
}

@Composable
private fun PhotoWallCard(fileNames: List<String>, onClick: (Int) -> Unit) {
    SectionCard(stringResource(R.string.insights_card_photos)) {
        if (fileNames.isEmpty()) {
            Text(stringResource(R.string.insights_empty_photos), style = MaterialTheme.typography.bodyMedium)
            return@SectionCard
        }
        val context = LocalContext.current
        fileNames.withIndex().chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                row.forEach { (index, name) ->
                    AsyncImage(
                        model = PhotoStorage.fileFor(context, name),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onClick(index) },
                    )
                }
                repeat(3 - row.size) { Box(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun LocationsCard(locations: List<LocationCount>) {
    SectionCard(stringResource(R.string.insights_card_locations)) {
        if (locations.isEmpty()) {
            Text(stringResource(R.string.insights_empty_locations), style = MaterialTheme.typography.bodyMedium)
            return@SectionCard
        }
        locations.forEach { loc ->
            Text(
                text = stringResource(R.string.insights_location_count, loc.lat, loc.lng, loc.count),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
```

- [ ] **Step 2: Confirm the arrow drawables exist (the calendar month pager uses them)**

Run: `ls app/src/main/res/drawable/ic_arrow_left_24dp.xml app/src/main/res/drawable/ic_arrow_right_24dp.xml`
- If **both exist**, continue.
- If **missing**, create each as a vector (left example below; mirror the pathData for right):

`ic_arrow_left_24dp.xml`:
```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp" android:height="24dp"
    android:viewportWidth="24" android:viewportHeight="24"
    android:tint="?attr/colorControlNormal">
    <path android:fillColor="@android:color/white"
        android:pathData="M15.41,7.41L14,6l-6,6 6,6 1.41,-1.41L10.83,12z" />
</vector>
```
`ic_arrow_right_24dp.xml`:
```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp" android:height="24dp"
    android:viewportWidth="24" android:viewportHeight="24"
    android:tint="?attr/colorControlNormal">
    <path android:fillColor="@android:color/white"
        android:pathData="M10,6L8.59,7.41 13.17,12l-4.58,4.59L10,18l6,-6z" />
</vector>
```

- [ ] **Step 3: Compile**

Run: `$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'; .\gradlew.bat compileDebugKotlin --console=plain`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/crazystudio/sportrecorder/ui/insights/InsightsScreen.kt app/src/main/res/drawable/ic_arrow_left_24dp.xml app/src/main/res/drawable/ic_arrow_right_24dp.xml
git commit -m "Add InsightsScreen with adherence/stats/photos/locations cards" -m "Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

(If the arrow drawables already existed, drop them from the `git add`.)

---

## Task 10: Wire the Insights tab into navigation

**Files:**
- Modify: `app/src/main/java/com/crazystudio/sportrecorder/ui/nav/Route.kt`
- Modify: `app/src/main/java/com/crazystudio/sportrecorder/ui/AppRoot.kt`
- Delete: `app/src/main/java/com/crazystudio/sportrecorder/ui/notifications/NotificationsScreen.kt`

- [ ] **Step 1: Rename the route** — in `Route.kt`, replace:

```kotlin
    @Serializable data object Notifications : Route
```
with:
```kotlin
    @Serializable data object Insights : Route
```

- [ ] **Step 2: Update imports in `AppRoot.kt`** — replace:

```kotlin
import com.crazystudio.sportrecorder.ui.notifications.NotificationsScreen
```
with:
```kotlin
import com.crazystudio.sportrecorder.ui.insights.InsightsScreen
import com.crazystudio.sportrecorder.ui.insights.InsightsViewModel
```

- [ ] **Step 3: Update the tab list** — replace the `tabs` list (lines ~64-68) with:

```kotlin
    val tabs = listOf(
        Tab(Route.Diet, "Home", R.drawable.ic_home_black_24dp),
        Tab(Route.Record, "Record", R.drawable.ic_dashboard_black_24dp),
        Tab(Route.Insights, "Insights", R.drawable.ic_baseline_insights_24),
    )
```

- [ ] **Step 4: Update the nav-selection `when`** — replace the `Route.Notifications` branch:

```kotlin
                                Route.Notifications -> dest.hasRoute(Route.Notifications::class)
```
with:
```kotlin
                                Route.Insights -> dest.hasRoute(Route.Insights::class)
```

- [ ] **Step 5: Replace the NavHost entry** — replace:

```kotlin
                composable<Route.Notifications> { NotificationsScreen() }
```
with:
```kotlin
                composable<Route.Insights> {
                    val vm: InsightsViewModel = hiltViewModel()
                    val state by vm.uiState.collectAsStateWithLifecycle()
                    InsightsScreen(
                        state = state,
                        onSelectPeriod = vm::setPeriod,
                        onShiftMonth = vm::shiftMonth,
                    )
                }
```

- [ ] **Step 6: Delete the placeholder screen**

```bash
git rm app/src/main/java/com/crazystudio/sportrecorder/ui/notifications/NotificationsScreen.kt
```

- [ ] **Step 7: Compile**

Run: `$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'; .\gradlew.bat compileDebugKotlin --console=plain`
Expected: BUILD SUCCESSFUL, no references to `Route.Notifications` / `NotificationsScreen` remain.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/crazystudio/sportrecorder/ui/nav/Route.kt app/src/main/java/com/crazystudio/sportrecorder/ui/AppRoot.kt
git commit -m "Replace Notifications tab with Insights tab" -m "Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 11: Full verification (build + test + detekt + manual)

**Files:** none (verification only).

- [ ] **Step 1: Full build + tests + detekt**

Run: `$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'; .\gradlew.bat build test --console=plain`
Expected: **BUILD SUCCESSFUL**; all unit tests pass; detekt clean. If detekt flags the new code (e.g. MagicNumber in the aggregator, LongParameterList, MaxLineLength), fix per the existing repo pattern (named constants, group params, wrap lines) and re-run.

- [ ] **Step 2: Install on the running emulator** (boot `Pixel_10_Pro` first if needed)

Run: `$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'; .\gradlew.bat installDebug --console=plain`
Expected: `Installed on 1 device.`

- [ ] **Step 3: Manual check** — launch the app, open the **回顧 / Insights** tab (3rd tab). Verify:
  - The adherence calendar renders a month grid with colored day cells; the streak line shows a number; prev/next month arrows page the calendar.
  - The stats card shows meal count + avg first/last meal times + late-night days.
  - The Week/Month chip toggles and the stats/photo wall update.
  - The photo wall shows meal photos; tapping one opens the full-screen viewer (swipe/zoom work); dismiss returns.
  - The locations card lists places or shows the empty state.
  - Labels appear in the device language (zh-rTW shows 回顧 / 斷食達成 / 統計 / 餐點 / 地點).

- [ ] **Step 4: Final commit** (only if Step 1 required fixes)

```bash
git add -A
git commit -m "Fix Insights detekt/build findings" -m "Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Self-review notes (verified against spec)

- **Placement** (repurpose Notifications tab) → Tasks 8–10.
- **Adherence rule** (eating window ≤ eating-hours goal; ≥1 meal; no-meal = neutral) → Task 2 + Task 3.
- **Streak** (consecutive on-target; empty today neutral; off-target/gap breaks) → Task 4.
- **Period** (Week/Month toggle drives stats+photos; calendar always monthly + pageable) → Tasks 5, 7, 9.
- **Four cards** (adherence+streak, stats, photo wall, locations) → Tasks 3/4, 5, 6, 9.
- **Photo viewer reuse** (`FullScreenPhotoViewer(fileNames, initialIndex, onDismiss)`) → Task 9.
- **Localization** (en + zh-rTW) → Task 8.
- **Defaults from spec**: 22:00 late-night threshold (`LATE_NIGHT_HOUR`), current-target caveat (uses `settings.eatingHours` for historical days — documented), location grouping ~100 m (`LOCATION_ROUNDING`).
- **Type consistency**: `compute(records, settings, now, period, monthAnchor)`, `InsightsResult` fields (`calendarDays`, `streak`, `stats`, `photoFileNames`, `locations`), `setPeriod`/`shiftMonth` used identically across ViewModel, screen, and AppRoot wiring.
