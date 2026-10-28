# Links Made by the Google Maps App — Fix Plan

**Goal:** A link shared or copied from the Google Maps **app** fills the right 店名, 地址 and position on paste/share and on 「貼上地圖連結更新」; venues already created with glued names are repaired.

**Spec:** `docs/superpowers/specs/2026-10-18-venue-branches-design.md` → *Addendum (2026-10-28): links made by the Google Maps app* (binding; owner decisions there). Diagnosis evidence: real Location headers in the scratchpad file `real-locations.txt` and the investigators' samples in `diag-reports.json` (paths given to each implementer).

## Global Constraints

- Exact-host allowlist and the one-request / no-redirect / 5 s resolver rules from #84 are unchanged. No second request to Google; no ftid decoding.
- The split is **confident or nothing**: when a pinless segment doesn't match a recognised shape, the whole segment stays the name (today's behaviour). A link with a pin is never split.
- Address rule (#89) stands: a lookup writes only into an empty address; a position change empties it — except that a move whose source is a Maps link writes **the link's trimmed address** with the pin.
- `AddressLookup` never throws; every lookup is bounded (10 s) and runs off the main thread; nothing waits on it except the place read itself.
- The record's own `location` is never touched.
- `commonMain` stays Android-free (iOS compiles it). Strings in both locales, positional args.
- detekt: lexicographic imports, braces on multi-line if/else, ≤ 2 returns per function.
- Commits end with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
- Gate: `$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'; .\gradlew.bat assembleDebug testDebugUnitTest :app:detekt :app:lintDebug :shared:jvmTest :app:compileDebugAndroidTestKotlin`. Instrumented tests only on `emulator-5554` (API 35).

## Review Focus

1. The real zh-TW Android Location → name 「鼎泰豐 信義店」, address 「臺北市大安區福住里信義路二段194號」.
2. A typed name like 「85度C」, 「7-11 信義店」 or 「Café, Bar」 is never split or "repaired".
3. A web-made link (spike REAL) parses exactly as before — no address, same name, same pin.
4. Pasting the same Maps-app place twice reuses one venue (no duplicate).
5. Repair is idempotent: a second app start changes nothing.

---

### Task 1: Read name and address from pinless links (pure)

**Files:** `shared/src/commonMain/kotlin/com/crazystudio/sportrecorder/domain/venue/MapsLink.kt` (+ a new `PlaceTitle.kt` in the same package), `shared/src/commonTest/.../domain/venue/MapsLinkTest.kt` (+ `PlaceTitleTest.kt`).

**Produces:** `data class MapsPlace(val name: String?, val lat: Double?, val lng: Double?, val address: String? = null, val geocodeQuery: String? = null)` — `geocodeQuery` is the text to forward-geocode when there is no pin (the full untrimmed address when split, else the whole segment; null when a pin exists or there is nothing). `object PlaceTitle { fun split(segment: String): Split?; data class Split(val name: String, val address: String, val trimmedAddress: String) }` — null when not confident. `MapsLink.find/parse` accept iOS `maps.google.com|www.google.com` with path `""`, `/` or `/maps` **and** a `q=` parameter (otherwise rejected as today); `/maps/search/<lat>,<lng>` and `q=<lat>,<lng>` give a pin with no name; a `/place/` segment that is only coordinates gives no name.

**Tests (exact values):** `androidAppLink_zhTW_splitsAddressAndName` (the real zh Location → name 「鼎泰豐 信義店」, address 「臺北市大安區福住里信義路二段194號」, lat/lng null, geocodeQuery 「106台灣臺北市大安區福住里信義路二段194號」); `androidAppLink_enUS_splitsNameFirst` (real en Location → name 「Din Tai Fung Xinyi Branch」, address 「No. 194號, Section 2, Xinyi Rd, Fuzhu Village, Da’an District, Taipei City」); `unrecognisedPinlessSegment_staysWhole` (the real Korean Location → name = whole segment, address null, geocodeQuery = whole segment); `webLink_isUnchanged` (REAL → exactly as before, address null, geocodeQuery null); `iosLink_readsQ`; `searchCoordinates_giveAPin`; `coordinatesOnlyPlace_hasNoName`; `PlaceTitle`: floors (「…194號2樓鼎泰豐」), 之 (「…5號之1…」), names starting with digits (「…12號85度C」 → name 「85度C」), and typed names never split (「85度C」, 「7-11 信義店」, 「Café, Bar」, 「鼎泰豐 信義店」 → null). Use every Taiwanese sample in `diag-reports.json` the split should handle.

### Task 2: Forward geocoding and a place reader

**Files:** `shared/.../platform/AddressLookup.kt` (add `suspend fun positionOf(query: String): GeoPoint?`), `app/.../platform/GeocoderAddressLookup.kt` (`getFromLocationName(query, 1)`, API 33+ listener / older blocking on IO, same `isPresent`, 10 s, double-resume guard, null on failure), new `shared/.../domain/venue/MapsPlaceReader.kt`: `class MapsPlaceReader(resolver: MapsLinkResolver, lookup: AddressLookup) { suspend fun read(link: MapsLink): MapsPlace? }` — resolves Short links, parses, and when the place has no pin but a `geocodeQuery`, fills lat/lng from `positionOf` (range-checked). Koin binding. `FakeAddressLookup` gains `positions: Map<String, GeoPoint?>`.

**Tests:** `MapsPlaceReaderTest` (app/src/test): pinless app link → pin from positionOf with the untrimmed address query; positionOf null → place keeps name/address, no pin; web link → positionOf never called; non-allowlisted Location → null.

### Task 3: Paste and share use the reader; addresses from links

**Files:** `EatTimeEditorViewModel.kt` (replace its readPlace with `MapsPlaceReader`; constructor swaps `mapsLinkResolver` for `mapsPlaceReader` or adds it last — keep Koin and fixtures consistent), `VenueRepository(.kt/Impl/both fakes)` (`create(name, lat, lng, now, address: String? = null)`, `setPosition(venueId, lat, lng, address: String? = null)` writing the given address with a real move, and `setAddressIfEmpty(venueId, address): Boolean`), `VenueDao.kt`. Refresh `uiState.venue` from `observeAll` by id. `useSharedVenue` without fill also fills a missing address from the link.

**Tests (VM):** `pastingAnAndroidAppLink_createsTheCleanVenueWithAddressAndPin`; `pastingItTwice_reusesOneVenue`; `pastingWhenTheAddressCantBeLocated_createsItWithNameAndAddressOnly`; `anExistingVenueWithoutAddress_getsTheLinksAddress`; `sameStoreMove_writesTheLinksAddressWithThePin`; `theSelectedVenue_showsItsFilledAddress`. Repo (androidTest): create/setPosition with address, setAddressIfEmpty.

### Task 4: 「貼上地圖連結更新」 fills 店名 and 地址

**Files:** `EatTimeEditorViewModel.kt` (reposition uses the reader; moves with the link's trimmed address; exposes `repositionDraft: RepositionDraft? (venueId, name)` consumed by the dialog), `EatTimeEditorUiState.kt`, `VenueEditDialogs.kt` (adopt the draft name into the 店名 draft; 儲存 disabled while `repositioning`; a no-position notice line), strings both locales: `editor_link_no_position` 「找不到這個地點的位置，店名和地址已經填好了。」 / "Couldn't find where this place is — the name and address are filled in."

**Tests:** VM: `repositionFromAnAppLink_movesWithTheLinksAddress_andOffersItsName`; `repositionWithRecords_asksFirst_thenWritesTheAddress`; `repositionWhenUnlocatable_offersNameAndAddress_movesNothing`. androidTest (`VenuePickerSheetTest`): the 店名 field shows the link's name after the update; 儲存 is disabled while reading.

### Task 5: Repair glued venues at app start

**Files:** new `shared/.../domain/venue/GluedVenueRepair.kt` (`class GluedVenueRepair(venues: VenueRepository, lookup: AddressLookup) { suspend fun run(now: Long) }`), called once per process from `SportApplication` in an application-lifetime scope (Koin `single`), never blocking startup.

**Tests (app/src/test):** `aGluedZhVenue_isRenamedAndGetsAddressAndPin`; `aGluedVenueNearASameNameVenue_isMergedIntoIt` (records move); `aGluedVenueFarFromASameNameVenue_staysABranch`; `typedNames_areNeverTouched`; `runningTwice_changesNothing`; `aFailedLookup_stillFixesNameAndAddress`.

### Task 6: Verify and document

Full gate; instrumented `VenueRepositoryTest`, `VenuePickerSheetTest`, `ShareEntryTest`, `RoomMigrationTest` on `emulator-5554`; by hand on the emulator with the **real Google Maps app** (Share → 複製到剪貼簿 → 📋 貼上地圖連結; ✎ → 貼上地圖連結更新; share directly to SportRecorder), zh-TW and en-US, screenshots; `docs/DEVELOPMENT.md` doc-index entry for this plan.
