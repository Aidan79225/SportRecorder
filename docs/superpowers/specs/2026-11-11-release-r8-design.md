# 發行版開啟 R8 · Turning R8 on for release builds

- **Date:** 2026-11-11
- **Status:** Implemented on `claude/enable-r8`; ships with the next release (the owner runs `/release`).
- **Trigger:** Play Console → Android vitals → 「DEX 程式碼最佳化程度低於門檻」, 2026-11-11: 模糊化 (obfuscation) 1%, below the 25% bar.

## Goal

Pass Google Play's "DEX code optimization" check, with **no change a user can see**.

Play wants **optimization, obfuscation and shrinking each ≥ 25%** for an app whose bundle holds more than
10 MB of uncompressed DEX. 0.15.0 had 33.6 MB of DEX and `isMinifyEnabled = false`, so R8 never ran.
Google's documentation says store visibility "may" be affected from **February 2027** (the Console's own
deadline was not in the alert text we were given).

## What changed

- `app/build.gradle.kts`: the `release` build type has `isMinifyEnabled = true`, with AGP's stock
  `proguard-android-optimize.txt`. Debug stays unminified.
- `app/proguard-rules.pro`: **no app-specific rules** — only a comment saying why, and when to add one.
- `.github/workflows/ci.yml`: a new step, `:app:minifyReleaseWithR8`, so a dependency bump that R8 rejects
  fails the PR instead of the release tag.
- `CLAUDE.md`, `docs/DEVELOPMENT.md` and the release skill say that release now runs R8 and when the
  minified app needs a smoke test. The release skill's signing text is also corrected: it still said the
  keystore is committed, but only `debug.keystore` is; the upload key comes from `local.properties` or
  repo secrets (`app/build.gradle.kts`, `release.yml`).

Deliberately **not** changed:

- **`isShrinkResources` stays off.** AGP's optimized resource shrinker needs `android.nonFinalResIds=true`;
  this repo still sets `false` (deprecated, removed in AGP 10). Resource shrinking is not one of the three
  Play checks. Revisit when that flag is flipped.
- **No `-keepattributes SourceFile,LineNumberTable`, no `-renamesourcefileattribute`.** R8's mapping file
  (format 2.2) already carries source-file ids and line ranges; retracing an obfuscated frame with the
  SDK's `retrace` resolves to `File.kt:line`. `-renamesourcefileattribute` would also overwrite R8's
  `r8-map-id` marker.
- **No mapping upload.** The AAB embeds it (`BUNDLE-METADATA/com.android.tools.build.obfuscation/proguard.map`),
  so Play deobfuscates crashes and ANRs by itself.

## Result

| | 0.15.0 (R8 off) | R8 on |
|---|---|---|
| DEX, uncompressed | 33.6 MB | 4.5 MB |
| Universal release APK | 18.2 MB | 8.7 MB |
| Not obfuscated / not optimized / not shrunk (`r8.json` in the AAB) | — (no `r8.json`) | 2.7% / 3.44% / 2.83% |

That is about **97%** obfuscated, optimized and shrunk, against a 25% bar. DEX is also below the 10 MB
line now, so the check should stop applying at all. The R8 build takes about 40 s on the existing 2 GB
Gradle heap.

## What R8 could have broken, and what keeps it

| Reached by name or reflection | Kept by |
|---|---|
| Room's generated `AppDatabase_Impl` (`Room.databaseBuilder(AppDatabase::class.java)` instantiates it reflectively) | room-runtime's consumer rule |
| `AutoBackupWorker` — WorkManager stores the class **name** in its own database | WorkManager's consumer rule. The name stays unrenamed, so work scheduled by 0.15.0 still resolves after the update |
| Activities, receivers, services, `FileProvider`, the quick-settings tile | AAPT rules generated from the manifest |
| kotlinx-serialization: `BackupDocument` and the typed navigation routes | compiler-generated serializers + the library's consumer rules. Serial names are string constants, so the backup file format does not depend on class names |
| `BottomSheetNavigator` — Navigation reads its `@Navigator.Name` annotation reflectively | the stock rules keep runtime-visible annotations (`r8.json`: `isRuntimeVisibleAnnotationsKept`). Without that the app would crash at start-up; it starts, and the meal editor (a sheet) opens, on the minified build |
| Enums persisted by name (`ReminderType.valueOf`) | the stock Android rules keep `values()` / `valueOf()`, and a constant's name is a string handed to the `Enum` constructor, so renaming the field changes nothing |
| Koin (no reflection; definitions are keyed by `KClass`) | nothing to keep. Only a type-merge of two separately registered types could bite; every screen resolved its dependencies on the minified build |
| Coil 3 (ServiceLoader), OkHttp, DataStore, Compose Multiplatform resources, Play services | their own consumer rules / R8's ServiceLoader handling |

