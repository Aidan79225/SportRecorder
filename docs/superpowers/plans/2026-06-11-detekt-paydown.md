# detekt Baseline Paydown Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Drive the 200-finding `app/detekt-baseline.xml` to ~0 via auto-correct (formatting), config suppression of two false-positive rules, and manual behavior-preserving fixes of the genuine remainder — then delete the baseline.

**Architecture:** Three layers. (1) `detekt --auto-correct` fixes ~110 ktlint/formatting findings. (2) `detekt.yml` config makes `FunctionNaming` ignore `@Composable` and relaxes `MagicNumber` (+ exclude `**/ui/**`). (3) Manually fix the ~30 genuine smells by rule cluster. Regenerate the baseline after each layer; when empty, delete the baseline file and drop the `baseline =` line.

**Tech Stack:** detekt 1.23.8 + detekt-formatting (ktlint), Kotlin, Gradle (KTS).

**Verification model:** `:app:detekt` and `assembleDebug` stay green after every layer; the baseline finding count strictly decreases; final state has an empty/deleted baseline with `clean assembleDebug assembleRelease testDebugUnitTest :app:check` all green and the app running unchanged.

**Environment (project memory):** `java` not on PATH — before every Gradle command: `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"` then `.\gradlew.bat <tasks>` from `C:\Users\Aidan\SportRecorder`. Emulator `Pixel_10_Pro` (API 37). **Commit hygiene:** stage only named paths (never `git add -A`); commit trailer `Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>`.

**Branch:** `feat/detekt-paydown` (spec already committed here).

**Baseline finding format:** `app/detekt-baseline.xml` lists each finding as `<ID>RuleName:File.kt$signature:line</ID>` inside `<CurrentIssues>`. To see the current findings at any point, read that file (after regenerating it) or run `.\gradlew.bat :app:detektBaseline` then read it.

---

## Phase 1 — Auto-correct + config suppression

### Task 1: Config the two false-positive rules, auto-correct formatting, regenerate baseline

**Files:** Modify `app/detekt.yml`, `app/detekt-baseline.xml` (regenerated), plus many source files (formatting only, by auto-correct)

- [ ] **Step 1: `FunctionNaming` — ignore `@Composable`.** In `app/detekt.yml`, the `FunctionNaming:` block (around line 333) is:
```yaml
  FunctionNaming:
    active: true
    excludes: ['**/test/**', '**/androidTest/**', '**/commonTest/**', '**/jvmTest/**', '**/androidUnitTest/**', '**/androidInstrumentedTest/**', '**/jsTest/**', '**/iosTest/**']
    functionPattern: '[a-z][a-zA-Z0-9]*'
    excludeClassPattern: '$^'
```
Add an `ignoreAnnotated` line so it becomes:
```yaml
  FunctionNaming:
    active: true
    excludes: ['**/test/**', '**/androidTest/**', '**/commonTest/**', '**/jvmTest/**', '**/androidUnitTest/**', '**/androidInstrumentedTest/**', '**/jsTest/**', '**/iosTest/**']
    functionPattern: '[a-z][a-zA-Z0-9]*'
    excludeClassPattern: '$^'
    ignoreAnnotated: ['Composable']
```

- [ ] **Step 2: `MagicNumber` — relax + exclude Compose UI.** In `app/detekt.yml`, the `MagicNumber:` block (around line 622): add `'**/ui/**'` to its `excludes` array, and flip three flags to `true`. Target state:
```yaml
  MagicNumber:
    active: true
    excludes: ['**/test/**', '**/androidTest/**', '**/commonTest/**', '**/jvmTest/**', '**/androidUnitTest/**', '**/androidInstrumentedTest/**', '**/jsTest/**', '**/iosTest/**', '**/*.kts', '**/ui/**']
    ignoreNumbers:
      - '-1'
      - '0'
      - '1'
      - '2'
    ignoreHashCodeFunction: true
    ignorePropertyDeclaration: true
    ignoreLocalVariableDeclaration: true
    ignoreConstantDeclaration: true
    ignoreCompanionObjectPropertyDeclaration: true
    ignoreAnnotation: true
    ignoreNamedArgument: true
```
(Changed: added `'**/ui/**'`; `ignorePropertyDeclaration` false→true; `ignoreLocalVariableDeclaration` false→true; `ignoreAnnotation` false→true.)

- [ ] **Step 3: Auto-correct the formatting findings.**
```
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat detekt --auto-correct
```
This rewrites source files to fix the ktlint/formatting rules (FinalNewline, NewLineAtEndOfFile, ArgumentListWrapping, MultiLineIfElse, NoUnusedImports, Wrapping, spacing, import ordering, indentation, etc.). It may report remaining non-correctable findings — that's fine; they're handled in Phase 2. (Exit code may be non-zero if findings remain; that does not undo the corrections.)

