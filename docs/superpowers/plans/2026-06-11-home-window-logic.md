# Home Window Logic + Display Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rewrite the Home eating/fasting window logic to the clarified model behind a pure, unit-tested `DietWindow` calculator, wire `DietViewModel` to it, and show clearer status + a time-info line on the Home timer.

**Architecture:** A pure `DietWindow.compute(eatTimesAsc, eatingHours, fastingHours, now)` returns a `DietWindowState` (phase/ring/elapsed/times). `DietViewModel` becomes a thin wrapper (DAO eat-times + per-second ticker + prefs → `compute` → `DietUiState`). `DietScreen` renders the phase-correct ring + a localized time-info line. The 5-day history bars are untouched.

**Tech Stack:** Kotlin, JUnit 4 (unit tests), Jetpack Compose, Hilt, Room (unchanged).

**Verification model:** `DietWindowTest` (pure JUnit) is the primary gate for the logic; then `assembleDebug`/`:app:check`/emulator for the UI. No behavior is left untested — the calculator is fully covered.

**Environment (project memory):** `java` not on PATH — before every Gradle command: `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"` then `.\gradlew.bat <tasks>` from `C:\Users\Aidan\SportRecorder`. Emulator `Pixel_10_Pro` (API 37). **Commit hygiene:** stage only named paths (never `git add -A`); commit trailer `Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>`. detekt has **no baseline** now, so new files must pass detekt (run `.\gradlew.bat :app:detekt`).

**Branch:** `feat/home-window-logic` (spec already committed here).

**Model recap** (F = current window first eat, L = last eat, Eh = eatingHours, Fh = fastingHours): Idle (no eats); Eating while `now < F+Eh` (ring `(now−F)/Eh`, elapsed = remaining `(F+Eh)−now`); Fasting while `now ≥ F+Eh` and `now−L < Fh` (ring `(now−L)/Fh`, elapsed `now−L`); Success when `now−L ≥ Fh`. New window starts when an eat lands after `F+Eh`.

---

## Task 1 — Pure `DietWindow` calculator (TDD)

**Files:**
- Create: `app/src/test/java/com/crazystudio/sportrecorder/ui/diet/DietWindowTest.kt`
- Create: `app/src/main/java/com/crazystudio/sportrecorder/ui/diet/DietWindow.kt`

- [ ] **Step 1: Write the failing test** — create `DietWindowTest.kt`:

```kotlin
package com.crazystudio.sportrecorder.ui.diet

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.TimeUnit

class DietWindowTest {
    private val eh = 8L
    private val fh = 16L
    private val ehMs = TimeUnit.HOURS.toMillis(eh)
    private val fhMs = TimeUnit.HOURS.toMillis(fh)
    private val f = 1_700_000_000_000L
    private fun h(n: Long) = TimeUnit.HOURS.toMillis(n)

    @Test fun noEats_isIdle() {
        val s = DietWindow.compute(emptyList(), eh, fh, f)
        assertEquals(DietPhase.IDLE, s.phase)
        assertEquals(0f, s.ringProgress, 0f)
    }

    @Test fun singleEat_duringWindow_isEating() {
        val s = DietWindow.compute(listOf(f), eh, fh, f + h(2))
        assertEquals(DietPhase.EATING, s.phase)
        assertEquals(0.25f, s.ringProgress, 0.001f)
        assertEquals(h(6), s.elapsedMillis)
        assertEquals(f, s.windowStart)
        assertEquals(f + ehMs, s.windowEnd)
    }

    @Test fun lastBiteBeforeWindowClose_stillEating() {
        val s = DietWindow.compute(listOf(f, f + h(4)), eh, fh, f + h(5))
        assertEquals(DietPhase.EATING, s.phase)
        assertEquals(h(3), s.elapsedMillis)
    }

    @Test fun afterWindowClose_fastingCountsFromLastBite() {
        val s = DietWindow.compute(listOf(f, f + h(4)), eh, fh, f + h(9))
        assertEquals(DietPhase.FASTING, s.phase)
        assertEquals(h(5), s.elapsedMillis)
        assertEquals(5f / 16f, s.ringProgress, 0.001f)
        assertEquals(f + h(4) + fhMs, s.fastTargetAt)
    }

    @Test fun fastingInProgress() {
        val s = DietWindow.compute(listOf(f), eh, fh, f + h(12))
        assertEquals(DietPhase.FASTING, s.phase)
        assertEquals(h(12), s.elapsedMillis)
        assertEquals(12f / 16f, s.ringProgress, 0.001f)
    }

    @Test fun success_whenFastReachesTarget() {
        val s = DietWindow.compute(listOf(f), eh, fh, f + h(16))
        assertEquals(DietPhase.SUCCESS, s.phase)
        assertEquals(1f, s.ringProgress, 0f)
        assertEquals(h(16), s.elapsedMillis)
    }

    @Test fun eatAfterWindowClose_startsNewWindow() {
        val s = DietWindow.compute(listOf(f, f + h(4), f + h(10)), eh, fh, f + h(11))
        assertEquals(DietPhase.EATING, s.phase)
        assertEquals(f + h(10), s.windowStart)
        assertEquals(f + h(10) + ehMs, s.windowEnd)
        assertEquals(f + h(10), s.lastEat)
    }

    @Test fun boundary_exactlyAtWindowEnd_flipsToFasting() {
        val s = DietWindow.compute(listOf(f), eh, fh, f + ehMs)
        assertEquals(DietPhase.FASTING, s.phase)
        assertEquals(ehMs, s.elapsedMillis)
    }

    @Test fun boundary_exactlyAtFastTarget_isSuccess() {
        val s = DietWindow.compute(listOf(f), eh, fh, f + fhMs)
        assertEquals(DietPhase.SUCCESS, s.phase)
    }

    @Test fun multipleEatsInWindow_lastEatIsLatest() {
        val s = DietWindow.compute(listOf(f, f + h(1), f + h(3)), eh, fh, f + h(9))
        assertEquals(DietPhase.FASTING, s.phase)
        assertEquals(f + h(3), s.lastEat)
        assertEquals(h(6), s.elapsedMillis)
    }
}
```

