# 回顧 / Insights — improvements design

- **Date:** 2026-09-21
- **Status:** Bucket (A) implemented on `claude/insights-improvements`; bucket (B) awaiting the owner's decision
- **Supersedes nothing** — builds on `docs/superpowers/specs/2026-06-16-insights-reflection-design.md`

## Goal

The Insights tab shipped as designed, but in use it reads like a **report card** rather than a
mirror. This spec keeps everything it does well (calendar, stats, photo wall, places) and fixes
the three things that undercut the North Star:

1. **Tone.** 「斷食達成」, a red `error`-coloured calendar cell, and a 「連續 0 天」 headline are
   verdicts. The mission is *只記錄、不評價*.
2. **Legibility.** Colours with no legend, numbers with no context or units, a zero-filled page
   for a brand-new user, and two strings missing from `zh-rTW`.
3. **Usefulness.** The one number a fasting-rooted diary should show — *how long your eating
   window actually is* — is missing, while lat/lng coordinates take up a whole card.

---

## Assessment of the page as it stands

### Content

| Card | Today | Gap |
|---|---|---|
| Period chips | Week / Month at the very top | Drives cards 2–4 only; the calendar ignores it. Nothing says so, so the chips read as scoping the whole page. |
| 斷食達成 | Streak headline + month grid | No legend; colour is the only channel; no “today” marker; pages infinitely into the future; empty months look identical to bad months. |
| 統計 | meal count · avg first · avg last · late-night days | No units, no period context, no *window length* — the number a 16:8 user actually wants. “Late-night days” encodes a norm. |
| 照片牆 | every photo in the period, 3-up | Composed eagerly inside a `verticalScroll` `Column` — an unbounded number of `AsyncImage`s in one pass. |
| 地點 | `📍 25.0330, 121.5654 · 3` | Raw coordinates are not a place; grouped at 3 decimals but printed at 4, so the last digit is always `0`. |

The page is also a **dead end**: you can see that the 11th was a long day, but you cannot get from
that cell to the meals that made it. That is the biggest missing link in Capture → Reflect →
Insight → Re-engage (bucket B).

### Tone (the important one)

- 「斷食達成」 / “Fasting adherence”, `ON_TARGET` / `OFF_TARGET`, 「深夜進食天數」 — the vocabulary
  of a grade, in the UI copy *and* in the domain enum.
- `AdherenceState.OFF_TARGET` renders with `MaterialTheme.colorScheme.error`. A day when you ate
  across ten hours is painted with the same colour the design system reserves for *errors*. This
  is the single clearest 評價使用者 red flag on the page.
- 「連續 %d 天」 shows 「連續 0 天」 the moment a chain breaks — a zero as a headline.
- A user with no records at all sees `0`, `0`, `—`, `—`, `0` and a grid of grey. Nothing welcomes
  them; the zeroes read as failure before they have done anything.

### Correctness

- `InsightsUiState()`’s default `monthAnchor = 0L`, used as the `stateIn` seed, renders a
  **January 1970** calendar for the first frame.
- `shiftMonth` is unbounded — you can page to 2031 and stare at empty grids.
- Averages truncate (`.toInt()`) instead of rounding.
- Records timestamped in the future are excluded from stats/photos (`it.time in from..now`) but
  still classify their calendar day. Left as-is, documented here.
- A day with a single meal has a 0-length window and is counted as “on target”. Accepted in the
  original spec; the neutral re-framing below makes it read correctly rather than flatteringly.
- Past days are scored against *today’s* `eatingHours` (no historical targets). Still true —
  bucket B.

### Localization / accessibility

- `insights_value_none` and `insights_location_count` exist only in `values/` — `zh-rTW` falls
  back to English.
- `contentDescription = "Previous month"` / `"Next month"` are hard-coded English literals.
- Calendar cells carry no content description, and state is encoded in colour alone.
- The app is dark-only (`SportRecorderTheme` always applies `DarkColors`), so there is no light
  mode to break; contrast of the current roles is adequate. The problem is semantic, not contrast.

### Style

- Card titles set `fontWeight = FontWeight.SemiBold` on top of `titleMedium`, which the type scale
  already defines as `Bold` — an override that *weakens* the role, against
  `2026-06-13-typography-roles-design.md`.
- `InsightsAggregatorTest` lives in `app/src/test` and uses `java.util.Calendar`, although the
  aggregator is pure `commonMain`. It therefore never runs on the JVM or iOS targets, and it
  depends on the machine’s default time zone.

---

