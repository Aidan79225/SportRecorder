# 每餐的好吃程度 · Rating how a meal tasted

- **Date:** 2026-11-18
- **Status:** Implemented in PR #101 (`claude/meal-rating`). Requested by the owner on 2026-11-18: a self-rating for each
  meal, to record "how good it tasted, in the moment"; empty by default and optional; "1–5 maybe, or what do you
  recommend?".
- **Builds on:** `2026-11-07-record-venues-and-meal-duration-design.md` — the same shape of change (one new field on a
  meal: Room column, backup, editor row, Record card), except that this field has no default value.

## Goal

One tap in the meal editor says how the meal tasted. **Nothing is asked for:** a new meal has no rating, the
quick-record tile and every other way of recording leave it empty, and a rated meal can go back to unrated.

## Decisions

| Question | Decision | Why |
|---|---|---|
| Scale | **Five levels, stored as 1–5.** The number is never shown on screen. | It is the owner's 1–5, and 3 is a real middle. A stored integer keeps the picture below changeable without touching data. |
| What the user sees | **Five faces** 😞 🙁 😐 🙂 😋, each with a word | A face says *how it felt*; stars and numbers say *score* — the grading feel the mission avoids. The words fix what each face means, for a screen reader too. |
| The words | 不好吃 · 不太好吃 · 還可以 · 好吃 · 非常好吃 (Not tasty · Not very tasty · Okay · Tasty · Very tasty) | One adjective, taste only — the same plain register as the rest of the editor. 還可以 is the middle ("it was fine"), where 普通 reads as "ordinary" and so as a mild verdict. |
| What it is about | **Taste only.** The row is 「好吃程度」 / "Taste" — not quality, not health | It records a sensation. It must never read as a verdict on a food (see the North-Star check). |
| Default, required | Empty, never required. `null` means "not rated" and is **not** the middle value. | The owner asked for it. |
| Where in the editor | A row under Duration, above Note | Visible without scrolling in a quick capture, and in the order of the thought: when, how long, how was it, anything to add. |
| Changing it | Tap another face; **✕ clears it**, like the Place row's ✕ | A mis-tap must be undoable, and "tap again to clear" has no label for a screen reader. |
| Where it shows | **The chosen face beside the time on Record cards**; nothing at all when unrated | The Record list is where the diary is read back. No word, no number on the card. |
| Insights, the day sheet | **Unchanged** — they show no rating | The owner asked for recording. Any later use must stay a neutral reflection (below). |
| Colour | The app adds none: no green/red, no per-level tint | The faces are the system's emoji, so each device draws them in its own style (all five are Unicode 7 or older, so the minSdk 24 floor has them); the only thing the app styles is a soft container on the chosen one in the editor. |

## What users see

**The editor** gets a row between Duration and Note:

```
好吃程度                                  非常好吃   <- the word joins the label's line once a face is chosen
 😞    🙁    😐    🙂    😋                  ✕       <- five faces share the width; the ✕ appears at the end
```

- The label reads 「好吃程度」 (English "Taste"). With nothing chosen it stands alone: no "Not set", no hint, nothing
  that reads as an empty field.
