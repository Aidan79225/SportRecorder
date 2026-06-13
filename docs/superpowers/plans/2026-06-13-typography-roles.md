# App-wide Typography → MD3 Roles Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make every text in the app render from a `MaterialTheme.typography.<role>` (no hardcoded `fontSize`/`fontWeight`), with color decoupled from the type scale — all sizes preserved so the UI looks identical.

**Architecture:** Clean up `Type.kt` (strip the baked-in `color` from the 4 legacy roles, fix the `headlineLarge` lineHeight bug, add `bodySmall`/`labelSmall`, drop the now-unused `colorScheme` param) and re-add explicit colors at the call sites that relied on the baked color (Select/Create) — one atomic change. Then convert the remaining hardcoded `fontSize` (Editor sheet, Record card, Notifications) to roles.

**Tech Stack:** Kotlin, Jetpack Compose (Material3), detekt (no baseline), Android Lint (baseline).

**Verification model:** Each phase builds green via `.\gradlew.bat assembleDebug :app:check`, and is verified by **emulator screenshots** showing the touched screens look identical (typography is visual; no unit tests). Reverse-check: grep `ui/` for `fontSize`/`fontWeight` → only `Type.kt` remains.

**Environment:** Build commands run from `C:\Users\Aidan\SportRecorder` in **PowerShell** — set `$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'` before each `.\gradlew.bat` call (generous timeout, ~600000 ms). Emulator: `Pixel_10_Pro`.

**Branch:** `refactor/typography-roles` (spec already committed here).

**detekt notes:** `Type.kt` keeps its `@Suppress("LongMethod")`. `ui/` is excluded from `MagicNumber` (sizes as literals are fine). No wildcard imports / no semicolons. Remove imports that become unused (`NoUnusedImports` fails the build).

---

## Phase 1 — Type scale cleanup + legacy call sites (one atomic change)

These four files change together: stripping the baked color from the 4 legacy roles and dropping the `createTypography` param would otherwise break the build (`Theme.kt`) and the appearance (Select/Create titles lose their green). One task, one commit, verified by screenshots.

### Task 1.1: Decouple color in `Type.kt` + re-color the call sites

**Files:**
- Modify (full rewrite): `app/src/main/java/com/crazystudio/sportrecorder/ui/theme/Type.kt`
- Modify: `app/src/main/java/com/crazystudio/sportrecorder/ui/theme/Theme.kt`
- Modify: `app/src/main/java/com/crazystudio/sportrecorder/ui/diet/select/SelectFastingTypeScreen.kt`
- Modify: `app/src/main/java/com/crazystudio/sportrecorder/ui/diet/create/fasting/CreateFastingTypeScreen.kt`

- [ ] **Step 1: Rewrite `Type.kt`** — no role carries color; `headlineLarge` lineHeight fixed (24→44); add `bodySmall`/`labelSmall`; drop the `colorScheme` param and its `ColorScheme` import:

```kotlin
package com.crazystudio.sportrecorder.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

@Suppress("LongMethod") // declarative MD3 type-scale config: one TextStyle per role
fun createTypography() = Typography(
    // No role carries a color — color is set at the call site via colorScheme roles.
    headlineLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 36.sp,
        lineHeight = 44.sp,
        letterSpacing = 0.5.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp,
    ),
    displayMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 52.sp,
        lineHeight = 60.sp,
        letterSpacing = 0.sp,
    ),
    displaySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 44.sp,
        lineHeight = 52.sp,
        letterSpacing = 0.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 26.sp,
        lineHeight = 32.sp,
        letterSpacing = 0.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        letterSpacing = 0.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.25.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp,
    ),
)
```

- [ ] **Step 2: Update the `Theme.kt` call.** In `app/src/main/java/com/crazystudio/sportrecorder/ui/theme/Theme.kt`, change the typography line from:

```kotlin
        typography = createTypography(DarkColors),
```
to:
```kotlin
        typography = createTypography(),
```

- [ ] **Step 3: Re-color `SelectFastingTypeScreen`.** The title used `headlineLarge`'s baked primary. Add an explicit color so it stays green. Change (around line 78):

```kotlin
                Text(
                    text = stringResource(id = R.string.diet_fasting_type_title),
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
```
(The grid items at the `titleMedium` call sites already set `color = MaterialTheme.colorScheme.onBackground` — leave them.)