## 初衷對照 / North-Star check (required by CLAUDE.md)

**Does this serve the mission?** Yes, and mostly by *removing* things. The page’s job is
**Reflect / Insight** — 「這是你的模式」. Every change below either strips a verdict or adds
context so a number cannot be misread as a grade.

Against the red flags:

- **評價使用者** — this is where the page is actually drifting today, so the fix is the centre of
  the work: no `error` red on a calendar day, no 「達成」 in a card title, no bare `0` streak, no
  「深夜」 (which carries “you shouldn’t have”). Replaced with descriptions: 「在你的視窗內」,
  「比視窗長一些」, 「還沒有紀錄」, 「22:00 後有紀錄的天數」.
- **記錄變成負擔** — untouched. Nothing here adds a step to capture.
- **外在壓力取代覺察** — the **streak is the remaining tension, by design**, exactly as CLAUDE.md
  warns. Bucket A only de-escalates it (a quiet line, hidden at 0, beside a neutral month count);
  **removing it outright is bucket B, and it is my recommendation.**
- **教練式命令的語氣** — no copy added here tells the user to do anything. The empty state invites
  (「記下第一餐,這裡就會慢慢長出你的樣子」), it does not instruct.

**Where this could still drift:** the average-window number (`8 小時 20 分`) sits one design
decision away from a verdict — the moment we render it against the goal (“目標 8 小時 · 你 9 小時
20 分”) it becomes a score. It is therefore shown **on its own, as a fact**, with no target
comparison and no colour. Same rule for 「22:00 後有紀錄的天數」: a count, never a warning colour.

---

## Scope — bucket (A), implemented now

Low-risk, no new platform surface, no navigation changes.

**A1 · Neutral rhythm vocabulary (tone).**
Rename `AdherenceState` → `DayWindowState` with `WITHIN_WINDOW` / `LONGER_WINDOW` / `NO_RECORD`,
and `adherenceFor` → `windowStateFor`, so the code stops carrying a verdict the copy is trying to
drop. Card title 「斷食達成」→「進食節奏」 / “Fasting adherence” → “Your rhythm”.

**A2 · Calendar reads as a picture, not a mark.**
`LONGER_WINDOW` moves from `colorScheme.error` to `tertiaryContainer` (a calm blue, not a fault);
`WITHIN_WINDOW` stays `primaryContainer`-family green; `NO_RECORD` stays a quiet surface tone. Add
a **three-item legend** in neutral words, a **ring on today**, and a per-cell
`contentDescription` so state is not colour-only.

**A3 · Streak de-escalated, month summary added.**
`InsightsResult` gains `monthSummary` (`recordedDays`, `withinWindowDays`). The card leads with
「這個月有 12 天留下紀錄,其中 9 天在你的視窗內」 — a count, not a chain. The streak line is
demoted to a quiet secondary line and **hidden entirely when it is 0**.

**A4 · Screen-level empty state.**
`InsightsResult.hasAnyRecords`. With no records at all, the four cards are replaced by one gentle
card instead of a wall of zeroes.

**A5 · Stats card earns its space.**
Adds **有紀錄的天數** and **平均進食區間** (`avgWindowMinutes`: mean of per-day *last − first* over
days with ≥2 meals, `null` otherwise) — both pure domain, both unit-tested. Values get units
(`8 小時 20 分` / `8h 20m`) and the card carries the period range (`9/15 – 9/21`).
「深夜進食天數」 → 「22:00 後有紀錄的天數」 / “Days with a meal after 22:00”. Averages round.

**A6 · Period selector where it belongs.**
Moved from the top of the screen to directly **above the stats card**, with a caption saying it
covers the cards below, so it no longer looks like it scopes the calendar.

**A7 · Photo wall bounded.**
Render at most 12 thumbnails (a 3 × 4 block) plus a neutral count line; tapping still opens the
full period list in `FullScreenPhotoViewer`, and the full log lives on the Record tab. This caps
eager composition inside the scrolling `Column` without a full lazy-list rewrite (that is B5).

**A8 · Correctness / polish.**
`stateIn` seeds `monthAnchor` with `now()` (no 1970 flash); `shiftMonth` clamps at the current
month and the next-month button disables there (`isAnchorCurrentMonth`); month label uses
`month.number`; places print 3 decimals, matching how they are grouped.

**A9 · Localization & style.**
`insights_value_none`, `insights_location_count` and every new string added to **both** `values/`
and `values-zh-rTW/`; the month arrows get localized content descriptions; the `SemiBold` override
on card titles is dropped so `titleMedium` keeps its role weight.

