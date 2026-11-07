# 紀錄頁的店家、店家搜尋，與用餐時間 · Venues on the record page, venue search, and meal duration

- **Date:** 2026-11-07
- **Status:** Designed, not implemented. Requested by the owner on 2026-11-07; "design on the simplest basis".
- **Builds on:** `2026-10-05-venue-design.md` (#83), `2026-10-18-venue-branches-design.md` (#89) — the
  venues and their addresses already exist and are already loaded with every record — and the pure
  `DietWindow` calculator.

## Goal

Three small changes, each the simplest thing that works:

1. **The Record tab shows each record's venue** (name and address). Today it shows nothing about it.
2. **The Record tab can be searched by venue**, so "what did I eat at this place before?" is a few
   keystrokes — in the list the user already knows, no new screen.
3. **A meal has a length.** Each record gets a duration, **1 hour by default, adjustable**, and the
   fast clock starts when the last meal **ends**. Capturing a meal still costs nothing extra.

## Decisions taken with the owner

| Question | Decision |
|---|---|
| A venue list page, or a filter on the record list? | The owner offered both and asked for the simplest: **a search field on the Record tab.** No venues page. |
| Should the fast clock follow the meal length? | **Yes — the fast starts when the last meal ends** (answered 2026-11-07). It replaces the hard-coded "+1 h for a single-meal window". |
| Default duration | **1 hour**; the owner asked for exactly this. Existing records read as 1 hour too. |
| How it ships | Two PRs: **Part A** (items 1–2, screen only, no data change) first; **Part B** (item 3: data + fast clock) stacked on it. |

## Part A — the Record tab

### A1. The venue on the card

- Under the time line, when the record has a venue: the venue's **name**, and under it — if it has
  one — its **address** in smaller, secondary-colour text. A record without a venue shows nothing
  extra.
- The existing "📍 lat, lng" line stays: it is where the user *was*, a different fact from where the
  food came from (#83's two-facts rule).

### A2. Search

- One single-line field above the list, 「搜尋店家（名稱或地址）」, with a ✕ that clears it. It is
  **shown only when at least one record has a venue**, so people who never use venues see no change.
- **Matching** is a pure function (`RecordVenueFilter`). The query is whitespace-normalised
  (`VenueName.normalize`, so full-width spaces work) and split into words; a record matches when
  **every word** occurs, case-insensitively, in `"<venue name> <venue address>"`. A blank query keeps
  every record. A record with no venue never matches a non-blank query. The order (newest first) is
  never changed. **臺 and 台 count as the same character** on both sides (Google writes addresses
  with 臺, people type 台), so 「台北」 finds 「臺北市…」.
  - 「麥當勞 信義」 shows only the branch whose address contains 「信義」 — the way to tell same-name
    branches apart without a new concept.
- **Tapping the venue on a card** puts `"<name> <address>"` (just the name when there is no address)
  into the field. Because the address words must match too, that narrows to that venue's records even
  when another branch shares its name. (Rare edge: a venue with no address matches by name only, and
  a sibling whose address contains all of this venue's address words also matches.)
- When nothing matches: one gentle line under the field, 「沒有符合的紀錄」.
- The query lives in the Record tab's ViewModel only. It is not saved, and it resets when the user
  leaves the tab.
- **No counts, no ranking, no "most visited".** The list is the answer.

## Part B — meal duration

### B1. Data

- `EatRecord.durationMinutes: Int`, default **60**, added as the **last** parameter so every existing
  construction still compiles; `val endTime: Long` = `time + durationMinutes` minutes. The default and
  the menu's choices live in one pure `MealDuration` object.
- **Room 9 → 10:** add `duration_minutes INTEGER NOT NULL DEFAULT 60` to `eat_time`. Every existing
  meal therefore reads as 1 hour — the same length the app has always assumed for a one-meal day.
- **Backup:** `BackupMeal.durationMinutes: Int? = null`. It is additive, so `SCHEMA_VERSION` does
  **not** change: an older app restoring a newer snapshot ignores the field, and a snapshot without it
  restores as 60 minutes.
- Every way of recording — the home button, the quick-settings tile, a Maps share, a pasted link —
  produces 60 without being touched, because the default lives in the domain type.

### B2. The editor

- A third row under *Time*, 「用餐時間」, styled like the Date and Time rows: the current value and a
  ▾. Tapping opens a menu: **15 分鐘 · 30 分鐘 · 45 分鐘 · 1 小時 · 1 小時 30 分鐘 · 2 小時 · 3 小時**.
- It already says **1 小時**, so recording a meal needs **no extra tap**.
- Editing an old record shows its stored value. A stored value that is not in the menu is displayed
  correctly and kept until the user chooses another.
- No other validation: meals may overlap — the app records, it does not judge.

### B3. Display

- Record card: **「12:30 – 13:30」** instead of just the start (local time, clock times only; a meal
  that ends after midnight shows 「23:30 – 00:30」).
- **Unchanged:** Insights and the single-day sheet read **start times only**. A duration never feeds a
  statistic or a verdict.

### B4. The fast clock

- `DietWindow` takes each meal as a start and an end. Meals are still grouped into eating days by
  **start** time (`groupIntoWindows` is unchanged).
- `fastStartAt` = the **latest end** in the current window (not the end of the last-*started* meal:
  a long meal followed by a quick drink ends when the long meal does).
- `windowEnd = max(first start + eatingHours, latest end)`.
- The hard-coded 1-hour grace for a single-meal window is **deleted**. At the 1-hour default a one-meal
  day behaves exactly as before; a different duration now moves the fast start, as it should.
- **A visible change, by design:** on a day with two or more meals the fast starts later than before
  by the last meal's duration (1 hour at the default), because the last meal now has a length.
- Used by Home and by the two reminders (window closing, fast complete).

## Out of scope

A venues page; visit counts or "most visited"; duration in Insights or the single-day sheet; a setting
for the default; changing the duration from the record card; durations finer than the menu.

## Testing

- **Pure, `commonTest` (JVM + iOS):** `RecordVenueFilter` (blank query, case, address, every word,
  same-name branches, no venue, order); `MealDuration`; the new `DietWindow` rules (one meal at the
  default is unchanged; a different duration moves the start; two meals; overlapping meals; the window
  end; midnight), `ReminderPlanner`.
- **ViewModels, `app/src/test`:** the Record tab's query / visible records / `hasVenues`; the editor's
  default, change, load and save of the duration; a quick record lasts 1 hour; the Home ViewModel.
- **Room, androidTest:** a 9 → 10 migration keeps every meal and reads it as 60; the repository
  round-trips a duration.
- **Backup:** a duration round-trips; a snapshot without one restores as 60; the golden fixtures are
  updated deliberately.
- **UI, androidTest:** the card shows venue and time range; tapping the venue fills the search; the
  search is hidden without venues; the editor's duration row defaults to 1 小時 and changes.

## 初衷對照 / North-Star check

- ✅ **記錄要快、要輕鬆** — the duration row is pre-set, so a meal is recorded exactly as fast as before;
  the quick tile and the Maps share are untouched.
- ✅ **看見自己，不是被督促** — the search lets people look back at where they ate; it shows no counts,
  no ranking, no "you went here 12 times".
- ✅ **更貼近真實** — a meal really takes time, and the fast clock now starts when eating stops,
  instead of a fixed guess.
- ⚠️ **Drift risk — a duration read as a verdict** ("you eat too fast / too slowly"). Nothing evaluates
  it: Insights ignores it and no statistic uses it.
- ⚠️ **Drift risk — the fast clock reads as shorter or longer.** On multi-meal days the fast now
  starts later than before. It is arithmetic on what the user recorded, and the owner chose it, but it
  is a visible change to the core clock and is called out in the release notes.
- ⚠️ **Old records read as 1 hour** although the user never said so. It is the length the app already
  assumed, so no clock changes for one-meal days; the user can edit any record.
