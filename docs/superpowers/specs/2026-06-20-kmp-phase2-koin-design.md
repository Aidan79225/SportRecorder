# KMP migration — Phase 2: Hilt → Koin

**Date:** 2026-06-20
**Status:** Implemented
**Parent:** `2026-06-20-kmp-ios-migration-design.md`

## Why now / why first

The goal is shipping the iOS app. iOS can't use Hilt (Android-only), so a KMP-capable DI
(Koin) is on the critical path. Doing the swap **now, while everything is still Android-only**,
makes it a contained, zero-behaviour-change refactor that's fully verifiable on Android — and it
avoids writing throwaway Hilt `@Provides` for the shared data layer (Phase 3) and rewiring later.
So Phase 2 = the DI swap, before the data layer.

## 初衷對照 / North-Star check

Pure infrastructure refactor — no product/behaviour change. Mission-neutral.

## Scope

Replace Hilt with **Koin 4.1** across the Android app:
- Delete the 5 `@Module`s (`Application/DataStore/Database/Reminder/Repository`); replace with one
  Koin `appModule` (`di/AppModule.kt`) — DataStore, Room DB + DAOs, repositories (bound to the
  shared interfaces), `ReminderNotifier`, `AlarmReminderScheduler` (→ `ReminderScheduler`),
  `RescheduleRemindersUseCase` (→ `RemindersRescheduler`), all use cases, all ViewModels.
- Strip `@Inject` / `@ApplicationContext` (constructors unchanged); remove `@HiltViewModel`,
  `@AndroidEntryPoint`, `@HiltAndroidApp`.
- `SportApplication.onCreate` → `startKoin { androidContext(...); modules(appModule) }`.
- Receivers (`ReminderReceiver`, `BootReceiver`) → `KoinComponent` + `by inject()`.
- Compose: `hiltViewModel()` → `koinViewModel()`.
- Gradle: drop the Hilt plugin + deps (keep KSP for Room); add `koin-android`,
  `koin-androidx-compose`.

Two ViewModels with a `now: () -> Long` secondary constructor (`DietViewModel`,
`InsightsViewModel`) are wired with explicit `viewModel { X(get()) }`; the rest with `viewModel {}`
constructor calls. `EatTimeEditorViewModel` gets its `SavedStateHandle` from Koin's viewModel scope.

## Out of scope (later phases)

Data-layer / use cases / ViewModels moving to `commonMain` (Phases 3+). This phase keeps the Koin
module in `:app`; it migrates to a shared Koin module when the data layer + use cases move.

## Testing / verification

- Unit tests construct VMs/use cases directly (constructors unchanged) → unaffected.
- Local gate green: `assembleDebug`, `testDebugUnitTest`, `:app:detekt`, `:app:lintDebug`,
  `:shared:jvmTest`.
- **On-device (emulator):** every screen exercised — Home (`DietViewModel`), Record, Insights,
  Settings, and the add-eat editor (`EatTimeEditorViewModel` + `SavedStateHandle`) — all resolve
  through Koin with no crashes. (Unit tests don't exercise the Koin graph, so this runtime check
  is required.)

## Risk

Koin graph errors only surface at runtime → covered by the device smoke test above. Koin 4.1 on
AGP 9.2 / Kotlin 2.3 (JVM/Android) compiles and runs.
