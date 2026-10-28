# 店家：分店、地址、編輯與刪除、貼上地圖連結 · Venue branches, addresses, edit/delete, paste a Maps link

- **Date:** 2026-10-18
- **Status:** Implemented on `claude/venue-branches-89` (PR pending).
  Issue [#89](https://github.com/Aidan79225/SportRecorder/issues/89), plus owner requests made while designing it.
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
- **The user can edit it** in the edit dialog. One rule keeps this simple *(builder's call)*: **a
  lookup only ever writes into an empty address, and a position change empties it.** So a typed
  address stays until the position moves (then it is cleared and looked up again — the old address
  described the old position), and an address the user clears is filled in again by the next lookup
  (accepted; there is no "deliberately blank" state). A lookup result is written only if the venue
  still has the position that was looked up and its address is still empty (a conditional update),
  so a slow lookup can never overwrite an edit or describe a stale position.
- **Display:** a second, smaller line under the venue name in the picker: the address when there is
  one (even without a position); 「沒有位置」 when there is neither; nothing while a venue with a
  position waits for its lookup *(builder's call)*.
- **Where else:** wherever names are listed together (the Insights map's venue list/accessibility
  text) and two listed names are the same, append the stored address in parentheses:
  「麥當勞（信義路二段 194 號）」; no parenthesis when the address is null or the same for both
  *(builder's call)*. Elsewhere the name alone is shown, as today.

## Matching a shared or pasted place (replaces #84's *Matching* section)

Given the Maps name and (maybe) its pin, against all venues whose name `sameAs` it:

1. **None** → create a new venue with the Maps name and Maps pin (as in #84; never the device GPS).
2. **One within 50 m** of the pin (the nearest, if several) → use it.
3. **Otherwise, one with no position** (most recently used, if several) → use it and give it the pin.
4. **Otherwise** (all same-name venues are more than 50 m away) → select the **nearest** one and ask
   the three-way question:

   > **「麥當勞」是同一家嗎？**
   > 你存過的「麥當勞」在 信義路二段，和這個連結的位置相距約 1.8 公里。
   > - **是另一家分店** → create a new venue (Maps name + pin), select it.
   > - **同一家，更新位置**（N 筆紀錄會跟著移動）→ move the existing venue to the pin.
   > - **同一家，位置不變** → keep it as it is, still selected.
   > Dismissing = 同一家，位置不變 (nothing is created or moved).

   The question shows at once — the link's own address is **not** looked up for it, so no network
   call sits in front of the dialog *(builder's call)*. It names the saved venue's address (「在
   信義路二段」 is omitted when unknown); distance is
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
- *(builder's call)* A paste is the user's own act, so it **replaces** whatever venue is selected and
  cancels any share or earlier paste still resolving. Each paste has its own "already answered"
  state. A paste is **not** replayed after process death (unlike a share, whose text lives in the
  route): it is gone, and the user can paste again.
- Network rules are #84's, unchanged: only `maps.app.goo.gl` / `goo.gl` contacted, one request,
  5 s, no redirects.

## Edit dialog

Opened from a **✎ button on each picker row** (it replaces today's ⋮ overflow menu; tapping the row
itself still selects the venue).

- **店名** — text field. **地址** — text field (prefilled from the lookup, editable, may be cleared).
- A line with the position and how many records use the venue: 「位置：25.0335, 121.5301 · 12 筆紀錄」,
  or 「沒有位置」.
- *(builder's call)* **Name and address are drafts saved by 儲存; the two position actions apply
  immediately** (as today's overflow action does) and update the dialog's position line. 取消 does
  not undo a position change.
- **用這筆紀錄的位置更新** — today's overflow action, moved here; only offered when the record has a fix.
- **貼上地圖連結更新** — reads the clipboard (same rule as above; falls back to the paste dialog), takes
  **only the pin** from the link (never the name) and moves this venue there. Moving a venue with
  records asks first, with the count, as today.
- **刪除** (bottom-left, destructive colour) → the delete confirmation.
- **取消 / 儲存.** Saving with a blank name is refused, as rename is today.
- **Renaming onto a name another venue already has** no longer only merges: the existing merge
  dialog gains a choice — **合併成同一家**（N 筆紀錄會移過去）/ **兩家都保留**. Keeping both just saves
  the new name. *(builder's call)* When several venues have that name, the merge target is the
  nearest one to this venue (when both have positions), otherwise the most recently used; the dialog
  names it with its address. The survivor keeps its own name, address and position. Saving with a
  name that `sameAs` the venue's current name never opens this dialog.

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

## Addendum (2026-10-28): links made by the Google Maps app

Bug report from the owner after 0.14.0: 「貼上連結不會把店名和地址正確填入欄位」 and 「無法使用連結更新店家」.

**Cause (reproduced on the emulator with the real Maps app, and with curl).** #84 was built on a link
made on the Maps *website*, whose resolved URL carries `/place/<name>/` and a `!3d!4d` pin. Links made
by the Maps **apps** do not:

- **Android app** (Share → 複製到剪貼簿, or share to SportRecorder; `…?g_st=ac`) →
  `https://www.google.com/maps/place/<ADDRESS and NAME glued>/data=!4m2!3m1!1s<ftid>?utm_source=mstt_1&entry=gps…`
  — **no pin, no camera**. In zh-TW the segment is the address glued to the name with no separator:
  `106台灣臺北市大安區福住里信義路二段194號鼎泰豐 信義店`. In en-US it is name first, comma-separated:
  `Din Tai Fung Xinyi Branch, No. 194號, Section 2, Xinyi Rd, …, Taipei City, Taiwan 106`.
- **iOS app** → pathless `https://maps.google.com?q=<address and name>&ftid=…&entry=gps`.

So today a paste creates a venue named by the whole glued string, with no position and no address, and
「貼上地圖連結更新」 always says it cannot read the link.

### Decisions taken with the owner (2026-10-28)

| Question | Decision |
|---|---|
| 「貼上地圖連結更新」 in ✎ | Fills **店名 and 地址** from the link, not only the pin. |
| Which address a venue keeps when it comes from a Maps link | **The link's own address, trimmed** of the leading postcode and country (「臺北市大安區福住里信義路二段194號」). |
| Position for a pinless link | **Look up the link's address with the phone's own geocoder** (forward geocoding). |
| Venues already created with glued names | **Repair them automatically.** |

### Reading a link (amends *Parsing rules* of #84)

- `MapsPlace` gains `address: String?`.
- A pinless `/place/` segment is split into name and address by a pure helper, **only when it is
  confident**; otherwise the whole segment stays the name (today's behaviour — never worse):
  - **Taiwanese address first** (zh): `[postcode][台灣|臺灣]<…>號[之N][N樓|…樓]` followed by the name.
  - **Name first** (en): `Name, <address>` where the address part is clearly an address (e.g. starts
    with `No.` or ends with the country/postcode). The implementer pins the exact rules with the
    real samples recorded during diagnosis; any shape they don't cover is left whole.
  - A web-made link (with a pin) is never split: its `/place/` name may legitimately contain commas.
- iOS `maps.google.com` / `www.google.com` with path `""`, `/` or `/maps` and a `q=` parameter is
  accepted (exact-host allowlist unchanged); `q` is decoded and split the same way. `ftid` is ignored.
- `/maps/search/<lat>,<lng>` and `q=<lat>,<lng>` give a pin with no name; a `/place/` segment that is
  only coordinates is no name.
- **Trimmed address** for storage: zh drops the leading postcode and 台灣/臺灣; en drops a trailing
  `, Taiwan <postcode>` (generally: a trailing country/postcode the split recognised).
- **Position:** when the link has no pin, the place's position is looked up by forward geocoding —
  the full (untrimmed) address when the split found one, else the whole segment — through a new
  `AddressLookup.positionOf(query)` (same platform service as the reverse lookup, 10 s bound, never
  throws). No second request to Google is made; the #84 network rule is unchanged. If the lookup fails,
  the place has a name and address but no position (the existing "name, no coordinates" Failure row).
- *(builder's call)* A title longer than 300 characters (normalized) is neither split nor looked up:
  every real one is under 200, and the split's patterns backtrack quadratically on crafted text while
  a shared link is parsed on the main thread. Such a title stays whole and has no position.

### Using a read place

- **Paste / share:** matching is unchanged (now on the clean name). A created venue gets the link's
  trimmed address; a used venue with no address gets it; "same store, move" writes it with the pin.
  The reverse lookup still fills an address only when the venue has none.
  *(builder's call)* "No venue by that name" still creates through `findOrCreate` (now taking the
  address), whose name check and insert are one transaction: a same-name venue another writer added
  after the match read the venues (the glued-name repair at app start, a second editor) is reused, and
  takes the link's address only if it has none. Only "another branch" always creates.
- *(builder's call)* The move question no longer says the records move to "where Google Maps puts it":
  for a pinless link the position is the phone geocoder's estimate. It now reads
  「有 %1$d 筆過去的紀錄會移到這個連結的位置。」 / "… will move to this link's place."
- **「貼上地圖連結更新」 in ✎:** the pin moves as today (asking first when the venue has records), and
  the link's **trimmed address is written with the move** (an address belongs to its position). The
  link's **name fills the 店名 draft**; 儲存 keeps it, 取消 keeps the old name *(builder's call: the
  address follows the position, so 取消 does not restore the old address once the pin has moved)*.
  If the link has no usable position, the name and address fill the drafts and a gentle line says the
  position could not be found; nothing moves.
- *(builder's call)* In ✎, a title the split left whole never fills 店名: it may be the address glued to
  the name (any place outside Taiwan, e.g. the real Korean link), and before this addendum ✎ never
  touched the name, so "never worse" means leaving the user's own. The pin still moves when the whole
  title can be looked up; when it can't, nothing is filled and the line says nothing was changed
  (「找不到這個地點的位置，店家資料沒有更動。」). Paste and share still name a new venue by the whole title.
- *(builder's call)* While a link is being read in ✎, 儲存 is disabled (取消 still cancels the read),
  so a save can no longer silently drop the move.
- *(builder's call)* The editor's selected venue is refreshed from the database by id, so a filled
  address or moved position is never shown stale.
- *(builder's call)* The repair below merges in the background while a meal at that venue may be
  open. `VenueRepository.resolve(id)` follows a merge made since the app started to the survivor, and
  the editor uses it when its venue leaves the list and again right before saving. A meal is then
  saved at the venue its records moved to, and never with the id of a deleted venue.

### Repairing glued venues

Once per app start, in the background: every venue whose **name** the confident split recognises as
"address + name" in the exact Maps shape is renamed to the name and given the trimmed address; its
position is looked up from the address if it has none. If, after that, another venue with the same
name lies within 50 m, the repaired one is **merged into it** (records move, nothing is lost);
otherwise both are kept (branches). The split's confidence rule is what keeps typed names safe: a
name the user typed is only touched if it starts with a Taiwanese postcode+台灣/臺灣 address or has
the exact en Maps shape. A failed position lookup is not retried; the name and address are still fixed.

### 初衷對照 / North-Star check

- ✅ Sharing from the real Maps app now fills the right name, address and position with no typing.
- ⚠️ The automatic repair rewrites venue names on its own — limited to the exact glued Maps shape, and
  a merge only happens for the same name within 50 m, so no record is lost or moved to another store.
- ⚠️ Forward geocoding sends an address text to the platform geocoder; it is the link's own address,
  never the user's location. Belongs with the Play data-safety review.

