# 從 Google 地圖分享進來 · Share a place from Google Maps design

- **Date:** 2026-10-10
- **Status:** Designed, not implemented. Issue [#84](https://github.com/Aidan79225/SportRecorder/issues/84).
- **Builds on:** `2026-10-05-venue-design.md` (#83) — the `Venue` entity, `VenueRepository`, and the
  editor's venue row all exist; this design only adds a way in.

## Goal

People find a restaurant in Google Maps. Getting it into a meal record today means switching app,
opening the editor and typing the name. This design makes it: **in Maps, tap Share → SportRecorder
→ Save.** The venue's name is never typed, and its position comes from Maps itself.

The issue originally scoped this as "paste a URL into the editor". The owner chose the Android
share sheet instead: no copying, no app switching, and the share is itself the user's explicit
action, so no separate "parse" button is needed.

## What a real share link contains (spike, 2026-10-10)

Designed against real data, not assumptions. A share link was taken from Google Maps for
鼎泰豐 信義店 and resolved with a plain HTTP client:

```
GET https://maps.app.goo.gl/3oUGbkxLYE6jt7L29

HTTP/1.1 302 Found
Location: https://www.google.com/maps/place/%E9%BC%8E%E6%B3%B0%E8%B1%90+%E4%BF%A1%E7%BE%A9%E5%BA%97/@25.0335035,121.530118,17z/data=!4m6!3m5!1s0x3442a9821392326d:0x93faef9e2359365c!8m2!3d25.0335035!4d121.530118!16s%2Fg%2F1td2dltc?entry=tts&g_ep=…&skid=…
```

What this settled:

1. **A short link is a plain 302 — no JavaScript.** The `Location` header alone carries everything,
   so the client never needs to load the Google Maps page itself. One request to `maps.app.goo.gl`,
   one header read.
2. **The resolved URL carries both the name and the pin.** Name in `/place/<name>/`, pin in
   `!3d<lat>!4d<lng>`. The feared "`?q=…&ftid=…` with no coordinates" form did not occur here; it is
   still handled (see *Failure*) because it may occur elsewhere.
3. **`@<lat>,<lng>` is the camera, not the pin.** They coincide only when the map is centred on the
   place; after any pan they differ. The pin (`!3d!4d`) is authoritative; `@` is a fallback.
4. **The name does not depend on the share text's format** — the URL carries it. The rest of the
   share text (the address, any name the Maps app adds) is **not used**, which removes any
   dependency on how the Maps app formats its share.

## Decisions taken with the owner

| Question | Decision |
|---|---|
| Entry point | **Android share sheet** (`ACTION_SEND`, `text/plain`). Not a paste field. |
| What a share opens | **A new meal record**, time = now (editable), venue already filled in. |
| Maps position differs from an existing venue's | **Maps wins, after asking**: "Update 〈name〉's position to Google Maps? N past records will move." |

## Architecture

Three units, following the existing pattern (platform capability = `commonMain` interface +
`:app` Android implementation, as `LocationProvider` already does; OkHttp already lives in `:app`).

| Unit | Where | Responsibility |
|---|---|---|
| `MapsLink` (parser) | `commonMain` `domain/venue/`, pure | Find a Maps URL in shared text; extract name and coordinates from a full Maps URL. |
| `MapsLinkResolver` | `commonMain` interface; `:app` OkHttp impl | Turn a short link into its full URL by reading one `Location` header. |
| Share entry | `:app` (`MainActivity` + nav) | Receive `ACTION_SEND`, open the editor with the shared text. |

The editor ViewModel gains one initialisation path, "opened from shared text": it runs the parser,
calls the resolver only when the link is short, then matches the result to a venue. All of that is
in the VM, so it is unit-tested with a fake resolver.

Two alternatives were rejected: a separate receiving activity that resolves first and then
launches the app (an extra component and an extra hand-off for no gain), and a confirmation screen
before the editor (an extra step, against the whole point of the feature).

## Parsing rules (`MapsLink`, pure)

**Finding a link in shared text.** The first substring matching one of:
- `https://maps.app.goo.gl/<id>` — short; needs resolving
- `https://goo.gl/maps/<id>` — legacy short; needs resolving
- `https://www.google.com/maps/…` or `https://maps.google.com/…` — full; parse directly

Anything else — no link, or a link to any other host — is "not a Maps link".

**Extracting from a full URL.**
- **Name:** the path segment after `/place/`, percent-decoded with `+` read as a space, then passed
  through `VenueName.normalize`. Absent → no name.
- **Coordinates:** `!3d<lat>!4d<lng>` if present; otherwise `@<lat>,<lng>`; otherwise none. Both
  values must parse as numbers within valid ranges (lat ±90, lng ±180), or they are discarded.

The result is `MapsPlace(name: String?, lat: Double?, lng: Double?)`. Any field may be missing; the
caller degrades accordingly.

## Resolving a short link (`MapsLinkResolver`)

This is the only part that touches the network, and it is deliberately narrow.

- **Only ever contacts `maps.app.goo.gl` or `goo.gl`.** A share target receives arbitrary text from
  any app; it must never make a request to a host chosen by that text. The allowlist is enforced
  before any request is made, in `commonMain`, and tested.
- **Redirects are not followed.** One request; read the `Location` header of the 3xx response.
- **The `Location` is validated** against the full-URL hosts above. Anything else — a consent page,
  another shortener, an error page — is a failure, never a second hop.
- **5-second timeout.** A share must not hang on a slow network.
- No cookies, no auth, no body read.

## Matching the result to a venue

Using `VenueRepository` from #83:

1. **No venue with this name** (`VenueName.sameAs`) → create it with the Maps name **and the Maps
   coordinates**. It does *not* adopt the device's current GPS — this is what fixes the weakness of
   #83's Ruling 6 (a delivery eaten at home pinning the restaurant to home) for every venue that
   arrives through Maps.
2. **Venue exists with no position, or 50 m or less from the Maps position** → use it; fill in the
   position if it had none.
3. **Venue exists more than 50 m away** → select it, and ask whether to move it to the Maps
   position, stating how many past records will move. The question is asked **once, in the editor,
   as soon as the link resolves** — not at save time, so it is never mixed up with saving the meal.
   Yes → `setPosition`. No, or dismissing it → keep. Either way the meal is attached to the venue.

**Why 50 m.** Ordinary GPS error sits inside it; a difference big enough to put the marker on a
different storefront sits outside it. Asking for every few metres of jitter would make the share
flow nag. The distance is a pure haversine function, unit-tested.

The record's own `location` (*where you were*) is captured exactly as for any new record and is
never derived from the venue — #83's two-facts rule is unchanged.

## Failure — a share never blocks a meal

Every failure opens the editor anyway, time = now, so the meal can still be saved:

| What happened | Result |
|---|---|
| No network / timeout / non-3xx / bad `Location` | Editor opens without a venue; gentle line: "Couldn't read this map link — you can choose a place." |
| Link resolved but carries no name | Same as above. Coordinates alone are discarded: a venue without a name is not a venue. |
| Link resolved with a name but no coordinates | Venue matched/created by name only; no position written. |
| Shared text holds no Maps link (e.g. a news article) | Editor opens without a venue; "This doesn't look like a Google Maps link." |

## Task lifecycle

`MainActivity` uses the default launch mode, so a share opens the app **inside the sharing app's
task** — standard Android share-target behaviour; Back returns to Maps. **Only when the activity was
launched by a share**, saving the meal or dismissing the editor finishes it, and the user lands back
in Maps, where they were. Every other way of opening the editor behaves exactly as today. The share
was a side trip, not a change of app.

## A known platform cost

An `ACTION_SEND` `text/plain` filter cannot be narrowed by content: SportRecorder will appear in the
share sheet for **every** text share, not only Maps. This is an Android limitation, not a design
choice. Non-Maps text is handled by the last row of *Failure*.

## Privacy and Play

This adds a network destination that is not the user's Drive: a request to `maps.app.goo.gl`
carrying the short link the user just shared. No personal data the app holds is sent. It may still
change the Play data-safety answers; per `.claude/skills/release/SKILL.md` that is the owner's call
in the Console, and it should be revisited before this ships.

## Testing

- **`MapsLink`**, `commonTest` (JVM + iOS), table-driven, seeded with the **real URL above**, plus:
  `@` and `!3d!4d` disagreeing (pin must win), only `@`, no coordinates, out-of-range numbers, a
  percent-encoded Chinese name with `+`, legacy `goo.gl/maps`, a link embedded mid-text, and a
  non-Maps host (must be rejected).
- **Host allowlist**, `commonTest`: an `evil.example` URL in shared text is never handed to the
  resolver.
- **Distance threshold**, `commonTest`: inside/outside 50 m, and across the antimeridian.
- **Editor VM**, `app/src/test`: each venue-matching branch and each failure row, with a fake
  resolver.
- **OkHttp resolver**: against a local `MockWebServer`, proving it reads `Location` without
  following, rejects a disallowed `Location`, and honours the timeout. A live call to `goo.gl` is
  not a test — it depends on the network and on Google.

## Out of scope

iOS share extension; a paste field in the editor; any change to how #83 creates venues from inside
the editor.

## 初衷對照 / North-Star check

- ✅ **記錄要快、要輕鬆** — "open app, find the editor, type the name" becomes "Share → pick the app →
  Save", and not one character of the name is typed.
- ✅ **The venue's position becomes trustworthy** — it comes from Maps, not from wherever the user
  happened to be standing.
- ⚠️ **Drift risk — silently writing wrong data.** The venue is pre-filled and visible before Save,
  and can be changed or cleared; moving an existing venue changes history, so it is asked first,
  with the number of records it affects.
- ⚠️ **Drift risk — the app reaching out on its own.** It makes exactly one request, only to a
  Google shortener, only because the user just shared a link with it.
- ⚠️ **A small intrusion** — appearing in every text share sheet. Platform-imposed; handled so it
  can never go wrong, only be unnecessary.
