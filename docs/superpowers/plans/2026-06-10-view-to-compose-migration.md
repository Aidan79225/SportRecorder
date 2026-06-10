# View → Jetpack Compose Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace all remaining View-based UI (Fragments, XML layouts, ViewBinding, RecyclerView adapters, custom Canvas Views, nav-graph + safe-args) with a single-Activity Jetpack Compose app using Navigation-Compose type-safe routes and `StateFlow<UiState>` view models, as a faithful 1:1 port.

**Architecture:** `MainActivity` calls `setContent { SportRecorderTheme { AppRoot() } }`. `AppRoot` is a `Scaffold` with a Compose `NavigationBar` (Diet / Record / Notifications) wrapping a `ModalBottomSheetLayout` + `NavHost`. Top-level screens are `composable` destinations; the 4 dialogs are `bottomSheet` destinations. Each screen has a `data class UiState` exposed as `StateFlow` from a Hilt `hiltViewModel()`, collected with `collectAsStateWithLifecycle()`.

**Tech Stack:** Jetpack Compose (BOM 2026.05.01), Navigation-Compose + material-navigation, hilt-navigation-compose, kotlinx-serialization (type-safe routes), Kotlin 2.3.10, Room (unchanged).

**Verification model:** This is UI migration — there are no unit tests for composables. Each task's gate is: `.\gradlew.bat assembleDebug` is green, and (for screen tasks) `installDebug` + a screenshot on the `Pixel_10_Pro` emulator (API 37) that matches the pre-migration look/behavior. The existing Fragment/XML/View source files are the exact behavioral spec — port them faithfully.

**Environment (see project memory):** `java` is not on PATH. Before every Gradle command:
`$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"` then `.\gradlew.bat <tasks>` from `C:\Users\Aidan\SportRecorder`. Emulator: `& "$SDK\emulator\emulator.exe" -avd Pixel_10_Pro -no-snapshot-load`; screenshots via `adb shell screencap -p /sdcard/x.png` then `adb pull` (NOT `> file.png`, which corrupts PNG under PowerShell).

**Branch:** `feat/view-to-compose-migration` (spec already committed here).

---

## Phase 0 — Compose app shell + navigation

### Task 0.1: Add Compose-navigation dependencies and the serialization plugin

**Files:**
- Modify: `build.gradle` (root — add serialization plugin to `plugins {}`)
- Modify: `app/build.gradle` (apply plugin + add deps)

- [ ] **Step 1: Add the Kotlin serialization plugin to the root `build.gradle` `plugins {}` block**

Add this line inside the existing top-level `plugins { }` block (alongside the ksp and compose plugins):

```groovy
    id 'org.jetbrains.kotlin.plugin.serialization' version '2.3.10' apply false
```

- [ ] **Step 2: Apply the serialization plugin in `app/build.gradle` `plugins {}` block**

Add to the `plugins { }` block in `app/build.gradle` (after `id 'org.jetbrains.kotlin.plugin.compose'`):

```groovy
    id 'org.jetbrains.kotlin.plugin.serialization'
```

- [ ] **Step 3: Add the navigation/compose/serialization dependencies in `app/build.gradle` `dependencies {}`**

Add these lines to the `dependencies { }` block (place near the other compose deps):

```groovy
    implementation 'androidx.navigation:navigation-compose:2.9.8'
    implementation 'androidx.hilt:hilt-navigation-compose:1.2.0'
    implementation 'androidx.lifecycle:lifecycle-runtime-compose'
    implementation 'androidx.compose.material:material-navigation'
    implementation 'org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3'
```

> `lifecycle-runtime-compose` and `material-navigation` are version-managed by the existing `compose-bom` / lifecycle BOM — no explicit version. If Gradle reports an unresolved version for either, pin `lifecycle-runtime-compose` to `2.7.0` (matches the existing `lifecycle-*` deps) and `material-navigation` to the version Google Maven lists for the current compose-bom; record what you pinned.

- [ ] **Step 4: Sync/build to verify dependencies resolve**

Run:
```
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat assembleDebug
```
Expected: `BUILD SUCCESSFUL` (no code changes yet — only deps).

- [ ] **Step 5: Commit**

```
git add build.gradle app/build.gradle
git commit -m "[Compose] Add navigation-compose, serialization, hilt-navigation-compose deps"
```

### Task 0.2: Define type-safe navigation routes

**Files:**
- Create: `app/src/main/java/com/crazystudio/sportrecorder/ui/nav/Routes.kt`

- [ ] **Step 1: Create the route definitions**

```kotlin
package com.crazystudio.sportrecorder.ui.nav

import kotlinx.serialization.Serializable

sealed interface Route {
    // Bottom-nav top-level destinations
    @Serializable data object Diet : Route
    @Serializable data object Record : Route
    @Serializable data object Notifications : Route

    // Bottom-sheet destinations
    @Serializable data object SelectFastingType : Route
    @Serializable data object CreateFastingType : Route
    @Serializable data object CreateEatTime : Route
    @Serializable data class CreateFoodRecord(val eatTimeId: Long) : Route
}
```

> `CreateFoodRecord.eatTimeId` mirrors the argument the current safe-args action passes. While porting Task 5.x, read `CreateEatTimeDialogFragment` + `CreateFoodRecordDialogFragment` (and the generated `...Directions`) to confirm the exact argument(s); if more than `eatTimeId` is passed, add the fields here.

- [ ] **Step 2: Build**

Run: `.\gradlew.bat assembleDebug` (with JAVA_HOME set). Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```
git add app/src/main/java/com/crazystudio/sportrecorder/ui/nav/Routes.kt
git commit -m "[Compose] Add type-safe navigation routes"
```