- [ ] **Step 4: Verify it still compiles** — `.\gradlew.bat assembleDebug` → BUILD SUCCESSFUL. Then skim `git diff --stat` and a couple of files to confirm the changes are formatting-only (no logic changes). Auto-correct never changes semantics, but verify the build is green.

- [ ] **Step 5: Regenerate the baseline** — delete and rebuild it so it reflects only what's left:
```
Remove-Item app\detekt-baseline.xml
.\gradlew.bat :app:detektBaseline
```
Then check the new count and breakdown:
```
(Select-String -Path app\detekt-baseline.xml -Pattern '<ID>').Count
```
Expected: a large drop from 200 to roughly ~30 (the genuine smells). View the rule breakdown by reading the IDs.

- [ ] **Step 6: Confirm green** — `.\gradlew.bat :app:detekt` → BUILD SUCCESSFUL (remaining findings are in the regenerated baseline).

- [ ] **Step 7: Commit**
```
git add app/detekt.yml app/detekt-baseline.xml
git add <each source file auto-correct changed — list them from `git status`, do NOT use `git add -A`>
git commit -m "[Quality] detekt: auto-correct formatting + ignore Composable/relax MagicNumber"
```

---

## Phase 2 — Fix the genuine smells to empty the baseline

### Task 2: Work the remaining findings by rule cluster, regenerate to ~0

**Files:** Various source files under `app/src/main/java/com/crazystudio/sportrecorder/` (per finding); `app/detekt-baseline.xml`; possibly `app/build.gradle.kts`

The remaining findings are listed in `app/detekt-baseline.xml` (from Task 1 Step 5) as `<ID>Rule:File.kt$...:line</ID>`. Read that file to get the exact file+line of each. Fix them using the per-rule recipe below — **all fixes must be behavior-preserving**. After each cluster, regenerate the baseline (`Remove-Item app\detekt-baseline.xml; .\gradlew.bat :app:detektBaseline`) and confirm the cluster's IDs are gone and the build stays green.

- [ ] **Step 1: `NoUnusedImports` / `WildcardImport` / `NoWildcardImports` / `ImportOrdering`** (if any survived auto-correct) — remove the unused import line; replace a wildcard import (`import x.*`) with explicit imports of the symbols actually used in that file (find them by searching the file). Re-run `assembleDebug` to confirm nothing broke.

- [ ] **Step 2: `UnusedPrivateMember` / `UnusedParameter`** — for each: confirm the member/param is truly unused (grep the symbol across the module). If unused, **delete** the private member; for an unused function parameter that's part of a required signature (e.g. an interface/override/Compose slot), prefix it with `_` or annotate the function `@Suppress("UnusedParameter") // required by <interface/contract>`. Otherwise delete it and its call-site arguments.

- [ ] **Step 3: `ImplicitDefaultLocale`** — a `String.format("...", ...)` without a `Locale`. Add `java.util.Locale.getDefault()` as the first arg, preserving the format string:
```kotlin
// before
String.format("%.5f, %.5f", lat, lng)
// after
String.format(java.util.Locale.getDefault(), "%.5f, %.5f", lat, lng)
```
(Use `Locale.getDefault()` to keep the exact current display behavior.)