- **Layout.** Two lines, because the editor is one scrolling column with the confirm button at its end, and a taller
  row here pushes that button off a phone-sized screen (the first, two-line-plus-header version did, on a 2400 px
  emulator; `BottomSheetHostTest` pins it). The word shares the label's line, and the ✕ has a fixed 48 dp slot at the
  end of the faces' line whether or not it shows, so choosing a face changes no height and moves no face. (The one
  exception is the largest font on the narrowest phones: the word wraps rather than clips, and a word that no longer
  fits on the label's line takes a second one, which moves everything below it, the faces included, down by a line.)
  The faces share the width the slot leaves, as a type role (`headlineMedium`, so they follow the user's font setting)
  inside a 48 dp container, the chosen one on a soft fill: they are 48 dp targets or wider from a 360 dp phone up
  (54 dp at 360), and about 46 dp on a 320 dp one (the sheet's 20 dp padding and the ✕ slot come off the width, the
  rest is shared five ways) rather than running off the sheet.
- **Screen reader.** The label and the chosen word are one stop ("Taste, Very tasty"). The faces are one selectable
  group of radio buttons, each described by its word ("Tasty, radio button, selected"); the emoji inside is hidden
  from the reader so a face is not heard twice. The group sets no count or position of its own, but on Android
  Compose derives them from a selectable group, so TalkBack may add the platform's standard "4 of 5" to a face, as
  it does for any radio group: the face's place in the row, never shown on screen, while the words carry the
  meaning. That is kept on purpose (it is the usual orientation in a group of five); if it ever reads as a score,
  `semantics { collectionInfo = CollectionInfo(-1, -1) }` on the group removes it, to be checked with TalkBack and
  VoiceOver. ✕ is its own button after the faces, described 「清除好吃程度」 / "Clear taste" (verb + object, like
  清除位置); its touch area is 48 dp (the icon button draws 40).

**Record cards** show the face right after the time (`12:30 – 13:30  🙂`), at the body size. It is described
「好吃程度：好吃」 / "Taste: Tasty" and the emoji is not a text stop of its own. A meal without a rating looks exactly
as it does today.

## Data

- **Room 10 → 11:** `ALTER TABLE eat_time ADD COLUMN taste_rating INTEGER` — nullable, no default, so every earlier
  meal is unrated. The entity's column has no `defaultValue` to match, and the exported schema `11.json` is committed.
- **Domain:** `EatRecord.tasteRating: Int?` is the last parameter (default `null`), so every existing construction
  compiles. A pure `TasteRating` object holds the range (1–5) and `sanitize`, which turns anything outside it into
  `null`: whatever a corrupt row or a hand-edited backup holds, the domain never carries a value off the scale.
- **Backup:** `BackupMeal.tasteRating: Int? = null`, written always (`encodeDefaults`), **no `SCHEMA_VERSION` bump**
  — the additive route that venues and durations took. A snapshot without the field, or with an out-of-range integer,
  restores as unrated. (A value that is not an integer at all, in a hand-edited file, fails the decode like any other
  malformed field, and `durationMinutes` does the same.)
  - *An older app* ignores the field, so restoring on it loses the ratings, **and the backups it writes carry none**.
    With two devices on one Drive account, the older device's daily backups slowly push the newer one's rated
    snapshots out of the seven that are kept (`KEEP_LAST = 7`). Accepted: it ends when both are updated, and a bump
    would make the older app refuse the whole snapshot — a worse failure for one optional field.
- **Writers:** every write goes through `EatRecord.toEntity`, and the editor keeps the loaded rating when only the
  note is edited (as it does the duration), so no path silently clears a rating. The venue SQL touches `venue_id`
  only.
- **Editor state** lives in the view model's memory like the note and duration: it survives a rotation, not process
  death. In edit mode the record loads asynchronously and fills the fields, so a face tapped in the instant before
  the load lands is overwritten — the same behaviour the note and duration already have.

## 初衷對照 / North-Star check

- **Does it serve the mission?** Yes. Noticing in the moment that a meal was a delight, or a letdown, is the kind of
  thing nobody can see later without a record — 「留住每一個美好的當下」, and 自我覺察 (did I enjoy what I ate?). The user
  rates their own meal; the app neither asks for it nor interprets it.
- **評價使用者 — the one to watch.** A rating beside a meal can turn into a good-food / bad-food label, or a grade for
  the user's day. So: it is about taste only (never health or "quality"); every face gets the same treatment (no
  green/red); no number is shown; there are no averages, no best/worst lists, no scores; nothing else is derived
  from it. **The rating is blind everywhere it could leak:** the Record search and venue filter match and order
  records exactly as before (a rated and an unrated meal are indistinguishable to them), the Insights aggregates are
  identical with or without ratings, and "On this day" picks the same meal either way — none of them chooses, skips
  or ranks by rating. Tests pin each of these, so a later change cannot start to depend on it unnoticed. The words
  describe how the food tasted *to the user* (不好吃 is a plain fact about a meal, not a failing).
- **記錄變成負擔 — none.** Optional and empty by default, with no prompt, reminder or "you haven't rated" cue. The
  quick-record tile and the default editor path cost no extra tap; setting it is one tap. *The tension:* the row is
  always in the editor, which is a standing, quiet "you could rate this" — an opinion, not a nag: it is a label in the
  secondary colour with no required mark, no count and no empty-state wording.