### Task 0.3: Build the Compose app shell (Scaffold + NavigationBar + NavHost) with placeholders

**Files:**
- Create: `app/src/main/java/com/crazystudio/sportrecorder/ui/AppRoot.kt`
- Modify: `app/src/main/java/com/crazystudio/sportrecorder/MainActivity.kt`

- [ ] **Step 1: Create `AppRoot.kt` with the scaffold, bottom bar, and NavHost wiring the already-Compose screens + placeholders**

```kotlin
package com.crazystudio.sportrecorder.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.navigation.ModalBottomSheetLayout
import androidx.compose.material.navigation.bottomSheet
import androidx.compose.material.navigation.rememberBottomSheetNavigator
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.crazystudio.sportrecorder.R
import com.crazystudio.sportrecorder.ui.nav.Route

private data class Tab(val route: Route, val label: String, @DrawableRes val icon: Int)

@Composable
fun AppRoot() {
    val bottomSheetNavigator = rememberBottomSheetNavigator()
    val navController = rememberNavController(bottomSheetNavigator)

    val tabs = listOf(
        Tab(Route.Diet, "Home", R.drawable.ic_home_black_24dp),
        Tab(Route.Record, "Record", R.drawable.ic_dashboard_black_24dp),
        Tab(Route.Notifications, "Notifications", R.drawable.ic_notifications_black_24dp),
    )

    ModalBottomSheetLayout(bottomSheetNavigator) {
        Scaffold(
            bottomBar = {
                val backStackEntry by navController.currentBackStackEntryAsState()
                val currentDest = backStackEntry?.destination
                NavigationBar {
                    tabs.forEach { tab ->
                        val selected = currentDest?.hierarchyHasRoute(tab.route) == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(Route.Diet) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(painterResource(tab.icon), contentDescription = tab.label) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        ) { padding ->
            NavHost(
                navController = navController,
                startDestination = Route.Diet,
                modifier = Modifier.fillMaxSize().padding(padding),
            ) {
                composable<Route.Diet> { Text("Diet (placeholder)") }
                composable<Route.Record> { Text("Record (placeholder)") }
                composable<Route.Notifications> { Text("Notifications (placeholder)") }

                bottomSheet<Route.SelectFastingType> { Text("SelectFastingType (placeholder)") }
                bottomSheet<Route.CreateFastingType> { Text("CreateFastingType (placeholder)") }
                bottomSheet<Route.CreateEatTime> { Text("CreateEatTime (placeholder)") }
                bottomSheet<Route.CreateFoodRecord> { Text("CreateFoodRecord (placeholder)") }
            }
        }
    }
}

// Selected-tab check that works for type-safe routes
private fun androidx.navigation.NavDestination.hierarchyHasRoute(route: Route): Boolean =
    hierarchy.any {
        when (route) {
            Route.Diet -> it.hasRoute(Route.Diet::class)
            Route.Record -> it.hasRoute(Route.Record::class)
            Route.Notifications -> it.hasRoute(Route.Notifications::class)
            else -> false
        }
    }
```

> The bottom-nav icon drawable names (`ic_home_black_24dp`, etc.) come from the current `res/menu/bottom_nav_menu.xml`. Open that file and use the exact `android:icon` drawables it references; adjust the three `R.drawable.*` names to match.

- [ ] **Step 2: Rewrite `MainActivity.kt` to host Compose**

Replace the entire file with:

```kotlin
package com.crazystudio.sportrecorder

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import com.crazystudio.sportrecorder.ui.AppRoot
import com.crazystudio.sportrecorder.ui.theme.SportRecorderTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SportRecorderTheme {
                AppRoot()
            }
        }
    }
}
```

- [ ] **Step 3: Build**

Run: `.\gradlew.bat assembleDebug` (JAVA_HOME set). Expected: `BUILD SUCCESSFUL`.
If `rememberNavController(bottomSheetNavigator)` / `ModalBottomSheetLayout` / `bottomSheet` are unresolved, confirm the `androidx.compose.material:material-navigation` dependency (Task 0.1) resolved and the import paths match the resolved artifact version; adjust imports to the resolved package and record the change.

- [ ] **Step 4: Run on emulator and screenshot**

```
.\gradlew.bat installDebug
$adb = "C:\Users\Aidan\AppData\Local\Android\Sdk\platform-tools\adb.exe"
& $adb shell am start -n com.crazystudio.sportrecorder/.MainActivity
& $adb shell screencap -p /sdcard/p0.png; & $adb pull /sdcard/p0.png .\p0.png
```
Expected: app launches, shows the bottom navigation bar with 3 tabs and the "Diet (placeholder)" text; tapping Record / Notifications swaps the placeholder text. No crash in `adb logcat`.

- [ ] **Step 5: Commit**

```
git add app/src/main/java/com/crazystudio/sportrecorder/ui/AppRoot.kt app/src/main/java/com/crazystudio/sportrecorder/MainActivity.kt
git commit -m "[Compose] Single-activity Compose shell with NavHost + bottom nav"
```

### Task 0.4: Wire the already-Compose fasting-type screens into the NavHost

**Files:**
- Modify: `app/src/main/java/com/crazystudio/sportrecorder/ui/AppRoot.kt`

The composables `SelectFastingTypeScreen` and `CreateFastingTypeScreen` already exist. Replace their placeholders with the real composables, reusing the navigation/preference logic currently in `SelectFastingTypeFragment` and `CreateFastingTypeFragment`.

