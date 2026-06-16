# 回顧 / Insights tab — design

**Date:** 2026-06-16
**Status:** Approved design, pending implementation plan

## Context

SportRecorder is a fasting-rooted food-diary app whose goal is *awareness through
easy recording*: 輕鬆記錄 → 意識到自己到底吃了什麼 → 更關注健康與生活. Framed as a
habit loop — **Capture → Reflect → Insight → Re-engage** — the app today has a strong
*Capture* engine (time, photo, note, location per meal) but almost nothing for
*Reflect* or *Insight*: the only way to look back is a flat reverse-chronological list
on the Record screen. The app therefore under-delivers its core promise.

This feature adds a **回顧 / Insights** surface that reflects the user's eating back to
them — patterns, not just a log — using data already collected. It also uniquely
unifies the fasting and diary halves of the app by scoring each day's *actual* eating
window against the user's fasting goal.

The **Notifications** tab is currently a "Coming soon" placeholder; we repurpose it
(dead space) rather than growing the bottom nav. Future reminders would live in
Settings, not a tab.

## Scope

A single scrollable Insights screen with four cards. A **Week / Month** toggle drives
the stats + photo-wall cards; the adherence calendar is always a pageable month.

1. **斷食達成 — adherence calendar + streak**
2. **統計 — eating-pattern stats**
3. **照片牆 — meal photo wall**
4. **地點 — eating locations** (light, text-only for v1)

Out of scope: reminders/notifications, calorie/macro tracking, per-day historical
targets, configurable thresholds, maps SDK, export/sync.

## Architecture

Mirrors the existing clean-architecture + Hilt pattern (see `DietRecordViewModel`,
`DietWindow`, `ObserveEatRecordsUseCase`).

| Layer | New file | Responsibility |
|-------|----------|----------------|
| Domain (pure) | `domain/insights/InsightsAggregator.kt` | Pure `compute(records, settings, now, period, monthAnchor) → InsightsResult`. No Android deps. |
| Domain models | in the same file or `domain/insights/InsightsModels.kt` | `DayAdherence`, `AdherenceState`, `InsightsStats`, `LocationCount`, `InsightsResult`, `Period` |
| UI | `ui/insights/InsightsViewModel.kt` | `@HiltViewModel`; combine record/settings flows with selected period/month UI state → `InsightsUiState` |
| UI | `ui/insights/InsightsUiState.kt` | UI state holder |
| UI | `ui/insights/InsightsScreen.kt` | Compose screen + the four card composables |

**Reused, do not re-implement:**
- `EatRecordRepository.observeAll(): Flow<List<EatRecord>>` (newest-first, includes photos) via `ObserveEatRecordsUseCase()`.
- `DietSettingsRepository.settings: Flow<DietSettings>` → current `eatingHours` / `fastingHours`.
- `DietTimeFormat` (`Calendar`-based `startOfDay` / `relativeDay`) for day bucketing — minSdk-24 safe (no `java.time`).
- `SimpleDateFormat` patterns already used (`HH:mm`, `yyyy/MM/dd`) for labels.
- `PhotoStorage.fileFor(context, fileName)` + **`FullScreenPhotoViewer(fileNames, initialIndex, onDismiss)`** for the photo wall.
- `EatRecord` domain model (`id, time, location: GeoPoint?, note, photos: List<EatPhoto>`).

### Data flow

```
ObserveEatRecordsUseCase()  ─┐
DietSettingsRepository.settings ─┼─ combine ─→ InsightsAggregator.compute(...) ─→ InsightsUiState
selectedPeriod (MutableStateFlow)┘                                    (.stateIn WhileSubscribed 5000)
selectedMonthAnchor (MutableStateFlow)
```

`now` is injected (clock seam) for deterministic tests, matching the existing
`DietViewModel` test pattern.

## Card details & algorithms

