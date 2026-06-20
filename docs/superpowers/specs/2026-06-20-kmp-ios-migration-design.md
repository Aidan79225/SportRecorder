# KMP + iOS migration — assessment & roadmap

**Date:** 2026-06-20
**Status:** Assessment + de-risking spike done; phased roadmap pending a chosen iOS date

## Context

We will ship SportRecorder to the Apple App Store; the **timing is not yet fixed**. This doc
captures (1) a layer-by-layer portability assessment of the current Android app, (2) the result
of a toolchain de-risking spike, and (3) a recommended phased approach so that when we commit to
an iOS date the runway is short.

It is an **assessment/roadmap**, not a single implementation spec. Each phase below becomes its
own brainstorm → spec → plan when we start it.

## 初衷對照 / North-Star check

A platform migration changes *where* the app runs, not *what* it is — the mission (只記錄、不評價;
留住美好的當下;自我覺察) is unaffected. One mission-relevant nuance: iOS local notifications work
differently from Android alarms (see below), so the *reminder UX* must be re-validated on iOS to
stay gentle and reliable — but the intent is unchanged. No drift.

## Strategy

**Android-first feature velocity, KMP-ready discipline.** Not a big-bang rewrite, and not "finish
Android first then port". The thing that keeps a later iOS port cheap is keeping business logic in
the pure domain and data behind interfaces — which clean architecture + the North-Star check
already enforce. So:

- **Now / ongoing:** keep shipping Android features; keep all new business logic in the pure
  Android-free domain.
- **When we pick an iOS date:** execute the phases below.

The crown jewel is already in place: the fasting/window/insights/reminder **logic is pure Kotlin,
Android-free, and unit-tested** — the hardest part of any port is done.

## Spike result — toolchain is iOS-ready ✅