- [ ] **Step 1: Read the two existing fragments + screens to copy their wiring**

Read `ui/diet/select/SelectFastingTypeFragment.kt`, `ui/diet/select/SelectFastingTypeScreen.kt`, `ui/diet/create/fasting/CreateFastingTypeFragment.kt`, `ui/diet/create/fasting/CreateFastingTypeScreen.kt`. Note the VM types, the `fastingItemFlow`, the preference writes (`DietPreference` + `Constants.DIET_*`), and the navigation calls.

- [ ] **Step 2: Replace the two `bottomSheet` placeholders with the real screens**

In `AppRoot.kt`, inject `DietPreference` access. Since `AppRoot` is a composable, obtain dependencies via `hiltViewModel()` for the view models and pass a callback for preference writes. Replace:

```kotlin
                bottomSheet<Route.SelectFastingType> { Text("SelectFastingType (placeholder)") }
                bottomSheet<Route.CreateFastingType> { Text("CreateFastingType (placeholder)") }
```

with (port the body of `SelectFastingTypeFragment.onCreateView` / `CreateFastingTypeFragment`):

```kotlin
                bottomSheet<Route.SelectFastingType> {
                    val vm: SelectFastingTypeViewModel = hiltViewModel()
                    val items by vm.fastingItemFlow.collectAsStateWithLifecycle(emptyList())
                    SelectFastingTypeScreen(
                        items,
                        onCreateClick = { navController.navigate(Route.CreateFastingType) },
                        onSelect = { fastingHours, eatingHours ->
                            vm.saveSelection(fastingHours, eatingHours) // move the DietPreference writes into the VM
                            navController.popBackStack()
                        },
                    )
                }
                bottomSheet<Route.CreateFastingType> {
                    val vm: CreateFastingTypeViewModel = hiltViewModel()
                    CreateFastingTypeScreen(/* same callbacks the existing CreateFastingTypeFragment passes */)
                }
```

> The current `SelectFastingTypeFragment` writes `DIET_FASTING_TIME_INTERVAL` / `DIET_EATING_TIME_INTERVAL` to `DietPreference` directly. Move that write into a new `SelectFastingTypeViewModel.saveSelection(fastingHours, eatingHours)` (inject `DietPreference` into the VM via Hilt) so the composable stays free of preference plumbing. Match the existing `CreateFastingTypeScreen` callback signature exactly — copy it from `CreateFastingTypeFragment`.

- [ ] **Step 3: Build, run, verify the fasting-type sheets open and select works**

`.\gradlew.bat assembleDebug` then `installDebug`. (Diet screen is still a placeholder, so temporarily add a button in the Diet placeholder to `navController.navigate(Route.SelectFastingType)` to test, OR defer this verification to Task 4.x when the Diet screen exists.) Expected: build green; if tested, the bottom sheet opens, lists fasting types, and selecting one dismisses it.

- [ ] **Step 4: Commit**

```
git add app/src/main/java/com/crazystudio/sportrecorder/ui/AppRoot.kt app/src/main/java/com/crazystudio/sportrecorder/ui/diet/select/SelectFastingTypeViewModel.kt
git commit -m "[Compose] Wire fasting-type bottom sheets into NavHost"
```

---

## Phase 1 — Custom Canvas views → Compose

### Task 1.1: Port `CircleProgressBar` to a Compose Canvas

**Files:**
- Create: `app/src/main/java/com/crazystudio/sportrecorder/ui/component/CircleProgress.kt`

Faithful translation of `ui/view/CircleProgressBar.kt::onDraw`. Key mapping (square canvas of side `w`, stroke = 20dp = `strokePx`): radius = `w/2 - strokePx`; arc bounds inset by `strokePx`; sweep gradient dark_green→light_green rotated so it starts at top (-90°); arc from `-90°` sweeping `360 * progress/100`; a moving end dot + grey tip dot at the arc end (trig: `center + (w - 2*strokePx)/2 * (cos,sin)(angleDeg - 90)`); a fixed dark-green start dot at top; full circle when `progress > 99`.

- [ ] **Step 1: Create the composable**

```kotlin
package com.crazystudio.sportrecorder.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.unit.dp
import com.crazystudio.sportrecorder.R
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun CircleProgress(
    progress: Float, // 0..100, matching the original CircleProgressBar.progress
    modifier: Modifier = Modifier,
) {
    val darkGreen = colorResource(R.color.dark_green)
    val lightGreen = colorResource(R.color.light_green)
    val bgBlack2 = colorResource(R.color.bg_black2)
    val grey1 = colorResource(R.color.grey_1)
    val strokePx = with(LocalDensity.current) { 20.dp.toPx() }

    Canvas(modifier.aspectRatio(1f)) {
        val w = size.minDimension
        val center = Offset(w / 2f, w / 2f)
        val radius = w / 2f - strokePx
        // sweep gradient starting at top (rotate -90deg): use a manual sweep with the start at 12 o'clock.
        val brush = Brush.sweepGradient(
            0f to darkGreen, 1f to lightGreen,
            center = center,
        )
        val stroke = Stroke(width = strokePx)
        if (progress > 99f) {
            drawCircle(brush = brush, radius = radius, center = center, style = stroke)
        } else {
            val sweep = 360f * progress / 100f
            drawCircle(color = bgBlack2, radius = radius, center = center, style = stroke)
            val arcInset = strokePx
            drawArc(
                brush = brush,
                startAngle = -90f,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = Offset(arcInset, arcInset),
                size = Size(w - 2 * arcInset, w - 2 * arcInset),
                style = stroke,
            )
            val rEnd = (w - strokePx * 2) / 2f
            val endRad = Math.toRadians((sweep - 90.0))
            val endX = (w / 2f + rEnd * cos(endRad)).toFloat()
            val endY = (w / 2f + rEnd * sin(endRad)).toFloat()
            drawCircle(brush = brush, radius = strokePx / 2f, center = Offset(endX, endY))
            // fixed start dot at top
            drawCircle(color = darkGreen, radius = strokePx / 2f, center = Offset(w / 2f, w / 2f - rEnd))
            // grey tip dot
            drawCircle(color = grey1, radius = strokePx / 3.25f, center = Offset(endX, endY))
        }
    }
}
```

