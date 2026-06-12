# Status-Page Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Redesign the Home/Diet status screen — remove the recent-days bar chart, show the fast's start→end times, rework the ring gradient to run dark→bright along the arc, move the status text above the ring, and put the progress % where the status used to be.

**Architecture:** `DietWindow` (domain, already tested) is unchanged — only presentation changes. Phase 1 removes the history feature and adds a cross-day helper + new strings (each commit stays green; the only visible change is bars disappearing). Phase 2 does the coupled `DietUiState`/`DietViewModel`/`DietScreen` redesign, the `CircleProgress` gradient, and string cleanup, then emulator verification.

**Tech Stack:** Kotlin, Jetpack Compose, Hilt, Coroutines/Flow, JUnit4, detekt (no baseline) + Android Lint (baseline).

**Verification model:** Each phase builds green via `.\gradlew.bat assembleDebug :app:check`. Phase 2 ends with emulator screenshots of EATING / FASTING / SUCCESS / IDLE. CI gate = `assembleDebug testDebugUnitTest :app:detekt :app:lintDebug`.

**Environment:** Build commands run from `C:\Users\Aidan\SportRecorder` in **PowerShell** — set `$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'` before each `.\gradlew.bat` call (generous timeout, ~600000 ms). `gh` authenticated as `Aidan79225`.

**Branch:** `feat/status-page-redesign` (spec already committed here).

**detekt landmines:** `DietScreen`/`CircleProgress`/`DietViewModel`/`DietTimeFormat` are all under `ui/` where `MagicNumber` is excluded — sizes/angles as literals are fine. `UnsafeCallOnNullableType` (`!!`) IS active in `ui/` — avoid `!!`; use null-check smart-casts. No wildcard imports; remove now-unused imports (`NoUnusedImports` fails the build). No semicolons (`NoSemicolons`).

---

## Phase 1 — Remove history + prep (green; only visible change: bars gone)

### Task 1.1: Cross-day helper + test (TDD)

**Files:**
- Create: `app/src/main/java/com/crazystudio/sportrecorder/ui/diet/DietTimeFormat.kt`
- Create: `app/src/test/java/com/crazystudio/sportrecorder/ui/diet/DietTimeFormatTest.kt`

- [ ] **Step 1: Write the failing test:**

```kotlin
package com.crazystudio.sportrecorder.ui.diet

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class DietTimeFormatTest {
    private fun at(hour: Int, dayOffset: Int = 0): Long =
        Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, dayOffset)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    @Test fun sameDay_isNotNextDay() {
        assertFalse(fastWindowCrossesDay(at(hour = 8), at(hour = 20)))
    }

    @Test fun acrossMidnight_isNextDay() {
        assertTrue(fastWindowCrossesDay(at(hour = 20), at(hour = 10, dayOffset = 1)))
    }
}
```

- [ ] **Step 2: Run to confirm it fails** (`Unresolved reference: fastWindowCrossesDay`).

Run: `$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'; .\gradlew.bat testDebugUnitTest --tests "*DietTimeFormatTest"`
Expected: FAIL — unresolved reference.

- [ ] **Step 3: Implement the helper:**

```kotlin
package com.crazystudio.sportrecorder.ui.diet

import java.util.Calendar

/** True if [endMillis] falls on a later calendar day than [startMillis] (device-default time zone). */
fun fastWindowCrossesDay(startMillis: Long, endMillis: Long): Boolean {
    val start = Calendar.getInstance().apply { timeInMillis = startMillis }
    val end = Calendar.getInstance().apply { timeInMillis = endMillis }
    val startDay = start.get(Calendar.YEAR) to start.get(Calendar.DAY_OF_YEAR)
    val endDay = end.get(Calendar.YEAR) to end.get(Calendar.DAY_OF_YEAR)
    return endDay.first > startDay.first ||
        (endDay.first == startDay.first && endDay.second > startDay.second)
}
```