- [ ] **Step 4: Re-color `CreateFastingTypeScreen`** at the four call sites that relied on the baked primary. Add `color = MaterialTheme.colorScheme.primary` to each:
  - The `CANCEL` button text (`titleLarge`, ~line 92):
    ```kotlin
                    Text(
                        text = "CANCEL",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
    ```
  - The `TimeSelectRow` title (`bodyLarge`, ~line 124):
    ```kotlin
        Text(
            text = stringResource(id = type.titleResId),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .weight(1f)
                .padding(start = 10.dp)
        )
    ```
  - The selected-value text (`bodyLarge`, ~line 131):
    ```kotlin
        Text(
            text = selectedValueState.value,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .padding(end = 10.dp)
                .clickable {
                    showSelectedDialog = true
                }
        )
    ```
  - The `SelectListDialog` list item (`bodyLarge`, ~line 173):
    ```kotlin
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp)
                            .clickable {
                                onSelectedListener(it)
                                onDismissRequest()
                            },
                        textAlign = TextAlign.Center,
    ```
  (The `OK` button already sets `color = MaterialTheme.colorScheme.onPrimary` — leave it.)

- [ ] **Step 5: Build + detekt + lint.**

Run: `$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'; .\gradlew.bat assembleDebug :app:detekt :app:lintDebug`
Expected: `BUILD SUCCESSFUL`. (If detekt flags an unused import in `Type.kt`, it's `ColorScheme` — confirm it's removed.)

- [ ] **Step 6: Commit.** (Trailer `Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>` on every commit.)

```bash
git add app/src/main/java/com/crazystudio/sportrecorder/ui/theme/Type.kt app/src/main/java/com/crazystudio/sportrecorder/ui/theme/Theme.kt app/src/main/java/com/crazystudio/sportrecorder/ui/diet/select/SelectFastingTypeScreen.kt app/src/main/java/com/crazystudio/sportrecorder/ui/diet/create/fasting/CreateFastingTypeScreen.kt
git commit -m "[Refactor] Decouple color from type scale; add bodySmall/labelSmall"
```

- [ ] **Step 7: Phase-1 visual check (controller runs this).** Install + screenshot the **Select** sheet (Home → tap the fasting chip) and the **Create** sheet (Select → `+`). Confirm: the "Fasting type" title is still green; the grid items unchanged; in Create, the `CANCEL` text + time-select labels/values are still green, `OK` unchanged. Any text that turned grey = a missed re-color → fix before proceeding.

---

## Phase 2 — Convert remaining hardcoded `fontSize` to roles

### Task 2.1: `EatTimeEditorSheet` (4× 18sp → `bodyLarge`)

**Files:**
- Modify: `app/src/main/java/com/crazystudio/sportrecorder/ui/diet/editor/EatTimeEditorSheet.kt`

- [ ] **Step 1: Convert the Location row texts** (~lines 114–122) — drop `fontSize = 18.sp`, add `style`:

```kotlin
            Text(
                text = "Location",
                style = MaterialTheme.typography.bodyLarge,
                color = colorScheme.onSurface,
            )
            Text(
                text = locationText,
                style = MaterialTheme.typography.bodyLarge,
                color = colorScheme.onSurface,
                textAlign = TextAlign.End,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 10.dp),
            )
```

- [ ] **Step 2: Convert the `HeaderRow` texts** (~lines 260–273):

```kotlin
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = content,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.End,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 10.dp),
        )
```

- [ ] **Step 3: Remove the now-unused `sp` import** if no `.sp` remains in the file: delete `import androidx.compose.ui.unit.sp`. (Run detekt in Step 5; if `.sp` is still used elsewhere, keep it.)

- [ ] **Step 4: Commit.**

```bash
git add app/src/main/java/com/crazystudio/sportrecorder/ui/diet/editor/EatTimeEditorSheet.kt
git commit -m "[Refactor] EatTimeEditorSheet: 18sp texts -> bodyLarge role"
```

---

### Task 2.2: `RecordScreen` (14sp → `bodySmall`, 12sp → `labelSmall`)

**Files:**
- Modify: `app/src/main/java/com/crazystudio/sportrecorder/ui/diet/record/RecordScreen.kt`

- [ ] **Step 1: Convert the date/time/note texts** (14sp → `bodySmall`, color unchanged):

The header date + time (~lines 160–169):
```kotlin
                Text(
                    text = dateFormat.format(Date(record.time)),
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurface,
                )
                Text(
                    text = timeFormat.format(Date(record.time)),
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurface,
                )
```
The note (~lines 183–187):
```kotlin
            Text(
                text = record.note,
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.onSurface,
            )
```