- **外在壓力 — none.** No count of rated meals, no streak, no badge.
- **教練式命令的語氣 — none.** The label is a noun; nothing in the copy is an instruction.
- **Why faces, not stars.** Stars are how a product is reviewed: they invite scoring, and 5/5 reads as "best" and 1–2
  as "failed". Faces running from unpleasant to delightful let the user record an honest 「沒有很好吃」 — which is the
  awareness (a meal eaten without enjoying it) rather than a grade.
- **Why five, not three.** Three faces (😞 😐 🙂) would be even less score-like, and were weighed. Five were kept
  because the owner suggested 1–5 and the two shades on each side say more about *how much* it was enjoyed. If
  five proves to be too fine a choice, shrinking is a read-side change (1–2 → 1, 3 → 2, 4–5 → 3): the stored
  integers stay and no migration is needed.
- **Why rating is editor-only for now.** A face to tap on each Record card would put a "rate this" target on every
  unrated card — a standing cue, the nag the mission avoids. So rating happens in the editor, one deliberate tap
  away. If ratings turn out rarely filled in, that is the thing to revisit, not something to chase with prompts.
- **Where it could drift: Insights.** "Your top-rated places" is a ranking of what the user chose — judgment. If the
  owner later wants Insights to use ratings, keep it a neutral reflection ("meals you marked 非常好吃 were mostly at
  …"; the face on a map marker), never top/bottom lists, scores or averages. Not part of this change.

## Tests

- `TasteRating`: the range, `sanitize` (in range kept; 0, 6, -1 and null → null). `TasteFace`: the exact face and
  word key of each of the five levels, in order (an exact list, because nothing else can notice a swap: a screen
  reader never sees the picture), and no look for null or an off-scale value.
- Mappers: entity ↔ domain carry the rating, and an out-of-range stored value reads as unrated. Backup mappers:
  the rating is written; an absent or out-of-range one restores as unrated. `BackupDocumentGoldenTest` keeps every
  old fixture decoding and gains the new shape; a service-level restore keeps the rating, the schema version stays 2,
  and a snapshot without ratings restores unrated.
- Editor view model: a new meal has none; choosing, changing and clearing; edit mode loads it; save stores it; an
  edit of the note alone keeps it; an out-of-range choice is ignored. The other ways of recording leave it empty: the
  quick-record use case, a shared Maps link and a pasted one each save an unrated meal, and a paste into an existing
  meal keeps the rating it already has.
- Rating-blindness: `InsightsAggregator`, `RecordVenueFilter` and `OnThisDay` give the same result for a rated and
  an unrated copy of the same meals.
- Instrumented (API 35 emulator): Room 10 → 11 migration keeps every meal, unrated; the repository and a backup
  round trip on real Room keep the rating, and so do the venue queries (deleting a venue, merging two); the editor's
  five faces (nothing chosen by default, one radio group described by words, no separate emoji stop, a tap reports
  its level, a tap on the chosen face chooses it again, the chosen face is selected with its word beside the label,
  ✕ takes it back, choosing moves nothing, a 360 dp phone at the largest font keeps every face ≥ 48 dp and the ✕'s
  touch area 48 dp, a 320 dp one keeps every face ≥ 44 dp and everything on the sheet); the editor's confirm button
  is still on screen (`BottomSheetHostTest`); the Record card shows the face after the time only when rated, in
  English and in Chinese. (Kotlin's `assert()` checks nothing on Android, so the instrumented tests use JUnit's.)
- Release smoke (R8), run on the API 35 emulator: the previous release (master, minified) was upgraded in place by the
  new one. The meal made before the upgrade was still there (the 10 → 11 migration ran on the minified build); the
  editor showed the five words (Compose Resources survive R8); a chosen face showed on the Record card as "Taste: Very
  tasty" and survived a force-stop and relaunch; ✕ then Save wrote it unrated again; a 1,500-event `monkey` run left
  the crash log empty. Room entities and the Compose Resources strings are reached by name, so a green
  `minifyReleaseWithR8` alone is not proof (`2026-11-11-release-r8-design.md`). Not run: Drive backup → restore with
  ratings (it needs a Google sign-in), and TalkBack.

## Not in this change

- Anything in Insights, or the day sheet, that shows or uses the ratings (see the drift note).
- Rating from the Record list without opening the editor (see "Why rating is editor-only for now").
- Showing the word, not just the face, on the card.
