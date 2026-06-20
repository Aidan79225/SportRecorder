# KMP migration — Phase 1: shared domain module (thin slice)

**Date:** 2026-06-20
**Status:** Approved design, implementing
**Parent:** `2026-06-20-kmp-ios-migration-design.md`

## Goal

Stand up a `:shared` Kotlin Multiplatform module that the Android `:app` depends on, moving only
**dependency-free** domain types into `commonMain`. The point is to validate the **`androidTarget`
/ AGP-9.2 integration in isolation** — no coroutines, no datetime — so if that mechanism is fiddly
at our bleeding-edge versions, the blast radius is just a few leaf types.

## 初衷對照 / North-Star check

Pure refactor — no behaviour change, no product surface touched. Mission-neutral.

## Scope

### `:shared` module
- Targets: `androidTarget`, `jvm`, `iosX64`, `iosArm64`, `iosSimulatorArm64`.
- `commonMain` dependencies: **none** (kotlin stdlib only, incl. `kotlin.time`).
- `commonTest`: `kotlin("test")`.
- Android namespace: `com.crazystudio.sportrecorder.shared`; `compileSdk = 36`, `minSdk = 24`.
- **Moved files keep their original `com.crazystudio.sportrecorder.domain.*` packages** so `:app`
  imports don't change — only the module boundary moves.

### Moves to `commonMain` (zero-dep, no `@Inject`, no `java.util`)
- `domain/model/*` — DietSettings, FastingWindow, CustomFastingType, EatRecord (+ GeoPoint, EatPhoto)
- `domain/diet/DietWindow.kt` — `java.util.concurrent.TimeUnit` → `kotlin.time` (`Duration.hours`)
- `domain/diet/DefaultFastingWindows.kt`
- `domain/reminder/` — ReminderType, ReminderPrefs, ScheduledReminder, ReminderScheduler,
  RemindersRescheduler
- `domain/insights/InsightsModels.kt`

### Stays in `:app` (later phases)
- Repository **interfaces** — import coroutines `Flow`; deferred to keep this slice deps-free.
- `ReminderPlanner`, `InsightsAggregator`, `ObserveDietStateUseCase` — use `java.util.Calendar`
  → **Phase 1b** (kotlinx-datetime).
- All **use cases** — `@Inject`/Hilt → **Phase 3** (DI swap).

### Tests
- `DietWindowTest` → `:shared/commonTest` as `kotlin.test` (runs on JVM + iOS in CI). Removed from
  `:app`.

## Build wiring
- `gradle/libs.versions.toml` — add `kotlin-multiplatform` plugin (Kotlin version) and the AGP KMP
  android-library plugin.
- Root `build.gradle.kts` — declare `kotlin-multiplatform` (and the KMP android-library plugin)
  with `apply false` (avoids the classpath-version conflict found in the spike).
- `settings.gradle.kts` — `include(":shared")`; `shared/.gitignore` with `/build`.
- `:app/build.gradle.kts` — `implementation(project(":shared"))`.
- `androidTarget` mechanism: try the AGP KMP android-library plugin first; fall back to
  `com.android.library` + `androidTarget()` if needed (this is the unknown being probed).

## CI
- Add `:shared:jvmTest` to the existing CI gate.
- Add a `macos-latest` job running `:shared:iosSimulatorArm64Test`.

## Success criteria
- Local: `:app:assembleDebug testDebugUnitTest :app:detekt :app:lintDebug` + `:shared:jvmTest` green.
- CI: macOS job green on `:shared:iosSimulatorArm64Test`.
- App behaviour unchanged; no `:app` import churn beyond the new module dependency.

## Risks
- **Primary:** `:app` (com.android.application, AGP 9.2) consuming a KMP `:shared` with an
  `androidTarget` — the exact mechanism/DSL at AGP 9.2 is unverified. Contained to leaf types.
- Coroutines/datetime ecosystem risk is **deferred** (not in this slice).
