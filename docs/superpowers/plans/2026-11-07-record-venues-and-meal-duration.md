# Record-Page Venues, Venue Search and Meal Duration — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** The Record tab shows each record's venue and can be searched by venue (Part A); a meal has a duration — 1 hour by default, adjustable — and the fast clock starts when the last meal ends (Part B).

**Architecture:** Part A is pure filtering (`RecordVenueFilter`, `commonMain`, unit-tested) plus a small search state in `DietRecordViewModel` and a few lines in `RecordScreen`. Part B adds `durationMinutes` to `EatRecord` (Room 9 → 10, backup field), a duration row in the editor, and makes `DietWindow` take each meal as a start and an end.

**Tech Stack:** Kotlin 2.3.10, Compose Multiplatform 1.11.1 (Material 3 only), Room 2.8.4 (KMP), Koin 4.1.0, kotlinx-datetime, kotlin.test (`commonTest`), JUnit4/Turbine (`app/src/test`), AndroidX Test (`app/src/androidTest`, local only).

**Spec:** `docs/superpowers/specs/2026-11-07-record-venues-and-meal-duration-design.md` — read it first. Binding where this plan is silent.

**Delivery:** Part A = Tasks 1–4 on branch `claude/record-venue-search`. Part B = Tasks 5–10 on branch `claude/meal-duration`, created from Part A's tip (both touch `RecordScreen`).

## Global Constraints

- `commonMain` stays Android-free (iOS compiles it): no `java.*`, no `String.format`; time via kotlinx-datetime.
- Every user-facing string in BOTH `shared/src/commonMain/composeResources/values/strings.xml` and `values-zh-rTW/strings.xml`, positional args (`%1$d`).
- **Material 3 only**; there is no material-icons dependency — new icons are vector drawables in `shared/src/commonMain/composeResources/drawable/` (reuse existing ones where possible, e.g. `ic_baseline_close_24`, `ic_baseline_access_time_24`).
- **A quick capture costs nothing extra:** no new required step, no new prompt. The default duration is 60 minutes and lives in the domain type, so every recording path gets it untouched.
- **No verdicts:** no counts, rankings or "most visited" for venues; no statistic, colour or wording that judges a duration. **Insights and `DayRecordsSheet` are not changed** — they read start times only.
- Search matches venue **name and address only**, case-insensitive, **every word** must match; a record without a venue never matches a non-blank query; order is never changed.
- `DietWindow.groupIntoWindows` is **unchanged** (eating days are grouped by START time).
- `SCHEMA_VERSION` of the backup stays as it is: the new field is additive.
- detekt: lexicographic imports with no blank lines, braces on multi-line if/else, ≤ 2 `return`s per function (`:shared` is not scanned by detekt — follow the rules anyway).
- Every commit ends with: `Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>`
- Gate: `$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'; .\gradlew.bat assembleDebug testDebugUnitTest :app:detekt :app:lintDebug :shared:jvmTest :app:compileDebugAndroidTestKotlin`. Instrumented tests only on the **API 35** AVD (`E2E_Api35`, `emulator-5554`); quote `"-Pandroid.testInstrumentationRunnerArguments.class=<fqcn>"` in PowerShell.

## Review Focus

1. **A record with no venue** — the card shows no empty lines, and a non-blank search hides it without error. → Task 3.
2. **Two branches with the same name** — tapping one card's venue shows only that branch's records. → Tasks 1 and 3.
3. **Upgrading a phone that already has meals** — every meal survives 9 → 10 and reads as 60 minutes; a backup made before this change restores as 60. → Tasks 5 and 7.
4. **A meal that crosses midnight** — the card reads 「23:30 – 00:30」 and the fast clock is right. → Tasks 6 and 9.
5. **Overlapping meals** — the fast starts at the *latest end*, not at the end of the last-started meal; and a one-meal day at the default duration is exactly as before. → Task 6.

---

## Part A — the Record tab

### Task 1: `RecordVenueFilter` (pure)

**Files:**
- Create: `shared/src/commonMain/kotlin/com/crazystudio/sportrecorder/domain/venue/RecordVenueFilter.kt`
- Test: `shared/src/commonTest/kotlin/com/crazystudio/sportrecorder/domain/venue/RecordVenueFilterTest.kt`