- [ ] **Step 2: Add a `@Preview` to eyeball it**

Append:

```kotlin
import androidx.compose.foundation.layout.size
import androidx.compose.ui.tooling.preview.Preview

@Preview
@Composable
private fun CircleProgressPreview() {
    CircleProgress(progress = 70f, modifier = Modifier.size(240.dp))
}
```

- [ ] **Step 3: Build**

Run: `.\gradlew.bat assembleDebug`. Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Visual parity check**

The decisive comparison happens in Task 2.x (Diet screen) against the pre-migration home screenshot. For now, confirm the Android Studio `@Preview` renders an arc with a green gradient, a moving end dot, a top start dot, and a grey tip. If the gradient direction looks reversed vs. the original, swap the `0f to darkGreen, 1f to lightGreen` stops or rotate via `Brush.sweepGradient` ordering until it matches the original (which rotates the shader -90°). Record any adjustment.

- [ ] **Step 5: Commit**

```
git add app/src/main/java/com/crazystudio/sportrecorder/ui/component/CircleProgress.kt
git commit -m "[Compose] Port CircleProgressBar to Compose Canvas"
```

### Task 1.2: Port `VerticalProgressBar` to a Compose Canvas

**Files:**
- Create: `app/src/main/java/com/crazystudio/sportrecorder/ui/component/VerticalProgress.kt`

Faithful translation of `ui/view/VerticalProgressBar.kt::onDraw`: a vertical line inset by `strokePx/2` top and bottom; background line + two end dots (bg_black2); when `progress > 0`, a green vertical-gradient (light_green top → dark_green bottom) line drawn from the bottom up to `height - (height - strokePx/2) * progress`, with green dots at the bottom and at the progress position.

- [ ] **Step 1: Create the composable**

```kotlin
package com.crazystudio.sportrecorder.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.unit.dp
import com.crazystudio.sportrecorder.R

@Composable
fun VerticalProgress(
    progress: Float, // 0f..1f, matching the original
    modifier: Modifier = Modifier,
) {
    val lightGreen = colorResource(R.color.light_green)
    val darkGreen = colorResource(R.color.dark_green)
    val bgBlack2 = colorResource(R.color.bg_black2)
    val strokePx = with(LocalDensity.current) { 20.dp.toPx() }

    Canvas(modifier) {
        val cx = size.width / 2f
        val h = size.height
        val top = strokePx / 2f
        val bottom = h - strokePx / 2f
        // background track + end dots
        drawLine(bgBlack2, Offset(cx, top), Offset(cx, bottom), strokeWidth = strokePx)
        drawCircle(bgBlack2, radius = strokePx / 2f, center = Offset(cx, bottom))
        drawCircle(bgBlack2, radius = strokePx / 2f, center = Offset(cx, top))
        if (progress > 0f) {
            val brush = Brush.verticalGradient(
                0f to lightGreen, 1f to darkGreen,
                startY = 0f, endY = h,
            )
            val progressTopY = bottom - (bottom * progress)
            drawCircle(brush = brush, radius = strokePx / 2f, center = Offset(cx, bottom))
            drawLine(brush = brush, start = Offset(cx, bottom), end = Offset(cx, progressTopY), strokeWidth = strokePx)
            drawCircle(brush = brush, radius = strokePx / 2f, center = Offset(cx, progressTopY))
        }
    }
}
```

> The original computes the progress top as `height - (height - widthPx/2) * progress`. Use `bottom - bottom * progress` only if it visually matches; otherwise use the exact original formula `h - (bottom) * progress`. Verify against the pre-migration 5-day bars in Task 2.x and adjust to match.

- [ ] **Step 2: Build**

Run: `.\gradlew.bat assembleDebug`. Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```
git add app/src/main/java/com/crazystudio/sportrecorder/ui/component/VerticalProgress.kt
git commit -m "[Compose] Port VerticalProgressBar to Compose Canvas"
```

---

## Phase 2 — Diet (home) screen

### Task 2.1: Convert `DietViewModel` to a `StateFlow<DietUiState>`

**Files:**
- Modify: `app/src/main/java/com/crazystudio/sportrecorder/ui/diet/DietViewModel.kt`
- Create: `app/src/main/java/com/crazystudio/sportrecorder/ui/diet/DietUiState.kt`

- [ ] **Step 1: Read the current `DietViewModel.kt` and `DietFragment.kt` in full**

Note every piece of state the Fragment renders: the current eating-window start/end (`lastEatTimeLiveData: Pair<Long,Long>`), the 5-day history (`historyLiveData: List<Pair<Long,Float>>`), the selected fasting item (`selectFastingItemLiveData`), the per-second timer loop, and the SharedPreferences listener that reacts to fasting-type changes. Preserve the interval-merging and history-calculation logic verbatim — only change how it is exposed.

- [ ] **Step 2: Create `DietUiState`**

