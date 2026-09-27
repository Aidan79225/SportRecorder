# 回顧 / Insights — one period control, eating days, and the day sheet (B4 · B6 · B7 · B8) design

- **Date:** 2026-09-27
- **Status:** Implemented on `claude/insight-page-improvements-2fe29z`, together with the map
  (`2026-09-27-insights-places-map-design.md`).
- **Builds on:** `2026-09-21-insights-improvements-design.md` (bucket B) and its plan
  `docs/superpowers/plans/2026-09-21-insights-improvements.md`. Where this document and that plan
  differ, this document is what was built.

## Goal

Finish the Insights page's turn from report card to mirror, and fix the things reading the code
turned up on the way:

1. **An empty-state flicker.** The `stateIn` seed looked like "no records", so every returning
   user saw 「這裡還空著」 for a frame at every open.
2. **Home and Insights disagreed about which day a meal was.** Home groups meals into eating
   windows (`DietWindow`); Insights bucketed by calendar date. A 00:30 snack after a 20:00 dinner
   was one window on Home and "tomorrow's first meal" on Insights — pulling the average first
   meal towards midnight for exactly the late-night eaters a fasting diary is for.
3. **Two period models on one page.** Week/Month scoped the stats but not the calendar; the
   calendar had its own month pager; nothing said so.
4. **Bucket B as approved:** B6 (drop the 22:00 metric), B4 (a day leads to its meals), B7 (the
   rhythm chart), and B8 (one period control) — plus the untranslated bottom-tab labels.

## Design

### Eating days (問題 2)

`DietWindow.groupIntoWindows` is now the single definition of "the same eating day": a meal joins
the current window while it is within `eatingHours + fastingHours / 2` of that window's **first**
meal; only a meal after a real fast opens a new one. Home's ring uses the last window;
`InsightsAggregator.mealsByEatingDay` buckets every meal by its window, keyed by the local date of
the window's first meal. Calendar, stats, chart, photos, map and the day sheet all read that one
map, so they can never disagree.

Consequences, stated rather than hidden:

- A window that crosses midnight keeps going: its last meal is minutes **past 24 h** from the
  eating day's midnight. The chart draws the band on past the day; the stats average the unwrapped
  value (23:30 and 00:30 average to 24:00, i.e. midnight, not 12:00) and the UI wraps it to the
  clock for display; the sheet marks such a meal 「隔天 00:30」.
- Changing the fasting type re-buckets the past (the merge limit depends on it). That was already
  true of the calendar's colours (B2); it is now also true of which day a meal lands on. B2 stays
  open and unchanged in scope.

### One period control (B8)

Week / Month chips and a ‹ › pager sit at the top and scope **every** card. `InsightsRange` turns
an anchor day + period into the days shown: Week = the seven days ending on the anchor, Month =
the anchor's calendar month. Paging moves the anchor by seven days or one month and never goes
past today. Switching Week ↔ Month keeps the anchor, so the user stays around the same days.
In Month, days still ahead are drawn at 40 % so the shape of the month reads, but nothing is
claimed about them. This also settles "the first of the month is empty": page back and the
previous month is whole.

### The chart (B7) — Week only

One row per day, time left to right, one band from first meal to last, every band the same
`primaryContainer` on a `surfaceVariant` track with faint 6-hour gridlines. Seven rows read as a
shape; thirty-one read as a barcode, so the chart card is shown in Week only (the 09-21 plan's
"render all rows in Month" option was rejected on the emulator-in-the-head test). The axis is 24 h
and grows in 6 h steps when a night ran long — the labels are plain hours (`0 6 12 18 24 30`), so
"30" is unambiguous where "06" twice would not be. A single meal draws a 3 dp sliver. Each row is
its own accessibility node (date, meal count, first–last) and a row with meals opens the day.

### The day sheet (B4)