### Day bucketing
Group `EatRecord`s by local calendar day using `Calendar` `startOfDay(record.time)`.
A meal near midnight is bucketed by its own calendar date.

### 1. Adherence calendar + streak
For each calendar day in the displayed month:
- **NO_DATA** — no meals that day (⚪).
- compute `window = lastMeal.time − firstMeal.time`; a single-meal day → `window = 0`.
- **ON_TARGET** (🟢) if `window ≤ eatingHours` (converted to millis via `TimeUnit.HOURS`).
- **OFF_TARGET** (🟠) otherwise.

**Streak** = consecutive ON_TARGET days counting back from the most recent day:
- If **today** has no meals yet, today is **neutral** — start counting from yesterday
  (so an in-progress today doesn't zero the streak).
- An OFF_TARGET day or a NO_DATA day (other than an empty in-progress today) **breaks**
  the streak.

**Historical-target caveat (accepted for v1):** only the *current* `eatingHours` setting
is stored, so past days are scored against today's goal. Per-day historical targets are
out of scope.

### 2. Stats (selected period)
- **Meal count** — number of `EatRecord`s in the period.
- **Avg first-meal time** — average clock time (minutes-since-midnight) of each day's
  first meal, rendered `HH:mm`.
- **Avg last-meal time** — same for each day's last meal.
- **Late-night count** — number of days with any meal at/after **22:00** (fixed v1
  threshold).

### 3. Photo wall (selected period)
Flatten all `EatPhoto.fileName`s from the period's records, newest-first, into a grid
(`LazyVerticalGrid`). Tapping a cell opens `FullScreenPhotoViewer` with the full ordered
`fileNames` list and the tapped index (same state pattern as `RecordScreen`:
`Pair<List<String>, Int>?`). Empty state when no photos.

### 4. Locations (selected period)
Group records that have a `GeoPoint` by rounded lat/lng, show a short list with counts
("📍 lat, lng — N 次"). Empty/hidden when no located records. No map SDK in v1.

### Period model
`Period { WEEK, MONTH }` toggle drives cards 2–4 (window relative to `now`). The
calendar (card 1) is always a full month with prev/next month paging via a
`selectedMonthAnchor` state, independent of the Week/Month toggle.

## Navigation & resource changes

- `ui/nav/Route.kt` — replace `data object Notifications : Route` with `data object Insights : Route`.
- `ui/AppRoot.kt` — swap the tab entry (`Tab(Route.Insights, <label>, <icon>)`), the
  bottom-nav `when` branch, and the `composable<Route.Insights> { InsightsScreen() }` entry.
- Delete `ui/notifications/NotificationsScreen.kt`.
- Add a tab icon vector `res/drawable/ic_baseline_insights_24.xml`.
- Strings in `res/values/strings.xml` + `res/values-zh-rTW/strings.xml`: tab label
  (`Insights` / `回顧`), card titles (斷食達成 / 統計 / 照片牆 / 地點), stat labels,
  streak text, week/month toggle, empty states.

## Testing

- **Unit — `InsightsAggregatorTest`** (pure, injected `now`): day bucketing; ON/OFF/NO_DATA
  classification incl. single-meal and midnight-crossing days; streak across gaps,
  off-target days, and an empty in-progress today; stats averages (first/last/avg);
  late-night counting; period windowing (week vs month). Mirrors `DietWindowTest` /
  the existing ViewModel clock-seam suite.
- **Manual (emulator):** open 回顧 tab; with seeded records verify calendar colors +
  streak headline, stats numbers, photo-wall grid → full-screen viewer, and the
  Week/Month toggle + month paging. Confirm zh-rTW + en labels.

## Verification checklist

- `./gradlew.bat build test` green (includes detekt — keep new code under thresholds:
  group long parameter lists, avoid magic numbers / extract constants like the 22:00
  threshold and color thresholds).
- New tab replaces Notifications and renders the four cards.
- Adherence/streak/stats match hand-computed expectations for a seeded dataset.