- [ ] **Step 2: Convert the location text** (12sp → `labelSmall`, ~lines 236–240):

```kotlin
            Text(
                text = "📍 ${String.format(Locale.ROOT, "%.4f, %.4f", loc.lat, loc.lng)}",
                style = MaterialTheme.typography.labelSmall,
                color = colorScheme.onSurfaceVariant,
            )
```

- [ ] **Step 3: Remove the now-unused `sp` import** if no `.sp` remains (delete `import androidx.compose.ui.unit.sp`; detekt confirms in the gate). `MaterialTheme` is already imported (the file uses `colorScheme`); confirm `MaterialTheme.typography` resolves.

- [ ] **Step 4: Commit.**

```bash
git add app/src/main/java/com/crazystudio/sportrecorder/ui/diet/record/RecordScreen.kt
git commit -m "[Refactor] RecordScreen: card text -> bodySmall/labelSmall roles"
```

---

### Task 2.3: `NotificationsScreen` + final gate

**Files:**
- Modify: `app/src/main/java/com/crazystudio/sportrecorder/ui/notifications/NotificationsScreen.kt`

- [ ] **Step 1: Give the stub text a role** (consistency):

```kotlin
package com.crazystudio.sportrecorder.ui.notifications

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
fun NotificationsScreen(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = "Coming soon",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
```

- [ ] **Step 2: Reverse-check no hardcoded sizing remains.** Use the Grep tool: pattern `fontSize|fontWeight`, path `app/src/main/java/com/crazystudio/sportrecorder/ui`. Expected: matches ONLY in `ui/theme/Type.kt` (the type-scale definition itself). Any other match = a missed conversion.

- [ ] **Step 3: Full CI-mirror gate.**

Run: `$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'; .\gradlew.bat assembleDebug testDebugUnitTest :app:detekt :app:lintDebug`
Expected: `BUILD SUCCESSFUL` — detekt, lint, and all existing unit tests green.

- [ ] **Step 4: Commit.**

```bash
git add app/src/main/java/com/crazystudio/sportrecorder/ui/notifications/NotificationsScreen.kt
git commit -m "[Refactor] NotificationsScreen: stub text -> bodyLarge role"
```

- [ ] **Step 5: Phase-2 visual check (controller runs this).** Install + screenshot **Record** (a card with date/time/note/location), the **Editor sheet** (FAB → date/time/note/Location rows/SAVE), and **Notifications**. Confirm each looks identical to before. `git status` clean.

---

## Self-Review (author check vs. spec)

- **Spec §1 type scale cleanup** → Task 1.1 Step 1 (`Type.kt`: strip color from the 4 legacy roles, `headlineLarge` lineHeight 24→44, add `bodySmall` 14 / `labelSmall` 12, drop param + `ColorScheme` import) + Step 2 (`Theme.kt` call). ✔
- **Spec §2 re-color legacy call sites** → Task 1.1 Steps 3–4 (`SelectFastingTypeScreen` title; `CreateFastingTypeScreen` CANCEL + TimeSelectRow title + selected value + dialog item — the audited set of call sites that had no explicit color). ✔
- **Spec §3 role-ify hardcoded** → Task 2.1 (Editor sheet 4× `bodyLarge`), Task 2.2 (Record `bodySmall`/`labelSmall`), Task 2.3 (Notifications `bodyLarge`). ✔
- **Spec §4 phasing** → Phase 1 atomic (Type+Theme+Select+Create, one commit, screenshots); Phase 2 conversions + final gate + reverse-grep. ✔
- **Spec §5 testing** → no unit tests (visual); emulator screenshots per phase (Task 1.1 Step 7, Task 2.3 Step 5); reverse-grep (Task 2.3 Step 2). ✔
- **Placeholder scan:** every code step shows the full replacement; exact run commands + expected results. No TBDs. ✔
- **Type consistency:** new role names `bodySmall`/`labelSmall` defined in Task 1.1 Step 1 and used in Task 2.2 (`bodySmall`/`labelSmall`). `createTypography()` (no param) defined in 1.1 Step 1 and called in 1.1 Step 2. Color decouple (1.1) lands with the call-site re-colors (1.1) in the same commit — no transient regression. ✔
- **detekt:** `Type.kt` keeps `@Suppress("LongMethod")`; `ColorScheme` import removed; unused `sp` imports removed in Phase 2 (per `NoUnusedImports`); `ui/` MagicNumber excluded. ✔