```kotlin
package com.crazystudio.sportrecorder.ui.diet

import com.crazystudio.sportrecorder.ui.diet.select.FastingItem

data class DietUiState(
    val elapsedText: String = "00:00:00",   // formatted current fasting/eating time
    val progress: Float = 0f,               // 0..100 for CircleProgress
    val fastingLabel: String = "",          // e.g. "16 : 8"
    val history: List<HistoryBar> = emptyList(),
    val selectedFastingItem: FastingItem.DefaultFastingItem? = null,
) {
    data class HistoryBar(val dateMillis: Long, val ratio: Float) // ratio 0f..1f
}
```

> Field names/types are a contract used by Task 2.2. If the current Fragment shows additional widgets (read `fragment_diet.xml`), add matching fields here rather than inventing UI.

- [ ] **Step 3: Rewrite the VM to expose `val uiState: StateFlow<DietUiState>`**

Replace the three `MutableLiveData` with a single `MutableStateFlow(DietUiState())`. Move the per-second timer into a `viewModelScope` coroutine that recomputes `elapsedText`/`progress` and updates `_uiState`. Replace the SharedPreferences listener with a `callbackFlow`/`DietPreference` observation that updates `selectedFastingItem` + `fastingLabel`. Keep the DAO-driven history as a `Flow` mapped into `history`. Use `combine(...)` to assemble `DietUiState`. Cancel nothing manually — everything lives in `viewModelScope`.

- [ ] **Step 4: Build**

Run: `.\gradlew.bat assembleDebug`. Expected: `BUILD SUCCESSFUL`. (The old `DietFragment` still references the old LiveData — it will be deleted in Phase 7, but it must still compile now. To avoid a broken intermediate, keep the old LiveData properties as thin wrappers delegating to the new state OR temporarily keep `DietFragment` compiling by leaving its observers reading the wrappers. Simplest: delete `DietFragment` + `fragment_diet.xml` now since the Diet route is built in Task 2.2 — do that deletion in Task 2.2 Step 1 to keep this task green by itself, OR move this VM change and Task 2.2 into one commit.)

> Recommended: merge Task 2.1 + 2.2 into a single working commit to avoid a half-migrated VM that breaks `DietFragment`. The split is for reasoning; commit them together.

- [ ] **Step 5: Commit (together with Task 2.2)** — see Task 2.2 Step 5.

### Task 2.2: Build `DietScreen` and wire the Diet route; delete `DietFragment`

**Files:**
- Create: `app/src/main/java/com/crazystudio/sportrecorder/ui/diet/DietScreen.kt`
- Modify: `app/src/main/java/com/crazystudio/sportrecorder/ui/AppRoot.kt`
- Delete: `app/src/main/java/com/crazystudio/sportrecorder/ui/diet/DietFragment.kt`
- Delete: `app/src/main/res/layout/fragment_diet.xml`
- Delete: `app/src/main/res/layout/item_vertical_bar.xml`

- [ ] **Step 1: Delete the old Diet Fragment + layouts**

```
git rm app/src/main/java/com/crazystudio/sportrecorder/ui/diet/DietFragment.kt app/src/main/res/layout/fragment_diet.xml app/src/main/res/layout/item_vertical_bar.xml
```

- [ ] **Step 2: Create `DietScreen`**

Port `fragment_diet.xml` + `DietFragment` layout/behavior faithfully: a centered `CircleProgress` with the "Fasting time" label + `elapsedText` overlaid in the middle; the `fastingLabel` chip with an edit pencil that navigates to `Route.SelectFastingType`; a row of 5 `VerticalProgress` bars with date labels (`history`); a FAB (`+`) that navigates to `Route.CreateEatTime`. Signature:

```kotlin
@Composable
fun DietScreen(
    state: DietUiState,
    onEditFastingType: () -> Unit,
    onAddEatTime: () -> Unit,
    modifier: Modifier = Modifier,
)
```

Use `CircleProgress(progress = state.progress)` and `VerticalProgress(progress = bar.ratio)` from Phase 1. Match colors/typography from `SportRecorderTheme`. Reproduce the date formatting the Fragment used (`SimpleDateFormat` patterns — copy them).

- [ ] **Step 3: Wire the Diet route in `AppRoot.kt`**

Replace `composable<Route.Diet> { Text("Diet (placeholder)") }` with:

```kotlin
                composable<Route.Diet> {
                    val vm: DietViewModel = hiltViewModel()
                    val state by vm.uiState.collectAsStateWithLifecycle()
                    DietScreen(
                        state = state,
                        onEditFastingType = { navController.navigate(Route.SelectFastingType) },
                        onAddEatTime = { navController.navigate(Route.CreateEatTime) },
                    )
                }
```

- [ ] **Step 4: Build + run + screenshot-compare against the pre-migration home screen**

`.\gradlew.bat assembleDebug` then `installDebug`; screenshot the home screen. Compare against the original home (timer circle `00:00:00`, `16 : 8` chip, 5 day bars `06/05`–`06/09`, FAB). Adjust `CircleProgress`/`VerticalProgress`/layout until it matches. Verify the per-second timer ticks and tapping the pencil opens the fasting-type sheet and the FAB opens the eat-time sheet.

- [ ] **Step 5: Commit (Task 2.1 + 2.2 together)**

```
git add -A
git commit -m "[Compose] Diet home screen with StateFlow VM; remove DietFragment"
```

---

## Phase 3 — Record screen

### Task 3.1: Convert `DietRecordViewModel` to `StateFlow` and build `RecordScreen`; delete the Fragment + adapter