Tapping a calendar cell **with a record** (or a chart row) opens `DayRecordsSheet` as a modal
bottom sheet over Insights (`Route.DayRecords(dayStart)`, the pattern the editor already uses).
Read-only: time, note, photos; no delete, no edit — Insights is for looking, the Record tab keeps
the destructive controls. Empty days are inert: no ripple, no sheet. Offering "log a meal for this
day" from the calendar would turn a mirror into a back-fill prompt, so it is deliberately absent.
`DayRecordsViewModel` reads `dayStart` from its `SavedStateHandle` (as `EatTimeEditorViewModel`
reads `eatTimeId`) and filters with the same `mealsByEatingDay`, so the sheet lists exactly what
the cell counted.

### The rest

- **B6:** `lateHourDays`, `LATE_HOUR`, the row and its strings are gone. 平均最後一餐 already
  says when days tend to end, without a threshold that is someone else's opinion.
- **Empty-state flicker:** `InsightsUiState.isLoaded` is false only for the seed; the screen
  draws nothing in that frame.
- **Tab labels:** `AppRoot` reads them from `R.string.title_*`; `title_insights` was added to
  `:app`'s resources in both locales.

## 初衷對照 / North-Star check

**Does this serve the mission?** Yes. Every change either removes a verdict (B6), lets a pattern
lead back to a moment (B4 closes Reflect → Insight → Re-engage), or makes the page tell the truth
more plainly (one period, one definition of a day, no flicker of "you have nothing").

Against the red flags:

- **評價使用者** — the chart is single-coloured by design; nothing is drawn against the goal. The
  22:00 metric, the last threshold on the page, is gone. Future days are dimmed, not marked.
- **記錄變成負擔** — nothing added to capture. Empty days deliberately do not invite back-filling.
- **外在壓力取代覺察** — no streak, no comparison, no "you missed". The period summary is a count.
- **教練式命令的語氣** — the only new copy is descriptive: 「看這一天」, 「隔天 00:30」, 「一天的形狀」.

**Where it could drift:**

- The band chart is one design decision away from a grade: colour the bands by whether they fit
  `eatingHours`, and it is a report card again. That line is written into the code comment and
  this spec; if a future ask wants it, the mission-aligned answer is the legend the calendar already
  has, not colour on the chart.
- "See this day" could grow edit/delete controls for convenience. They stay on the Record tab.

## Testing

- **Unit (`shared/src/commonTest`, `:shared:jvmTest`)** — `DietWindowTest` (grouping rule, and
  `compute` still uses only the latest window); `InsightsRangeTest` (week/month ranges, paging,
  clamps at today); `InsightsAggregatorTest` (eating-day bucketing incl. the late snack and the
  meal after a real fast, cells with today/future, stats past 24 h, bands, compute over one range,
  future-dated records); `RhythmChartScaleTest` (axis growth and ticks).
- **Unit (`app/src/test`, `testDebugUnitTest`)** — `InsightsViewModelTest` (not loaded until the
  first emission, period switch keeps the anchor, month and week paging, forward clamp);
  `DayRecordsViewModelTest` (a day's meals incl. the late snack, the next day, an empty day).
- **Flow (`app/src/test/flow`)** — `InsightsReflectFlowTest`: empty diary → record a few days →
  month reads back (the snack under the 12th, not the 13th) → tap the 12th → the sheet lists dinner
  + snack → Week shows the 12th's band running past midnight → paging back a week is empty.
- **Static** — `:app:detekt`, `:app:lintDebug`.
- **Manual (device, both locales)** — no empty-card flicker when reopening the tab; tab labels in
  zh-rTW; week row headers match their days; tapping a coloured cell opens the sheet, an empty
  one does nothing; a photo in the sheet opens the viewer above it; back dismisses to the same
  period; the chart shows only in Week and its rows announce under TalkBack.

## Verification note (2026-09-27)

Same container limits as before (no Android SDK, Google Maven blocked): the four `commonTest`
suites above (74 tests, with the map's 12) compiled and passed under a standalone Kotlin 2.3.10
compiler + kotlinx-datetime 0.7.1. Everything that needs Compose, the Android test fakes or
navigation — the screen, the chart, the sheet, both ViewModels, their tests and the flow test — is
**unverified by build** and must be gated on CI.