**Interfaces:**
- Consumes: `EatRecord`, `Venue`, `VenueName.normalize`.
- Produces:
  ```kotlin
  object RecordVenueFilter {
      /** Records whose venue matches EVERY word of [query] in "<name> <address>", case-insensitively. Blank query → [records] as given. */
      fun matching(records: List<EatRecord>, query: String): List<EatRecord>
      /** What tapping a card's venue puts in the search field: "<name> <address>", or just the name when there is no (or a blank) address. */
      fun queryFor(venue: Venue): String
  }
  ```

- [ ] **Step 1: Write the failing tests**
  ```kotlin
  @Test fun blankQuery_keepsEveryRecord()                  // "", "   " and U+3000 → the same list, same order
  @Test fun matchesTheVenueNameIgnoringCase()              // "starbucks" finds a venue named "Starbucks 信義"
  @Test fun matchesTheAddress()                            // "信義路" finds a venue at "臺北市大安區信義路二段194號"
  @Test fun everyWordMustMatch()                           // two 「麥當勞」, addresses "臺北市信義路" / "臺北市忠孝東路": "麥當勞 信義" → only the first; "麥當勞 信義 忠孝" → none
  @Test fun aRecordWithoutAVenue_neverMatchesANonBlankQuery()
  @Test fun aVenueWithoutAnAddress_matchesByNameOnly()
  @Test fun theOrderOfTheInputIsKept()
  @Test fun fullWidthSpacesSeparateWords()                 // "麥當勞　信義" (U+3000) behaves like "麥當勞 信義"
  @Test fun queryFor_isTheNameThenTheAddress()             // "麥當勞 臺北市大安區信義路二段194號"; just "Ichiran" when the address is null or blank
  @Test fun queryFor_matchesExactlyThatVenuesRecords_evenWhenABranchSharesItsName()
  ```
- [ ] **Step 2: Run, watch them fail** — `.\gradlew.bat :shared:jvmTest --tests "*RecordVenueFilterTest*"` → unresolved reference `RecordVenueFilter`.
- [ ] **Step 3: Implement** — normalise the query with `VenueName.normalize`, split on a single space, drop empties; haystack = `listOfNotNull(name, address?.takeIf { it.isNotBlank() }).joinToString(" ")`; a record matches when `words.all { haystack.contains(it, ignoreCase = true) }`.
- [ ] **Step 4: Run, watch them pass.**
- [ ] **Step 5: Commit** — `feat(records): filter records by their venue's name or address`.

---

### Task 2: when to offer the search (`RecordVenueFilter.canSearch`) — *the ViewModel version was superseded*

**Superseded (Ruling 8, code-review-loop, 2026-11-07).** The first version gave `DietRecordViewModel` a `query`, `showSearch`, `hasVenues`, `setQuery` and `searchVenue`. It held the search text twice (ViewModel and screen) and cost three rework rounds. **Final design: the Record screen owns the text (`rememberSaveable`) and filters its own list with `RecordVenueFilter` — as the venue picker already does — and `DietRecordViewModel` is exactly as on master.** What Task 2 became:

**Files:** Modify `shared/src/commonMain/kotlin/com/crazystudio/sportrecorder/domain/venue/RecordVenueFilter.kt` and its test.

**Interfaces:**
- Produces: `fun canSearch(records: List<EatRecord>, query: String): Boolean` = `query.isNotEmpty() || records.any { it.venue != null }` — so the field can never vanish while a query is active.
- Tests: `canSearch_isFalseWhenNoRecordHasAVenueAndNothingIsTyped`, `canSearch_isTrueWhenARecordHasAVenue`, `canSearch_staysTrueWhileAQueryIsTyped_evenIfNoRecordHasAVenueAnymore`; and a blank address is "none" through `VenueAddress.clean` (`anAddressWithPaddingLeavesNoDoubleSpaceInTheQuery`).

---