**Files:**
- Modify: `app/src/main/java/com/crazystudio/sportrecorder/ui/diet/record/DietRecordViewModel.kt`
- Create: `app/src/main/java/com/crazystudio/sportrecorder/ui/diet/record/RecordScreen.kt`
- Modify: `app/src/main/java/com/crazystudio/sportrecorder/ui/AppRoot.kt`
- Delete: `DietRecordFragment.kt`, `DietRecordAdapter.kt`, `res/layout/fragment_diet_record.xml`, `res/layout/item_diet_record.xml`

- [ ] **Step 1: Read `DietRecordFragment.kt`, `DietRecordAdapter.kt`, `item_diet_record.xml`**

Note the displayed columns (id, date `yyyy/MM/dd`, time `HH:mm`) and the long-press-to-delete flow (current `AlertDialog` confirmation → `viewModel.deleteEatTime`).

- [ ] **Step 2: Expose records as `StateFlow<List<EatTime>>`**

In `DietRecordViewModel`, change `eatTimeLiveData` to `val records: StateFlow<List<EatTime>>` (map the DAO `Flow` with `.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())`). Keep `deleteEatTime(eatTime)`.

- [ ] **Step 3: Build `RecordScreen`**

```kotlin
@Composable
fun RecordScreen(
    records: List<EatTime>,
    onDelete: (EatTime) -> Unit,
    modifier: Modifier = Modifier,
)
```

A `LazyColumn` of rows showing id / formatted date / formatted time (copy the `SimpleDateFormat` patterns). Long-press a row → show an M3 `AlertDialog` confirm → `onDelete`. Wire the route in `AppRoot.kt` (mirror the Diet wiring with `hiltViewModel()` + `collectAsStateWithLifecycle()`).

- [ ] **Step 4: Delete old files**

```
git rm app/src/main/java/com/crazystudio/sportrecorder/ui/diet/record/DietRecordFragment.kt app/src/main/java/com/crazystudio/sportrecorder/ui/diet/record/DietRecordAdapter.kt app/src/main/res/layout/fragment_diet_record.xml app/src/main/res/layout/item_diet_record.xml
```

- [ ] **Step 5: Build + run + verify**

`.\gradlew.bat assembleDebug` + `installDebug`. Tap the Record tab; verify the list renders existing eat-time records and long-press delete works (with confirm dialog). Screenshot-compare to the original Record tab.

- [ ] **Step 6: Commit**

```
git add -A
git commit -m "[Compose] Record screen with LazyColumn; remove DietRecordFragment + adapter"
```

---

## Phase 4 — Create Eat Time bottom sheet

### Task 4.1: Convert `CreateEatTimeViewModel` to `StateFlow` and build `CreateEatTimeSheet`; delete the Fragment + adapter

**Files:**
- Modify: `app/src/main/java/com/crazystudio/sportrecorder/ui/diet/create/eating/CreateEatTimeViewModel.kt`
- Create: `app/src/main/java/com/crazystudio/sportrecorder/ui/diet/create/eating/CreateEatTimeSheet.kt`
- Modify: `app/src/main/java/com/crazystudio/sportrecorder/ui/AppRoot.kt`
- Delete: `CreateEatTimeDialogFragment.kt`, `CreateEatTimeAdapter.kt`, `res/layout/fragment_create_eat_time.xml`, `res/layout/item_create_eat_header.xml`

- [ ] **Step 1: Read `CreateEatTimeDialogFragment.kt`, `CreateEatTimeAdapter.kt`, `fragment_create_eat_time.xml`, `item_create_eat_header.xml`**

Note: selected date + time (a `Calendar`), the food-record list for this eat time, the date click → `DatePickerDialog`, time click → `TimePickerDialog`, the "+" row → navigate to `CreateFoodRecord`, per-food delete, and the create/confirm action (`createEatingTime()`).

- [ ] **Step 2: Expose `StateFlow<CreateEatTimeUiState>`**

Create `CreateEatTimeUiState(date: Calendar, foods: List<FoodRecord>)`. Convert the two `MutableLiveData` to one `MutableStateFlow`; map the DAO food-record `Flow` into it. Keep `updateDate()`, `updateTime()`, `createEatingTime()`, `deleteFoodRecord()`.

- [ ] **Step 3: Build `CreateEatTimeSheet`**

A bottom-sheet composable (rendered by the `bottomSheet<Route.CreateEatTime>` destination). Contents: a date field (tap → M3 `DatePickerDialog` or the platform `DatePickerDialog`), a time field (→ time picker), a "+ add food record" row → `onAddFood`, a list of foods each with a delete button, and a confirm button calling `createEatingTime()` then `popBackStack()`. Signature:

```kotlin
@Composable
fun CreateEatTimeSheet(
    state: CreateEatTimeUiState,
    onPickDate: () -> Unit,
    onPickTime: () -> Unit,
    onAddFood: () -> Unit,
    onDeleteFood: (FoodRecord) -> Unit,
    onConfirm: () -> Unit,
)
```

Wire `bottomSheet<Route.CreateEatTime>` in `AppRoot.kt` with `hiltViewModel()` + state collection; `onAddFood = { navController.navigate(Route.CreateFoodRecord(eatTimeId = <id from VM>)) }` (use the same id the current adapter passes).

- [ ] **Step 4: Delete old files**

```
git rm app/src/main/java/com/crazystudio/sportrecorder/ui/diet/create/eating/CreateEatTimeDialogFragment.kt app/src/main/java/com/crazystudio/sportrecorder/ui/diet/create/eating/CreateEatTimeAdapter.kt app/src/main/res/layout/fragment_create_eat_time.xml app/src/main/res/layout/item_create_eat_header.xml
```

