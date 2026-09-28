# Material 3 Bottom-Sheet Navigator Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Host all four bottom-sheet routes on Material 3 `ModalBottomSheet` through an app-owned navigator, and remove every Material 2 dependency.

**Architecture:** `ui/nav/BottomSheetNavigator.kt` ports Navigation Compose's `DialogNavigator`/`DialogHost` with `Dialog` replaced by `ModalBottomSheet(skipPartiallyExpanded = true)`, plus a typed `bottomSheet<T>` builder. `AppRoot` swaps `ModalBottomSheetLayout` for `BottomSheetHost`. Routes, ViewModels and `SavedStateHandle` args are untouched.

**Tech Stack:** Navigation Compose 2.9.8 public APIs (`Navigator`, `NavigatorState`, `FloatingWindow`, `NavDestinationBuilder`, `LocalOwnersProvider`), Material 3 `ModalBottomSheet`, Compose `ui-test-junit4`.

**Spec:** `docs/superpowers/specs/2026-09-28-m3-bottom-sheet-navigator-design.md`

## Global Constraints

- Branch `claude/fix-editor-sheet-half-expanded` (PR #69; HEAD c77df13, which carries the M2 workaround this plan replaces). Commits end with `Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>`.
- Material 3 only: after Task 2 no `androidx.compose.material:` (non-`material3`) artifact remains in `app/build.gradle.kts` or `gradle/libs.versions.toml`.
- Gate before every commit touching `:app` main or `:shared`: `.\gradlew.bat assembleDebug testDebugUnitTest :app:detekt :app:lintDebug :shared:jvmTest`; test APK: `:app:assembleDebugAndroidTest`. Emulator runs: `$env:ANDROID_SERIAL="<adb devices>"` (currently `emulator-5560`), quoted `-Pandroid.testInstrumentationRunnerArguments.class=<fqcn>`; PowerShell sets `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"` in the same call. Max timeout.
- detekt on `:app`: `MagicNumber` outside `ui/` named (the new file is under `ui/nav/` so excluded), `TooManyFunctions` 11, `MaxLineLength` 120, `LongParameterList` 6/7, `ReturnCount` 2.
- Reference source to port faithfully (fetched during design): `androidx.navigation.compose.DialogNavigator` / `DialogHost` at androidx-main — the implementer re-reads them if in doubt: `https://raw.githubusercontent.com/androidx/androidx/androidx-main/navigation/navigation-compose/src/commonMain/kotlin/androidx/navigation/compose/DialogHost.kt` and `DialogNavigator.kt`.

---

### Task 1: `BottomSheetNavigator` + `BottomSheetHost` + `bottomSheet<T>` with an instrumented host test

**Files:**
- Create: `app/src/main/java/com/crazystudio/sportrecorder/ui/nav/BottomSheetNavigator.kt`
- Create: `app/src/androidTest/java/com/crazystudio/sportrecorder/ui/nav/BottomSheetHostTest.kt`
- Delete: `app/src/androidTest/java/com/crazystudio/sportrecorder/ui/nav/AppBottomSheetNavigatorTest.kt` (replaced by the host test; its assertion moves over)

**Interfaces (produced):**
- `class BottomSheetNavigator : Navigator<BottomSheetNavigator.Destination>` (`@Navigator.Name("bottomSheet")`)
- `inline fun <reified T : Any> NavGraphBuilder.bottomSheet(typeMap: Map<KType, NavType<*>> = emptyMap(), noinline content: @Composable (NavBackStackEntry) -> Unit)`
- `@Composable fun BottomSheetHost(navigator: BottomSheetNavigator)`

- [ ] **Step 1: Write the navigator, builder and host**

```kotlin
package com.crazystudio.sportrecorder.ui.nav

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.FloatingWindow
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination
import androidx.navigation.NavDestinationBuilder
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.NavType
import androidx.navigation.Navigator
import androidx.navigation.compose.LocalOwnersProvider
import androidx.navigation.get
import kotlin.reflect.KClass
import kotlin.reflect.KType

private const val NAVIGATOR_NAME = "bottomSheet"

/**
 * Navigator for routes shown as a Material 3 [ModalBottomSheet]. AndroidX ships a sheet navigator
 * only for Material 2, so this is a port of Navigation Compose's own `DialogNavigator`: a
 * [FloatingWindow] destination whose entries stay on the back stack (own ViewModelStore, own
 * SavedStateHandle) while [BottomSheetHost] renders the visible ones.
 */
@Navigator.Name(NAVIGATOR_NAME)
class BottomSheetNavigator : Navigator<BottomSheetNavigator.Destination>(NAVIGATOR_NAME) {

    internal val backStack get() = state.backStack
    internal val transitionsInProgress get() = state.transitionsInProgress

    /** A user dismissal (swipe, scrim, back) — the sheet has already animated out. */
    internal fun dismiss(entry: NavBackStackEntry) = popBackStack(entry, false)

    override fun navigate(entries: List<NavBackStackEntry>, navOptions: NavOptions?, navigatorExtras: Extras?) {
        entries.forEach { entry -> state.push(entry) }
    }

    override fun createDestination(): Destination = Destination(this) {}

    override fun popBackStack(popUpTo: NavBackStackEntry, savedState: Boolean) {
        state.popWithTransition(popUpTo, savedState)
        // popWithTransition also marks the incoming sheet below the popped one as transitioning,
        // holding it in STARTED; release it so it can move to RESUMED. The popped entry itself is
        // released by the host once its slide-out ends.
        val popIndex = state.transitionsInProgress.value.indexOf(popUpTo)
        state.transitionsInProgress.value.forEachIndexed { index, entry ->
            if (index > popIndex) onTransitionComplete(entry)
        }
    }

    internal fun onTransitionComplete(entry: NavBackStackEntry) {
        if (state.transitionsInProgress.value.contains(entry)) state.markTransitionComplete(entry)
    }

    @NavDestination.ClassType(Composable::class)
    class Destination(
        navigator: BottomSheetNavigator,
        internal val content: @Composable (NavBackStackEntry) -> Unit,
    ) : NavDestination(navigator), FloatingWindow
}

/** Builder behind [bottomSheet], mirroring `NavGraphBuilder.dialog<T>`. */
class BottomSheetNavigatorDestinationBuilder(
    private val sheetNavigator: BottomSheetNavigator,
    route: KClass<*>,
    typeMap: Map<KType, NavType<*>>,
    private val content: @Composable (NavBackStackEntry) -> Unit,
) : NavDestinationBuilder<BottomSheetNavigator.Destination>(sheetNavigator, route, typeMap) {
    override fun instantiateDestination(): BottomSheetNavigator.Destination =
        BottomSheetNavigator.Destination(sheetNavigator, content)
}

/** Add a typed route rendered as a Material 3 modal bottom sheet. */
inline fun <reified T : Any> NavGraphBuilder.bottomSheet(
    typeMap: Map<KType, NavType<*>> = emptyMap(),
    noinline content: @Composable (NavBackStackEntry) -> Unit,
) {
    destination(
        BottomSheetNavigatorDestinationBuilder(provider[BottomSheetNavigator::class], T::class, typeMap, content),
    )
}

/**
 * Renders every visible sheet entry as a [ModalBottomSheet] that skips the half-height stop, so a
 * tall sheet (the meal editor) opens with its confirm button on screen. A user dismissal pops the
 * entry; a programmatic pop slides the sheet out before the entry is released.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BottomSheetHost(navigator: BottomSheetNavigator) {
    val saveableStateHolder = rememberSaveableStateHolder()
    val backStack by navigator.backStack.collectAsState()
    val visibleEntries = rememberVisibleEntries(backStack)
    visibleEntries.PopulateVisibleEntries(backStack)
    val transitionsInProgress by navigator.transitionsInProgress.collectAsState()
    val composedEntries = remember { mutableStateListOf<NavBackStackEntry>() }

    visibleEntries.forEach { entry ->
        key(entry.id) {
            val destination = entry.destination as BottomSheetNavigator.Destination
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            val inBackStack = backStack.contains(entry)
            LaunchedEffect(inBackStack) {
                if (!inBackStack) {
                    // Popped from code (e.g. after saving): animate out, then let the entry go.
                    sheetState.hide()
                    navigator.onTransitionComplete(entry)
                }
            }
            ModalBottomSheet(
                onDismissRequest = { navigator.dismiss(entry) },
                sheetState = sheetState,
            ) {
                DisposableEffect(entry) {
                    composedEntries.add(entry)
                    onDispose {
                        navigator.onTransitionComplete(entry)
                        composedEntries.remove(entry)
                    }
                }
                entry.LocalOwnersProvider(saveableStateHolder) { destination.content(entry) }
            }
        }
    }
    // Entries popped before they were ever composed would otherwise stay "in transition" forever.
    LaunchedEffect(transitionsInProgress, composedEntries) {
        transitionsInProgress.forEach { entry ->
            if (!navigator.backStack.value.contains(entry) && !composedEntries.contains(entry)) {
                navigator.onTransitionComplete(entry)
            }
        }
    }
}

@Composable
private fun rememberVisibleEntries(backStack: Collection<NavBackStackEntry>): SnapshotStateList<NavBackStackEntry> =
    remember(backStack) {
        mutableStateListOf<NavBackStackEntry>().also { list ->
            list.addAll(backStack.filter { it.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) })
        }
    }

@Composable
private fun MutableList<NavBackStackEntry>.PopulateVisibleEntries(backStack: Collection<NavBackStackEntry>) {
    backStack.forEach { entry ->
        DisposableEffect(entry.lifecycle) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_START && !contains(entry)) add(entry)
                if (event == Lifecycle.Event.ON_STOP) remove(entry)
            }
            entry.lifecycle.addObserver(observer)
            onDispose { entry.lifecycle.removeObserver(observer) }
        }
    }
}
```

Implementer notes: `Navigator(name)` constructor, `NavigatorState.push/popWithTransition/markTransitionComplete`, `NavDestinationBuilder(navigator, route: KClass<*>?, typeMap)`, `NavGraphBuilder.provider`, `NavigatorProvider.get(KClass)` (import `androidx.navigation.get`) and `NavBackStackEntry.LocalOwnersProvider` are all public in navigation 2.9.8; if a name differs, read the library's `DialogNavigator.kt`/`NavGraphBuilder.kt` sources in the Gradle cache (`navigation-compose-android` / `navigation-common` sources jars, or javap) and adapt — do not change the design. `ModalBottomSheet` requires `@OptIn(ExperimentalMaterial3Api::class)` in this Material 3 version. If `state.push` is not available (older API), use `pushWithTransition` and mark complete in a `LaunchedEffect(entry)` once composed.

- [ ] **Step 2: Write the host test (replaces `AppBottomSheetNavigatorTest`)**

```kotlin
package com.crazystudio.sportrecorder.ui.nav

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.crazystudio.sportrecorder.shared.resources.Res
import com.crazystudio.sportrecorder.shared.resources.diet_eat_create
import com.crazystudio.sportrecorder.ui.diet.editor.EatTimeEditorSheet
import com.crazystudio.sportrecorder.ui.diet.editor.EatTimeEditorUiState
import com.crazystudio.sportrecorder.ui.theme.SportRecorderTheme
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import org.jetbrains.compose.resources.getString
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@Serializable private object Home
@Serializable private object Editor
@Serializable private object Second

@RunWith(AndroidJUnit4::class)
class BottomSheetHostTest {
    @get:Rule val compose = createComposeRule()

    private fun setUpGraph() {
        compose.setContent {
            SportRecorderTheme {
                val navigator = remember { BottomSheetNavigator() }
                val navController = rememberNavController(navigator)
                NavHost(navController, startDestination = Home) {
                    composable<Home> {
                        Button(onClick = { navController.navigate(Editor) }) { Text("open") }
                    }
                    bottomSheet<Editor> {
                        Column {
                            Button(onClick = { navController.navigate(Second) }) { Text("stack") }
                            Button(onClick = { navController.popBackStack() }) { Text("pop") }
                            EatTimeEditorSheet(
                                state = EatTimeEditorUiState(), photoModel = { null },
                                onPickDate = {}, onPickTime = {}, onNoteChange = {}, onAddPhoto = {}, onSelectPhoto = {},
                                onRemovePendingPhoto = {}, onRemoveExistingPhoto = {}, onRecaptureLocation = {},
                                onClearLocation = {}, onConfirm = {},
                            )
                        }
                    }
                    bottomSheet<Second> { Text("second sheet") }
                }
                BottomSheetHost(navigator)
            }
        }
    }

    private fun createLabel() = runBlocking { getString(Res.string.diet_eat_create) }

    private fun openEditor() {
        compose.onNodeWithText("open").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText(createLabel()).fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle()
    }

    @Test fun tallSheet_opensWithConfirmButtonOnScreen() {
        setUpGraph(); openEditor()
        val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
        val confirm = compose.onNodeWithText(createLabel()).fetchSemanticsNode().boundsInRoot
        assertTrue("confirm bottom ${confirm.bottom} > root ${root.bottom}", confirm.bottom <= root.bottom)
    }

    @Test fun popBackStackFromContent_closesTheSheet() {
        setUpGraph(); openEditor()
        compose.onNodeWithText("pop").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText(createLabel()).fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithText("open").assertIsDisplayed()
    }

    @Test fun systemBack_dismissesAndPops() {
        setUpGraph(); openEditor()
        Espresso.pressBack()
        compose.waitUntil(5_000) { compose.onAllNodesWithText(createLabel()).fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithText("open").assertIsDisplayed()
    }

    @Test fun sheetOverSheet_showsTheSecond() {
        setUpGraph(); openEditor()
        compose.onNodeWithText("stack").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("second sheet").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("second sheet").assertIsDisplayed()
    }
}
```

Add the missing imports (`Column`, `remember`). A `ModalBottomSheet` renders in its own window; Compose test rule's `onNodeWithText` searches all windows of the test activity by default (`ComposeTestRule` merges root nodes across dialogs/popups), so the queries work. If `Espresso.pressBack()` throws because the sheet window owns focus, use `compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }` via `createAndroidComposeRule<ComponentActivity>()` instead and say so.

- [ ] **Step 3: Compile, run the class on the emulator (4/4), then the gate; commit**

```bash
git rm app/src/androidTest/java/com/crazystudio/sportrecorder/ui/nav/AppBottomSheetNavigatorTest.kt
git add app/src/main/java/com/crazystudio/sportrecorder/ui/nav/BottomSheetNavigator.kt app/src/androidTest/java/com/crazystudio/sportrecorder/ui/nav/BottomSheetHostTest.kt
git commit -m "feat(nav): Material 3 bottom-sheet navigator and host (port of DialogNavigator/DialogHost)

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 2: Swap AppRoot onto the M3 host, remove Material 2, revert the M2-era paddings, verify on device

**Files:**
- Modify: `app/src/main/java/com/crazystudio/sportrecorder/ui/AppRoot.kt`
- Delete: `app/src/main/java/com/crazystudio/sportrecorder/ui/nav/AppBottomSheetNavigator.kt`
- Modify: `app/build.gradle.kts`, `gradle/libs.versions.toml` (remove `androidx-compose-material` and `androidx-compose-material-navigation`)
- Modify: `shared/src/commonMain/kotlin/com/crazystudio/sportrecorder/ui/diet/select/SelectFastingTypeScreen.kt`, `ui/diet/create/fasting/CreateFastingTypeScreen.kt`, `ui/diet/editor/EatTimeEditorSheet.kt` (drop the `statusBarsPadding()` lines and their imports added on this branch; keep the selector's `fillMaxWidth` + `navigationBarsPadding`)

- [ ] **Step 1: AppRoot** — replace imports `androidx.compose.material.navigation.{ModalBottomSheetLayout, bottomSheet, rememberBottomSheetNavigator}` and `com.crazystudio.sportrecorder.ui.nav.rememberAppBottomSheetNavigator` with `com.crazystudio.sportrecorder.ui.nav.{BottomSheetNavigator, BottomSheetHost, bottomSheet}`; `val bottomSheetNavigator = remember { BottomSheetNavigator() }`; remove the `ModalBottomSheetLayout(bottomSheetNavigator) { … }` wrapper (keep its body) and add `BottomSheetHost(bottomSheetNavigator)` inside the `Scaffold` content lambda, right after `NavHost` (not after the `Scaffold(...)` call: Scaffold subcomposes its content, so a sibling would read the navigator's state before `NavHost` attaches it and crash). The four `bottomSheet<Route.X> { … }` calls stay as they are.
- [ ] **Step 2: Dependencies** — delete the two catalog entries and the two `implementation(...)` lines; `grep -rn "compose.material\." app/src/main` must show only `material3` imports.
- [ ] **Step 3: Revert the paddings** — remove the three `statusBarsPadding()` modifiers (and now-unused imports). Keep `SelectFastingTypeScreen`'s `fillMaxWidth()` + `navigationBarsPadding()`.
- [ ] **Step 4: Gate + test APK + emulator run** of `BottomSheetHostTest` and `EatTimeEditorSheetTest`.
- [ ] **Step 5: Device verification** (installDebug, grant location; screenshots via `adb shell screencap` + `adb pull`; read them): (a) home FAB → editor sheet opens full with CREATE on screen; (b) tap Date row → the date picker dialog appears ABOVE the sheet; (c) tap the Note field → keyboard; CREATE still reachable (scroll); (d) system back closes the sheet; (e) 「16 : 8」 → selector opens content-sized with the title clear of the status bar, last row above the gesture bar; tap the `+` tile → the create sheet stacks on top; (f) Insights → tap a calendar day → day sheet. If the editor's bottom padding is visibly doubled (large gap under CREATE), remove `navigationBarsPadding()` from `EatTimeEditorSheet` and `DayRecordsSheet` and re-check. Record what you saw in the report with the screenshot paths.
- [ ] **Step 6: Commit**

```bash
git commit -am "refactor(nav): host bottom sheets on the Material 3 navigator; drop Material 2

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```
(stage the deletion of `AppBottomSheetNavigator.kt` explicitly with `git rm`.)

---

### Task 3: Docs

- `docs/DEVELOPMENT.md` §2 `:app` line: add `ui/nav/BottomSheetNavigator`(M3 sheet host); §6 row: `| 09-28 | Material 3 bottom-sheet navigator(移除 M2) | ✓ | ✓ | 已完成 |`. Commit `docs: record the Material 3 sheet navigator`.

## Self-review
Spec sections → T1 (navigator/host/builder/tests), T2 (AppRoot, deps, sheet screens, device checks), T3 (docs). Names consistent: `BottomSheetNavigator`, `BottomSheetHost`, `bottomSheet<T>`, `Destination`. Known judgement points left to the implementer with explicit fallbacks: `state.push` availability, back-press dispatch in the test, doubled bottom padding.