- [ ] **Step 2: Run the test, watch it fail** — `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :app:testDebugUnitTest --tests "*DietWindowTest*"` → FAILS to compile (`DietWindow`/`DietPhase` unresolved). Expected.

- [ ] **Step 3: Implement `DietWindow.kt`** to make it pass:

```kotlin
package com.crazystudio.sportrecorder.ui.diet

import java.util.concurrent.TimeUnit

enum class DietPhase { IDLE, EATING, FASTING, SUCCESS }

data class DietWindowState(
    val phase: DietPhase,
    val ringProgress: Float = 0f,
    val elapsedMillis: Long = 0L,
    val windowStart: Long? = null,
    val windowEnd: Long? = null,
    val lastEat: Long? = null,
    val fastTargetAt: Long? = null,
)

/** Pure (Android-free) eating/fasting window calculator. */
object DietWindow {
    fun compute(
        eatTimesAsc: List<Long>,
        eatingHours: Long,
        fastingHours: Long,
        now: Long,
    ): DietWindowState {
        if (eatTimesAsc.isEmpty()) return DietWindowState(phase = DietPhase.IDLE)

        val ehMillis = TimeUnit.HOURS.toMillis(eatingHours)
        val fhMillis = TimeUnit.HOURS.toMillis(fastingHours)

        // Current eating window = most recent cluster; a new window starts when an
        // eat lands after firstEat + eatingHours.
        var first = eatTimesAsc[0]
        var last = eatTimesAsc[0]
        for (t in eatTimesAsc) {
            if (t > first + ehMillis) {
                first = t
                last = t
            } else {
                last = t
            }
        }

        val windowEnd = first + ehMillis
        val fastTargetAt = last + fhMillis

        return when {
            now < windowEnd -> DietWindowState(
                phase = DietPhase.EATING,
                ringProgress = ((now - first).toFloat() / ehMillis).coerceIn(0f, 1f),
                elapsedMillis = (windowEnd - now).coerceAtLeast(0L),
                windowStart = first,
                windowEnd = windowEnd,
                lastEat = last,
                fastTargetAt = fastTargetAt,
            )
            now - last >= fhMillis -> DietWindowState(
                phase = DietPhase.SUCCESS,
                ringProgress = 1f,
                elapsedMillis = now - last,
                windowStart = first,
                windowEnd = windowEnd,
                lastEat = last,
                fastTargetAt = fastTargetAt,
            )
            else -> DietWindowState(
                phase = DietPhase.FASTING,
                ringProgress = ((now - last).toFloat() / fhMillis).coerceIn(0f, 1f),
                elapsedMillis = now - last,
                windowStart = first,
                windowEnd = windowEnd,
                lastEat = last,
                fastTargetAt = fastTargetAt,
            )
        }
    }
}
```

- [ ] **Step 4: Run the test, watch it pass** — `.\gradlew.bat :app:testDebugUnitTest --tests "*DietWindowTest*"` → BUILD SUCCESSFUL (10 tests pass). Also `.\gradlew.bat :app:detekt` → green (new files pass detekt; no baseline).

