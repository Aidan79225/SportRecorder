# 店家：分店、地址、編輯與刪除、貼上地圖連結 · Venue branches, addresses, edit/delete, paste a Maps link

- **Date:** 2026-10-18
- **Status:** Designed, not implemented. Issue [#89](https://github.com/Aidan79225/SportRecorder/issues/89),
  plus owner requests made while designing it.
- **Builds on:** `2026-10-05-venue-design.md` (#83) and `2026-10-10-maps-share-design.md` (#84).
  Both still hold except where this document says otherwise.

## Why

Two things went wrong once real places started arriving from Google Maps:

1. **Chain stores share a name but are different stores.** Two 「麥當勞」 are two places. Today a
   shared 「麥當勞」 that is far from the saved one is treated as *the same venue that moved*, and
   the app asks to move it. For a chain that question is wrong: the user wants a second venue.
   Neither a name nor a distance can tell "different branch" from "same store, old position was
   wrong", so the user has to be asked — once, in words they can answer.
2. **Once two venues share a name, the list needs something else to tell them apart.** The owner
   decided: **the address**, shown for every venue.

The owner also asked for an **edit-venue dialog** and a **way to delete a venue**, and #89 asks for
**pasting a Maps link** from inside the app instead of only receiving a share.

## Decisions taken with the owner

| Question | Decision |
|---|---|
| Can two venues have the same name? | **Yes.** Branches of a chain are separate venues. No serial suffixes. |
| How are same-name venues told apart? | **By address**, shown under the name for **every** venue. |
| A shared/pasted place has the name of a saved venue but is far away | Ask: **another branch** / **same store, update its position** / **same store, keep its position**. |
| Editing a venue | A dialog: name, address, position actions, delete. |
| Deleting a venue | Confirm, stating how many records use it. Records stay; their venue becomes 「未設定」. |
| Paste a Maps link (#89) | A 「貼上地圖連結」 row at the top of the venue picker. |

The mockups the owner approved (picker list, edit dialog, delete confirm, the three-way question)
are described screen by screen below.

## Data model

`Venue` gains **`address: String?`** — a short, human-readable address such as 「信義路二段 194 號」,
or null when unknown.

- The **unique index on `venue.name` is dropped** (a non-unique index stays, for lookup).
  Room migration **8 → 9**: add the nullable `address` column; replace the unique name index with a
  non-unique one. Covered by the existing migration test pattern (`RoomMigrationTest`).
- `VenueName.normalize` / `sameAs` are unchanged: they still decide whether two names are *the same
  name*. They no longer imply *the same venue*.
- Identity is the **id**. Everywhere that found "the venue called X" must now say which one (see
  *Matching* and *Backup*).

## Addresses

- **Source:** reverse geocoding of the venue's position by the platform's own geocoder
  (Android `Geocoder`). A platform capability in the existing pattern: a `commonMain` interface
  (e.g. `AddressLookup`) and an `:app` implementation. iOS gets no implementation in this round
  (the iOS host app is skipped); `commonMain` must still compile for iOS.
- **When:** whenever a venue gains or changes its position (created from Maps, "use this record's
  position", "update from a Maps link", a typed venue adopting a record's fix) **and** whenever the
  picker shows a venue that has a position but no address yet (back-fills venues from #83/#84).
  Each lookup is a single best-effort request; a failure leaves the address null and is retried the
  next time the picker shows that venue. It never blocks or delays anything the user is doing.
- **Format:** road + number when the geocoder gives them (Taiwan: 「信義路二段 194 號」); otherwise the
  shortest useful line it returns. Device locale. Exact field choice is the builder's.
- **The user can edit it** in the edit dialog. A user-edited address is never overwritten by a later
  lookup unless the position itself changes (then the lookup runs again and replaces it — the old
  address described the old position).
- **Display:** a second, smaller line under the venue name in the picker. Venue with no position:
  「沒有位置」. Position but no address yet: the second line is simply absent.
- **Where else:** wherever names are listed together (the Insights map's venue list/accessibility
  text) and two listed names are the same, append the address in parentheses:
  「麥當勞（信義路二段）」. Elsewhere the name alone is shown, as today.

## Matching a shared or pasted place (replaces #84's *Matching* section)

Given the Maps name and (maybe) its pin, against all venues whose name `sameAs` it:

1. **None** → create a new venue with the Maps name and Maps pin (as in #84; never the device GPS).
2. **One within 50 m** of the pin (the nearest, if several) → use it.
3. **Otherwise, one with no position** (most recently used, if several) → use it and give it the pin.
4. **Otherwise** (all same-name venues are more than 50 m away) → select the **nearest** one and ask
   the three-way question:

   > **「麥當勞」是同一家嗎？**
   > 你存過的「麥當勞」在 信義路二段，這個連結在 忠孝東路四段，相距約 1.8 公里。
   > - **是另一家分店** → create a new venue (Maps name + pin), select it.
   > - **同一家，更新位置**（N 筆紀錄會跟著移動）→ move the existing venue to the pin.
   > - **同一家，位置不變** → keep it as it is, still selected.
   > Dismissing = 同一家，位置不變 (nothing is created or moved).

   The address line names the saved venue's address (or omits it when unknown); distance is
   rounded (m under 1 km, otherwise km with one decimal). The record count is stated only when
   N > 0.
5. **The link carried no pin** → steps 2–4 don't apply: use the most recently used same-name venue;
   if there is none, create one without a position.

This **replaces #84 Ruling 11** (a far venue with 0 records moved without asking): with branches, the
question is about *which store*, not only about history, so it is always asked. The existing
"answered once, survives process death" guarantee stays.

`SharedVenue.match` remains the pure, unit-tested home of this decision; its result type gains the
"ask which store" case in place of `AskToMove`.

## Typed names (unchanged, stated for clarity)

Typing in the picker's search box still finds existing venues by name; 「使用「X」」 is still only
offered for a name that no venue has. A second branch arrives through Maps, or by renaming in the
edit dialog — not by typing a duplicate.

## Paste a Maps link (#89)

- The picker's first row is **「📋 貼上地圖連結」**.
- Tapping it reads the clipboard **once, because the user tapped** (never on its own). If the
  clipboard text contains a Maps link (`MapsLink.find`), the picker closes and the editor runs the
  **exact same path as a share**: 「正在讀取地圖連結…」 in the venue row, the same matching, the same
  inline failure line, the same "the user's own choice wins" and "save wins" rules.
- If the clipboard has no Maps link, a small dialog opens with a text field (empty) to paste into,
  and a confirm button; confirming runs the same path. Its failure lines are the existing ones.
- Works in **create and edit mode**. In edit mode it changes only the record's venue; the record's
  own `location` is never touched (the #83 two-facts rule).
- Network rules are #84's, unchanged: only `maps.app.goo.gl` / `goo.gl` contacted, one request,
  5 s, no redirects.

## Edit dialog

Opened from a **✎ button on each picker row** (it replaces today's ⋮ overflow menu; tapping the row
itself still selects the venue).

- **店名** — text field. **地址** — text field (prefilled from the lookup, editable, may be cleared).
- A line with the position and how many records use the venue: 「位置：25.0335, 121.5301 · 12 筆紀錄」,
  or 「沒有位置」.
- **用這筆紀錄的位置更新** — today's overflow action, moved here; only offered when the record has a fix.
- **貼上地圖連結更新** — reads the clipboard (same rule as above; falls back to the paste dialog), takes
  **only the pin** from the link (never the name) and moves this venue there. Moving a venue with
  records asks first, with the count, as today.
- **刪除** (bottom-left, destructive colour) → the delete confirmation.
- **取消 / 儲存.** Saving with a blank name is refused, as rename is today.
- **Renaming onto a name another venue already has** no longer merges silently-by-confirmation only:
  the existing merge dialog gains a choice — **合併成同一家**（N 筆紀錄會移過去）/ **兩家都保留**.
  Keeping both just saves the new name.

## Delete

> **要刪除「麥當勞」嗎？**
> 有 12 筆紀錄用了這家店。刪除後，這些紀錄還在，只是店家會變成「未設定」。
> 取消 · **刪除**

With 0 records the second sentence is omitted. Deleting clears `venue_id` on its records and deletes
the venue in one transaction. If the record open in the editor had that venue, its venue becomes
「未設定」.

## Backup

Names are no longer a unique key, and the backup references venues **by name** today, so:

- Each backup venue gains a **`key`** (its id at backup time, as a string) and each meal a
  **`venueKey`**. Restore maps meals to venues by key; a snapshot without keys (written before this
  change) still maps by name, as today. `address` is backed up too.
- **`BackupDocument.SCHEMA_VERSION` goes 1 → 2.** An older app restoring a new snapshot would insert
  two same-name venues into its unique index and fail mid-restore; with the bump it refuses the
  snapshot cleanly with its existing "made by a newer version" path. Restore of version-1 snapshots
  keeps working.

## Privacy and Play

Reverse geocoding sends a venue's position to the platform geocoder (Google on Android). It happens
only for positions the user's venues already have, never the user's live location on its own, and
it is a system service rather than a new third-party destination. It still belongs in the Play
data-safety review together with #84's `maps.app.goo.gl` — the owner's call in the Console.

## Out of scope

iOS address lookup and iOS share/paste; editing a venue's position by dragging a map pin; merging
from the edit dialog other than by rename; undo after delete.

## Testing

- **Pure, `commonTest`:** the new matching table (none / within 50 m / nearest of several / no-position
  fallback / all far → ask with the nearest / no pin); distance wording.
- **Repository / migration, androidTest:** 8 → 9 migration keeps rows and allows two same-name venues;
  delete clears `venue_id` in one transaction; rename-onto-existing both ways.
- **Backup, JVM tests:** round-trip with two same-name venues keeps each meal on its own branch;
  a version-1 snapshot still restores by name.
- **Editor VM, `app/src/test`:** each answer of the three-way question; paste from clipboard (link /
  no link → dialog); paste in edit mode leaves `location` alone; address lookup success, failure and
  "user-edited address survives until the position changes"; delete of the selected venue.
- **UI:** the picker row (name + address / 「沒有位置」), ✎ opens the dialog, delete confirm text with
  and without records.

## 初衷對照 / North-Star check

- ✅ **記錄要快** — pasting a link from a chat now takes one tap from the picker instead of a round
  trip through another app; the three-way question replaces a question that was wrong for chains.
- ✅ **Seeing your own places clearly** — two 「麥當勞」 are now two places with their own history and
  map position, which is what actually happened.
- ⚠️ **Drift risk — the app silently rewriting history.** Moving a venue still asks, with the count;
  delete asks, with the count, and never deletes a meal.
- ⚠️ **Drift risk — more questions in the capture flow.** The three-way question appears only when a
  shared name matches a saved venue that is more than 50 m away; within 50 m nothing is asked.
- ⚠️ **The app reaching out on its own.** Address lookups run in the background for venues the user
  already has. They send a place's position, not the user's live location, and they fail quietly.
