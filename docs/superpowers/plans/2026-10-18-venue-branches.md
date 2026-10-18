# Venue Branches, Addresses, Edit/Delete, Paste a Maps Link — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Same-name venues (chain branches) can coexist and are told apart by an address shown under every name; a shared or pasted Maps place that collides with a far-away same-name venue asks "another branch / same store, move / same store, keep"; venues can be edited and deleted; a Maps link can be pasted from the venue picker.

**Architecture:** The data layer drops the unique name index and gains `address` (Room 8 → 9); the repository gets explicit create / edit / merge / delete / conditional-address primitives. Two new platform capabilities (`AddressLookup` via Android `Geocoder`, `ClipboardReader`) follow the `LocationProvider` pattern. The pure `SharedVenue.match` learns the branch rule; the editor ViewModel reuses its #84 share path for pastes; the picker gets address lines, a paste row, and an edit dialog with delete.

**Tech Stack:** Kotlin 2.3.10, Compose Multiplatform 1.11.1 (Material 3 only), Room 2.8.4 (KMP), Koin 4.1.0, kotlinx-serialization, kotlin.test (`commonTest`), JUnit4/Turbine (`app/src/test`), AndroidX Test (`app/src/androidTest`, local-only, API 35 AVD).

**Spec:** `docs/superpowers/specs/2026-10-18-venue-branches-design.md` — read it first; it records every owner decision and every *(builder's call)*. It amends #83 (`2026-10-05-venue-design.md`) and #84 (`2026-10-10-maps-share-design.md`).

## Global Constraints

- **Venue names are no longer unique.** `VenueName.sameAs` still decides "same name"; identity is the **id**. No serial suffixes, ever.
- **Address rule:** a lookup only ever writes into an **empty** address, and **any position change empties it**. A lookup result is written only if the venue still has the looked-up position and an empty address (conditional update).
- **Matching a shared/pasted place** (spec *Matching*): none same-name → create at the Maps pin; nearest same-name **≤ 50 m** → use; else a same-name with **no position** (most recently used) → use + give it the pin; else ask about the **nearest** one; link without a pin → most recently used same-name, else create without position. The 3-way question is **always** asked in that case (this removes #84's zero-record silent move).
- **Network:** only `maps.app.goo.gl` / `goo.gl` for links (unchanged); the geocoder is the platform's. Nothing here adds a request to any other host. Clipboard is read **only** in direct response to a tap.
- **Deleting a venue never deletes a meal:** its records' `venue_id` is cleared and the venue deleted in **one transaction**.
- **Backup:** `SCHEMA_VERSION` **1 → 2**; venues carry `key` (id at backup time, as a string) and `address`; meals carry `venueKey`. Version-1 snapshots still restore by name.
- **The record's own `location` is never derived from or changed by a venue** (#83 two-facts rule), including on paste in edit mode.
- `commonMain` stays Android-free (iOS compiles it). Every user-facing string in BOTH `values/strings.xml` and `values-zh-rTW/strings.xml`, positional args (`%1$s`, `%1$d`); plurals as `<plurals>` (zh: `other` only).
- detekt: lexicographic imports, no blank lines between them; braces on multi-line if/else; ≤ 2 `return`s per function; split long composables/functions rather than suppressing new rules.
- Every commit ends with: `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`
- Gate: `$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'; .\gradlew.bat assembleDebug testDebugUnitTest :app:detekt :app:lintDebug :shared:jvmTest :app:compileDebugAndroidTestKotlin`. Instrumented tests only on the **API 35** AVD (`E2E_Api35`, `emulator-5554`); quote `"-Pandroid.testInstrumentationRunnerArguments.class=<fqcn>"` in PowerShell.

## Review Focus

1. **Upgrading a phone that already has #83/#84 venues** — every venue and every record's venue survive 8 → 9, and afterwards a second 「麥當勞」 can be inserted. → Task 1 `migrates8to9_keepsVenuesAndAllowsSameNames`.
2. **Restoring a backup that has two branches** — each meal comes back on *its* branch, not the first one with that name. → Task 2 `twoBranches_roundTripOnTheirOwnVenues`.
3. **A slow address lookup racing a user edit or a move** — the edit or the new position wins; the stale result is dropped. → Task 4 `aLookupThatFinishesAfterAMove_isDropped`, Task 1 `setAddressIfUnchanged_*`.
4. **Deleting the venue the open record is using** — the editor shows 「未設定」 and Save stores no venue (no dangling id). → Task 7 `deletingTheSelectedVenue_clearsIt`.
5. **Pasting while a share is still resolving / pasting twice** — only the last paste applies; nothing from the earlier one is created. → Task 6 `aSecondPaste_cancelsTheFirst`.

---

### Task 1: Data layer — address, non-unique names, new repository primitives

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/crazystudio/sportrecorder/domain/model/Venue.kt` (add `address`)
- Modify: `shared/src/commonMain/kotlin/com/crazystudio/sportrecorder/entity/VenueEntity.kt`, `dao/VenueDao.kt`, `database/AppDatabase.kt` (version 9), `database/Migrations.kt` (`MIGRATION_8_9`), `data/repository/VenueRepositoryImpl.kt`, `domain/repository/VenueRepository.kt`; exported Room schema JSON for version 9 (committed, as for 8).
- Modify: `app/src/test/java/com/crazystudio/sportrecorder/fake/FakeVenueRepository.kt`, `shared/src/commonTest/kotlin/com/crazystudio/sportrecorder/backup/fakes/FakeVenueRepository.kt` (implement the new API).
- Test: `app/src/androidTest/java/com/crazystudio/sportrecorder/database/RoomMigrationTest.kt`, `.../database/VenueRepositoryTest.kt`.

**Interfaces:**
- Produces:
  ```kotlin
  data class Venue(val id: Int, val name: String, val lat: Double?, val lng: Double?, val lastUsedAt: Long, val address: String? = null)

  interface VenueRepository {
      fun observeAll(): Flow<List<Venue>>
      /** Typed names: the most recently used venue with this name, else a new one. Adopts lat/lng only if it has no position. */
      suspend fun findOrCreate(name: String, lat: Double?, lng: Double?, now: Long): Venue
      /** Always a new venue, even if the name exists (a new branch). */
      suspend fun create(name: String, lat: Double?, lng: Double?, now: Long): Venue
      suspend fun touch(venueId: Int, now: Long)
      /** Moves the venue AND clears its address (the old address described the old position). */
      suspend fun setPosition(venueId: Int, lat: Double?, lng: Double?)
      /** Writes [address] only if the venue is still at (lat, lng) and its address is null. True if written. */
      suspend fun setAddressIfUnchanged(venueId: Int, lat: Double, lng: Double, address: String): Boolean
      /** Saves name + address as given (name normalized; blank address → null). Never merges. */
      suspend fun edit(venueId: Int, name: String, address: String?, now: Long): Venue
      /** Re-points [fromId]'s records to [intoId] and deletes [fromId], in one transaction. Survivor keeps its own name/address/position, but adopts [fromId]'s position if it has none. */
      suspend fun merge(fromId: Int, intoId: Int, now: Long): Venue
      /** Clears venue_id on its records and deletes it, in one transaction. */
      suspend fun delete(venueId: Int)
      suspend fun recordCount(venueId: Int): Int
      /** Restore: drop all, insert [venues] in order; returns the inserted venues (fresh ids) in the same order. */
      suspend fun replaceAll(venues: List<Venue>): List<Venue>
  }
  ```
  `rename(...)` is **removed**; until Task 7 the editor VM's `requestRename`/`confirmRename` keep today's behaviour by calling `edit` (no collision) or `merge` into the most recently used same-name venue (collision).

- [ ] **Step 1: Failing tests (androidTest, Room in-memory / migration helper as the files already do)**
  - `migrates8to9_keepsVenuesAndAllowsSameNames` — seed a v8 DB with venue 「麥當勞」(lat/lng) and an `eat_time` row pointing at it; migrate; the row still points at it, `address` is null, and inserting a second 「麥當勞」 succeeds.
  - `create_allowsASecondVenueWithTheSameName`
  - `findOrCreate_picksTheMostRecentlyUsedOfSameNames`
  - `setPosition_clearsTheAddress`
  - `setAddressIfUnchanged_writesOnlyWhenPositionAndEmptyAddressStillMatch` — three cases: matches → true & written; position moved → false; address already set → false.
  - `edit_savesNameAndAddress_blankAddressBecomesNull_andNeverMerges`
  - `merge_repointsRecordsAndDeletesTheOther_survivorKeepsItsOwnFields`
  - `delete_clearsRecordsVenueAndRemovesIt` — the record survives with `venue_id` null.
  - `replaceAll_returnsInsertedVenuesInOrderWithFreshIds`
- [ ] **Step 2: Run, watch them fail** — `connectedDebugAndroidTest` with class `com.crazystudio.sportrecorder.database.VenueRepositoryTest`, then `...database.RoomMigrationTest`.
- [ ] **Step 3: Implement.** Migration 8 → 9: `ALTER TABLE venue ADD COLUMN address TEXT`; drop the unique `index_venue_name`; create a non-unique `index_venue_name` (match the name Room generates for `Index(value = ["name"])`, check the exported v8 schema). DAO: conditional `UPDATE venue SET address = :address WHERE id = :id AND lat = :lat AND lng = :lng AND address IS NULL` (return affected rows), `UPDATE eat_time SET venue_id = NULL WHERE venue_id = :venueId`. Transactions as `rename` does today (`useWriterConnection { immediateTransaction { … } }`). Update both fakes to the new contract.
- [ ] **Step 4: Run, watch them pass; run the full gate.**
- [ ] **Step 5: Commit** — `feat(venue): allow same-name venues and give venues an address`.

---

### Task 2: Backup — keys, address, schema 2

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/crazystudio/sportrecorder/backup/BackupDocument.kt`, `BackupMappers.kt`, `BackupService.kt`
- Test: `shared/src/commonTest/kotlin/com/crazystudio/sportrecorder/backup/` (the existing BackupService test file; find it there)

**Interfaces:**
- Consumes: `VenueRepository.replaceAll(...): List<Venue>`, `Venue.address` (Task 1).
- Produces: `BackupVenue(name, lat, lng, lastUsedAt, address: String? = null, key: String? = null)`; `BackupMeal(..., venueName: String? = null, venueKey: String? = null)`; `SCHEMA_VERSION = 2`.

- [ ] **Step 1: Failing tests**
  - `twoBranches_roundTripOnTheirOwnVenues` — two 「麥當勞」 (different lat/lng/address), one meal on each; backup → restore → each meal's venue has its own lat/lng/address.
  - `aVersion1Snapshot_stillRestoresVenuesByName` — a v1 JSON (no keys) restores and attaches by name.
  - `addressIsBackedUp`
  - `aSchema2Snapshot_isRefusedByAVersion1Reader` is NOT testable here (older app); instead assert `BackupDocument.SCHEMA_VERSION == 2` is what `backup()` writes.
- [ ] **Step 2: Run, watch them fail** — `.\gradlew.bat :shared:jvmTest --tests "*Backup*"`.
- [ ] **Step 3: Implement.** `key = venue.id.toString()` at backup; restore zips `doc.venues` with `replaceAll`'s result to map key → new venue; meals use `venueKey` when present, else `venueName` (first `sameAs` match, as today). Update the KDoc that says "the NAME, never its id" to explain the key.
- [ ] **Step 4: Run, watch them pass; full gate.**
- [ ] **Step 5: Commit** — `feat(backup): keep same-name venues apart with a per-snapshot key`.

---

### Task 3: Matching a shared place against branches

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/crazystudio/sportrecorder/domain/venue/SharedVenue.kt`
- Test: `shared/src/commonTest/kotlin/com/crazystudio/sportrecorder/domain/venue/SharedVenueTest.kt`

**Interfaces:**
- Produces (replaces `AskToMove`):
  ```kotlin
  sealed interface SharedVenueMatch {
      data class Create(val name: String, val position: GeoPoint?) : SharedVenueMatch
      data class Use(val venue: Venue, val fillPosition: GeoPoint?) : SharedVenueMatch
      /** Same name, every same-name venue > 50 m away: ask about [venue], the nearest. */
      data class AskWhichStore(val venue: Venue, val to: GeoPoint, val meters: Double) : SharedVenueMatch
  }
  sealed interface RoundedDistance { data class Meters(val value: Int) : RoundedDistance; data class Kilometers(val tenths: Int) : RoundedDistance }
  fun roundedDistance(meters: Double): RoundedDistance   // < 1000 m → Meters(rounded); else Kilometers(one decimal, as tenths: 1834 m → 18)
  ```
- [ ] **Step 1: Failing tests** — `noSameName_creates`; `nearestWithinFiftyMetres_isUsed` (two branches, pin 20 m from the second → Use(second)); `noneNear_butOneWithoutPosition_isUsedAndGetsThePin` (most recently used of the position-less ones); `allFar_asksAboutTheNearest` (meters == distance to the nearest); `noPin_usesTheMostRecentlyUsedSameName`; `noPin_noSameName_createsWithoutPosition`; `roundedDistance_metresBelowOneKilometre` (349.6 → Meters(350)); `roundedDistance_kilometresWithOneDecimal` (1834.0 → Kilometers(18)). Update or delete the old `AskToMove` tests.
- [ ] **Step 2: Run, watch them fail** — `:shared:jvmTest --tests "*SharedVenueTest*"`.
- [ ] **Step 3: Implement.** Pure; the editor VM compile must be kept green: map `AskWhichStore` onto today's ask-to-move path for now (Task 5 replaces it) and delete the zero-record silent move.
- [ ] **Step 4: Run, watch them pass; full gate.**
- [ ] **Step 5: Commit** — `feat(venue): match a shared place against same-name branches`.

---

### Task 4: Address lookup

**Files:**
- Create: `shared/src/commonMain/kotlin/com/crazystudio/sportrecorder/platform/AddressLookup.kt`
- Create: `shared/src/commonMain/kotlin/com/crazystudio/sportrecorder/domain/venue/VenueAddressFiller.kt`
- Create: `app/src/main/java/com/crazystudio/sportrecorder/platform/GeocoderAddressLookup.kt`
- Modify: `app/src/main/java/com/crazystudio/sportrecorder/di/AppModule.kt`
- Create: `app/src/test/java/com/crazystudio/sportrecorder/fake/FakeAddressLookup.kt`
- Test: `app/src/test/java/com/crazystudio/sportrecorder/platform/GeocoderAddressFormatTest.kt`, `app/src/test/java/com/crazystudio/sportrecorder/domain/venue/VenueAddressFillerTest.kt`

**Interfaces:**
- Produces:
  ```kotlin
  interface AddressLookup { /** Short address at [point], or null on any failure. Never throws. */ suspend fun addressAt(point: GeoPoint): String? }
  class VenueAddressFiller(private val lookup: AddressLookup, private val venues: VenueRepository) {
      /** For each venue with a position and no address, look it up and write it with setAddressIfUnchanged. Sequential; failures skipped. */
      suspend fun fill(venues: List<Venue>)
  }
  internal fun shortAddress(thoroughfare: String?, subThoroughfare: String?, firstLine: String?): String?  // in GeocoderAddressLookup.kt
  ```
- [ ] **Step 1: Failing tests** — format: `roadAndNumber` (「信義路二段」, 「194號」 → 「信義路二段 194號」); `roadOnly`; `fallsBackToTheFirstLine`; `nothingIsNull`. Filler: `fillsOnlyVenuesWithAPositionAndNoAddress`; `aFailedLookupLeavesItEmpty`; `aLookupThatFinishesAfterAMove_isDropped` (fake lookup suspends; meanwhile `setPosition` → the late result is not written).
- [ ] **Step 2: Run, watch them fail** — `testDebugUnitTest --tests "*GeocoderAddressFormatTest*" --tests "*VenueAddressFillerTest*"`.
- [ ] **Step 3: Implement.** Android: `Geocoder.isPresent()` false → null; API 33+ `getFromLocation(lat, lng, 1, listener)` bridged with `suspendCancellableCoroutine`, older APIs the blocking call on `Dispatchers.IO`; device default locale; any exception → null. Koin: `single<AddressLookup> { GeocoderAddressLookup(androidContext()) }`, `single { VenueAddressFiller(get(), get()) }`.
- [ ] **Step 4: Run, watch them pass; full gate.**
- [ ] **Step 5: Commit** — `feat(venue): look up a venue's address from its position`.

---

### Task 5: Editor — the "which store?" question, and filling addresses

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/crazystudio/sportrecorder/ui/diet/editor/EatTimeEditorViewModel.kt`, `EatTimeEditorUiState.kt`, `EatTimeEditorSheet.kt`
- Modify: `app/src/main/java/com/crazystudio/sportrecorder/ui/AppRoot.kt`, `di/AppModule.kt` (one more `get()`)
- Modify: strings, both locales
- Test: `app/src/test/java/com/crazystudio/sportrecorder/ui/diet/editor/EatTimeEditorViewModelTest.kt`

**Interfaces:**
- Consumes: `AskWhichStore`, `roundedDistance` (T3); `VenueAddressFiller` (T4); `VenueRepository.create` (T1).
- Produces: VM constructor gains `venueAddressFiller: VenueAddressFiller` (last parameter). UiState: `pendingStore: PendingStore? = null` with `data class PendingStore(val venue: Venue, val to: GeoPoint, val distance: RoundedDistance, val movedRecords: Int, val sharedName: String)`; `fun answerAnotherBranch()`, `fun answerSameStoreMove()`, `fun answerSameStoreKeep()` (dismiss = keep). `pendingMove`, `confirmMove()`, `cancelMove()` are **kept** (Task 7 reuses them for "update from a link") but no longer set by the share path. The existing `shareMoveAnswered` SavedStateHandle flag now records any answer to `pendingStore`.
- Strings (en / zh-rTW):
  | key | en | zh-rTW |
  |---|---|---|
  | `editor_store_title` | Is this the same %1$s? | 「%1$s」是同一家嗎？ |
  | `editor_store_body_at` | The %1$s you saved is at %2$s, about %3$s from this link. | 你存過的「%1$s」在 %2$s，和這個連結的位置相距約 %3$s。 |
  | `editor_store_body` | The %1$s you saved is about %2$s from this link. | 你存過的「%1$s」和這個連結的位置相距約 %2$s。 |
  | `distance_meters` | %1$d m | %1$d 公尺 |
  | `distance_kilometers` | %1$s km | %1$s 公里 |
  | `editor_store_branch` | It's another branch | 是另一家分店 |
  | `editor_store_move` *(plurals)* | one: Same place — update its position (%1$d past record will move) · other: …(%1$d past records will move) | other: 同一家，更新位置（%1$d 筆紀錄會跟著移動） |
  | `editor_store_move_none` | Same place — update its position | 同一家，更新位置 |
  | `editor_store_keep` | Same place — keep its position | 同一家，位置不變 |
  Kilometres are shown with one decimal from `tenths` (format in Kotlin, no `String.format` in commonMain: `"${tenths / 10}.${tenths % 10}"`).
- [ ] **Step 1: Failing tests** — `aFarSameNameVenue_asksWhichStore` (pendingStore set, distance & count right, nothing written yet); `anotherBranch_createsANewVenueAtThePin_andSelectsIt` (old venue untouched); `sameStoreMove_movesIt`; `sameStoreKeep_keepsIt`; `zeroRecords_stillAsks` (replaces the #84 silent-move test); `anAnsweredQuestion_isNotAskedAgainAfterProcessDeath`; `aCreatedOrMovedVenue_getsItsAddressFilled` (fake lookup returns 「信義路二段 194號」 → repository venue has it); `pickingAVenueWhileAsked_withdrawsTheQuestion`. Update existing tests that asserted `pendingMove` from a share.
- [ ] **Step 2: Run, watch them fail** — `testDebugUnitTest --tests "*EatTimeEditorViewModelTest*"`.
- [ ] **Step 3: Implement** the VM, then the sheet: an `AlertDialog` with title + body (`_at` variant when the saved venue has an address) and three stacked `TextButton`s in the order branch / move / keep; `onDismissRequest` = keep. Call `venueAddressFiller.fill(listOf(venue))` (in `viewModelScope`, never blocking the flow) after any create or position change the VM makes. Wire callbacks in `AppRoot`.
- [ ] **Step 4: Run, watch them pass; full gate.**
- [ ] **Step 5: Commit** — `feat(venue): ask whether a far same-name place is another branch`.

---

### Task 6: Paste a Maps link from the picker

**Files:**
- Create: `shared/src/commonMain/kotlin/com/crazystudio/sportrecorder/platform/ClipboardReader.kt`, `app/src/main/java/com/crazystudio/sportrecorder/platform/AndroidClipboardReader.kt`, `app/src/test/java/com/crazystudio/sportrecorder/fake/FakeClipboardReader.kt`
- Modify: `EatTimeEditorViewModel.kt`, `EatTimeEditorUiState.kt`, `VenuePickerSheet.kt`, `AppRoot.kt`, `AppModule.kt`, strings both locales
- Test: `EatTimeEditorViewModelTest.kt`

**Interfaces:**
- Produces: `interface ClipboardReader { suspend fun text(): String? }` (Android: `ClipboardManager.primaryClip` first item `coerceToText`, on the main thread; null on anything else). VM constructor gains `clipboardReader: ClipboardReader` (last). UiState: `pasteDialog: PasteTarget? = null` with `sealed interface PasteTarget { data object Select : PasteTarget; data class Reposition(val venue: Venue) : PasteTarget }` (Task 7 uses `Reposition`). VM: `suspend fun pasteMapsLink(): Boolean` — true when the clipboard held a Maps link and the place flow started (the picker then closes); false → `pasteDialog = PasteTarget.Select`. `fun pasteText(text: String)` (from the dialog; closes it and starts the flow; non-Maps text → the existing `ShareNotice.NotAMapLink`). `fun dismissPasteDialog()`.
- Behaviour: a paste clears the selected venue, cancels any share/paste job, runs the same flow as a share (`resolvingLink`, matching, `shareNotice`, `pendingStore`), works in **edit mode**, has its own in-memory "answered" state, is **not** persisted in SavedStateHandle.
- Strings: `editor_venue_paste` Paste a map link / 貼上地圖連結 · `editor_paste_title` Paste a map link / 貼上地圖連結 · `editor_paste_hint` Paste a Google Maps link here / 把 Google 地圖連結貼在這裡 · `editor_paste_use` Use / 使用 · cancel reuses `backup_cancel`.
- [ ] **Step 1: Failing tests** — `pastingAMapsLink_runsTheSharePath_andSelectsTheVenue`; `pastingWithoutALink_opensThePasteDialog_andMakesNoRequest`; `pasteText_fromTheDialog_runsTheSamePath`; `pasteInEditMode_changesTheVenueButNotTheLocation`; `aSecondPaste_cancelsTheFirst` (first resolver call suspended, second paste completes; release first → only the second's venue exists); `aPasteAfterAnAnsweredShare_asksAgain`.
- [ ] **Step 2: Run, watch them fail.**
- [ ] **Step 3: Implement.** Picker: first row 「📋 貼上地圖連結」 (leading clipboard icon from Material icons, text `editor_venue_paste`); on tap `scope.launch { if (vm.pasteMapsLink()) dismiss() }`. Paste dialog: `AlertDialog` with an `OutlinedTextField` (hint), Use / Cancel; rendered by the picker sheet. Koin binding for `ClipboardReader`.
- [ ] **Step 4: Run, watch them pass; full gate.**
- [ ] **Step 5: Commit** — `feat(venue): paste a Google Maps link from the venue picker (#89)`.

---

### Task 7: Picker rows with addresses; edit dialog; delete

**Files:**
- Modify: `VenuePickerSheet.kt` (rows, ✎ instead of ⋮, edit dialog, delete confirm, merge-or-keep, reposition question), `EatTimeEditorViewModel.kt`, `EatTimeEditorUiState.kt`, `AppRoot.kt`, strings both locales
- Test: `EatTimeEditorViewModelTest.kt`; `app/src/androidTest/java/com/crazystudio/sportrecorder/ui/diet/editor/` — add `VenuePickerSheetTest.kt` (or extend an existing picker test if one exists)

**Interfaces:**
- Consumes: `edit`, `merge`, `delete`, `setPosition`, `recordCount` (T1); `VenueAddressFiller` (T4); `PasteTarget.Reposition`, `ClipboardReader` (T6); `pendingMove`/`confirmMove`/`cancelMove` (kept from #84).
- Produces (VM; replaces `requestRename`/`confirmRename`/`cancelRename`; `rename` usages gone):
  - `fun saveVenue(venue: Venue, name: String, address: String?)` — blank name refused (as rename is today); name `sameAs` the venue's current name, or no other venue has it → `edit`; otherwise `pendingMerge = PendingMerge(from = venue, into = target, movedRecords, newName, newAddress)` where target = nearest same-name venue when both have positions, else most recently used.
  - `fun mergeVenues()` (→ `merge(from.id, into.id)`, the record's selection follows to the survivor), `fun keepBothVenues()` (→ `edit` with the new name), `fun cancelMerge()`.
  - `fun requestDelete(venue: Venue)` → `pendingDelete = PendingDelete(venue, records)`; `fun confirmDelete()` → `delete`, and if it was the selected venue the selection becomes null; `fun cancelDelete()`.
  - `suspend fun repositionFromClipboard(venue: Venue): Boolean` — clipboard Maps link → resolve (short) → `parse` → pin only (name ignored); records > 0 → `pendingMove` (existing dialog, now rendered by the picker); 0 → `setPosition` directly; no link → `pasteDialog = PasteTarget.Reposition(venue)`; `pasteText` handles both targets. No pin / unreadable → `ShareNotice.MapLinkUnreadable` shown inside the edit dialog.
  - `useThisPositionFor(venue)` kept (immediate). After every position change: `venueAddressFiller.fill(listOf(updated))`.
  - When the picker opens (`fun onPickerShown()`), `venueAddressFiller.fill(venueOptions)` back-fills missing addresses.
- UI: each row = name (bodyLarge) + second line (bodySmall, onSurfaceVariant): address; 「沒有位置」 (`venue_no_position`) when neither; nothing while waiting. Trailing ✎ (`Icons.Outlined.Edit`, content description `editor_venue_edit`) opens the edit dialog. Edit dialog per spec *Edit dialog*: fields 店名/地址 (drafts, `rememberSaveable`), position line 「位置：%1$s, %2$s · %3$d 筆紀錄」 (4 decimals) or 「沒有位置」, the two position actions (use-this-record only when the record has a fix), 刪除 (error colour, bottom-start), 取消 / 儲存. Delete confirm and merge-or-keep dialogs per spec.
- Strings (en / zh-rTW): `venue_no_position` No position / 沒有位置 · `editor_venue_edit` Edit place / 編輯店家 · `editor_venue_name` Name / 店名 · `editor_venue_address` Address / 地址 · `editor_venue_position` Position: %1$s, %2$s / 位置：%1$s, %2$s · `editor_venue_records` *(plurals)* %1$d record(s) / %1$d 筆紀錄 · `editor_venue_use_link` Update from a map link / 貼上地圖連結更新 · `editor_venue_delete` Delete / 刪除 · `editor_delete_title` Delete %1$s? / 要刪除「%1$s」嗎？ · `editor_delete_body` *(plurals)* one: %1$d record uses this place. It stays — its place just becomes "Not set". · other: %1$d records use this place. They stay — their place just becomes "Not set". / 有 %1$d 筆紀錄用了這家店。刪除後，這些紀錄還在，只是店家會變成「未設定」。 (omitted when 0) · `editor_venue_merge_confirm` → Merge into one / 合併成同一家 · `editor_venue_merge_keep` Keep both / 兩家都保留 · `editor_venue_merge_where` That one is at %1$s. / 那一家在 %1$s。 · save reuses an existing save string if present, else `editor_venue_save` Save / 儲存. Remove strings that become unused (`editor_venue_rename`, `editor_venue_more`) only if nothing references them.
- [ ] **Step 1: Failing tests (VM)** — `savingANewName_withoutCollision_edits`; `savingTheSameName_neverAsksToMerge` (address-only change); `savingOntoAnotherVenuesName_offersMergeOrKeep_withTheNearestAsTarget`; `keepBoth_leavesTwoSameNameVenues`; `merge_movesTheRecords_andTheSelectionFollows`; `deletingAVenue_asksWithTheCount`; `deletingTheSelectedVenue_clearsIt` (then `save()` stores no venue); `repositionFromALink_withRecords_asksFirst`; `repositionFromALink_takesOnlyThePin_notTheName`; `openingThePicker_backfillsMissingAddresses`.
- [ ] **Step 2: Failing UI test (androidTest)** — `aRowShowsTheAddress_orNoPosition`; `thePencilOpensTheEditDialog`; `deleteConfirmation_mentionsTheRecordCount`.
- [ ] **Step 3: Run, watch them fail** (VM via `testDebugUnitTest`, UI via `connectedDebugAndroidTest` on `emulator-5554`).
- [ ] **Step 4: Implement.**
- [ ] **Step 5: Run, watch them pass; full gate; then by hand on the emulator: share a 「鼎泰豐 信義店」 link, open the picker → address line appears; edit → rename onto another venue's name → merge-or-keep; delete → count shown; paste row with a link on the clipboard (`adb shell cmd clipboard` is not available on API 35 — copy from the emulator's Chrome address bar or a notes app). Save screenshots to the scratchpad and say what you saw.**
- [ ] **Step 6: Commit** — `feat(venue): show addresses, edit and delete venues`.

---

### Task 8: Insights — tell same-name venues apart

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/crazystudio/sportrecorder/domain/insights/InsightsModels.kt` (`LocationCount` gains `address: String? = null`), `InsightsAggregator.kt` (sets it), `ui/insights/map/PlacesMap.kt` + `MapLayers.kt` (labels), strings both locales
- Test: `shared/src/commonTest/kotlin/com/crazystudio/sportrecorder/domain/insights/InsightsAggregatorTest.kt`, and a pure label test next to `PlacesMap`'s existing tests (find them under `shared/src/commonTest/.../ui/insights/map/`)

**Interfaces:**
- Produces: `internal fun venueLabels(locations: List<LocationCount>, withAddress: (name: String, address: String) -> String): List<String?>` — a location's label is its name, or `withAddress(name, address)` when another location in the list has a `sameAs` name and its address is non-null and different. String `insights_venue_with_address`: en `%1$s (%2$s)` / zh `%1$s（%2$s）`.
- [ ] **Step 1: Failing tests** — aggregator: `twoBranches_stayTwoMarkersWithTheirAddresses`; labels: `uniqueNames_areUnchanged`; `sameNames_getTheirAddresses`; `sameNames_withoutAddresses_areUnchanged`.
- [ ] **Step 2: Run, watch them fail** — `:shared:jvmTest --tests "*InsightsAggregatorTest*" --tests "*PlacesMap*"`.
- [ ] **Step 3: Implement**; the pills and the accessibility description both use the labels. iOS calls `InsightsScreen`, so keep its parameters unchanged.
- [ ] **Step 4: Run, watch them pass; full gate.**
- [ ] **Step 5: Commit** — `feat(insights): tell same-name venues apart by address`.

---

## Final verification

- [ ] Full gate green; `ios-shared` green on the PR.
- [ ] Instrumented: `RoomMigrationTest`, `VenueRepositoryTest`, `ShareEntryTest`, the picker test — all on API 35.
- [ ] By hand, both locales: two branches of one chain via share; the 3-way question each answer; paste from picker; edit/merge/keep/delete; Insights map shows both branches.
- [ ] `docs/DEVELOPMENT.md` doc index gains this spec + plan; spec **Status** flipped to implemented.
- [ ] Before release: Play data-safety — the platform geocoder now receives venue positions (owner's call).