- [ ] **Step 5: Commit**
```
git add app/src/main/java/com/crazystudio/sportrecorder/ui/diet/DietWindow.kt app/src/test/java/com/crazystudio/sportrecorder/ui/diet/DietWindowTest.kt
git commit -m "[Feat] Pure DietWindow calculator + unit tests for eating/fasting phases"
```

---

## Task 2 — Strings, UiState fields, DietViewModel rewrite

**Files:**
- Modify: `app/src/main/res/values/strings.xml`, `app/src/main/res/values-zh-rTW/strings.xml`
- Modify: `app/src/main/java/com/crazystudio/sportrecorder/ui/diet/DietUiState.kt`
- Modify: `app/src/main/java/com/crazystudio/sportrecorder/ui/diet/DietViewModel.kt`

- [ ] **Step 1: Add strings to `values/strings.xml`** (inside `<resources>`):
```xml
    <string name="diet_eating_window">Eating window %1$s – %2$s</string>
    <string name="diet_fast_target">Ends at %1$s</string>
    <string name="diet_fast_done">Goal reached</string>
    <string name="diet_no_record">Add your first meal</string>
```

- [ ] **Step 2: Add matching `values-zh-rTW/strings.xml`** entries:
```xml
    <string name="diet_eating_window">進食視窗 %1$s – %2$s</string>
    <string name="diet_fast_target">預計完成 %1$s</string>
    <string name="diet_fast_done">已達標</string>
    <string name="diet_no_record">新增第一餐</string>
```
(After adding, the lint baseline won't list these as missing because both locales have them. Run `.\gradlew.bat :app:updateLintBaseline` only if a stale MissingTranslation surfaces; normally not needed.)

- [ ] **Step 3: Add time-info fields to `DietUiState`.** Read `DietUiState.kt`; add three fields with defaults (keep all existing fields):
```kotlin
    val timeInfoRes: Int = 0,        // 0 = no time-info line
    val timeInfoArg1: String = "",
    val timeInfoArg2: String = "",
```

- [ ] **Step 4: Rewrite `DietViewModel` to use `DietWindow`.** Read the current `DietViewModel.kt`. Make these changes:
  - Change the private `DietData` to carry the ascending eat-time list instead of a merged pair:
    ```kotlin
    private data class DietData(
        val eatTimesAsc: List<Long>,
        val history: List<Pair<Long, Float>>,
    )
    ```
  - Update `dietDataFlow`:
    ```kotlin
    private val dietDataFlow: Flow<DietData> = historyFlow().map { data ->
        DietData(
            eatTimesAsc = data.map { it.time },   // flowByTimeInterval is ORDER BY time ASC
            history = computeHistory(data),
        )
    }
    ```
  - Replace `buildState(...)` entirely with:
    ```kotlin
    private fun buildState(dietData: DietData): DietUiState {
        val eatingHours = dietPreference.preference.getLong(Constants.DIET_EATING_TIME_INTERVAL, 8)
        val fastingHours = dietPreference.preference.getLong(Constants.DIET_FASTING_TIME_INTERVAL, 16)
        val fastingLabel = "%d : %d".format(fastingHours, eatingHours)
        val selectedFastingItem = FastingItem.defaultFastingItems.firstOrNull {
            it.fastingHours == fastingHours && it.eatingHours == eatingHours
        }
        val history = dietData.history.map { (date, ratio) ->
            DietUiState.HistoryBar(dateMillis = date, ratio = ratio)
        }

        val s = DietWindow.compute(
            dietData.eatTimesAsc, eatingHours, fastingHours, System.currentTimeMillis(),
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
        if (millis == null) "" else HM_FORMAT.format(java.util.Date(millis))
    ```
  - Add to the `companion object`: `private val HM_FORMAT = SimpleDateFormat("HH:mm", Locale.getDefault())`.
  - **Delete** the now-unused `mergeInterval(...)` and `lastEatingTime(...)` functions (the merged-pair path is gone). KEEP `historyFlow`, `computeHistory`, `mergeIntervalWithFourHours`, `effectiveProgress`, `formatElapsed` (history is unchanged).
  - Add imports: `DietPhase`/`DietWindow` are same-package (no import needed); ensure `R`, `SimpleDateFormat`, `Locale`, `Date` imports exist.

- [ ] **Step 5: Build + run all unit tests** — `.\gradlew.bat assembleDebug :app:testDebugUnitTest` → BUILD SUCCESSFUL (DietWindowTest still green; VM compiles against DietWindow). `.\gradlew.bat :app:detekt` → green.

- [ ] **Step 6: Commit**
```
git add app/src/main/res/values/strings.xml app/src/main/res/values-zh-rTW/strings.xml app/src/main/java/com/crazystudio/sportrecorder/ui/diet/DietUiState.kt app/src/main/java/com/crazystudio/sportrecorder/ui/diet/DietViewModel.kt
git commit -m "[Feat] Wire DietViewModel to DietWindow; add phase-correct status + time info"
```

---

## Task 3 — Home screen time-info line

**Files:** Modify `app/src/main/java/com/crazystudio/sportrecorder/ui/diet/DietScreen.kt`

- [ ] **Step 1: Read `DietScreen.kt`** to find the center column (status icon / status text / prompt / `state.elapsedText`).

- [ ] **Step 2: Add a time-info `Text` under the elapsed-time text.** Where the elapsed time is shown in the circle center, add below it:
```kotlin
if (state.timeInfoRes != 0) {
    Text(
        text = stringResource(state.timeInfoRes, state.timeInfoArg1, state.timeInfoArg2),
        fontSize = 14.sp,
        color = grey_1,
        modifier = Modifier.padding(top = 4.dp),
    )
}
```
(Ensure imports: `androidx.compose.ui.res.stringResource`, `com.crazystudio.sportrecorder.ui.theme.grey_1`, `androidx.compose.ui.unit.sp`, `androidx.compose.ui.unit.dp` — most already present. `stringResource(id, vararg)` accepts the two args; pass both even when the template only uses `%1$s` — the extra arg is ignored.)

- [ ] **Step 3: Build + run on emulator** — `.\gradlew.bat assembleDebug` then `.\gradlew.bat installDebug`. Verify on the Home screen: with a recent eat record the timer shows the right phase. To exercise phases without waiting, create eat records via the FAB with **past times** (the editor's date/time pickers let you back-date): e.g. set an eat ~10h ago with a 16:8 schedule → Home shows **Fasting**, ring `(now−lastEat)/16h`, and "預計完成 HH:mm"; set an eat a few minutes ago → **Eating**, ring small, "進食視窗 HH:mm–HH:mm". Screenshot to `%TEMP%\sr_home.png`. `adb logcat -d -t 150` no FATAL EXCEPTION.

- [ ] **Step 4: Commit**
```
git add app/src/main/java/com/crazystudio/sportrecorder/ui/diet/DietScreen.kt
git commit -m "[Feat] Home: show eating-window / fast-completion time info line"
```

---

## Task 4 — Final verification

**Files:** none.

- [ ] **Step 1:** `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat clean assembleDebug assembleRelease testDebugUnitTest :app:check` → all `BUILD SUCCESSFUL` (incl. `DietWindowTest` + detekt with no baseline + lint).
- [ ] **Step 2: Emulator phase walk** — back-date eat records to drive Eating → Fasting → Success; confirm the ring progress and the time-info line match the model (eating ring grows to full then resets to fasting ring; fasting elapsed counts from the last bite). Screenshot each phase to `%TEMP%`.
- [ ] **Step 3:** No commit (verification only).

---

## Self-Review (author check vs. spec)

- **Spec coverage:** pure calculator + state → Task 1 (`DietWindow.kt`); clustering rule (new window after `F+Eh`) → Task 1 Step 3 loop; four phases incl. fasting-from-last-bite → Task 1 Step 3 `when`; VM thin wrapper → Task 2 Step 4; time-info line (eating window / projected completion) → Task 2 (res + args) + Task 3 (UI); unit tests (~10 incl. key jump + boundaries + new window) → Task 1 Step 1; i18n strings (en + zh-rTW) → Task 2 Steps 1–2; history bars untouched (`mergeIntervalWithFourHours`/`computeHistory` kept) → Task 2 Step 4; idle prompt → `diet_no_record`. All spec sections map to tasks.
- **Placeholder scan:** full code for `DietWindow`, the test (10 concrete asserts), the VM `buildState`, the strings, and the UI snippet. Exact commands with expected results. No vague steps.
- **Type/name consistency:** `DietWindow.compute(eatTimesAsc, eatingHours, fastingHours, now)` and `DietWindowState(phase, ringProgress, elapsedMillis, windowStart, windowEnd, lastEat, fastTargetAt)` are identical across the test (Task 1 Step 1), implementation (Step 3), and VM usage (Task 2 Step 4). `DietUiState` new fields `timeInfoRes/Arg1/Arg2` defined in Task 2 Step 3 and consumed in Task 3 Step 2. `hm()`/`HM_FORMAT` defined and used in Task 2 Step 4.
- **Behavior/verification:** the calculator is the tested contract; the UI is exercised via back-dated records to hit each phase without waiting.
