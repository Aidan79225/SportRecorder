# KMP migration — Phase 3a: DataStore repos → commonMain

**Date:** 2026-06-20
**Status:** Implemented
**Parent:** `2026-06-20-kmp-ios-migration-design.md`

## Goal

Move the two DataStore-backed repositories into `commonMain` using the multiplatform
`datastore-preferences-core`, probing **DataStore on iОС** in isolation (the smaller half of the
data layer; Room is Phase 3b).

## 初衷對照 / North-Star check

Pure infrastructure refactor — no behaviour change. Mission-neutral.

## Scope

- Add `androidx.datastore:datastore-preferences-core` (multiplatform) to `:shared` (`api` — the
  repos expose `DataStore<Preferences>` in their constructors).
- Move into `commonMain` (same packages, so `:app`/Koin references are unchanged):
  - `data/repository/DietSettingsRepositoryImpl`
  - `data/repository/ReminderPreferencesRepositoryImpl`
  - `util/Constants` (the preference key names; used only by these two repos)
- Fix the one common-incompatibility: `java.io.IOException` → `okio.IOException` (DataStore's MP
  artifact brings okio).

## Deliberately NOT moved (no `expect/actual` needed yet)

The `DataStore<Preferences>` **creation** (file path + the Android-only `SharedPreferencesMigration`)
stays in `:app`'s Koin module. The repos take the DataStore as a constructor param, so the logic is
platform-agnostic; iOS will provide its own DataStore when the iOS app graph is built (later phase).
Compiling the moved repos for the iОС target is itself the DataStore-on-iОС probe.

## Stays in `:app` (Phase 3b)

`EatRecordRepositoryImpl`, `FastingTypeRepositoryImpl` (Room) + entities/DAOs/DB/migrations.

## Testing

- Local gate green: `:shared:jvmTest`, `assembleDebug`, `testDebugUnitTest`, `:app:detekt`,
  `:app:lintDebug`.
- `DietSettingsRepositoryTest` stays in `:app` (JVM) and exercises the moved impl.
- iОС verified by the macOS CI job (`:shared:iosSimulatorArm64Test`) compiling the module — which
  now includes the DataStore repos — for Kotlin/Native.