**A10 · Tests move home.**
`InsightsAggregatorTest` moves to `shared/src/commonTest` on `kotlin.test` with a **fixed
`TimeZone`**, so it runs on JVM/Android/iOS and no longer depends on the machine’s zone. New cases
cover `avgWindowMinutes`, `daysWithRecords`, `monthSummary`, `hasAnyRecords`, `isToday`,
`periodStart`/`periodEnd` and `isAnchorCurrentMonth`. `InsightsViewModelTest` stays in `app/src/test` (it needs
the Android fakes) and gains forward-clamp / initial-anchor cases.

## Not in scope — bucket (B), needs the owner's decision

| # | Idea | Why it needs a decision | Recommendation |
|---|---|---|---|
| **B1** | **Remove the streak entirely**, leaving only 「這個月 N 天在你的視窗內」 | CLAUDE.md names streaks as borderline-by-design; A3 only softens it. Deleting a shipped feature is the owner’s call. | **Do it.** It is the last pressure mechanic on the page, and the month summary already says the same thing without a chain that can break. |
| **B2** | Per-day **historical fasting targets** | Only the current `eatingHours` is stored; every past day is re-scored whenever the goal changes. Needs a stored goal history + migration. | Worth doing — until then the calendar quietly rewrites the past. Medium effort, no UI. |
| **B3** | Turn coordinates into **place names** (`expect/actual` geocoder) or a small static map | Platform APIs on both targets, and a privacy question (whether any lookup leaves the device). | Do the on-device geocoder; skip the map SDK. If it can’t stay on-device, drop the card instead of shipping coordinates. |
| **B4** | **Tap a calendar day → that day’s meals** | The biggest content win, but it needs navigation design (filtered Record tab vs. a sheet) beyond the Insights feature. | Do it next. It is what turns a pattern back into a moment, and closes the loop to Re-engage. |
| **B5** | Rewrite the screen as a `LazyColumn` with a fully lazy photo wall | Structural refactor of the whole screen; A7 caps the cost instead. | Do it when the wall becomes a real browsing surface (i.e. alongside B4). |
| **B6** | Drop 「22:00 後有紀錄的天數」, or make the hour user-set | Even re-worded, it is the one metric with an implicit norm baked in. | Make the hour a setting, or drop it. A fixed 22:00 is someone else’s opinion of a late meal. |
| **B7** | A **rhythm chart** — first/last meal per day as bands across the period | New drawing code (hand-rolled Canvas; no chart lib in CMP), and it is the page’s next big feature, not a fix. | Do it. A band chart says 「這是你的模式」 better than any four numbers can. |
| **B8** | Make Week/Month drive the calendar too (one period control) | Changes the card’s information model; A6 disambiguates the current split instead. | Only alongside B4/B7, when the page is re-laid-out anyway. |

## Testing

- **Unit (`shared/src/commonTest`, `:shared:jvmTest`)** — `InsightsAggregatorTest`, fixed
  `TimeZone`: window classification (incl. `LONGER_WINDOW` naming), streak across gaps /
  off-window days / an empty in-progress today, `daysWithRecords`, `avgWindowMinutes` (skips
  single-meal days, `null` when none), rounding of first/last averages, 22:00 day counting,
  `monthSummary`, `hasAnyRecords`, `isToday`, `periodStart`/`periodEnd`, `isAnchorCurrentMonth`,
  week/month windowing.
- **Unit (`app/src/test`, `testDebugUnitTest`)** — `InsightsViewModelTest`: initial anchor is
  `now()` (not `0L`), `setPeriod`, backward paging, and forward paging clamped at the current
  month.
- **Static** — `:app:detekt`, `:app:lintDebug`.
- **Manual (emulator)** — empty account shows the gentle empty card, not zeroes; legend matches
  cell colours; today is ringed; next-month is disabled on the current month; both locales render
  every new string with no English leaking into `zh-rTW`.

## Verification note (2026-09-21)

This container has JDK 21 and a working Gradle wrapper, but **no Android SDK**, and the egress
proxy denies `dl.google.com` (Google Maven), so AGP/Compose/AndroidX cannot resolve. None of
`:shared:jvmTest`, `testDebugUnitTest`, `:app:detekt`, `:app:lintDebug` or `assembleDebug` could
be run here. The pure domain layer was instead compiled and its tests executed with a
standalone Kotlin 2.3.10 compiler + `kotlinx-datetime` fetched from Maven Central; the UI and
ViewModel changes are **unverified by build** and must be gated on CI before merge.
