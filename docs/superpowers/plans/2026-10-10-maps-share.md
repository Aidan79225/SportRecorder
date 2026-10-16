# Share a Place from Google Maps — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** In Google Maps, tap Share → SportRecorder → Save: a new meal record opens with the venue already chosen, its name never typed and its position taken from Maps.

**Architecture:** A pure parser in `commonMain` finds a Maps link in shared text and reads name + pin from a full Maps URL; a platform `MapsLinkResolver` (OkHttp in `:app`) turns a short link into a full one by reading a single `Location` header; a pure matcher decides create / use / ask-to-move against existing venues. The editor ViewModel runs all three when opened with shared text; `MainActivity` only receives the share and hands the text over.

**Tech Stack:** Kotlin 2.3.10, Compose Multiplatform 1.11.1, Koin 4.1.0, OkHttp 4.12.0 (+ `mockwebserver`, already a test dependency), kotlin.test (`commonTest`), JUnit4/Turbine (`app/src/test`), AndroidX Test (`app/src/androidTest`, local-only).

**Spec:** `docs/superpowers/specs/2026-10-10-maps-share-design.md` — read it first. It records the real `maps.app.goo.gl` → `302` → `Location` response this plan is built on. Issue [#84](https://github.com/Aidan79225/SportRecorder/issues/84). Builds on #83's `Venue` / `VenueRepository` / `VenueName`.

## Global Constraints

- **Only `maps.app.goo.gl` and `goo.gl` (path `/maps/…`) may ever be contacted.** A share target receives arbitrary text from any app; no request may go to a host chosen by that text.
- **Full-Maps hosts accepted for parsing and as a `Location` target:** `www.google.com` and `maps.google.com`, path starting `/maps`. Anything else is "not a Maps link". `https` only.
- **Redirects are never followed.** One request; read the 3xx `Location`; never a second hop.
- **Timeout: 5 seconds** (connect + read).
- **Pin beats camera:** `!3d<lat>!4d<lng>` first, `@<lat>,<lng>` only if no pin. Lat must be within ±90 and lng within ±180, or both are discarded.
- **Name** = the path segment after `/place/`, percent-decoded with `+` as a space, then `VenueName.normalize`. The rest of the share text is not used.
- **Same-place threshold: 50 m or less** (haversine, Earth radius 6 371 000 m).
- **A venue created from a share takes the Maps position, never the device's GPS.** Do not route it through the editor's existing `createVenue`, which adopts the record's fix.
- **`EatRecord.location` is unchanged:** the record still captures *where you were*; it is never derived from the venue.
- **A share never blocks a meal:** every failure still opens a usable editor, time = now.
- **Only a share launch finishes the activity.** Every other way of opening the editor behaves exactly as today.
- `commonMain` stays Android-free. Every user-facing string in BOTH `values/strings.xml` and `values-zh-rTW/strings.xml`, positional args.
- detekt: lexicographic imports with no blank lines; braces on multi-line if/else; ≤ 2 `return`s per function.
- Every commit ends with: `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`
- Instrumented tests run only on the **API 35** AVD (`E2E_Api35`); the Android 17 AVD fails every Espresso test with `NoSuchMethodException: InputManager.getInstance`.

## Review Focus

The spec does not test these, and each would bite a real person:

1. **The real Android share text shape** — name, address and link on separate lines, the link last. The link must still be found, and the name must still come from the URL, not the first line. → Task 1, `findsTheLinkInsideARealMultiLineShare`.
2. **Rotation / process recreation of a share-launched editor** — must not open a second editor, must not finish the activity, and re-running the share must not create a second venue or re-ask a move already answered. → Task 4 `runningTheSameShareTwice_isIdempotent`; Task 6 instrumented `recreating_keepsOneEditorAndDoesNotFinish`.
3. **Saving before the link resolves** — the meal saves (without a venue) and a resolution arriving afterwards changes nothing and does not crash. → Task 4, `savingBeforeTheLinkResolves_savesWithoutAVenue`.
4. **Names Google percent-encodes** — parentheses, `&`, mixed scripts. They must decode exactly. → Task 1, `decodesPunctuationAndMixedScripts`.
5. **Non-Maps text** — a news link or plain text: editor opens with "not a Maps link" and **no network request is made at all**. → Task 4, `nonMapsText_neverCallsTheResolver`.

Known limitation, deliberately not fixed here: a Maps name that differs from an existing venue only by spacing (「鼎泰豐 信義店」 vs 「鼎泰豐信義店」) creates a second venue, because #83's `VenueName.sameAs` keeps interior spaces. The merge-by-rename tool from #83 cleans it up.

---

### Task 1: `MapsLink` — find a link, read a place

**Files:**
- Create: `shared/src/commonMain/kotlin/com/crazystudio/sportrecorder/domain/venue/MapsLink.kt`
- Test: `shared/src/commonTest/kotlin/com/crazystudio/sportrecorder/domain/venue/MapsLinkTest.kt`

**Interfaces:**
- Consumes: `VenueName.normalize` (#83).
- Produces:
  ```kotlin
  sealed interface MapsLink {
      val url: String
      class Short internal constructor(override val url: String) : MapsLink  // needs resolving
      class Full internal constructor(override val url: String) : MapsLink   // parse directly
      companion object {
          /** First allowlisted Maps URL in [text], or null. */
          fun find(text: String): MapsLink?
          /** Name + pin from a full Maps URL; null unless [fullUrl]'s host is a full-Maps host. */
          fun parse(fullUrl: String): MapsPlace?
      }
  }
  data class MapsPlace(val name: String?, val lat: Double?, val lng: Double?)
  ```
  The constructors are `internal` on purpose: outside `:shared`, the only way to obtain a `Short` is `find`, so **the type itself guarantees nothing un-allowlisted ever reaches the network** (Task 3's resolver accepts only `Short`).

- [ ] **Step 1: Write the failing tests**

```kotlin
private const val REAL = "https://www.google.com/maps/place/%E9%BC%8E%E6%B3%B0%E8%B1%90+%E4%BF%A1%E7%BE%A9%E5%BA%97/@25.0335035,121.530118,17z/data=!4m6!3m5!1s0x3442a9821392326d:0x93faef9e2359365c!8m2!3d25.0335035!4d121.530118!16s%2Fg%2F1td2dltc?entry=tts"

@Test fun parsesTheRealSharedLink() {
    assertEquals(MapsPlace("鼎泰豐 信義店", 25.0335035, 121.530118), MapsLink.parse(REAL))
}
@Test fun thePinBeatsTheCamera() {
    val panned = REAL.replace("@25.0335035,121.530118", "@25.1,121.6")
    assertEquals(25.0335035, MapsLink.parse(panned)!!.lat)
}
@Test fun fallsBackToTheCameraWithoutAPin() // "/@25.04,121.53,17z" and no !3d → (25.04, 121.53)
@Test fun noCoordinatesStillGivesTheName()  // "/maps/place/X/" → MapsPlace("X", null, null)
@Test fun outOfRangeCoordinatesAreDiscarded() // !3d95.0!4d121.5 → lat and lng both null
@Test fun decodesPunctuationAndMixedScripts() // "/place/MOS+Burger+%E6%91%A9%E6%96%AF+(%E4%BF%A1%E7%BE%A9)+%26+Co/" → "MOS Burger 摩斯 (信義) & Co"
@Test fun findsAShortLink()      // assertIs<MapsLink.Short>(find("https://maps.app.goo.gl/3oUGbkxLYE6jt7L29"))
@Test fun findsALegacyShortLink() // "https://goo.gl/maps/abc123" → Short
@Test fun findsAFullLink()       // find(REAL) is Full, and its url == REAL
@Test fun findsTheLinkInsideARealMultiLineShare() {
    val text = "鼎泰豐 信義店\n106臺北市大安區信義路二段194號\nhttps://maps.app.goo.gl/3oUGbkxLYE6jt7L29"
    assertEquals("https://maps.app.goo.gl/3oUGbkxLYE6jt7L29", MapsLink.find(text)!!.url)
}
@Test fun rejectsOtherHosts() {
    assertNull(MapsLink.find("see https://evil.example/maps/place/X/@1,2"))
    assertNull(MapsLink.find("https://maps.app.goo.gl.evil.example/abc"))  // suffix trick
    assertNull(MapsLink.parse("https://consent.google.com/maps/place/X/"))
    assertNull(MapsLink.find("http://maps.app.goo.gl/abc"))               // not https
}
@Test fun plainTextHasNoLink() // find("今天吃了很多") == null
```

- [ ] **Step 2: Run, watch it fail**

`$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'; .\gradlew.bat :shared:jvmTest --tests "*MapsLinkTest*"` → unresolved reference `MapsLink`.

- [ ] **Step 3: Implement `MapsLink` / `MapsPlace`**

Match the host **exactly** (parse scheme/host/path, never `contains`) — that is what defeats the `maps.app.goo.gl.evil.example` test. Percent-decoding must be done by hand (no `java.net.URLDecoder` in `commonMain`): read `%XX` as UTF-8 bytes, `+` as space.

- [ ] **Step 4: Run, watch it pass** — same command, all green.

- [ ] **Step 5: Commit** — `feat(maps-share): parse Google Maps links`.

---

### Task 2: Distance and venue matching

**Files:**
- Create: `shared/src/commonMain/kotlin/com/crazystudio/sportrecorder/domain/venue/SharedVenue.kt`
- Test: `shared/src/commonTest/kotlin/com/crazystudio/sportrecorder/domain/venue/SharedVenueTest.kt`

**Interfaces:**
- Consumes: `Venue`, `VenueName.sameAs`, `GeoPoint` (#83 / existing).
- Produces:
  ```kotlin
  fun GeoPoint.metersTo(other: GeoPoint): Double

  sealed interface SharedVenueMatch {
      data class Create(val name: String, val position: GeoPoint?) : SharedVenueMatch
      data class Use(val venue: Venue, val fillPosition: GeoPoint?) : SharedVenueMatch
      data class AskToMove(val venue: Venue, val to: GeoPoint) : SharedVenueMatch
  }
  object SharedVenue {
      const val SAME_PLACE_METERS = 50.0
      fun match(name: String, position: GeoPoint?, venues: List<Venue>): SharedVenueMatch
  }
  ```

- [ ] **Step 1: Write the failing tests**

```kotlin
@Test fun noSuchVenue_createsItWithTheMapsPosition()       // → Create("鼎泰豐 信義店", GeoPoint(25.03, 121.53))
@Test fun matchesByVenueNameRules()                         // existing "鼎泰豐 信義店", shared " 鼎泰豐  信義店 " → Use
@Test fun existingWithoutAPosition_getsTheMapsOne()         // Use(venue, fillPosition = maps point)
@Test fun existingWithinFiftyMetres_isUsedUntouched()       // ~30 m apart → Use(venue, fillPosition = null)
@Test fun existingFurtherThanFiftyMetres_asksToMove()       // ~120 m apart → AskToMove(venue, to = maps point)
@Test fun exactlyFiftyMetres_countsAsTheSamePlace()          // boundary is inclusive
@Test fun sharedLinkWithoutCoordinates_neverAsks()          // position = null, venue exists far away → Use(venue, null)
@Test fun distanceIsCorrectAcrossTheAntimeridian()          // (0, 179.9995).metersTo((0, -179.9995)) < 120.0
@Test fun distanceSanity()                                  // 1° latitude ≈ 111_195 m ± 50
```

- [ ] **Step 2: Run, watch it fail** — `:shared:jvmTest --tests "*SharedVenueTest*"`.
- [ ] **Step 3: Implement** — haversine for `metersTo`; `match` per the three spec cases, `<= SAME_PLACE_METERS` is "same place".
- [ ] **Step 4: Run, watch it pass.**
- [ ] **Step 5: Commit** — `feat(maps-share): decide create / use / ask-to-move for a shared place`.

---

### Task 3: `MapsLinkResolver` — one header, nothing more

**Files:**
- Create: `shared/src/commonMain/kotlin/com/crazystudio/sportrecorder/platform/MapsLinkResolver.kt`
- Create: `app/src/main/java/com/crazystudio/sportrecorder/platform/OkHttpMapsLinkResolver.kt`
- Modify: `app/src/main/java/com/crazystudio/sportrecorder/di/AppModule.kt`
- Test: `app/src/test/java/com/crazystudio/sportrecorder/platform/OkHttpMapsLinkResolverTest.kt`

**Interfaces:**
- Consumes: `MapsLink.Short` (Task 1).
- Produces:
  ```kotlin
  interface MapsLinkResolver {
      /** The 3xx `Location` for [link], or null on ANY failure. Never follows a redirect. */
      suspend fun resolve(link: MapsLink.Short): String?
  }
  class OkHttpMapsLinkResolver(client: OkHttpClient) : MapsLinkResolver {
      internal suspend fun resolveUrl(url: String): String?   // test seam: MockWebServer URLs can't be a Short
  }
  ```
  `resolve` delegates to `resolveUrl(link.url)`. **`resolve` does not re-validate the `Location`** — `MapsLink.parse` does that in `commonMain` (Task 4), where it is tested; the resolver only fetches.

- [ ] **Step 1: Write the failing tests** (MockWebServer)

```kotlin
@Test fun returnsTheLocationOfA302()        // enqueue 302 + Location: <REAL> → resolveUrl(...) == REAL
@Test fun doesNotFollowTheRedirect()        // 302 → second enqueued response must remain unrequested: server.requestCount == 1
@Test fun nonRedirectIsNull()               // 200 with a body → null
@Test fun redirectWithoutLocationIsNull()   // 302, no header → null
@Test fun timesOutAfterFiveSeconds()        // MockResponse().setHeadersDelay(6, SECONDS) → null, and elapsed < 6.5 s
@Test fun sendsNoCookiesOrAuth()            // recorded request has no Cookie / Authorization header
```

- [ ] **Step 2: Run, watch it fail** — `testDebugUnitTest --tests "*OkHttpMapsLinkResolverTest*"`.
- [ ] **Step 3: Implement** — build a dedicated client from the injected one with `followRedirects(false)`, `followSslRedirects(false)`, 5 s connect/read/call timeouts, no cookie jar; read headers only and close the body; every exception → `null`. Run on `Dispatchers.IO` (fine in `:app`).
- [ ] **Step 4: Register in Koin** — `single<MapsLinkResolver> { OkHttpMapsLinkResolver(OkHttpClient()) }`. Keep imports lexicographic.
- [ ] **Step 5: Run, watch it pass; run the full gate:**
  `.\gradlew.bat assembleDebug testDebugUnitTest :app:detekt :app:lintDebug :shared:jvmTest`
- [ ] **Step 6: Commit** — `feat(maps-share): resolve a short Maps link by reading one Location header`.

---

### Task 4: Editor ViewModel — open from shared text

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/crazystudio/sportrecorder/ui/diet/editor/EatTimeEditorViewModel.kt`
- Modify: `shared/src/commonMain/kotlin/com/crazystudio/sportrecorder/ui/diet/editor/EatTimeEditorUiState.kt`
- Modify: `app/src/main/java/com/crazystudio/sportrecorder/di/AppModule.kt` (one more `get()`)
- Create: `app/src/test/java/com/crazystudio/sportrecorder/fake/FakeMapsLinkResolver.kt`
- Test: `app/src/test/java/com/crazystudio/sportrecorder/ui/diet/editor/EatTimeEditorViewModelTest.kt`

**Interfaces:**
- Consumes: `MapsLink`, `MapsPlace` (T1); `SharedVenue`, `SharedVenueMatch`, `metersTo` (T2); `MapsLinkResolver` (T3); `VenueRepository.findOrCreate / setPosition / recordCount` (#83).
- Produces (consumed by Task 5's UI and Task 6's routing):
  - Constructor gains `mapsLinkResolver: MapsLinkResolver` as its **last** parameter.
  - Reads `savedStateHandle.get<String>("sharedText")` — the route field name Task 6 adds.
  - `EatTimeEditorUiState` gains `resolvingLink: Boolean = false` and `pendingMove: PendingMove? = null`, with `data class PendingMove(val venue: Venue, val to: GeoPoint, val movedRecords: Int)`.
  - `EditorMessage` gains `MapLinkUnreadable` and `NotAMapLink`.
  - `fun confirmMove()` and `fun cancelMove()`.

**Behaviour** (only when `sharedText` is non-null and the editor is in create mode):
`find` → null ⇒ `NotAMapLink`, **no resolver call**. `Short` ⇒ `resolve`; null ⇒ `MapLinkUnreadable`. Then `parse`; null, or a place with no name ⇒ `MapLinkUnreadable` (coordinates alone are discarded). Then `SharedVenue.match`:
`Create` ⇒ `venueRepository.findOrCreate(name, lat, lng, now)` and select it · `Use` ⇒ select it, and `setPosition` if `fillPosition` · `AskToMove` ⇒ select it and set `pendingMove` with `recordCount`.
`resolvingLink` is true from start to finish of that sequence. **Saving is never blocked by it.**

- [ ] **Step 1: Write the failing tests** — `FakeMapsLinkResolver` returns a configured `String?` and counts calls.

```kotlin
@Test fun sharedShortLink_createsTheVenueWithTheMapsPosition()   // venue at (25.0335035, 121.530118), not the fake GPS fix
@Test fun sharedFullLink_neverCallsTheResolver()                 // resolver.calls == 0
@Test fun nonMapsText_neverCallsTheResolver()                    // message == NotAMapLink, resolver.calls == 0, venue == null
@Test fun unresolvableLink_saysSoAndLeavesTheVenueEmpty()        // resolver returns null → MapLinkUnreadable
@Test fun aLocationToAnotherHost_isUnreadable()                  // resolver returns "https://consent.google.com/..." → MapLinkUnreadable
@Test fun linkWithoutAName_isUnreadableEvenWithCoordinates()
@Test fun existingVenueFarAway_asksBeforeMoving()                // pendingMove.movedRecords == records at that venue; position unchanged until confirmMove()
@Test fun confirmMove_movesIt_cancelMove_keepsIt()
@Test fun existingVenueWithoutPosition_getsTheMapsPosition()
@Test fun theRecordsOwnLocationIsStillTheDevicesFix()            // uiState.location comes from FakeLocationProvider, not Maps
@Test fun savingBeforeTheLinkResolves_savesWithoutAVenue()       // suspend the fake; save(); then release → saved record has no venue, no crash
@Test fun runningTheSameShareTwice_isIdempotent()                // two VMs over the same repo + text → one venue; second sees it within 50 m → no pendingMove
@Test fun editModeIgnoresSharedText()                            // eatTimeId > 0 with sharedText → resolver.calls == 0
@Test fun withoutSharedText_nothingChanges()                     // resolver.calls == 0, resolvingLink stays false
```

- [ ] **Step 2: Run, watch it fail** — `testDebugUnitTest --tests "*EatTimeEditorViewModelTest*"`.
- [ ] **Step 3: Implement** the behaviour above in the VM; add the state fields, messages and the two functions. Add the `get()` to the VM's Koin registration.
- [ ] **Step 4: Run, watch it pass; run the full gate.**
- [ ] **Step 5: Commit** — `feat(maps-share): open the editor from shared Maps text`.

---

### Task 5: Editor UI — resolving, asking, failing

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/crazystudio/sportrecorder/ui/diet/editor/EatTimeEditorSheet.kt`
- Modify: `shared/src/commonMain/composeResources/values/strings.xml`, `values-zh-rTW/strings.xml`
- Modify: `app/src/main/java/com/crazystudio/sportrecorder/ui/AppRoot.kt` (pass the two new callbacks)
- Test: `app/src/test/java/com/crazystudio/sportrecorder/ui/diet/editor/EatTimeEditorViewModelTest.kt` only if a message-consumption path changes; otherwise verified on device.

**Interfaces:**
- Consumes: `resolvingLink`, `pendingMove`, `MapLinkUnreadable`, `NotAMapLink`, `confirmMove()`, `cancelMove()` (Task 4).
- Produces: `EatTimeEditorSheet` gains `onConfirmMove: () -> Unit` and `onCancelMove: () -> Unit`. (`InsightsScreen` is the only shared screen iOS's `MainViewController` calls; the editor sheet is not, so no iOS change.)

- [ ] **Step 1: Add the strings, both locales**

| key | en | zh-rTW |
|---|---|---|
| `editor_venue_resolving` | Reading the map link… | 正在讀取地圖連結… |
| `editor_link_unreadable` | Couldn't read this map link — you can choose a place. | 沒辦法讀取這個地圖連結，可以手動選擇店家。 |
| `editor_not_map_link` | This doesn't look like a Google Maps link. | 這看起來不是 Google 地圖的連結。 |
| `editor_move_title` | Update %1$s's position? | 要更新「%1$s」的位置嗎？ |
| `editor_move_body` *(plurals)* | one: %1$d past record will move to where Google Maps puts it. · other: %1$d past records will move to where Google Maps puts it. | other: 有 %1$d 筆過去的紀錄會移到 Google 地圖標示的位置。 |
| `editor_move_confirm` | Update | 更新 |
| `editor_move_keep` | Keep | 保留 |

- [ ] **Step 2: Show the three states in the sheet** — while `resolvingLink`, the venue row's value reads `editor_venue_resolving`; when `pendingMove != null`, an `AlertDialog` (title + plural body with `movedRecords`, confirm/keep; dismissing = keep); both new messages through the editor's existing transient-message path (the same one `VenuePositionUpdated` uses).
- [ ] **Step 3: Wire `onConfirmMove = vm::confirmMove`, `onCancelMove = vm::cancelMove` in `AppRoot`.**
- [ ] **Step 4: Run the full gate.**
- [ ] **Step 5: Commit** — `feat(maps-share): show resolving, the move question and link failures`.

---

### Task 6: Receive the share

**Files:**
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/java/com/crazystudio/sportrecorder/MainActivity.kt`
- Modify: `app/src/main/java/com/crazystudio/sportrecorder/ui/nav/Route.kt`
- Modify: `app/src/main/java/com/crazystudio/sportrecorder/ui/AppRoot.kt`
- Test: `app/src/androidTest/java/com/crazystudio/sportrecorder/ShareEntryTest.kt`

**Interfaces:**
- Consumes: the `"sharedText"` SavedStateHandle key (Task 4).
- Produces:
  - `Route.EatTimeEditor(val eatTimeId: Int = 0, val sharedText: String? = null)`.
  - `AppRoot(sharedText: String? = null, launchedByShare: Boolean = false, onShareDone: () -> Unit = {})`.

- [ ] **Step 1: Manifest** — on `MainActivity`, an intent filter: action `android.intent.action.SEND`, category `DEFAULT`, mimeType `text/plain`. Leave launch mode at the default (the spec relies on the share opening inside the sender's task).
- [ ] **Step 2: `MainActivity`** — `launchedByShare = intent?.action == Intent.ACTION_SEND` (stable across recreation, because the intent is retained); `sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)` **only when `savedInstanceState == null`**, else null — this is what stops a rotation from opening a second editor. Pass both, plus `onShareDone = ::finish`, to `AppRoot`.
- [ ] **Step 3: `AppRoot`** — when `sharedText != null`, navigate once to `Route.EatTimeEditor(sharedText = it)`. When `launchedByShare` and the editor destination, having been on the back stack, is no longer on it (saved or dismissed), call `onShareDone()`. Drive this from the restored back stack, **not** from a composable's `onDispose` — `onDispose` also fires on rotation, which would close the app mid-edit.
- [ ] **Step 4: Write the instrumented test** (no network needed — it uses non-Maps text)

```kotlin
@Test fun aShareOpensTheEditor()              // ActivityScenario with SEND + EXTRA_TEXT "hello" → editor visible, "not a Google Maps link" shown
@Test fun recreating_keepsOneEditorAndDoesNotFinish() // scenario.recreate() → exactly one editor (onAllNodesWithText(<save label>).assertCountEquals(1)), state RESUMED
@Test fun aNormalLaunchNeverFinishesOnSave()  // MAIN launch → open editor → save → activity still RESUMED
```

- [ ] **Step 5: Run it** on the API 35 AVD:
  `$env:ANDROID_SERIAL = 'emulator-5554'; .\gradlew.bat :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.crazystudio.sportrecorder.ShareEntryTest"` → 3/3.
- [ ] **Step 6: Check by hand on the emulator** with a real share link, and say what you saw:
  `adb shell am start -a android.intent.action.SEND -t text/plain --es android.intent.extra.TEXT "鼎泰豐 信義店 https://maps.app.goo.gl/3oUGbkxLYE6jt7L29" -n com.crazystudio.sportrecorder/.MainActivity`
  Editor opens → venue row shows 「鼎泰豐 信義店」 → Save → activity finishes. Repeat after a venue of that name exists far away → the move question appears with a count.
- [ ] **Step 7: Run the full gate; commit** — `feat(maps-share): receive a shared place from Google Maps`.

---

## Final verification

- [ ] Full gate green; `ios-shared` green on the PR (Tasks 1, 2, 4 and 5 touch `commonMain`).
- [ ] Instrumented: `ShareEntryTest` 3/3 on API 35.
- [ ] By hand, both locales: real share → venue filled → Save returns to the sender; non-Maps text → message, no venue; airplane mode → "couldn't read" and the meal still saves.
- [ ] Update `docs/DEVELOPMENT.md`'s doc index with this spec and plan, and flip the spec's **Status** to implemented.
- [ ] Before release: the Play data-safety answers may need revisiting — a new network destination (`maps.app.goo.gl`). Owner's call in the Console.