### Task 3: `RecordScreen` — the venue on the card, and the search field

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/crazystudio/sportrecorder/ui/diet/record/RecordScreen.kt`
- Modify: strings, both locales
- Test: `app/src/androidTest/java/com/crazystudio/sportrecorder/ui/diet/record/RecordScreenTest.kt` (new)
- `AppRoot.kt` and `DietRecordViewModel.kt` are **unchanged**.

**Interfaces:**
- Consumes: `RecordVenueFilter.matching / canSearch / queryFor`, `VenueAddress.clean`.
- Produces: `RecordScreen(records, onDelete, onEditRecord, photoModel, onPhotoClick, modifier)` — the same signature as on master; `records` is every record and the screen filters it.
- Strings (en / zh-rTW): `record_search_hint` Search places (name or address) / 搜尋店家（名稱或地址）; `record_search_clear` Clear search / 清除搜尋; `record_search_empty` No matching records / 沒有符合的紀錄; `record_venue_search` Show this place's records / 看這家店的紀錄.
- Behaviour: a single-line `OutlinedTextField` (the hint as its `label`, so it stays the accessible name once it holds text) with a trailing ✕ (`ic_baseline_close_24`) above the list, shown when `canSearch`. `var query by rememberSaveable`; `shown = remember(records, query) { matching(records, query) }`. **One `search(value)` path** — typing, the ✕ and a venue tap (`search(queryFor(venue))`) — sets the query and `requestScrollToItem(0)` on a hoisted `LazyListState`, so a new query starts at the newest match. On the card, under the time: the venue name (`bodyMedium`) and, when `VenueAddress.clean` leaves one, the address (`labelSmall`, `onSurfaceVariant`); the block is `combinedClickable(role = Role.Button, onClickLabel = record_venue_search, onLongClick = the card's long-press)`. When `query.isNotBlank()` and nothing matches: the `record_search_empty` line (a polite live region) instead of the list. `imePadding()` on the screen. The "📍 lat, lng" line and the delete dialog are unchanged.
- Tests (`RecordScreenTest`, passing every record and letting the screen filter): `aCardShowsItsVenueNameAndAddress`, `aRecordWithoutAVenueShowsNoVenueLines`, `aBlankAddressShowsNoAddressLine`, `theSearchFieldIsHiddenWhenNoRecordHasAVenue`, `anEmptyDiaryDoesNotClaimThatNothingMatched`, `typingNarrowsTheList_andTheClearButtonShowsEverythingAgain`, `tappingTheVenueFillsTheSearchWithItsNameAndAddress_andShowsOnlyThatVenue` (two same-name venues), `aSearchWithNoMatchSaysSo`, `longPressingTheVenueStillOffersToDeleteTheRecord`, `theSearchFieldStaysWhileAQueryIsActive_evenWhenTheLastVenueRecordIsDeleted`, `aNewSearchStartsAtTheTopOfTheResults`.

### Task 4: Verify and document Part A

**Files:**
- Modify: `docs/DEVELOPMENT.md` (§6 doc index: a row for this spec and plan, same format as the 10-18 row), the spec's **Status** line.

- [ ] **Step 1:** Full gate; `RecordScreenTest` and `EatTimeEditorSheetTest` on `emulator-5554`.
- [ ] **Step 2: By hand on the emulator** (`installDebug`; create two venues with the same name and different addresses by sharing two Maps URLs, e.g. `https://www.google.com/maps/place/Test+Shop/@25.03,121.53,17z/data=!3d25.03!4d121.53` and `…/@25.10,121.60,17z/data=!3d25.10!4d121.60`, then record one meal at each, plus one meal with no venue). Check and screenshot (scratchpad, `adb shell screencap -p` + `adb pull` from PowerShell): the cards show name + address; the search narrows by name and by address word; tapping a card's venue shows only that branch; ✕ clears; a record with no venue is hidden while searching; the field is absent when no record has a venue (fresh install). Say what you saw.
- [ ] **Step 3:** Update `docs/DEVELOPMENT.md` and the spec's Status (`Part A implemented on claude/record-venue-search (PR pending); Part B designed`). Commit — `docs(records): index the venue search and meal-duration spec`.

---

## Part B — meal duration

### Task 5: Meal duration in the data layer

**Files:**
- Create: `shared/src/commonMain/kotlin/com/crazystudio/sportrecorder/domain/model/MealDuration.kt`, `shared/src/commonTest/kotlin/com/crazystudio/sportrecorder/domain/model/MealDurationTest.kt`
- Modify: `domain/model/EatRecord.kt`, `entity/EatTime.kt`, `database/AppDatabase.kt` (version 10), `database/Migrations.kt`, `data/mapper/EatRecordMappers.kt`, `data/repository/EatRecordRepositoryImpl.kt` (all three `EatTime(...)` constructions: insert, update, replace-all), the committed Room schema `shared/schemas/com.crazystudio.sportrecorder.database.AppDatabase/10.json` (generated by the build), and both fakes if they rebuild records field by field.
- Test: `app/src/androidTest/java/com/crazystudio/sportrecorder/database/RoomMigrationTest.kt`, `app/src/androidTest/java/com/crazystudio/sportrecorder/data/EatRecordRepositoryRoomTest.kt`, the existing mapper test (`EatRecordMappersTest` — find it) 

**Interfaces:**
- Produces:
  ```kotlin
  object MealDuration {
      const val DEFAULT_MINUTES = 60
      const val MILLIS_PER_MINUTE = 60_000L
      /** The editor's menu, ascending. */
      val CHOICES: List<Int> = listOf(15, 30, 45, 60, 90, 120, 180)
      /** (hours, remaining minutes): 90 → (1, 30); 60 → (1, 0); 45 → (0, 45). */
      fun parts(minutes: Int): Pair<Int, Int>
  }
  data class EatRecord(
      ...existing parameters unchanged..., val venue: Venue? = null,
      val durationMinutes: Int = MealDuration.DEFAULT_MINUTES,     // LAST, so every existing call compiles
  ) { val endTime: Long get() = time + durationMinutes * MealDuration.MILLIS_PER_MINUTE }
  ```
  `EatTime.durationMinutes: Int = MealDuration.DEFAULT_MINUTES` with `@ColumnInfo(name = "duration_minutes", defaultValue = "60")`. Migration 9 → 10: `ALTER TABLE eat_time ADD COLUMN duration_minutes INTEGER NOT NULL DEFAULT 60`.

- [ ] **Step 1: Failing tests**
  ```kotlin
  // commonTest
  @Test fun defaultIsOneHour()
  @Test fun choicesAreAscending_andContainTheDefault()
  @Test fun partsSplitsHoursAndMinutes()                      // 90 → (1, 30), 60 → (1, 0), 45 → (0, 45), 180 → (3, 0)
  @Test fun endTimeIsStartPlusDuration()                      // time = 1_000, 90 min → 1_000 + 5_400_000; and the default is 3_600_000
  // androidTest
  RoomMigrationTest.migrates9to10_keepsEveryMealAndReadsItAsOneHour   // seed a v9 DB with 2 meals (one with a venue and photos) → both survive, durationMinutes == 60, the venue link intact
  EatRecordRepositoryRoomTest.aDurationRoundTrips_onInsertAndOnUpdate // save 90 → findById 90; update to 30 → 30; replaceAll keeps it
  // mapper test
  aMealsDurationIsCarriedFromTheRowToTheRecord
  ```
- [ ] **Step 2: Run, watch them fail** — `:shared:jvmTest --tests "*MealDurationTest*"` first; then the androidTest classes on `emulator-5554`.
- [ ] **Step 3: Implement** as listed. The build generates `10.json`; commit it. Room does not compare a default the entity does not declare, but the entity here declares `defaultValue = "60"`, so make the migration's DDL match exactly and let `migrates9to10…` prove it.
- [ ] **Step 4: Run, watch them pass; run the full gate.**
- [ ] **Step 5: Commit** — `feat(meals): a meal has a duration, 1 hour by default`.

---

### Task 6: The fast clock follows the last meal's end

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/crazystudio/sportrecorder/domain/diet/DietWindow.kt`, `domain/usecase/ObserveDietStateUseCase.kt`, `domain/usecase/RescheduleRemindersUseCase.kt`, `domain/reminder/ReminderPlanner.kt`, `ui/diet/DietViewModel.kt`
- Test: `shared/src/commonTest/.../domain/diet/DietWindowTest.kt`, `.../domain/reminder/ReminderPlannerTest.kt`, `.../flow/DietStateFlowTest.kt`, `app/src/test/.../ui/diet/DietViewModelTest.kt` — update every `compute`/`plan`/snapshot call to the new types, and every expectation that changes meaning (listed below)

**Interfaces:**
- Consumes: `EatRecord.endTime` (Task 5).
- Produces:
  ```kotlin
  /** One eating occasion, as epoch millis. */
  data class MealSpan(val start: Long, val end: Long)

  object DietWindow {
      fun <T> groupIntoWindows(itemsAsc: List<T>, eatingHours: Long, fastingHours: Long, timeOf: (T) -> Long): List<List<T>>   // UNCHANGED
      fun compute(mealsAsc: List<MealSpan>, eatingHours: Long, fastingHours: Long, now: Long): DietWindowState   // ascending by start
  }
  ReminderPlanner.plan(mealsAsc: List<MealSpan>, settings: DietSettings, prefs: ReminderPrefs, now: Long, timeZone: TimeZone = …)
  ObserveDietStateUseCase.DietSnapshot(val mealsAsc: List<MealSpan>, val settings: DietSettings)
  ```
  Rules inside `compute`: group with `groupIntoWindows(mealsAsc, …) { it.start }`, take the last window; `first = window.first().start`; `latestEnd = window.maxOf { it.end }`; `windowEnd = maxOf(first + eatingMillis, latestEnd)`; `fastStartAt = latestEnd`; `fastTargetAt = fastStartAt + fastingMillis`; `lastEat` stays the START of the last-started meal. **Delete `SINGLE_MEAL_FAST_GRACE_MILLIS` and the `mealCount == 1` branch.** Callers build `MealSpan(it.time, it.endTime)`; `RescheduleRemindersUseCase` sorts by `start`.

- [ ] **Step 1: Failing tests** (in `DietWindowTest`; `eh = 8`, `fh = 16`, `f = 1_700_000_000_000L`; a helper `meal(start, minutes = 60)`)
  ```kotlin
  @Test fun singleMeal_atTheDefaultDuration_fastStartsOneHourAfter_asBefore()      // [meal(f)] → fastStartAt == f + h(1)
  @Test fun singleMeal_aDifferentDuration_movesTheFastStart()                      // meal(f, 30) → f + 30 min; meal(f, 120) → f + h(2)
  @Test fun twoMeals_fastStartsWhenTheLastMealEnds()                               // [meal(f), meal(f + h(3))] → f + h(4)  (was f + h(3))
  @Test fun overlappingMeals_fastStartsAtTheLatestEnd()                            // [meal(f, 180), meal(f + h(1), 15)] → f + h(3)
  @Test fun windowEnd_extendsToTheLastMealsEnd()                                   // [meal(f), meal(f + h(9))] → windowEnd == f + h(10)  (was f + h(9))
  @Test fun aMealThatCrossesMidnight_isHandledLikeAnyOther()
  ```
  and update the existing ones whose meaning changes: `multiMeal_fastStartsAtLastMealNoGrace` → renamed `multiMeal_fastStartsWhenTheLastMealEnds`; `afterWindowClose_fastingCountsFromLastBite` (elapsed becomes `h(4)` for meals at f and f+h(4), now = f+h(9)); `slightOverrun_extendsWindowEnd`; `lateMealWithinTolerance_mergesIntoWindow`; `compute_usesTheLatestWindowOnly`; the boundary tests. Add to `ReminderPlannerTest`: `fastCompleteFollowsTheLastMealsEnd` (a 30-minute meal fires 30 minutes after the start + fastingHours; the single-meal tests at the default are unchanged), and to `DietStateFlowTest`: `aLongerMeal_movesTheHomeFastStart`.
- [ ] **Step 2: Run, watch them fail** — `.\gradlew.bat :shared:jvmTest --tests "*DietWindowTest*"` (compile error: `MealSpan`).
- [ ] **Step 3: Implement** as above and fix every caller and test.
- [ ] **Step 4: Run, watch them pass; run the full gate.**
- [ ] **Step 5: Commit** — `feat(diet): the fast starts when the last meal ends`.

---

### Task 7: Backup carries the duration

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/crazystudio/sportrecorder/backup/BackupDocument.kt`, `BackupMappers.kt`, `BackupService.kt` if it rebuilds meals
- Test: `shared/src/commonTest/.../backup/BackupServiceTest.kt`, `BackupDocumentGoldenTest.kt`, `BackupMappersTest.kt`, and `app/src/androidTest/.../backup/BackupRoundTripRoomTest.kt`

**Interfaces:**
- Produces: `BackupMeal(…existing…, val durationMinutes: Int? = null)`; `EatRecord.toBackup()` writes it; `BackupMeal.toDomain()` reads `durationMinutes ?: MealDuration.DEFAULT_MINUTES`. **`SCHEMA_VERSION` does not change.**

- [ ] **Step 1: Failing tests**
  ```kotlin
  @Test fun aMealsDurationRoundTripsThroughABackup()                       // 90 → backup → restore → 90
  @Test fun aSnapshotWithoutDurations_restoresEveryMealAsOneHour()         // JSON from before this change
  @Test fun aSnapshotWithADuration_isStillReadableByTheCurrentSchema()     // schemaVersion unchanged
  ```
  plus the golden fixtures: add `durationMinutes` to the current-version golden, keep one fixture without it, and say so in the commit message.
- [ ] **Step 2: Run, watch them fail** — `.\gradlew.bat :shared:jvmTest --tests "*Backup*"`.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run, watch them pass; run the full gate; run `BackupRoundTripRoomTest` on `emulator-5554`.**
- [ ] **Step 5: Commit** — `feat(backup): keep each meal's duration`.

---

### Task 8: The editor's 「用餐時間」 row

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/crazystudio/sportrecorder/ui/diet/editor/EatTimeEditorViewModel.kt`, `EatTimeEditorUiState.kt`, `EatTimeEditorSheet.kt`, `app/src/main/java/com/crazystudio/sportrecorder/ui/AppRoot.kt`, strings (both locales)
- Test: `app/src/test/java/com/crazystudio/sportrecorder/ui/diet/editor/` (add `EatTimeEditorViewModelDurationTest.kt`, reusing `EatTimeEditorFixture.kt`), `app/src/test/.../domain/usecase/` quick-record test, `app/src/androidTest/.../ui/diet/editor/EatTimeEditorSheetTest.kt`, and every other call site of `EatTimeEditorSheet` (`BottomSheetHostTest`, …)

**Interfaces:**
- Consumes: `MealDuration` (Task 5).
- Produces: `EatTimeEditorUiState.durationMinutes: Int = MealDuration.DEFAULT_MINUTES`; `fun setDuration(minutes: Int)` on the ViewModel; `EatTimeEditorSheet(…, onDurationChange: (Int) -> Unit, …)`. Edit mode loads `record.durationMinutes`; `save()` puts `state.durationMinutes` into the `EatRecord`.
- Strings (en / zh-rTW):

  | key | en | zh-rTW |
  |---|---|---|
  | `diet_eat_duration_title` | Duration | 用餐時間 |
  | `duration_minutes` | %1$d min | %1$d 分鐘 |
  | `duration_hours` | %1$d hr | %1$d 小時 |
  | `duration_hours_minutes` | %1$d hr %2$d min | %1$d 小時 %2$d 分鐘 |

  Text for `m` minutes: under 60 → `duration_minutes(m)`; a whole number of hours → `duration_hours(h)`; else `duration_hours_minutes(h, rest)` — from `MealDuration.parts`.
- UI: a third row under the Time row, built like the Date/Time rows (`HeaderRow`, `ic_baseline_access_time_24`, the text above, the `ic_baseline_arrow_drop_down` action), with a `DropdownMenu` anchored to it listing `MealDuration.CHOICES`; choosing one calls `onDurationChange` and closes the menu. A stored value that is not a choice is displayed as is (the menu still lists only the choices).

- [ ] **Step 1: Failing tests**
  ```kotlin
  // ViewModel (app/src/test)
  @Test fun aNewMeal_startsAtOneHour()
  @Test fun aChosenDuration_isSavedWithTheMeal()
  @Test fun editingAMeal_loadsItsStoredDuration()                   // 90 in the repository → state.durationMinutes == 90
  @Test fun savingWithoutTouchingTheRow_storesOneHour()
  @Test fun anOddStoredDuration_isKeptUntilTheUserChoosesAnother()  // 75 survives a save that doesn't touch the row
  @Test fun aQuickRecord_lastsOneHour()                             // QuickRecordMealUseCase → saved record has durationMinutes == 60
  // androidTest (EatTimeEditorSheetTest)
  @Test fun theDurationRowReadsOneHourByDefault_andChoosingNinetyMinutesReportsIt()   // the row shows 「1 小時」/"1 hr"; open the menu, pick 1 hr 30 min → onDurationChange(90)
  ```
- [ ] **Step 2: Run, watch them fail** — `testDebugUnitTest --tests "*EatTimeEditorViewModelDurationTest*"`.
- [ ] **Step 3: Implement.** Update every `EatTimeEditorSheet` call site (no-op lambda in tests that don't care).
- [ ] **Step 4: Run, watch them pass; run the full gate; run `EatTimeEditorSheetTest` and `BottomSheetHostTest` on `emulator-5554`.**
- [ ] **Step 5: Commit** — `feat(editor): choose how long a meal took`.

---

### Task 9: The card shows the time range

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/crazystudio/sportrecorder/ui/diet/record/RecordScreen.kt`
- Test: `shared/src/commonTest/kotlin/com/crazystudio/sportrecorder/ui/diet/record/RecordTimeFormatTest.kt` (new), `app/src/androidTest/.../ui/diet/record/RecordScreenTest.kt`

**Interfaces:**
- Consumes: `EatRecord.endTime` (Task 5).
- Produces: `internal fun formatClockRange(start: Long, end: Long, zone: TimeZone = TimeZone.currentSystemDefault()): String` → `"HH:mm – HH:mm"` (an en dash with a space on each side; clock times only). The card's time line uses it. The date line is unchanged.

- [ ] **Step 1: Failing tests**
  ```kotlin
  // commonTest, a fixed zone (e.g. TimeZone.UTC)
  @Test fun aRangeWithinOneDay()               // 12:30 + 60 min → "12:30 – 13:30"
  @Test fun aRangeAcrossMidnight()             // 23:30 + 60 min → "23:30 – 00:30"
  @Test fun aNinetyMinuteRange()               // 12:00 + 90 → "12:00 – 13:30"
  // androidTest
  @Test fun aCardShowsItsTimeRange()           // record with durationMinutes = 90
  ```
- [ ] **Step 2: Run, watch them fail** — `.\gradlew.bat :shared:jvmTest --tests "*RecordTimeFormatTest*"`.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run, watch them pass; run the full gate; run `RecordScreenTest` on `emulator-5554`.**
- [ ] **Step 5: Commit** — `feat(records): a card shows when the meal started and ended`.

---

### Task 10: Verify and document Part B

**Files:**
- Modify: `docs/DEVELOPMENT.md`, the spec's **Status** line.

- [ ] **Step 1:** Full gate. Instrumented on `emulator-5554`: `RoomMigrationTest`, `EatRecordRepositoryRoomTest`, `BackupRoundTripRoomTest`, `EatTimeEditorSheetTest`, `RecordScreenTest`, `BottomSheetHostTest`, `ShareEntryTest`.
- [ ] **Step 2: By hand on the emulator** (screenshots to the scratchpad, say what you saw): (a) Home → add → **Create** with no other tap → the Record tab shows 「hh:mm – hh:mm+1h」; (b) ✎ that record → the row reads 1 小時 → choose 30 分鐘 → Save → the card reads the 30-minute range; (c) Home's fast-start label equals the last meal's end (compare with a 30-minute and a 3-hour meal); (d) a Maps share still opens the editor with 1 小時; (e) zh-TW text of the new row and menu (switch the app language with `adb shell cmd locale set-app-locales com.crazystudio.sportrecorder --locales zh-TW`, and restore it to `[]` afterwards).
- [ ] **Step 3:** Update `docs/DEVELOPMENT.md` and the spec's Status (implemented, with both PRs). Commit — `docs: meal duration implemented`.
- [ ] **Step 4:** Draft the release note for the owner (user-facing, both locales) and state in it that on days with two or more meals the fast now starts when the last meal ends.
