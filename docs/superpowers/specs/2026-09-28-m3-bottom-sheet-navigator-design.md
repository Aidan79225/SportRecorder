# Material 3 bottom-sheet navigator — Design

**Status:** approved 2026-09-28 · **Supersedes:** the Material 2 half-expanded workaround on PR #69

## Goal

Host every bottom-sheet route (record editor, fasting-type selector, create fasting type, day
records) on Material 3's `ModalBottomSheet`, and drop the last Material 2 dependencies
(`androidx.compose.material:material-navigation` and the `material` artifact PR #69 added). The
owner's rule: the app is Material 3 only.

## Why we own the navigator

AndroidX ships a sheet navigator only for Material 2. Nothing equivalent exists for M3 on Google
Maven or Maven Central (only `material3-adaptive-navigation-suite`, which is unrelated). Navigation
Compose's own `DialogNavigator` + `DialogHost` (~220 lines, public APIs: `Navigator`,
`NavigatorState.push/popWithTransition/markTransitionComplete`, `FloatingWindow`,
`NavBackStackEntry.LocalOwnersProvider`, `NavDestinationBuilder`) is the exact shape we need with
`Dialog` swapped for `ModalBottomSheet`. Porting it keeps routes, typed arguments, per-entry
ViewModels and `SavedStateHandle` untouched.

## Design

`app/src/main/java/com/crazystudio/sportrecorder/ui/nav/BottomSheetNavigator.kt` (Android-only; the
sheet host is an `:app` concern like `AppRoot`):

- `@Navigator.Name("bottomSheet") class BottomSheetNavigator : Navigator<BottomSheetNavigator.Destination>()`
  - `navigate` → `state.push(entry)` (as upstream `DialogNavigator`); `popBackStack` →
    `state.popWithTransition`, then release the incoming sheet below the popped one (held in STARTED
    as transitioning) so it can reach RESUMED, exactly as `DialogNavigator` does.
  - `Destination(navigator, content: @Composable (NavBackStackEntry) -> Unit) : NavDestination, FloatingWindow`.
- `inline fun <reified T : Any> NavGraphBuilder.bottomSheet(typeMap = emptyMap(), noinline content)` —
  a `NavDestinationBuilder<Destination>` subclass, mirroring `NavGraphBuilder.dialog<T>`.
- `@Composable fun BottomSheetHost(navigator: BottomSheetNavigator)` — the port of `DialogHost`:
  visible entries (lifecycle ≥ STARTED) each get
  `ModalBottomSheet(onDismissRequest = { navigator.dismiss(entry) }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true))`
  whose content is `entry.LocalOwnersProvider(saveableStateHolder) { destination.content(entry) }`.
  - **User dismiss** (swipe, scrim, back): M3 animates the hide, then calls `onDismissRequest` →
    `popBackStack(entry, false)`.
  - **Programmatic pop** (`navController.popBackStack()` after save): the entry leaves `backStack`
    but stays in `transitionsInProgress`. NavController moves it to CREATED at once, which drops it
    from the visible list, so the host keeps rendering composed, popped, in-transition entries. A
    `LaunchedEffect` on "in back stack?" runs `sheetState.hide()` and, in a `finally` (back or a drag
    can interrupt the hide), `navigator.onTransitionComplete(entry)`, so the sheet slides out instead
    of vanishing and is always released. The `DisposableEffect`/`dialogsToDispose` leak guard from
    `DialogHost` is kept.
  - Sheet over sheet (選類型 → 新增類型) composes two `ModalBottomSheet`s; the newer one stacks on
    top, and dismissing it reveals the first — same as two dialogs today.
- `AppRoot`: `ModalBottomSheetLayout(bottomSheetNavigator) { … }` → `BottomSheetHost(navigator)`
  placed inside the `Scaffold` content lambda, after `NavHost` (a sibling after `Scaffold(...)` runs
  before the subcomposed `NavHost` attaches the navigator and crashes); `rememberNavController(navigator)` unchanged; the four `bottomSheet<…>`
  bodies unchanged apart from the import.

### Sheet screens

M3's sheet respects system bars itself (`contentWindowInsets`) and shows a drag handle, so:
- Revert PR #69's `statusBarsPadding()` on the three screens; keep the selector content-sized
  (`fillMaxWidth`, not `fillMaxSize`) — an M3 sheet with `fillMaxSize` content is full-height.
- `EatTimeEditorSheet` keeps `imePadding()`; whether its `navigationBarsPadding()` now double-pads is
  checked on device and removed if so (M3 applies the bottom inset to the sheet already).
- Date/time pickers are Android dialogs opened from inside the sheet; they must appear above the
  sheet window. Verified on device.

### Dependencies

Remove `androidx.compose.material:material-navigation` and `androidx.compose.material:material`
from `app/build.gradle.kts` and the catalog. `AppBottomSheetNavigator.kt` (the M2 workaround) is
deleted.

## Testing

- `BottomSheetHostTest` (androidTest, `createComposeRule`): a `NavHost` with a `composable` home and
  a `bottomSheet` route hosting the real `EatTimeEditorSheet`; asserts (1) the confirm button's
  bottom is within the root (the #69 regression, now on M3), (2) dismissing via
  `onDismissRequest` pops the entry (home visible, sheet gone), (3) `popBackStack()` from the sheet
  content removes the sheet, (4) navigating sheet → sheet shows the second sheet's content.
- `EatTimeEditorSheetTest` lives on PR #68's branch (`claude/test-coverage-gaps`); re-run it against
  this host once both PRs merge.
- Manual on emulator: all four sheets open fully, pickers appear above the editor, keyboard on the
  note field keeps the confirm button reachable, system back closes the sheet.

## 初衷對照 / North-Star check

No user-facing feature change; the capture sheet stops hiding its button. Nothing judges or nags.

## Docs

`docs/DEVELOPMENT.md` §2 (`ui/nav/BottomSheetNavigator`), §6 row.