- [ ] **Step 5: Build + run + verify**

Open the sheet via the Diet FAB. Verify date/time pickers work, adding/deleting foods works, and confirming creates the eat time (then appears on the Record tab). Screenshot-compare.

- [ ] **Step 6: Commit**

```
git add -A
git commit -m "[Compose] CreateEatTime bottom sheet; remove fragment + multi-type adapter"
```

---

## Phase 5 — Create Food Record bottom sheet

### Task 5.1: Build `CreateFoodRecordSheet`; convert VM; delete the Fragment

**Files:**
- Modify: `app/src/main/java/com/crazystudio/sportrecorder/ui/diet/create/food/CreateFoodRecordViewMode.kt`
- Create: `app/src/main/java/com/crazystudio/sportrecorder/ui/diet/create/food/CreateFoodRecordSheet.kt`
- Modify: `app/src/main/java/com/crazystudio/sportrecorder/ui/AppRoot.kt`
- Delete: `CreateFoodRecordDialogFragment.kt`, `res/layout/fragment_create_food_record.xml`, `res/layout/item_food_record_edit_text.xml`

- [ ] **Step 1: Read `CreateFoodRecordDialogFragment.kt`, `fragment_create_food_record.xml`, `item_food_record_edit_text.xml`**

Note the form fields (name, carbs, protein, fat) and the `createFoodRecord(eatTimeId, name, carbs, protein, fat)` call + how `eatTimeId` is received (safe-args arg).

- [ ] **Step 2: Build `CreateFoodRecordSheet`**

```kotlin
@Composable
fun CreateFoodRecordSheet(
    onConfirm: (name: String, carbs: String, protein: String, fat: String) -> Unit,
)
```

Reproduce the reusable "icon + title + EditText" rows from `item_food_record_edit_text.xml` as a small private composable. Local `remember { mutableStateOf("") }` per field; confirm → `onConfirm(...)` then `popBackStack()`. The VM method `createFoodRecord` already exists (suspend) — call it from the destination via the VM, passing `eatTimeId` from `Route.CreateFoodRecord.eatTimeId`.

- [ ] **Step 3: Wire `bottomSheet<Route.CreateFoodRecord>` in `AppRoot.kt`**

```kotlin
                bottomSheet<Route.CreateFoodRecord> { entry ->
                    val args = entry.toRoute<Route.CreateFoodRecord>()
                    val vm: CreateFoodRecordViewModel = hiltViewModel()
                    val scope = rememberCoroutineScope()
                    CreateFoodRecordSheet(onConfirm = { name, carbs, protein, fat ->
                        scope.launch {
                            vm.createFoodRecord(args.eatTimeId, name, carbs, protein, fat)
                            navController.popBackStack()
                        }
                    })
                }
```

