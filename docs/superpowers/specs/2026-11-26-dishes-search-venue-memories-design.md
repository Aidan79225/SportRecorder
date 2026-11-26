# 吃了什麼、更好找、店家回顧、好吃的回憶 · Dishes, search, venue summary, memories

Status: design, implementing on branch `claude/diary-dishes-memories` (one PR). Four changes that make the diary
answer "what did I eat, where, and how was it?":

1. **Dishes** — an optional 「吃了什麼」 list on each meal.
2. **Search + taste filter** — the Record tab searches place, dish and note, and can show only meals with chosen faces.
3. **Venue summary** — when the Record list is narrowed to one place, a card says what that place has been for you.
4. **Memories** — 「N 年前的今天」 (already exists for one year ago; now up to five) and a new 「還記得這一餐嗎？」.

## Decisions

- **Dishes are plain text, stored on the meal.** `EatRecord.dishes: List<String>` (default empty). Room column
  `eat_time.dishes TEXT`, nullable, NULL = none (migration 11 → 12, `12.json` exported). Encoded one dish per line
  (`'\n'`): names are normalized so they never contain a newline. No separate table: every list, search and summary here
  already runs in memory over all records, so a table would buy nothing and cost a migration with a foreign key.
- **`Dishes` (pure, domain/model)**: `MAX_PER_MEAL = 10`, `MAX_LENGTH = 30`. `normalize(name)`: trim, any run of
  whitespace (incl. U+3000, newlines, tabs) → one space, drop other control characters, cut to 30 chars, blank → null.
  `sanitize(list)`: normalize each, drop blanks, drop duplicates case-insensitively (first spelling wins), keep the
  first 10. `encode(list): String?` / `decode(String?): List<String>` (decode sanitizes, so a hand-edited or future value
  never reaches the UI unclean). `suggestions(records, typed, chosen, limit = 6)`: distinct earlier dishes, most-used
  first then most recent, minus `chosen`, filtered by `typed` (case-insensitive contains); never shows counts.
- **Backup**: `BackupMeal.dishes: List<String> = emptyList()`, additive, `SCHEMA_VERSION` stays 2 (same reasoning as
  `tasteRating`: an older app ignores the field, so a restore there drops dishes; a bump would make it refuse the whole
  snapshot). Restore sanitizes.
- **Editor**: a 「吃了什麼 / What you ate」 row between Taste and Note: committed dishes as removable chips
  (content description "Remove <dish>" / 「移除<dish>」), a one-line "Add a dish" field (IME action Done adds), and
  up to 6 suggestion chips under it, shown only when there are any. Text still typed when the user taps CREATE/SAVE is
  committed as a dish — nothing typed is ever lost. Nothing is required; the row starts empty. Quick-record and Maps
  share never ask for dishes.
- **Cards**: the Record card (and the day sheet if it is a one-line change) shows dishes as one line `A · B · C`,
  max 2 lines.
- **Search** (`RecordSearch`, pure, domain/record): the text query keeps `RecordVenueFilter`'s rules (normalize, every
  word must match, case-insensitive, 臺/台 folded) but the haystack is `"<venue name> <venue address> <dishes> <note>"`.
  The optional taste filter is a `Set<Int>` of levels: when non-empty only records rated in the set remain (unrated
  never match). Order is kept; nothing is ranked or counted. `RecordVenueFilter.queryFor(venue)` stays as is (venue tap
  fills the field); accepted side effect: a note or dish containing a venue's words also matches that tap's query.
  `canSearch` is true when any record has a venue, a dish or a note, or while a query/filter is active. Field hint:
  "Search records (place, dish, note)" / 「搜尋紀錄（店家、品項、備註）」. A row of five face chips (word as the
  accessible name) appears under the field only when at least one record is rated; clearing search also clears it.
- **Venue summary** (`VenueSummary.of(textMatched: List<EatRecord>, query: String): VenueSummary?`): non-null only when
  the query is non-blank, the text-matched records all share one non-null venue id, and there are at least 2 of them.
  The text match only decides whether the user is looking at one place; visits, dishes and faces then count every meal at that place (before the taste filter), so searching a dish never understates the visits. Fields: venue,
  `visits`, `lastVisit` (newest `time`), `dishes: List<Pair<String, Int>>` top 5 by count then recency, `tasteCounts`
  (levels 5..1 with a non-zero count). Card at the top of the Record list: name + address, 「去過 N 次 · 最近 2026/11/18」,
  dish chips 「滷肉飯 ×3」, faces 「😋×2 🙂×1」 (screen-reader text spells the words).
- **Memories** (Insights, beside the existing On-this-day card, outside the period pager):
  - `OnThisDay.find` looks back 1 … 5 years and returns the nearest year that holds a day; `OnThisDayMemory.yearsAgo`
    drives the title: 「去年的今天」 / 「%1$d 年前的今天」, "A year ago today" / "%1$d years ago today".
  - `TastyMemory.pick(records, settings, now, timeZone)`: candidates are meals rated ≥ 4 whose eating day is at least 30
    days before today and that have something to show (photo, dish, venue name or note). One is chosen
    deterministically by local epoch-day modulo the candidate count (candidates sorted by time, then id), so the card
    is the same all day and changes the next. Returns the meal, its eating-day start (same mapping as On-this-day, so the
    day sheet it opens agrees), photo, venue name, dishes, level. Title 「還記得這一餐嗎？」 / "Remember this meal?";
    body `<venue> · <dishes>` (or the note); the face on the end; tap opens that day. If both memories would open the
    same day, only On-this-day is shown. Nothing is ever shown for low or unrated meals, and there is no notification.

## 初衷對照 / North-Star check

- **評價使用者**: dishes are not classified (no good/bad food, no calories). The venue summary reports what happened
  (times, dishes, the user's own faces) — no average score, no "favourite", no ranking between places, no comparison to
  other periods. Faces are the user's own words, only ever repeated back.
- **記錄變成負擔**: dishes are optional, the row starts empty, suggestions come from the user's own history and are one tap,
  typed text is never lost, quick-record/share are untouched. The face filter is hidden until the user has rated something.
  Drift risk: a visible empty row can read as "you forgot something" — it carries no hint text beyond the field's own
  placeholder and never blocks CREATE.
- **外在壓力取代覺察**: no streaks, counts of "days with dishes", reminders to fill dishes, or push for memories. The
  memory cards appear only when a memory exists and otherwise leave no trace (no "nothing a year ago" line).
- **教練式命令的語氣**: copy is a question or a statement ("還記得這一餐嗎？", "去過 3 次"), never an instruction.
- Borderline: "去過 N 次" and dish counts are numbers that could read as a habit verdict. They are shown as plain
  history on a place the user picked, never as a goal, and not at all for a single visit.

## Tests

Pure units: `Dishes` (normalize/sanitize/encode/decode/suggestions), `RecordSearch` (words, folding, haystack, taste set,
unrated, order), `VenueSummary` (thresholds, same venue, dish ranking, face counts), `OnThisDay` multi-year,
`TastyMemory` (threshold 4, 30-day gap, determinism per day, eating-day mapping, empty). Data: mapper round trip, backup
golden + older-snapshot decode + restore sanitizing, Room migration 11 → 12 and repository round trip (androidTest).
UI (androidTest + commonTest where the repo already tests that surface): editor chips add/remove/suggest/commit-on-save,
Record card dishes line, search by dish/note, face chips, venue summary card, memory cards. en + zh-TW strings, positional
`%1$d` args.

## Not in this change

Nutrition or calories, food recognition from photos, a separate venue screen, dish editing history, memory
notifications, memories on Home, dish categories/tags beyond free text.