## Verification

**Static.** R8's `-printconfiguration`, `-printseeds`, `-printusage` and the mapping, reviewed per
reflection surface by four independent auditors, then challenged by skeptics and a critic: no confirmed
finding. A separate review of the finished diff added the `BottomSheetNavigator` row above (kept; it is
exercised at start-up).

**Dynamic** — API 35 emulator, release APK installed **in place over an unminified 0.15.0** that already
held data, so the existing database, DataStore, reminders and WorkManager state were exercised too:

- Home, Record and Insights show the same data as before the upgrade; reminders are re-armed.
- New meal with note, new place and a 1 h 30 duration; later edit of the duration to 45 min.
- Record-tab search by address text.
- Insights map, and a calendar day opened through a typed navigation argument.
- Maps share (`SEND` intent) → editor prefilled → meal created.
- Gallery photo pick → meal with photo on the Record tab.
- Fasting type picker: pick a preset, create custom types.
- Both reminders fire (device clock moved to just before them).
- App language switched to zh-TW; the quick-record tile adds a meal.
- Rotation with the editor open; process killed while editing, then restored.
- Backup screen opens, and "Sign in with Google" brings up Play services' sign-in UI.
- `monkey` with 1500 random events (seed 7): no crash, process alive. `logcat -b crash` stayed empty.

**Not verified here** — please check on a phone once the internal-track build is installed:

1. **A real Drive backup → restore round trip.** It needs a Google sign-in, so the `BackupDocument`
   serializers were checked statically only: the serializer, its `Companion.serializer()` and `descriptor`
   survive R8, every field name is still a string in the DEX, and the code always calls
   `BackupDocument.serializer()` explicitly (no reflective lookup). The typed navigation routes use the
   same machinery and ran.
2. Typing Chinese with a real IME.
3. Play Console: the alert should clear once Play has processed the next release (it can take days).

## 初衷對照 / North-Star check

- **Does it serve the mission?** Not directly — nothing a user sees changes. It protects the app's
  discoverability on Play, and the APK shrinks to less than half.
- **Red flags:** none apply. No scoring, no new capture step, no pressure, no new copy.
- **Where the real risk is:** the opposite of a nudge. A minified build that crashes while recording, or
  silently drops a backup, makes 「記錄變成負擔」 in the worst way. That is why this change carries an
  in-place-upgrade smoke test, a CI build step, and the checklist below.
- **Data safety:** nothing new is collected or sent; the mapping file goes only to Play, inside the AAB.

## Smoke-test checklist for future R8-sensitive changes

Run when dependencies, `app/proguard-rules.pro`, or code reached by name/reflection changes (the release
skill points here). Use the dedicated test emulator — `E2E_Api35`.

1. Build `:app:assembleRelease` — it must be signed (upload key in `local.properties`; an unsigned APK
   can't be installed) — and keep the previous release's APK, signed with the same key.
2. Install the previous release, create data (a meal with a note, place and duration; reminders on), then
   `adb install -r` the new APK. Home/Record/Insights unchanged; reminders re-armed.
3. Walk the flows listed under **Dynamic** above. Handy shell helpers: `cmd alarm set-time` (reminders),
   `cmd locale set-app-locales com.crazystudio.sportrecorder --locales zh-TW`, `cmd statusbar add-tile` /
   `click-tile` (quick tile), `am start -a android.intent.action.SEND -t text/plain --es
   android.intent.extra.TEXT '<maps link>'` (Maps share), `am kill` (process death).
4. `adb logcat -b crash` is empty; `monkey -p com.crazystudio.sportrecorder --throttle 250 1500` leaves the
   process alive.
5. Retrace a frame if one ever shows up: SDK `cmdline-tools/latest/bin/retrace.bat
   app/build/outputs/mapping/release/mapping.txt trace.txt`.

If something breaks, add the **narrowest** `-keep` that fixes it to `app/proguard-rules.pro` with a comment
naming the class and why. A package-wide `-keep` drags down the percentages Play checks.