(Add imports: `androidx.navigation.toRoute`, `androidx.compose.runtime.rememberCoroutineScope`, `kotlinx.coroutines.launch`. Match `createFoodRecord`'s exact parameter types — read the VM; convert String→numeric as the original Fragment did.)

- [ ] **Step 4: Delete old files**

```
git rm app/src/main/java/com/crazystudio/sportrecorder/ui/diet/create/food/CreateFoodRecordDialogFragment.kt app/src/main/res/layout/fragment_create_food_record.xml app/src/main/res/layout/item_food_record_edit_text.xml
```

- [ ] **Step 5: Build + run + verify**

From the CreateEatTime sheet, tap "+" → fill the food form → confirm → verify the food appears back in the eat-time sheet's list. Screenshot-compare.

- [ ] **Step 6: Commit**

```
git add -A
git commit -m "[Compose] CreateFoodRecord bottom sheet; remove fragment"
```

---

## Phase 6 — Notifications screen

### Task 6.1: Build `NotificationsScreen`; delete the Fragment

**Files:**
- Create: `app/src/main/java/com/crazystudio/sportrecorder/ui/notifications/NotificationsScreen.kt`
- Modify: `app/src/main/java/com/crazystudio/sportrecorder/ui/AppRoot.kt`
- Delete: `NotificationsFragment.kt`, `res/layout/fragment_notifications.xml`
- Optionally delete: `NotificationsViewModel.kt` (if only used for the placeholder text)

- [ ] **Step 1: Read `NotificationsFragment.kt` + `fragment_notifications.xml`**

It is a single centered "Coming soon" `TextView`.

- [ ] **Step 2: Create `NotificationsScreen`**

```kotlin
package com.crazystudio.sportrecorder.ui.notifications

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
fun NotificationsScreen(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Coming soon")
    }
}
```

Wire `composable<Route.Notifications> { NotificationsScreen() }` in `AppRoot.kt`.

- [ ] **Step 3: Delete old files**

```
git rm app/src/main/java/com/crazystudio/sportrecorder/ui/notifications/NotificationsFragment.kt app/src/main/res/layout/fragment_notifications.xml
```

- [ ] **Step 4: Build + run + verify**

Tap the Notifications tab; verify "Coming soon" is centered. `.\gradlew.bat assembleDebug` green.

- [ ] **Step 5: Commit**

```
git add -A
git commit -m "[Compose] Notifications screen; remove fragment"
```

---

## Phase 7 — Cleanup & final verification

### Task 7.1: Remove dead Fragment/Nav infrastructure and ViewBinding

**Files:**
- Delete: `app/src/main/res/navigation/mobile_navigation.xml`
- Delete: `app/src/main/res/menu/bottom_nav_menu.xml` (only after confirming `AppRoot` uses its drawables directly, not the menu)
- Delete: `app/src/main/res/layout/activity_main.xml`
- Delete: any remaining unused item layouts (`res/layout/item_select.xml`) and `ui/view/SquareLinearLayout.kt`, `ui/view/CircleProgressBar.kt`, `ui/view/VerticalProgressBar.kt`
- Delete: `ui/timer/TimerFragment.kt` if it is an unused stub (confirm it is not referenced)
- Modify: `app/build.gradle` (remove safe-args plugin + navigation-fragment/ui deps; disable `viewBinding`)
- Modify: `build.gradle` (remove the safe-args classpath)

- [ ] **Step 1: Grep for leftover references before deleting**

Run (via Grep tool): search for `findNavController`, `NavHostFragment`, `BottomSheetDialogFragment`, `ViewBinding`, `R.layout.`, `mobile_navigation`, `Directions` across `app/src/main`. Anything still referencing the old world must be migrated or deleted first. The 3 already-Compose fragments (`SelectFastingTypeFragment`, `CreateFastingTypeFragment`, `TimerFragment`) are now unused — delete them too (their screens live in `AppRoot`).

- [ ] **Step 2: Delete the old fragments now superseded by NavHost screens**

```
git rm app/src/main/java/com/crazystudio/sportrecorder/ui/diet/select/SelectFastingTypeFragment.kt app/src/main/java/com/crazystudio/sportrecorder/ui/diet/create/fasting/CreateFastingTypeFragment.kt app/src/main/java/com/crazystudio/sportrecorder/ui/timer/TimerFragment.kt
git rm app/src/main/res/navigation/mobile_navigation.xml app/src/main/res/layout/activity_main.xml app/src/main/res/menu/bottom_nav_menu.xml app/src/main/res/layout/item_select.xml
git rm app/src/main/java/com/crazystudio/sportrecorder/ui/view/SquareLinearLayout.kt app/src/main/java/com/crazystudio/sportrecorder/ui/view/CircleProgressBar.kt app/src/main/java/com/crazystudio/sportrecorder/ui/view/VerticalProgressBar.kt
```

> Keep `bottom_nav_menu.xml` only if Step 1 of Task 0.3 still reads its drawables. Since `AppRoot` hardcodes the drawable IDs, the menu XML can go — but the drawable resources it referenced must remain in `res/drawable*`.

- [ ] **Step 3: Remove safe-args + navigation-fragment from Gradle, disable viewBinding**

In `app/build.gradle`: remove `id 'androidx.navigation.safeargs.kotlin'` from `plugins {}`; remove `navigation-fragment-ktx` and `navigation-ui-ktx` dependencies; in `buildFeatures { }` remove `viewBinding true` (keep `compose true`). In root `build.gradle`: remove the `classpath "androidx.navigation:navigation-safe-args-gradle-plugin:$nav_version"` line (and the now-unused `nav_version` def if nothing else uses it).

- [ ] **Step 4: Build clean**

Run: `.\gradlew.bat clean assembleDebug`. Expected: `BUILD SUCCESSFUL` with no generated `*Binding` / `*Directions` references and no `R.layout` errors.

- [ ] **Step 5: Commit**

```
git add -A
git commit -m "[Compose] Remove Fragment/nav-graph/safe-args/ViewBinding infrastructure"
```

### Task 7.2: Full-app smoke test on the emulator

**Files:** none.

- [ ] **Step 1: Install and exercise every flow**

```
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat installDebug
```
Walk: Diet home (timer ticking, history bars, FAB) → edit fasting type (select + create custom) → add eat time (date/time pickers, add food, delete food, confirm) → Record tab (list shows the new record, long-press delete) → Notifications tab. Confirm no crashes in `adb logcat` and Room data persists across an app restart.

- [ ] **Step 2: Screenshot each screen and compare against pre-migration**

Capture Diet / Record / Notifications / each sheet via `adb shell screencap` + `adb pull`; confirm visual parity with the original View-based screens.

- [ ] **Step 3: Final release build**

Run: `.\gradlew.bat assembleRelease`. Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: No commit needed** (verification only). If any screenshot revealed a parity gap, fix it in the owning screen's file and commit with `[Compose] Fix <screen> parity: <detail>`.

---

## Self-Review (author check vs. spec)

- **Spec coverage:** single-Activity Compose shell (Task 0.3, MainActivity), Navigation-Compose + type-safe routes (0.2/0.3), hilt-navigation-compose + StateFlow/UiState (every screen VM task), bottom sheets via material-navigation (0.3/0.4/4/5), custom-view ports (1.1/1.2), all five screen ports (2–6), dependency add/remove + viewBinding off (0.1/7.1), theme reuse (0.3), faithful-parity verification (2.2/3/4/5/6/7.2). All spec sections map to tasks.
- **Placeholder scan:** screen-port tasks reference the exact existing source files as the behavioral spec and give concrete UiState contracts + composable signatures + wiring code; canvas ports and shell give full code. No "TBD"/"add error handling"-style placeholders.
- **Type/name consistency:** `Route.*` used identically across 0.2→7; `uiState: StateFlow<…>` + `collectAsStateWithLifecycle()` pattern consistent; `CircleProgress(progress: Float 0..100)` and `VerticalProgress(progress: Float 0..1)` match their Diet usage.
- **Known residual uncertainty (called out in-task):** exact `material-navigation` artifact version/import path (0.1/0.3), gradient orientation of the ported canvases (1.1/1.2 — resolved by screenshot parity in 2.2), and the precise `CreateFoodRecord` route args (0.2/5.1 — resolved by reading the current safe-args action).