- [ ] **Step 4: Run to confirm it passes.**

Run: `$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'; .\gradlew.bat testDebugUnitTest --tests "*DietTimeFormatTest"`
Expected: PASS (2 tests).

- [ ] **Step 5: Commit** (trailer `Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>` on every commit):

```bash
git add app/src/main/java/com/crazystudio/sportrecorder/ui/diet/DietTimeFormat.kt app/src/test/java/com/crazystudio/sportrecorder/ui/diet/DietTimeFormatTest.kt
git commit -m "[Feature] Add fastWindowCrossesDay helper + test"
```

---

### Task 1.2: Add the fast-window strings (both locales)

**Files:**
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-zh-rTW/strings.xml`

- [ ] **Step 1: Add to `values/strings.xml`** (inside `<resources>`, next to the other `diet_*` strings, e.g. after `diet_no_record` on line 43):

```xml
    <string name="diet_fast_window">Fast %1$s → %2$s</string>
    <string name="diet_next_day">next day %1$s</string>
```

- [ ] **Step 2: Add to `values-zh-rTW/strings.xml`** (next to the other `diet_*` strings, e.g. after `diet_no_record` on line 42):

```xml
    <string name="diet_fast_window">斷食 %1$s → %2$s</string>
    <string name="diet_next_day">隔日 %1$s</string>
```

- [ ] **Step 3: Verify build.**

Run: `$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'; .\gradlew.bat assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit.**

```bash
git add app/src/main/res/values/strings.xml app/src/main/res/values-zh-rTW/strings.xml
git commit -m "[Feature] Add diet_fast_window + diet_next_day strings"
```

---

### Task 1.3: Remove the history feature (bars become empty; delete the calculator)

**Files:**
- Modify: `app/src/main/java/com/crazystudio/sportrecorder/domain/usecase/ObserveDietStateUseCase.kt`
- Modify: `app/src/main/java/com/crazystudio/sportrecorder/ui/diet/DietViewModel.kt`
- Delete: `app/src/main/java/com/crazystudio/sportrecorder/domain/diet/DietHistoryCalculator.kt`
- Delete: `app/src/test/java/com/crazystudio/sportrecorder/domain/diet/DietHistoryCalculatorTest.kt`

- [ ] **Step 1: Rewrite `ObserveDietStateUseCase.kt`** — drop `history` from `DietSnapshot` and the calculator call (keep the window bounds that feed `eatTimesAsc`; rename the now-misnamed const):

```kotlin
package com.crazystudio.sportrecorder.domain.usecase

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
    )

    /** [now] anchors the (fixed-at-subscription) window of recent eat times fed to DietWindow. */
    operator fun invoke(now: Long): Flow<DietSnapshot> {
        val before = dayStart(now, DAY_OFFSET_TOMORROW)
        val after = windowStart(now)
        return combine(
            eatRecordRepository.observeInWindow(after = after, before = before),
            dietSettingsRepository.settings,
        ) { records, settings ->
            DietSnapshot(eatTimesAsc = records.map { it.time }, settings = settings)
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
            set(Calendar.DAY_OF_YEAR, get(Calendar.DAY_OF_YEAR) - RECENT_WINDOW_DAYS)
            set(Calendar.HOUR_OF_DAY, WINDOW_START_HOUR)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    private companion object {
        const val DAY_OFFSET_TOMORROW = 1
        const val RECENT_WINDOW_DAYS = 8
        const val WINDOW_START_HOUR = 16
    }
}
```

- [ ] **Step 2: Edit `DietViewModel.kt`** — stop mapping history. In `buildState`, delete the `val history = snapshot.history.map { ... }` line (currently line 49) and remove `history = history,` from the `base = DietUiState(...)` constructor (currently line 61). Leave everything else (the `when(s.phase)` with `timeInfoRes`, `formatHistoryDate`, etc.) untouched for now. After this edit the `base` block reads:

```kotlin
        val base = DietUiState(
            progress = s.ringProgress * 100f,
            fastingLabel = fastingLabel,
            selectedFastingItem = selectedFastingItem,
            elapsedText = formatElapsed(s.elapsedMillis),
        )
```
(`DietUiState.history` still exists and now defaults to `emptyList()`, so `DietScreen`'s bar row renders nothing — bars are gone, code still compiles.)

- [ ] **Step 3: Delete the calculator + its test.**

```bash
git rm app/src/main/java/com/crazystudio/sportrecorder/domain/diet/DietHistoryCalculator.kt app/src/test/java/com/crazystudio/sportrecorder/domain/diet/DietHistoryCalculatorTest.kt
```

- [ ] **Step 4: Phase-1 gate.**

Run: `$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'; .\gradlew.bat assembleDebug :app:check`
Expected: `BUILD SUCCESSFUL` — detekt, lint, and all unit tests (including `DietTimeFormatTest`, `DietWindowTest`, the prior-slice tests) green; `DietHistoryCalculatorTest` is gone.

- [ ] **Step 5: Commit.**

```bash
git add app/src/main/java/com/crazystudio/sportrecorder/domain/usecase/ObserveDietStateUseCase.kt app/src/main/java/com/crazystudio/sportrecorder/ui/diet/DietViewModel.kt
git commit -m "[Refactor] Remove diet history aggregation (bars dropped)"
```

---

## Phase 2 — Layout + fast-window + ring gradient

### Task 2.1: `DietUiState` + `DietViewModel` + `DietScreen` redesign (coupled)

These three change together (the state shape change drives both the VM and the screen), so they land in one commit that compiles.

**Files:**
- Modify (full rewrite): `app/src/main/java/com/crazystudio/sportrecorder/ui/diet/DietUiState.kt`
- Modify: `app/src/main/java/com/crazystudio/sportrecorder/ui/diet/DietViewModel.kt`
- Modify (full rewrite): `app/src/main/java/com/crazystudio/sportrecorder/ui/diet/DietScreen.kt`
- Delete: `app/src/main/java/com/crazystudio/sportrecorder/ui/component/VerticalProgress.kt`

- [ ] **Step 1: Rewrite `DietUiState.kt`** — drop `history`/`HistoryBar`/`timeInfo*`, add the fast-window fields:

```kotlin
package com.crazystudio.sportrecorder.ui.diet

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.crazystudio.sportrecorder.R
import com.crazystudio.sportrecorder.ui.diet.select.FastingItem

data class DietUiState(
    val elapsedText: String = "00:00:00",
    val progress: Float = 0f, // 0..100 for CircleProgress
    val fastingLabel: String = "", // e.g. "16 : 8"
    val selectedFastingItem: FastingItem.DefaultFastingItem? = null,
    @DrawableRes val statusIcon: Int = R.drawable.ic_baseline_fastfood_24,
    @StringRes val statusTextRes: Int = R.string.diet_status_fasting,
    @StringRes val promptTextRes: Int = R.string.diet_fasting_time,
    // Fast window shown below the ring; empty [fastStart] hides the line.
    val fastStart: String = "",
    val fastEnd: String = "",
    val fastEndsNextDay: Boolean = false,
)
```

- [ ] **Step 2: Update `DietViewModel.kt`** — set the fast-window fields, drop the `timeInfo*` wiring, and remove `formatHistoryDate`/`HISTORY_DATE_FORMAT`. Replace `buildState` (and the companion) with:

```kotlin
    private fun buildState(snapshot: ObserveDietStateUseCase.DietSnapshot, now: Long): DietUiState {
        val eatingHours = snapshot.settings.eatingHours
        val fastingHours = snapshot.settings.fastingHours
        val fastingLabel = "%d : %d".format(fastingHours, eatingHours)
        val selectedFastingItem = FastingItem.defaultFastingItems.firstOrNull {
            it.fastingHours == fastingHours && it.eatingHours == eatingHours
        }

        val s = DietWindow.compute(
            eatTimesAsc = snapshot.eatTimesAsc,
            eatingHours = eatingHours,
            fastingHours = fastingHours,
            now = now,
        )

        val fastStartMs = s.windowEnd
        val fastEndMs = s.fastTargetAt
        val base = DietUiState(
            progress = s.ringProgress * 100f,
            fastingLabel = fastingLabel,
            selectedFastingItem = selectedFastingItem,
            elapsedText = formatElapsed(s.elapsedMillis),
            fastStart = hm(fastStartMs),
            fastEnd = hm(fastEndMs),
            fastEndsNextDay = fastStartMs != null && fastEndMs != null &&
                fastWindowCrossesDay(fastStartMs, fastEndMs),
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
            )
            DietPhase.FASTING -> base.copy(
                statusIcon = R.drawable.ic_baseline_no_food_24,
                statusTextRes = R.string.diet_status_fasting,
                promptTextRes = R.string.diet_fasting_time,
            )
            DietPhase.SUCCESS -> base.copy(
                statusIcon = R.drawable.ic_baseline_no_food_24,
                statusTextRes = R.string.diet_status_success,
                promptTextRes = R.string.diet_fasting_time,
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
        private val HM_FORMAT = SimpleDateFormat("HH:mm", Locale.getDefault())
    }
```

Then remove the now-unused import `import java.util.Locale`? It's still used by `HM_FORMAT`. Keep `SimpleDateFormat`, `Date`, `Locale`, `TimeUnit` imports (all still used). `IDLE` no longer needs explicit fast fields because `base` already yields empty `fastStart` when `windowEnd` is null (IDLE has null window) — `hm(null)` returns `""`, hiding the line.

- [ ] **Step 3: Rewrite `DietScreen.kt`** — remove the bar row, move status above the ring, add the `%`, and add the fast-window line below the ring:

```kotlin
package com.crazystudio.sportrecorder.ui.diet

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.crazystudio.sportrecorder.R
import com.crazystudio.sportrecorder.ui.component.CircleProgress
import com.crazystudio.sportrecorder.ui.theme.SportRecorderTheme
import kotlin.math.roundToInt

@Composable
@Suppress("LongMethod") // cohesive single-screen layout; splitting hurts readability
fun DietScreen(
    state: DietUiState,
    onEditFastingType: () -> Unit,
    onAddEatTime: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colorScheme.surface),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Status string — moved above the ring.
            Text(
                text = stringResource(id = state.statusTextRes),
                color = colorScheme.primary,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 16.dp),
            )

            // Ring with overlaid icon / percentage / prompt / countdown.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, start = 24.dp, end = 24.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircleProgress(
                    progress = state.progress,
                    modifier = Modifier.fillMaxWidth(),
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        painter = painterResource(id = state.statusIcon),
                        contentDescription = null,
                        tint = colorScheme.onSurface,
                        modifier = Modifier.size(36.dp),
                    )
                    Text(
                        text = "${state.progress.roundToInt()}%",
                        color = colorScheme.primary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = stringResource(id = state.promptTextRes),
                        color = colorScheme.onSurfaceVariant,
                        fontSize = 16.sp,
                    )
                    Text(
                        text = state.elapsedText,
                        color = colorScheme.primary,
                        fontSize = 44.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            // Fast window (start → end), below the ring. Hidden when no record yet.
            if (state.fastStart.isNotEmpty()) {
                val endText = if (state.fastEndsNextDay) {
                    stringResource(id = R.string.diet_next_day, state.fastEnd)
                } else {
                    state.fastEnd
                }
                Text(
                    text = stringResource(id = R.string.diet_fast_window, state.fastStart, endText),
                    color = colorScheme.onSurfaceVariant,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }

            // Fasting-type chip with edit pencil.
            Row(
                modifier = Modifier
                    .padding(top = 12.dp)
                    .clip(RoundedCornerShape(50))
                    .background(colorScheme.surfaceContainer)
                    .clickable { onEditFastingType() }
                    .padding(horizontal = 12.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = state.fastingLabel,
                    color = colorScheme.onSurfaceVariant,
                    fontSize = 18.sp,
                )
                Icon(
                    painter = painterResource(id = R.drawable.ic_baseline_edit_24),
                    contentDescription = null,
                    tint = colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(start = 5.dp)
                        .size(18.dp),
                )
            }

            Spacer(modifier = Modifier.height(100.dp))
        }

        FloatingActionButton(
            onClick = onAddEatTime,
            containerColor = colorScheme.primary,
            contentColor = colorScheme.onPrimary,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 20.dp),
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_baseline_add_24),
                contentDescription = null,
            )
        }
    }
}

@Preview
@Composable
@Suppress("UnusedPrivateMember") // @Preview entry point used by the IDE preview tooling
private fun DietScreenPreview() {
    SportRecorderTheme {
        DietScreen(
            state = DietUiState(
                elapsedText = "02:21:18",
                progress = 75f,
                fastingLabel = "16 : 8",
                statusTextRes = R.string.diet_status_fasting,
                promptTextRes = R.string.diet_fasting_time,
                fastStart = "23:00",
                fastEnd = "15:00",
                fastEndsNextDay = true,
            ),
            onEditFastingType = {},
            onAddEatTime = {},
        )
    }
}
```

Note: `Arrangement` import is retained (used by the chip `Row`'s implicit defaults? no) — verify imports after writing: the rewrite no longer uses `Arrangement` (the bar row used it). **Remove `import androidx.compose.foundation.layout.Arrangement`** and **`import androidx.compose.foundation.layout.width`** (both only used by the deleted bar row). Keep all others. `:app:detekt` (`NoUnusedImports`) will flag any miss — fix per its message.

- [ ] **Step 4: Delete the unused component.**

```bash
git rm app/src/main/java/com/crazystudio/sportrecorder/ui/component/VerticalProgress.kt
```

- [ ] **Step 5: Verify build + detekt + lint.**

Run: `$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'; .\gradlew.bat assembleDebug :app:detekt :app:lintDebug`
Expected: `BUILD SUCCESSFUL`. (If `NoUnusedImports` flags `Arrangement`/`width`/`Date`-etc, remove exactly what it names.)

- [ ] **Step 6: Commit.**

```bash
git add app/src/main/java/com/crazystudio/sportrecorder/ui/diet/DietUiState.kt app/src/main/java/com/crazystudio/sportrecorder/ui/diet/DietViewModel.kt app/src/main/java/com/crazystudio/sportrecorder/ui/diet/DietScreen.kt
git commit -m "[Feature] Status screen: status header, percentage, fast window; drop bars"
```

---

### Task 2.2: Rework the `CircleProgress` gradient (dark→bright along the arc)

**Files:**
- Modify (full rewrite): `app/src/main/java/com/crazystudio/sportrecorder/ui/component/CircleProgress.kt`

- [ ] **Step 1: Rewrite `CircleProgress.kt`** so the gradient starts dark at 12 o'clock and reaches brightest at the progress tip (rotate the draw so the sweep's 0° aligns with the arc start; place the bright stop at the progress fraction):

```kotlin
package com.crazystudio.sportrecorder.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

@Suppress("LongParameterList")
@Composable
fun CircleProgress(
    progress: Float, // 0..100
    modifier: Modifier = Modifier,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    gradientStart: Color = MaterialTheme.colorScheme.primaryContainer, // dark
    gradientEnd: Color = MaterialTheme.colorScheme.primary, // bright
    tipColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    val strokePx = with(LocalDensity.current) { 20.dp.toPx() }
    Canvas(modifier.aspectRatio(1f)) {
        val w = size.minDimension
        val center = Offset(w / 2f, w / 2f)
        val radius = w / 2f - strokePx
        val stroke = Stroke(width = strokePx)
        val fraction = (progress / 100f).coerceIn(0f, 1f)
        if (progress > 99f) {
            // Full ring: dark at the 12 o'clock start, brightest just before it closes.
            rotate(degrees = -90f, pivot = center) {
                val brush = Brush.sweepGradient(0f to gradientStart, 1f to gradientEnd, center = center)
                drawCircle(brush = brush, radius = radius, center = center, style = stroke)
            }
        } else {
            drawCircle(color = trackColor, radius = radius, center = center, style = stroke)
            val inset = strokePx
            rotate(degrees = -90f, pivot = center) {
                // Bright stop placed at the progress fraction so the tip is the brightest point.
                val brush = Brush.sweepGradient(
                    0f to gradientStart,
                    fraction.coerceAtLeast(0.001f) to gradientEnd,
                    center = center,
                )
                drawArc(
                    brush = brush,
                    startAngle = 0f, // rotate(-90) already moved the start to 12 o'clock
                    sweepAngle = 360f * fraction,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = Size(w - 2 * inset, w - 2 * inset),
                    style = stroke,
                )
            }
            // Start dot (dark) at 12 o'clock; tip dot (bright + inner tipColor) at the arc end.
            val rEnd = (w - strokePx * 2) / 2f
            val endRad = Math.toRadians(360.0 * fraction - 90.0)
            val endX = (w / 2f + rEnd * cos(endRad)).toFloat()
            val endY = (w / 2f + rEnd * sin(endRad)).toFloat()
            drawCircle(color = gradientStart, radius = strokePx / 2f, center = Offset(w / 2f, w / 2f - rEnd))
            drawCircle(color = gradientEnd, radius = strokePx / 2f, center = Offset(endX, endY))
            drawCircle(color = tipColor, radius = strokePx / 3.25f, center = Offset(endX, endY))
        }
    }
}

@Preview
@Composable
@Suppress("UnusedPrivateMember") // @Preview entry point used by the IDE preview tooling
private fun CircleProgressPreview() {
    CircleProgress(progress = 70f, modifier = Modifier.size(240.dp))
}
```

- [ ] **Step 2: Verify build + detekt.**

Run: `$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'; .\gradlew.bat assembleDebug :app:detekt`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit.**

```bash
git add app/src/main/java/com/crazystudio/sportrecorder/ui/component/CircleProgress.kt
git commit -m "[Feature] CircleProgress: dark->bright gradient aligned to the arc"
```

---

### Task 2.3: Remove the now-unused time-info strings

**Files:**
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-zh-rTW/strings.xml`

- [ ] **Step 1: Confirm they're unreferenced.** Use the Grep tool: pattern `diet_eating_window|diet_fast_target|diet_fast_done`, path `app/src`. Expected: ZERO matches (the VM no longer sets `timeInfoRes`). If any remain, stop and investigate.

- [ ] **Step 2: Remove the three strings from BOTH files.**
  - `values/strings.xml`: delete the `diet_eating_window`, `diet_fast_target`, `diet_fast_done` lines (currently lines 40–42).
  - `values-zh-rTW/strings.xml`: delete the same three (currently lines 39–41).

- [ ] **Step 3: Verify build + lint** (lint would flag a removed string still referenced).

Run: `$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'; .\gradlew.bat assembleDebug :app:lintDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit.**

```bash
git add app/src/main/res/values/strings.xml app/src/main/res/values-zh-rTW/strings.xml
git commit -m "[Refactor] Remove unused diet time-info strings"
```

---

### Task 2.4: Phase-2 gate + emulator verification

**Files:** none (verification only).

- [ ] **Step 1: Full CI-mirror gate.**

Run: `$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'; .\gradlew.bat assembleDebug testDebugUnitTest :app:detekt :app:lintDebug`
Expected: `BUILD SUCCESSFUL` — all unit tests pass (`DietTimeFormatTest`, `DietWindowTest`, prior-slice tests), detekt + lint clean.

- [ ] **Step 2: Confirm deletions are clean.** Grep `app/src` for `VerticalProgress`, `DietHistoryCalculator`, and `HistoryBar` — expected: ZERO matches. Grep for `state.history`/`timeInfoRes` — ZERO matches.

- [ ] **Step 3: Emulator visual check** (boot `Pixel_10_Pro` if needed; install with `installDebug`). Verify each phase renders correctly — the simplest way to exercise phases is to set different windows / add an eat record via the FAB:
  - **EATING** (add an eat record now, short eating window): status "進食中" **above** the ring; center shows icon, a `%`, prompt, big countdown; below the ring "斷食 HH:mm → HH:mm" (the upcoming fast); no bars; ring dark→bright with the brightest at the tip.
  - **FASTING**: status "斷食中" above; fast line shows the current fast window (with "隔日" if it crosses midnight).
  - **SUCCESS**: status "成功"; ring full (brightest near the close); fast line present.
  - **IDLE** (no records — uninstall+reinstall or clear data to reach this): status "斷食中", `0%`, prompt "新增第一餐", **no** fast line.
  - Take a screenshot of at least the EATING and FASTING states (`adb shell screencap` → `adb pull`).

- [ ] **Step 4: Final commit** (only if a visual fix was needed; otherwise skip). Ensure `git status` is clean.

---

## Self-Review (author check vs. spec)

- **Spec §1 remove history** → Task 1.3 (drop from use case + VM, delete `DietHistoryCalculator`+test) and Task 2.1 (drop `history`/`HistoryBar` from state, remove bar row from screen, delete `VerticalProgress`). ✔
- **Spec §2 fast window** → Task 1.1 (`fastWindowCrossesDay` + test), Task 1.2 (strings), Task 2.1 (VM sets `fastStart`/`fastEnd`/`fastEndsNextDay`; screen renders the line with "隔日"; hidden in IDLE since `hm(null)=""`). ✔
- **Spec §3 ring gradient** → Task 2.2 (rotated sweep, bright stop at the progress fraction, dark start dot / bright tip dot). ✔
- **Spec §4 layout reorg** → Task 2.1 (status header above ring; center `icon → % → prompt → countdown`). ✔
- **Spec §5 state/VM** → Task 2.1 (`DietUiState` fields; VM stays Context-free using `HM_FORMAT` + `fastWindowCrossesDay`; `%` derived in screen via `roundToInt`). ✔
- **Spec §6 strings** → Task 1.2 (add `diet_fast_window`/`diet_next_day` both locales), Task 2.3 (remove unused three both locales). ✔
- **Spec §7 testing** → `DietTimeFormatTest` (Task 1.1); visual via emulator (Task 2.4). `DietHistoryCalculatorTest` removed (Task 1.3). ✔
- **Spec §8 phasing / compile-ordering** → Phase 1 keeps everything green (bars just empty) before the coupled Phase-2 commit; Task 2.1 changes state+VM+screen together. ✔
- **Placeholder scan:** complete code in every code step; exact commands + expected results. No TBDs. ✔
- **Type consistency:** `DietSnapshot(eatTimesAsc, settings)` matches between use case (1.3) and VM (2.1). `fastStart`/`fastEnd`/`fastEndsNextDay` identical across `DietUiState` (2.1), VM (2.1), screen (2.1), preview (2.1). `fastWindowCrossesDay(start, end)` identical between helper (1.1), test (1.1), and VM call (2.1). `progress: Float 0..100` unchanged into `CircleProgress` (2.2). ✔
- **detekt:** no `!!` (null-check smart-casts in VM); `MagicNumber` is `ui/`-excluded for all touched files; `RECENT_WINDOW_DAYS`/consts kept named in the (domain) use case; no semicolons; unused imports removed in 2.1. ✔
