# 回顧 / Insights improvements (B4 · B6 · B7) — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Finish the Insights page's turn from report card to mirror. Drop the last metric that carries someone else's norm (**B6**), let a day on the calendar lead back to the meals that made it (**B4**), and draw the period's eating windows as bands so the pattern is *seen* rather than read off four numbers (**B7**).

**Architecture:** Unchanged from the rest of the app. All computation goes in the pure `InsightsAggregator` (`commonMain`, Android-free, unit-tested via `:shared:jvmTest`). All UI is Compose Multiplatform in `shared/.../ui/insights`, taking callbacks; **navigation lives entirely in `:app`** (`ui/nav/Route.kt` + `ui/AppRoot.kt`, androidx.navigation + `material-navigation`'s `bottomSheet`), so the shared screens never know where a tap goes.

**Tech Stack:** Kotlin `2.3.10`, Compose Multiplatform `1.11.1`, kotlinx-datetime `0.7.1`, Koin, Coil 3, kotlin.test (commonTest) + JUnit4/Turbine (`app/src/test`).

**Spec:** `docs/superpowers/specs/2026-09-21-insights-improvements-design.md` (bucket B; B1 already implemented on this branch).

## Global Constraints

- `commonMain` stays **Android-free**: no `java.*`, `System.currentTimeMillis`, `Dispatchers.IO`, `String.format`. Use `kotlin.time.Clock`, kotlinx-datetime, manual padding/formatting (see `InsightsScreen.pad2` / `coord`).
- **Every user-facing string goes through Compose Resources in BOTH `values/` and `values-zh-rTW/`.** A string added to one locale only is a bug, not a TODO.
- **Tone is a hard constraint, not a polish pass.** Nothing added here may read as a verdict: no colour that means "bad", no metric rendered against a target, no copy that instructs. When in doubt prefer a description (「這是你的模式」) over an assessment (「你沒達標」). Re-read the spec's 初衷對照 before writing copy.
- Pure logic belongs in `domain/insights` and must be unit-tested in `shared/src/commonTest`. UI files hold no arithmetic beyond formatting.
- **Touching a shared screen's signature breaks iOS.** `shared/src/iosMain/.../ui/shared/MainViewController.kt` calls `InsightsScreen(...)` directly; every new required parameter must be added there in the same commit or the `ios-shared` CI job fails.
- Another agent owns the end-to-end test suite. **Do not add anything under `app/src/androidTest`** and do not restructure shared test infrastructure; stay inside `ui/insights`, `domain/insights`, their tests, their strings, and these docs.
- Every commit ends with:
  ```
  Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
  Claude-Session: https://claude.ai/code/session_01DDiCFYk6H6pQ4qmNQxweqA
  ```

## Phases & verifiability

- **Phase 1 (Task 1): B6 — drop the 22:00 metric.** Pure deletion. Fully verified by `:shared:jvmTest` + `testDebugUnitTest`. No schema, no migration, no device.
- **Phase 2 (Tasks 2–5): B4 — a calendar day leads to its meals.** Aggregator + shared sheet are unit-testable; the navigation wiring in `:app` and the sheet's feel need a **device/emulator** check.
- **Phase 3 (Tasks 6–9): B7 — rhythm band chart.** The band data is fully unit-testable; the Canvas rendering needs a **device/emulator** check in both locales, and its text equivalent needs a TalkBack pass.

Run the full gate after each phase:

```bash
./gradlew :shared:jvmTest testDebugUnitTest :app:detekt :app:lintDebug assembleDebug
```

Phases 2 and 3 both touch `commonMain`, so also confirm the `ios-shared` CI job (`:shared:iosSimulatorArm64Test`) is green before merging.

> **Container note (2026-09-21):** the environment this plan was written in has no Android SDK and its egress proxy blocks `dl.google.com`, so **no Gradle task can run there** — only the pure domain layer can be checked, by driving the Kotlin compiler against Maven Central directly. Whoever executes this plan must run the real gate, or gate every task on CI.

## File Structure

```
shared/src/commonMain/kotlin/com/crazystudio/sportrecorder/
  domain/insights/InsightsModels.kt       # -lateHourDays, +DayBand, +InsightsResult.bands
  domain/insights/InsightsAggregator.kt   # -LATE_HOUR, +bandsFor()
  ui/insights/InsightsScreen.kt           # -late row, +onDayClick, +RhythmChartCard
  ui/insights/RhythmChart.kt              # NEW — hand-rolled Canvas band chart
  ui/insights/DayRecordsSheet.kt          # NEW — one day's meals, read-only
  ui/insights/DayRecordsViewModel.kt      # NEW — records for one dayStart
  composeResources/values/strings.xml
  composeResources/values-zh-rTW/strings.xml
shared/src/commonTest/kotlin/.../domain/insights/InsightsAggregatorTest.kt
shared/src/iosMain/.../ui/shared/MainViewController.kt   # keep the preview call compiling
app/src/main/java/com/crazystudio/sportrecorder/
  ui/nav/Route.kt                         # +DayRecords(dayStart)
  ui/AppRoot.kt                           # +bottomSheet<Route.DayRecords>, +onDayClick wiring
  di/AppModule.kt                         # +viewModel { DayRecordsViewModel(...) }
app/src/test/java/.../ui/insights/DayRecordsViewModelTest.kt   # NEW
```

---

## Phase 1 — B6: drop the 22:00 metric

### Recommendation (decided, not open)

**Drop the metric. Do not make the hour a setting.**

- Making it configurable does not remove the norm, it asks the user to *install* one. The page would then carry a second target beside `eatingHours`, and every number on it would be measured against a goal — precisely the 評價 drift the improvements spec exists to undo.
- The information is not lost. **平均最後一餐 / Avg last meal** already says when your days tend to end, as a plain description with no threshold at all. A "days after 22:00" count is the same fact, thresholded and made lossy.
- B7 shows it far better: a band stretching to the right edge *is* a late night, visible without anyone naming a cutoff.
- Cost asymmetry seals it. Dropping: one field, one UI row, two strings × two locales, one test. Making it a setting: `DietSettings` + a new `Constants` key + `DietSettingsRepositoryImpl` read/write + a Settings UI row + `BackupDietSettings` + **`BackupDocument.SCHEMA_VERSION` 1 → 2** — and that bump is permanent surface: `BackupService.restore` rejects any snapshot whose `schemaVersion` is *newer* than the running app (`BackupSchemaTooNewException`), so a v2 snapshot written by a newer install can never be restored by an older one. That is a real, forever cost to carry for a metric we do not believe in.

### Task 1: Remove `lateHourDays`

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/crazystudio/sportrecorder/domain/insights/InsightsModels.kt`
- Modify: `shared/src/commonMain/kotlin/com/crazystudio/sportrecorder/domain/insights/InsightsAggregator.kt`
- Modify: `shared/src/commonMain/kotlin/com/crazystudio/sportrecorder/ui/insights/InsightsScreen.kt`
- Modify: `shared/src/commonMain/composeResources/values/strings.xml`, `values-zh-rTW/strings.xml`
- Modify: `shared/src/commonTest/kotlin/.../domain/insights/InsightsAggregatorTest.kt`

**Interfaces:** Removes `InsightsStats.lateHourDays`, `InsightsAggregator.LATE_HOUR`, `insights_stat_late`.

- [ ] **Step 1: Delete the test that only exists for it.** Remove `statsFor_lateHourCountsDistinctDays` and the `lateHourDays` assertions in `statsFor_empty`. Keep every other stats test.
- [ ] **Step 2: Drop the field.** In `InsightsModels.kt` remove `lateHourDays` from `InsightsStats` and from `InsightsResult.EMPTY`.
- [ ] **Step 3: Drop the computation.** In `InsightsAggregator.kt` remove `LATE_HOUR`, the `lateDays` local, and the `lateHourDays =` argument. Check whether `MINUTES_PER_HOUR` is still used (it is — `minutesSinceMidnight`), and remove any import left unused.
- [ ] **Step 4: Drop the UI row.** In `InsightsScreen.kt` remove the last `StatRow`, the `insights_stat_late` import, and the now-unused `InsightsAggregator` import.
- [ ] **Step 5: Drop the strings.** Remove `insights_stat_late` from **both** locale files.
- [ ] **Step 6: Verify.**
  ```bash
  ./gradlew :shared:jvmTest testDebugUnitTest :app:detekt :app:lintDebug assembleDebug
  ```
  Expected: BUILD SUCCESSFUL. Then `grep -rn "lateHour\|LATE_HOUR\|insights_stat_late" shared/src app/src` must return nothing.
- [ ] **Step 7: Commit**
  ```bash
  git add -A
  git commit   # feat(insights): drop the 22:00 metric (bucket B6)
  ```
  Body: say that Avg last meal already carries this as a description, and that a configurable hour was rejected because it installs a norm and costs a permanent `SCHEMA_VERSION` bump.

---

## Phase 2 — B4: a calendar day leads to its meals

### Decide before coding

**1. Where the day's meals appear.** Three options, given that navigation is `:app`-only and the shared screens take callbacks:

| Option | `:app` cost | `commonMain` cost | Verdict |
|---|---|---|---|
| **A · Modal bottom sheet** — `bottomSheet<Route.DayRecords>` on the existing `ModalBottomSheetLayout` | ~25 lines: one `Route.DayRecords(dayStart)` + one `bottomSheet {}` block + `PhotoViewerHost` wiring | new `DayRecordsSheet` (~120 lines) + small VM | **Recommended.** Reuses the pattern `EatTimeEditor` / `SelectFastingType` already use, keeps the calendar behind the sheet (this is "look closer", not "go somewhere"), and back = dismiss for free. |
| **B · Full `composable<Route.DayRecords>` destination** | ~20 lines, **plus** a `hasRoute(Route.DayRecords::class)` branch in the bottom-nav `hierarchy` check or no tab appears selected while it is open | same screen, needs its own header + back affordance | Fallback if the sheet proves cramped for a day with many photos. |
| **C · Reuse the Record tab, filtered to a day** | looks free, is not: `Route.Record` is a **tab**, and giving a tab an argument breaks the bottom-nav `hasRoute` matching and the "switch tabs" semantics — the user lands on a filtered Record tab with no obvious way back to the full list | none | **Reject.** Also `RecordScreen` carries long-press **delete** and an edit pencil, so a "just looking" gesture would land on destructive controls. |

**2. Read-only or editable?** Recommend **read-only**: reuse the visual language of `RecordCard` (time, note, photo carousel) but with no delete and no edit pencil. Insights is for looking; if an edit is wanted later, route to the existing `Route.EatTimeEditor(eatTimeId)` rather than duplicating destructive actions on a reflection surface.

**3. What does tapping an empty day do?** Recommend **nothing** — no ripple, no sheet. Opening an empty sheet is a dead end, and offering "log a meal for this day" from the calendar turns a reflection surface into a back-fill prompt, which changes what the diary means. Needs the owner's sign-off because the opposite (back-filling from the calendar) is a defensible Re-engage feature.

**4. Will B7's chart rows open the same sheet?** Recommend yes — build the entry point as `onDayClick: (dayStart: Long) -> Unit` so Phase 3 reuses it rather than inventing a second path.

### Task 2: `DayRecordsViewModel`

**Files:**
- Create: `shared/src/commonMain/kotlin/com/crazystudio/sportrecorder/ui/insights/DayRecordsViewModel.kt`
- Test: `app/src/test/java/com/crazystudio/sportrecorder/ui/insights/DayRecordsViewModelTest.kt`

**Interfaces:** Produces `DayRecordsViewModel(observeEatRecords, photoImageSource, dayStart: Long, timeZone)` exposing `StateFlow<List<EatRecord>>` for that day, oldest-first, and `photoModel(fileName)`.

- [ ] **Step 1: Write the failing test.** Records across three days → only the requested day's come back, ascending by time; a day with no records yields an empty list. Use the existing `FakeEatRecordRepository` / `FakePhotoImageSource` / `MainDispatcherRule`, and a fixed `TimeZone` — no default-zone dependence.
- [ ] **Step 2: Implement.** Filter with `InsightsAggregator.dayStart(record.time, timeZone) == dayStart`; `.stateIn(viewModelScope, WhileSubscribed(5000), emptyList())`. No new use case — `ObserveEatRecordsUseCase` already gives the whole stream.
- [ ] **Step 3: Verify** `./gradlew testDebugUnitTest`. Commit.

### Task 3: `DayRecordsSheet` (shared, read-only)

**Files:**
- Create: `shared/src/commonMain/kotlin/com/crazystudio/sportrecorder/ui/insights/DayRecordsSheet.kt`
- Modify: both `strings.xml`

- [ ] **Step 1: Strings, both locales.** A title showing the date (reuse the `insights_date_short` pattern, or add `insights_day_title` = `%1$d/%2$d`), a meal-count line, and an empty line that will not normally be seen. Gentle, descriptive; no verdict about the day.
- [ ] **Step 2: Build the sheet.** A `Column` with the date header, then one row per meal: `HH:mm` (reuse the `pad2` style), the note when present, and a photo strip using the existing `PhotoThumbnail` + an `onPhotoClick(fileNames, index)` callback so the host can raise `FullScreenPhotoViewer`. No delete, no edit.
- [ ] **Step 3: Cap the height.** A day can hold many photos: `Modifier.heightIn(max = ...)` + `verticalScroll`, matching how `RecordScreen` bounds its carousel at `360.dp`.
- [ ] **Step 4: Verify** `:shared:jvmTest` + `assembleDebug` compile. Commit.

### Task 4: Make calendar days tappable

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/crazystudio/sportrecorder/ui/insights/InsightsScreen.kt`
- Modify: `shared/src/iosMain/.../ui/shared/MainViewController.kt`

- [ ] **Step 1: Thread the callback.** `InsightsScreen(..., onDayClick: (Long) -> Unit)` → `RhythmCard` → `CalendarGrid` → `DayBox`. Only attach `clickable` when `cell.state != DayWindowState.NO_RECORD`, so empty days stay inert (decision 3 above).
- [ ] **Step 2: Keep the semantics honest.** `DayBox` uses `clearAndSetSemantics`, which drops the click affordance from accessibility. Add the click through `Modifier.clickable(onClickLabel = <localized "see this day">)` **before** `clearAndSetSemantics`, and append the same label to the cell's `contentDescription`, or the cell will be unreachable by TalkBack. Add that string to both locales.
- [ ] **Step 3: Fix the iOS preview.** `MainViewController.kt` must pass `onDayClick = {}` or the `ios-shared` job fails.
- [ ] **Step 4: Verify** `assembleDebug` + `:shared:jvmTest`. Commit.

### Task 5: Wire the sheet into `:app`

**Files:**
- Modify: `app/src/main/java/com/crazystudio/sportrecorder/ui/nav/Route.kt`
- Modify: `app/src/main/java/com/crazystudio/sportrecorder/ui/AppRoot.kt`
- Modify: `app/src/main/java/com/crazystudio/sportrecorder/di/AppModule.kt`

- [ ] **Step 1: Add the route.** `@Serializable data class DayRecords(val dayStart: Long) : Route`.
- [ ] **Step 2: Add the destination.** A `bottomSheet<Route.DayRecords>` block next to the existing ones, reading `dayStart` from the entry's typed route, resolving `DayRecordsViewModel` via `koinViewModel { parametersOf(dayStart) }`, and wrapping the content in `PhotoViewerHost` so a tapped photo still opens full screen **above** the sheet — verify that ordering on device, it is the one thing this layout can get wrong.
- [ ] **Step 3: Register the VM** in `AppModule.kt` with a parameter for `dayStart`.
- [ ] **Step 4: Navigate.** In the `composable<Route.Insights>` block pass `onDayClick = { dayStart -> navController.navigate(Route.DayRecords(dayStart)) }`.
- [ ] **Step 5: Manual verify on emulator.** Seed a few days; tap a day with records → sheet shows exactly that day's meals in order; tap a photo → full-screen viewer opens over the sheet and dismisses back to it; tap an empty day → nothing happens; back dismisses the sheet and leaves the calendar on the same month. Check both locales.
- [ ] **Step 6: Verify** the full gate + `ios-shared`. Commit.

---

## Phase 3 — B7: rhythm band chart

### Decide before coding

**1. Chart form.** One **row per day, time on the x-axis from 00:00 to 24:00**, each day drawn as a single rounded band from its first meal to its last. It reads as "here are your days stacked up", needs no legend beyond axis labels, and matches the calendar's vocabulary (a window has a start and an end).

**2. Which period.** At phone width, Week (7 rows) is comfortable; Month (28–31 rows) gets tall and thin. Decide: (a) render whatever the period holds with a minimum row height and let the card grow, or (b) show the chart only for Week and hide it for Month. **Recommend (a)** with a floor of ~6dp per row — a month of bands is exactly the "here is my shape" picture this feature is for, and the page already scrolls. Owner should confirm they are happy with a tall card in Month.

**3. Colour must not encode judgement.** Every band is the **same** colour (`primaryContainer` on a `surfaceVariant` track, the calendar's palette so the two cards agree). Do **not** colour bands by whether they fit `eatingHours` — that turns the chart into a grade sheet and undoes the whole of bucket A. This is the North-Star-critical constraint of this phase.

**4. Accessibility.** A `Canvas` is invisible to screen readers, and a chart that is shape-and-colour only needs a text equivalent. Plan: `clearAndSetSemantics` on the chart with a generated one-sentence summary (day count + typical start/end), **and** keep the numeric stats card directly beneath it as the durable text equivalent. Both in both locales.

### Task 6: `DayBand` data in the aggregator

**Files:**
- Modify: `shared/.../domain/insights/InsightsModels.kt` (add `DayBand`, add `InsightsResult.bands`)
- Modify: `shared/.../domain/insights/InsightsAggregator.kt` (add `bandsFor`)
- Test: `shared/src/commonTest/.../InsightsAggregatorTest.kt`

**Interfaces:** Produces `data class DayBand(val dayStart: Long, val dayOfMonth: Int, val firstMinutes: Int, val lastMinutes: Int, val mealCount: Int)` and `InsightsAggregator.bandsFor(records, periodStart, now, timeZone): List<DayBand>`.

- [ ] **Step 1: Write the failing tests.** One row per calendar day **in the period, including days with no records** (absent from the list vs. present with `mealCount = 0` — pick one and test it; recommend *present with `mealCount = 0`* so the chart never silently shifts rows). Ascending by `dayStart`. A single-meal day gives `firstMinutes == lastMinutes`. A day whose meals span 09:00→17:30 gives `540`/`1050`. Fixed `TimeZone`.
- [ ] **Step 2: Implement `bandsFor`.** Reuse the existing day bucketing; walk the period day by day with `LocalDate.plus(1, DateTimeUnit.DAY)` rather than assuming 86 400 000 ms steps (DST).
- [ ] **Step 3: Watch the function count.** `InsightsAggregator` is an `object` sitting at 9 functions; the original Insights plan kept it under detekt's `TooManyFunctions` threshold of 11. Note that detekt is currently applied **only to `:app`**, so `:shared` is not actually linted today — hold the line anyway (it is the convention the file was written to, and `:shared` may be added to detekt later). If `bandsFor` would take it past 11, fold a private helper inline rather than splitting the file.
- [ ] **Step 4: Wire into `compute`** as `bands = bandsFor(...)` over the same `inPeriod` window the stats use, and extend the `compute_*` tests.
- [ ] **Step 5: Verify** `:shared:jvmTest`. Commit.

### Task 7: `RhythmChart` composable

**Files:**
- Create: `shared/src/commonMain/kotlin/com/crazystudio/sportrecorder/ui/insights/RhythmChart.kt`
- Modify: both `strings.xml`

- [ ] **Step 1: Strings, both locales.** Axis labels (`00:00`, `06:00`, `12:00`, `18:00`, `24:00` — build from numbers, no words), the chart card title (e.g. 「一天的形狀」 / "The shape of your days"), the accessibility summary (`%1$d` days, `%2$s`–`%3$s`), and the empty-period line.
- [ ] **Step 2: Draw it.** `Canvas` in `commonMain` (precedent: `ui/component/CircleProgress.kt`). Per row: a full-width track, then a band from `firstMinutes/1440` to `lastMinutes/1440` of the width. Use `LocalDensity` for dp→px as `CircleProgress` does.
- [ ] **Step 3: Handle the sparse cases explicitly.**
  - `mealCount == 0` → draw the track only, no band. The row must still occupy its slot.
  - `firstMinutes == lastMinutes` (single meal) → a zero-width band would vanish; draw a minimum-width mark (~3dp) so one meal is still visible.
  - `bands.isEmpty()` → do not draw the chart at all; render the same neutral "no records in this period" line the other cards use.
- [ ] **Step 4: Gridlines.** Faint `outlineVariant` verticals at 00/06/12/18/24 with `labelSmall` `onSurfaceVariant` labels; at phone width keep to those five.
- [ ] **Step 5: Accessibility.** `clearAndSetSemantics { contentDescription = summary }` where `summary` is the localized sentence from Step 1, built from `bands.size` and the average first/last already in `InsightsStats`.
- [ ] **Step 6: Verify** `assembleDebug`. Commit.

### Task 8: Put the chart on the page

**Files:**
- Modify: `shared/.../ui/insights/InsightsScreen.kt`
- Modify: `shared/src/iosMain/.../ui/shared/MainViewController.kt` (only if the screen's signature changes again)

- [ ] **Step 1: Place it** in a `SectionCard` **between** the period selector and the stats card, so the picture comes first and the numbers read as its caption (and serve as its text equivalent).
- [ ] **Step 2: Tap-through.** If Phase 2 landed, a row with `mealCount > 0` calls the same `onDayClick(dayStart)`; empty rows stay inert. Give the row the same localized `onClickLabel` as the calendar cell.
- [ ] **Step 3: Verify** the full gate. Commit.

### Task 9: Manual + accessibility pass

- [ ] **Step 1: Emulator, both locales.** Week and Month; a day with one meal; a day with none; an account with a single day of data; an empty account (the chart must not appear at all, the `NothingYetCard` still owns that state).
- [ ] **Step 2: TalkBack.** The chart announces its summary sentence; calendar cells and chart rows announce their date, state and click label; nothing is reachable only by colour.
- [ ] **Step 3: Contrast.** The app is dark-only (`SportRecorderTheme` always applies `DarkColors`): check band-on-track and gridline-on-surface contrast there, and confirm no `colorScheme.error` appears anywhere on the page.
- [ ] **Step 4: Update the spec.** Flip B4/B6/B7 from *planned* to *done* in `2026-09-21-insights-improvements-design.md`, and record the two shape decisions (Month renders all rows; bands are single-coloured by design). Commit.

---

## Out of scope (still open from the spec's bucket B)

- **B2** — per-day historical fasting targets (the calendar still re-scores the past when the goal changes).
- **B3** — place names instead of coordinates (`expect/actual` on-device geocoder).
- **B5** — full `LazyColumn` rewrite with a lazy photo wall. Worth revisiting **after** B4: once the day sheet exists, the wall's 12-photo cap matters less, but the page will have grown by a chart.
- **B8** — one period control driving the calendar as well.