A throwaway spike (branch `claude/kmp-spike`, since deleted) created a minimal `:shared` KMP module,
ported the pure `DietWindow` calculator into `commonMain`, and verified it on a **macOS GitHub
Actions runner** (iOS/Kotlin-Native can't compile on the Windows dev box).

**Outcome:** on this repo's **AGP 9.2 / Kotlin 2.3.10** stack, the iOS chain runs end to end —
`downloadKotlinNativeDistribution` (kotlin-native 2.3.10) → `compileKotlinIosSimulatorArm64` →
`linkDebugTestIosSimulatorArm64` → `iosSimulatorArm64Test` all green, with the ported tests
**executing on the iOS simulator**. The core toolchain is de-risked, and a Mac is not required —
a `macos-latest` CI runner does it.

### Reproducing the spike (it was a throwaway; recreate when starting the real work)

1. Version catalog — add the KMP plugin (same Kotlin version):
   ```toml
   [plugins]
   kotlin-multiplatform = { id = "org.jetbrains.kotlin.multiplatform", version.ref = "kotlin" }
   ```
2. **Root `build.gradle.kts`** — declare it once with `apply false` (else a subproject `alias(...)`
   fails with *"plugin is already on the classpath with an unknown version"*, because AGP pulls the
   Kotlin plugin onto the classpath):
   ```kotlin
   alias(libs.plugins.kotlin.multiplatform) apply false
   ```
3. `settings.gradle.kts` — `include(":shared")`; add `shared/.gitignore` with `/build`.
4. `shared/build.gradle.kts` (spike kept it deps-free to isolate the toolchain question — no
   `androidTarget` yet):
   ```kotlin
   plugins { alias(libs.plugins.kotlin.multiplatform) }
   kotlin {
       jvm()
       iosX64(); iosArm64(); iosSimulatorArm64()
       sourceSets { commonTest.dependencies { implementation(kotlin("test")) } }
   }
   ```
5. macOS CI job: `./gradlew :shared:jvmTest :shared:iosSimulatorArm64Test`.

### Spike findings

- **Domain is Android-free but not JVM-free.** `DietWindow` used `java.util.concurrent.TimeUnit`
  (replaced with millis arithmetic in the spike). `ReminderPlanner` and `InsightsAggregator` use
  `java.util.Calendar`. For real `commonMain` these move to `kotlin.time` / **`kotlinx-datetime`**.
  Small and mechanical.
- The plugin-classpath conflict (step 2) is the only structural gotcha found.

## Layer-by-layer portability

| Layer | LOC | Portability | Work |
|---|---|---|---|
| **domain** (models / DietWindow / InsightsAggregator / ReminderPlanner / use cases / repo interfaces) | ~755 | ✅ near-direct | Move to `commonMain`; swap `TimeUnit`/`Calendar` for `kotlin.time`/`kotlinx-datetime`. |
| **data** (Room + DataStore + repo impls) | ~560 | 🟡 adapt | Room 2.7+ is KMP (iOS bundled SQLite); `EatTimeDao` must drop `LiveData` → `Flow`; DataStore multiplatform; DB/file paths via `expect/actual`; repos drop `Context`/Hilt. |
| **DI** (Hilt) | ~159 | ❌ replace | Hilt is Android-only → Koin (KMP) or manual DI; touches every `@Inject`/`@HiltViewModel` (~31 files, mechanical). ViewModels → KMP `androidx.lifecycle`. |
| **reminders** (AlarmManager/Receiver/Channel) | ~392 | 🟡 logic reused | `ReminderPlanner` is shared; scheduler/notifier are `expect/actual`. See iOS notes. |
| **util** (image pipeline / photo / location) | ~253 | ❌ iOS rewrite | See platform list. |
| **ui** | ~3,389 | path-dependent | See UI strategy. |

## iOS platform work (new `iosMain` implementations)

- **Local notifications** — Android AlarmManager + BroadcastReceiver + BootReceiver → iOS
  `UNUserNotificationCenter` with `UNCalendarNotificationTrigger`. **Design difference:** iOS can't
  run code when a notification fires to schedule the next one, and caps pending notifications at 64
  → reschedule strategy becomes "re-arm on app foreground + schedule the next N". The pure
  `ReminderPlanner` is reused unchanged.
- **Image pipeline** (downscale / EXIF rotate / webp) — `android.graphics` → iOS `ImageIO`/Core
  Graphics; **webp encoding on iOS is awkward** → likely store HEIC/JPEG on iOS (decision needed).
- **Photo source** (camera + gallery) — Android intent + MediaStore + FileProvider → `PHPicker` /
  `UIImagePickerController` + app sandbox.
- **Location** — FusedLocation → `CoreLocation` (small).
- **Permissions** — Info.plist usage strings + system prompts.
- **Date/time pickers** — currently `android.app.DatePickerDialog/TimePickerDialog` → Compose M3
  pickers or native.

## UI strategy (the big fork)

- **A. Compose Multiplatform (share UI to iOS)** — reuses most Compose, but with an adaptation tax:
  resources → `composeResources`, navigation / `material-navigation` (bottom sheet) → KMP variants,
  `coil` → `coil3`, `rememberLauncherForActivityResult` (camera/photo/permission) → platform glue.
  **Risk:** CMP maturity at AGP 9.2 / Kotlin 2.3 is unverified (the spike did *not* probe CMP).
- **B. Native SwiftUI (rewrite UI, share backend)** — full UI rewrite but each side idiomatic and
  no CMP-on-iOS risk; needs Swift/iOS skill.

**Recommendation:** decide A vs B at the start of the UI phase, after a CMP-on-iOS spike. Default to
**B (native SwiftUI)** unless that spike shows CMP is solid at our toolchain — B is the lower-risk
path to a quality iOS app and keeps the shared module a pure backend.

## Phased roadmap (each phase = its own spec + plan)

0. **(done)** Toolchain de-risk spike — ✅ iOS builds at AGP 9.2 / K 2.3.
1. **Shared domain module** — extract `domain` into `:shared` `commonMain`; `TimeUnit`/`Calendar`
   → `kotlin.time`/`kotlinx-datetime`; `:app` consumes `:shared` (adds `androidTarget` → probes the
   AGP-9.2 KMP-android integration). Tests run on JVM + iOS in CI.
2. **Shared data layer** — Room-KMP + DataStore-multiplatform behind the existing repo interfaces;
   `expect/actual` for DB/file paths.
3. **DI swap** — Hilt → Koin (or manual), KMP ViewModels.
4. **iOS platform features** — notifications, location, photo/camera, image pipeline, storage.
5. **iOS UI** — A or B per the CMP spike.
6. **iOS release pipeline** — Xcode project, signing, App Store Connect, privacy labels, review.

## Effort (one experienced KMP dev, rough)

~**2–4 months** total. Phases 1–3 (shared backend) are the lower-risk, high-leverage core; phases
4–6 (iOS platform + UI + store) carry the calendar time and the remaining risk.

## Risks

1. **Bleeding-edge versions** — core KMP/iOS is proven (spike), but the *library ecosystem*
   (Room-KMP, CMP, kotlinx-datetime, Koin) at AGP 9.2 / Kotlin 2.3 is not yet verified; probe each
   as its phase begins.
2. **iOS release pipeline is all-new** — signing, App Store Connect, privacy nutrition labels,
   review latency.
3. **Reminders + image format** need explicit iOS design decisions (above).

## Next action

When an iOS date is chosen, brainstorm **Phase 1 (shared domain module)** into its own spec.