- [ ] **Step 4: `SwallowedException` / `TooGenericExceptionCaught`** — do NOT change control flow (the existing "fail safe → return null/default" behavior must stay). For `SwallowedException`, include the caught exception in the handler (e.g. log it, or rethrow-wrapped only if that's already the intent). Minimal behavior-preserving fix: reference the exception so it isn't swallowed silently, e.g.:
```kotlin
} catch (e: Exception) {
    Log.w("LocationProvider", "location unavailable", e)
    null
}
```
For `TooGenericExceptionCaught` (catching `Exception`/`Throwable`), narrow to the specific type(s) actually thrown if known; if the broad catch is deliberate (safe-degrade boundary), keep it and add `@Suppress("TooGenericExceptionCaught") // intentional safe-degrade boundary`.

- [ ] **Step 5: `MagicNumber` (logic layer only)** — any `MagicNumber` left is outside `**/ui/**` (e.g. time/percent math in a ViewModel or util). Extract each to a named `private const val` with a descriptive name at the top of the file/class, e.g.:
```kotlin
private const val MILLIS_PER_HOUR = 3600_000L
```
and replace the literal with the constant. Keep the value identical.

- [ ] **Step 6: `MaxLineLength` / `MaximumLineLength`** — manually wrap the over-long line (break at a sensible argument/operator boundary) without changing semantics. Re-run `assembleDebug`.

- [ ] **Step 7: `MatchingDeclarationName` / `Filename`** — rename the file so it matches its single top-level declaration (e.g. `CreateFoodRecordViewMode.kt` → the actual class name file). **Before renaming**, grep the repo for any reference to the old filename; Kotlin references the class (not the filename), so usually only the file path changes — use `git mv`. Re-run `assembleDebug`.

- [ ] **Step 8: `UseCheckOrError`** — replace `throw IllegalStateException("msg")` with `error("msg")`, or `if (!cond) throw IllegalStateException(...)` with `check(cond) { ... }`, preserving the message/condition.

- [ ] **Step 9: `LongMethod` / `LongParameterList` / `TooManyFunctions`** — if a function is cheaply splittable into private helpers without changing behavior, do so. If it isn't worth refactoring (e.g. a Composable with many slot params, or a VM `combine` builder), add a justified suppress on that declaration:
```kotlin
@Suppress("LongMethod") // cohesive single-responsibility builder; splitting hurts readability
```
(One short reason per suppress.)

- [ ] **Step 10: Any other residual rule** — read its detekt docs message in the report and apply the standard fix, behavior-preserving; if genuinely a false positive for this codebase, `@Suppress` it inline with a reason (do NOT re-add it to the baseline).

- [ ] **Step 11: Drive the baseline to empty** — after the clusters, regenerate: `Remove-Item app\detekt-baseline.xml; .\gradlew.bat :app:detektBaseline`. If `app/detekt-baseline.xml` now has zero `<ID>` entries (empty `<CurrentIssues/>`), **delete the baseline and its config reference**:
```
git rm app/detekt-baseline.xml
```
and in `app/build.gradle.kts` remove the line `baseline = file("$projectDir/detekt-baseline.xml")` from the `detekt { }` block (leave `buildUponDefaultConfig` and `config.setFrom(...)`).

- [ ] **Step 12: Verify detekt is clean with no baseline** — `.\gradlew.bat :app:detekt` → BUILD SUCCESSFUL with no findings (and no baseline file). If a few findings genuinely remain that you intentionally keep, prefer inline `@Suppress` (with reason) over re-adding a baseline; only keep a baseline if there's a real residual you can't suppress cleanly — note it.

- [ ] **Step 13: Commit** (stage `app/build.gradle.kts`, the deleted baseline, and every source file you changed — list them explicitly):
```
git commit -m "[Quality] detekt: fix remaining smells; remove now-empty baseline"
```

---

## Phase 3 — Final verification

### Task 3: Whole-build green + behavior unchanged

**Files:** none.

- [ ] **Step 1:** `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat clean assembleDebug assembleRelease testDebugUnitTest :app:check` → all `BUILD SUCCESSFUL` (build + tests + detekt + Lint green).
- [ ] **Step 2: Install smoke** — `.\gradlew.bat installDebug`; launch; walk the main flows (Home timer, Record cards, create/edit eat time, fasting-type select). No behavior change vs before. `adb logcat -d -t 200` no FATAL EXCEPTION.
- [ ] **Step 3: Canary** — append `fun detektCanary(): Int { val x=12345 ; return x }` to `app/src/main/java/com/crazystudio/sportrecorder/util/StringUtils.kt`; `.\gradlew.bat :app:detekt` → BUILD FAILED (proves new issues are caught with no baseline); revert the file; re-run → BUILD SUCCESSFUL. Do not commit the canary.
- [ ] **Step 4:** No commit (verification only).

---

## Self-Review (author check vs. spec)

- **Spec coverage:** auto-fix formatting → Task 1 Step 3; FunctionNaming/MagicNumber config → Task 1 Steps 1–2; regenerate-and-shrink baseline → Task 1 Step 5 / Task 2 Step 11; manual clusters (unused, locale, exceptions, line length, naming, constants, long-method @Suppress, UseCheckOrError) → Task 2 Steps 1–10; empty baseline → delete file + drop build line → Task 2 Step 11; keep `:app:check` green + app behavior + canary → Phase 3. All spec sections map to tasks.
- **Placeholder scan:** the config edits show exact before/after YAML; the auto-correct + regenerate commands are exact; each manual rule has a concrete code recipe. The per-finding file:line is intentionally read from the regenerated baseline at run time (it can't be known until auto-correct runs) — this is a data lookup, not a vague placeholder; every rule has a concrete fix pattern.
- **Consistency:** the regenerate-baseline incantation (`Remove-Item … ; :app:detektBaseline`) and the finding-count check are identical across tasks; `detekt.yml` edits in Task 1 are the only config changes and Phase 2 references them (`**/ui/**` exclusion ⇒ only logic MagicNumbers remain for Step 5).
- **Behavior-preservation guardrails:** exception steps explicitly forbid control-flow changes; constant extraction keeps identical values; renames use `git mv` after a grep. All consistent with the spec's "no behavior change" constraint.
