# MD3 Color System + Role-Based UI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the hand-rolled, mostly-bypassed color setup with a complete brand-seeded MD3 dark `ColorScheme`, and migrate all ~76 raw-color UI usages to semantic `MaterialTheme.colorScheme` roles.

**Architecture:** A single complete dark `ColorScheme` (brand green `#38D69F` as primary, current dark greys anchored as surfaces, all other roles derived) in `Theme.kt`; the app forces dark. The two custom Canvas components take `colorScheme`-defaulted color params; every screen reads `MaterialTheme.colorScheme.<role>` instead of importing raw `Color` vals. No light theme, no dynamic color, no layout/logic change.

**Tech Stack:** Jetpack Compose Material3, Kotlin.

**Verification model:** `assembleDebug` + `:app:check` (detekt has **no baseline** — changed files must pass) after each task; emulator screenshots per screen to confirm the brand still reads well and contrast is adequate. `testDebugUnitTest`/`assembleRelease` green (colors don't affect logic).

**Environment (project memory):** `java` not on PATH — before every Gradle command: `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"` then `.\gradlew.bat <tasks>` from `C:\Users\Aidan\SportRecorder`. Emulator `Pixel_10_Pro` (API 37); adb at `C:\Users\Aidan\AppData\Local\Android\Sdk\platform-tools\adb.exe`; screenshots to `%TEMP%`. **Commit hygiene:** stage only named paths (never `git add -A`); trailer `Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>`.

**Branch:** `feat/md3-color-system` (spec already committed here).

**Role mapping (used throughout):**
| raw | → `MaterialTheme.colorScheme.` |
|---|---|
| `light_green` | `primary` |
| `dark_green` | `primaryContainer` |
| `white` | `onSurface` (text/icons on dark) |
| `grey_1` | `onSurfaceVariant` (secondary text/icons, dots) |
| `bg_black` / `colorResource(R.color.bg_black)` | `surface` |
| `bg_black2` | `surfaceVariant` (dividers) / `surfaceContainer` (card bg) |

---

## Task 1: Complete dark `ColorScheme` + dark-only `Theme.kt`

**Files:** Modify `app/src/main/java/com/crazystudio/sportrecorder/ui/theme/Theme.kt`

- [ ] **Step 1: Replace `Theme.kt` entirely** with a complete dark scheme (brand-anchored) and a dark-only theme:

```kotlin
package com.crazystudio.sportrecorder.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = light_green,
    onPrimary = Color(0xFF003828),
    primaryContainer = dark_green,
    onPrimaryContainer = Color(0xFF5BF0B8),
    inversePrimary = Color(0xFF006C4E),
    secondary = Color(0xFFB2CCBF),
    onSecondary = Color(0xFF1D352B),
    secondaryContainer = Color(0xFF334B40),
    onSecondaryContainer = Color(0xFFCEE9DB),
    tertiary = Color(0xFFA6CCDC),
    onTertiary = Color(0xFF093543),
    tertiaryContainer = Color(0xFF264B5A),
    onTertiaryContainer = Color(0xFFC2E8F9),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = bg_black,
    onBackground = white,
    surface = bg_black,
    onSurface = white,
    surfaceVariant = bg_black2,
    onSurfaceVariant = grey_1,
    surfaceTint = light_green,
    inverseSurface = Color(0xFFE2E3DF),
    inverseOnSurface = Color(0xFF2E312F),
    outline = Color(0xFF8B9389),
    outlineVariant = Color(0xFF414941),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF3A3A3A),
    surfaceDim = Color(0xFF1F1F1F),
    surfaceContainerLowest = Color(0xFF1F1F1F),
    surfaceContainerLow = bg_black,
    surfaceContainer = Color(0xFF303030),
    surfaceContainerHigh = Color(0xFF3A3A3A),
    surfaceContainerHighest = bg_black2,
)

@Composable
fun SportRecorderTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        typography = createTypography(DarkColors),
        content = content,
    )
}
```
(This drops `LightColorScheme`, the `darkTheme`/`dynamicColor` params, `isSystemInDarkTheme`/`lightColorScheme` imports, the commented dynamic-color/status-bar block, and the `@Suppress("UnusedParameter")`. All call sites use `SportRecorderTheme { ... }` with no args, so removing the params is safe.)

- [ ] **Step 2: Build** — `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat assembleDebug` → BUILD SUCCESSFUL. Then `.\gradlew.bat :app:detekt` → green. (UI still uses raw colors, so the app looks the same; only the ~9 existing `colorScheme.*` usages get the fuller scheme.)

- [ ] **Step 3: Commit**
```
git add app/src/main/java/com/crazystudio/sportrecorder/ui/theme/Theme.kt
git commit -m "[Theme] Complete brand-seeded MD3 dark color scheme; dark-only"
```

---

## Task 2: Parameterize the Canvas components from `colorScheme`

**Files:** Modify `app/src/main/java/com/crazystudio/sportrecorder/ui/component/CircleProgress.kt`, `app/src/main/java/com/crazystudio/sportrecorder/ui/component/VerticalProgress.kt`

- [ ] **Step 1: Replace `CircleProgress.kt`** with a version that takes `colorScheme`-defaulted color params (brand gradient `primaryContainer`→`primary`, track `surfaceVariant`, tip `onSurfaceVariant`):

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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun CircleProgress(
    progress: Float, // 0..100
    modifier: Modifier = Modifier,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    gradientStart: Color = MaterialTheme.colorScheme.primaryContainer,
    gradientEnd: Color = MaterialTheme.colorScheme.primary,
    tipColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    val strokePx = with(LocalDensity.current) { 20.dp.toPx() }
    Canvas(modifier.aspectRatio(1f)) {
        val w = size.minDimension
        val center = Offset(w / 2f, w / 2f)
        val radius = w / 2f - strokePx
        val brush = Brush.sweepGradient(0f to gradientStart, 1f to gradientEnd, center = center)
        val stroke = Stroke(width = strokePx)
        if (progress > 99f) {
            drawCircle(brush = brush, radius = radius, center = center, style = stroke)
        } else {
            val sweep = 360f * progress / 100f
            drawCircle(color = trackColor, radius = radius, center = center, style = stroke)
            val inset = strokePx
            drawArc(
                brush = brush,
                startAngle = -90f,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = Size(w - 2 * inset, w - 2 * inset),
                style = stroke,
            )
            val rEnd = (w - strokePx * 2) / 2f
            val endRad = Math.toRadians((sweep - 90.0))
            val endX = (w / 2f + rEnd * cos(endRad)).toFloat()
            val endY = (w / 2f + rEnd * sin(endRad)).toFloat()
            drawCircle(brush = brush, radius = strokePx / 2f, center = Offset(endX, endY))
            drawCircle(color = gradientStart, radius = strokePx / 2f, center = Offset(w / 2f, w / 2f - rEnd))
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

- [ ] **Step 2: Replace `VerticalProgress.kt`** similarly (gradient top `primary` → bottom `primaryContainer`, track `surfaceVariant`):

```kotlin
package com.crazystudio.sportrecorder.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

@Composable
fun VerticalProgress(
    progress: Float, // 0f..1f
    modifier: Modifier = Modifier,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    gradientTop: Color = MaterialTheme.colorScheme.primary,
    gradientBottom: Color = MaterialTheme.colorScheme.primaryContainer,
) {
    val strokePx = with(LocalDensity.current) { 20.dp.toPx() }
    Canvas(modifier) {
        val cx = size.width / 2f
        val h = size.height
        val top = strokePx / 2f
        val bottom = h - strokePx / 2f
        drawLine(trackColor, Offset(cx, top), Offset(cx, bottom), strokeWidth = strokePx)
        drawCircle(trackColor, radius = strokePx / 2f, center = Offset(cx, bottom))
        drawCircle(trackColor, radius = strokePx / 2f, center = Offset(cx, top))
        if (progress > 0f) {
            val brush = Brush.verticalGradient(0f to gradientTop, 1f to gradientBottom, startY = 0f, endY = h)
            val progressTopY = h - (h - strokePx / 2f) * progress
            drawCircle(brush = brush, radius = strokePx / 2f, center = Offset(cx, bottom))
            drawLine(brush = brush, start = Offset(cx, bottom), end = Offset(cx, progressTopY), strokeWidth = strokePx)
            drawCircle(brush = brush, radius = strokePx / 2f, center = Offset(cx, progressTopY))
        }
    }
}
```

- [ ] **Step 3: Build + screenshot** — `.\gradlew.bat assembleDebug :app:detekt` → green; `.\gradlew.bat installDebug`; open Home; screenshot to `%TEMP%\sr_canvas.png`. The ring + history bars should look essentially the same (theme greens ≈ the old brand greens). Call sites in `DietScreen` need NO change (the new params default from `colorScheme`).

- [ ] **Step 4: Commit**
```
git add app/src/main/java/com/crazystudio/sportrecorder/ui/component/CircleProgress.kt app/src/main/java/com/crazystudio/sportrecorder/ui/component/VerticalProgress.kt
git commit -m "[Theme] Drive CircleProgress/VerticalProgress colors from colorScheme"
```

---

## Task 3: Migrate the screen files to semantic roles

**Files:** Modify (each in `app/src/main/java/com/crazystudio/sportrecorder/`): `ui/AppRoot.kt`, `ui/diet/DietScreen.kt`, `ui/diet/record/RecordScreen.kt`, `ui/diet/editor/EatTimeEditorSheet.kt`, `ui/diet/select/SelectFastingTypeScreen.kt`, `ui/diet/create/fasting/CreateFastingTypeScreen.kt`

For EACH file: read it, replace every raw color with its mapped role from `MaterialTheme.colorScheme`, and remove the now-unused `import com.crazystudio.sportrecorder.ui.theme.{white,grey_1,bg_black,bg_black2,light_green,dark_green}` lines and any `colorResource(R.color.bg_black)` (→ `MaterialTheme.colorScheme.surface`). Work one file per step, build after each, screenshot the affected screen.

Mapping (from the header table) with these per-file specifics:
- **Card / sheet backgrounds** currently `bg_black2` (RecordScreen card, etc.) → `surfaceContainer`.
- **Dividers** currently `bg_black2` → `outlineVariant`.
- **Surface/page backgrounds** `bg_black` / `colorResource(R.color.bg_black)` → `surface`.
- **Body text/icons** `white` → `onSurface`; **secondary text / 📍 / dots / placeholder** `grey_1` → `onSurfaceVariant`.
- **Accent** `light_green` → `primary`; the **bottom-nav unselected** currently `grey_1` → `onSurfaceVariant`, selected `light_green` → `primary`, indicator `bg_black2` → `secondaryContainer`.
- If a color is needed outside composable scope, hoist `val colorScheme = MaterialTheme.colorScheme` at the top of the composable and use `colorScheme.<role>`.

- [ ] **Step 1: `AppRoot.kt`** — migrate (NavigationBar item colors: `selectedIconColor`/`selectedTextColor` = `primary`, `unselected*` = `onSurfaceVariant`, `indicatorColor` = `secondaryContainer`; any `bg_black*`/`white` → roles). Build `.\gradlew.bat assembleDebug :app:detekt` green; `installDebug`; screenshot the bottom nav + a sheet to `%TEMP%\sr_md3_approot.png`.

- [ ] **Step 2: `DietScreen.kt`** — migrate all 17 (status text/elapsed `white`→`onSurface`; prompt/time-info/date labels `grey_1`→`onSurfaceVariant`; chip bg `bg_black2`→`surfaceContainer`; `light_green`→`primary`; page background `bg_black`→`surface`). Build green; screenshot Home to `%TEMP%\sr_md3_home.png`.

- [ ] **Step 3: `RecordScreen.kt`** — migrate all 14 (card bg `bg_black2`→`surfaceContainer`; divider `bg_black2`→`outlineVariant`; text `white`→`onSurface`; 📍/page dots `grey_1`→`onSurfaceVariant`; page bg `bg_black`→`surface`). Build green; screenshot Record to `%TEMP%\sr_md3_record.png`.

- [ ] **Step 4: `EatTimeEditorSheet.kt`** — migrate all 16 (sheet bg `colorResource(R.color.bg_black)`→`surface`; row text/icons `white`→`onSurface`; secondary `grey_1`→`onSurfaceVariant`; accents `light_green`→`primary`; note field colors already use roles — keep). Build green; screenshot the editor sheet to `%TEMP%\sr_md3_editor.png`.

- [ ] **Step 5: `SelectFastingTypeScreen.kt` + `CreateFastingTypeScreen.kt`** — migrate the remaining 5 (`white`→`onSurface`, `light_green`→`primary`, backgrounds→`surface`/`surfaceContainer`). Build green; screenshot the fasting-type sheets to `%TEMP%\sr_md3_fasting.png`.

- [ ] **Step 6: Grep check** — confirm NO screen file under `ui/` (excluding `ui/theme/` and the canvas component defaults) still imports/uses `white|grey_1|bg_black|bg_black2|light_green|dark_green` or `colorResource(R.color.bg_black`. Fix any stragglers.

- [ ] **Step 7: Commit** (stage exactly the 6 screen files):
```
git commit -m "[Theme] Migrate UI to MD3 colorScheme roles"
```

---

## Task 4: Prune dead raw colors + final verification

**Files:** Modify `app/src/main/java/com/crazystudio/sportrecorder/ui/theme/Color.kt`

- [ ] **Step 1: Grep each `Color.kt` val** for remaining references across `app/src/main`. Delete any val that is now referenced nowhere (likely candidates: `bg_black_transparent_25`, `bg_black_transparent_5`, `bg_white_transparent_25`, `light_green_50`, `black`, `transparent`, `google_green`, `google_blue`, `google_red`, `google_yellow`). KEEP the ones still used by `Theme.kt` (`light_green`, `dark_green`, `bg_black`, `bg_black2`, `white`, `grey_1`). Do NOT touch `res/values/ic_launcher_background.xml` (launcher icon).

- [ ] **Step 2: Build + detekt** — `.\gradlew.bat assembleDebug :app:detekt` → green (no unresolved references; no unused-import/val findings in the changed file).

- [ ] **Step 3: Full verification** — `.\gradlew.bat clean assembleDebug assembleRelease testDebugUnitTest :app:check` → all BUILD SUCCESSFUL.

- [ ] **Step 4: Emulator full pass** — `installDebug`; screenshot Home, Record, the create/edit sheet, fasting-type sheets, Notifications, bottom nav. Confirm: brand green correct, dark surfaces consistent, text/icon contrast adequate on every surface. Compare against the pre-change look (should read as the same app, now role-driven). Save shots to `%TEMP%`.

- [ ] **Step 5: Commit**
```
git add app/src/main/java/com/crazystudio/sportrecorder/ui/theme/Color.kt
git commit -m "[Theme] Remove unused raw color values"
```

---

## Self-Review (author check vs. spec)

- **Spec coverage:** complete dark scheme (brand-seeded, all roles, surfaces anchored) → Task 1; UI → roles (mapping table) → Task 3; canvas parameterized from `colorScheme` → Task 2; Theme dark-only cleanup → Task 1; prune dead colors → Task 4; verification (build/check/screenshots/grep) → each task + Task 4. All spec sections map to tasks.
- **Placeholder scan:** Task 1 and Task 2 give complete file contents; Task 3 gives the concrete mapping + per-file specifics + the build/screenshot/commit loop (the only per-line work is mechanical raw→role substitution against an explicit table — actionable, not vague). No "handle it" steps.
- **Type/name consistency:** the role mapping is identical across the header table and Task 3; `CircleProgress`/`VerticalProgress` new param names (`trackColor`/`gradientStart`/`gradientEnd`/`tipColor`, `gradientTop`/`gradientBottom`) are defined in Task 2 and default from `colorScheme` so Task 3 leaves their call sites unchanged; `Theme.kt`'s `DarkColors` references the brand vals kept in `Color.kt` (Task 4 prunes only the truly-unused ones).
- **Risk control:** each migration step builds + screenshots so a contrast/role mistake is caught immediately; surfaces anchored to the current greys keep the brand recognizable; detekt-no-baseline enforces clean imports.
