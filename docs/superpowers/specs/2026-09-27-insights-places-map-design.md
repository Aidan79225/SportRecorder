# 回顧 / Insights — 地點卡改為地圖 design

2026-09-28:marker 合併與全螢幕縮放見 `2026-09-28-insights-map-clusters-and-zoom-design.md`

- **Date:** 2026-09-27
- **Status:** Implemented on `claude/insight-page-improvements-2fe29z` (this spec + code in one PR).
- **Supersedes:** bucket **B3** of `2026-09-21-insights-improvements-design.md` (place names via a
  geocoder). The owner asked for a map instead; this is that.

## Goal

The 地點 / Places card printed `📍 25.033, 121.565 · 3` — a coordinate is not a place, and nobody
reads their week back from decimals. Replace the list with **a still map**: the period's places
drawn as dots on real streets, each dot carrying its meal count.

## Design

**What it is.** One picture, not a map app. `PlacesMap` fits every place of the selected period
into a 220 dp frame at the largest zoom that keeps them all inside a 32 dp margin (a single place
gets a fixed neighbourhood zoom, 15). It does not pan, zoom or open anything on tap. Under it, one
line of text (「3 個地點 · 12 餐記了位置」) is the map's text equivalent.

**How it is drawn.** Raster tiles from OpenStreetMap, placed by hand in `commonMain`:

- `ui/insights/map/MapProjection.kt` — pure Web Mercator math: project a coordinate to tile
  units, fit a bounding box to a viewport, enumerate the tiles that cover it (columns wrap at
  the antimeridian, rows stop at the poles). Unit-tested in `commonTest`, no Compose imports.
- `ui/insights/map/PlacesMap.kt` — a `BoxWithConstraints` that lays each tile down with Coil's
  `AsyncImage` at its computed offset, then a marker per place, then the attribution.
- Tiles are 256 px; above 2× density they are scaled by `density / 2` so a 3× phone gets a
  gently upscaled tile instead of a postage stamp or a 3× blur.
- Markers are all `primary` with a `surface` ring, radius 14 dp growing 1 dp per meal up to 8.
  Same colour everywhere: a place is a place, not a good or bad one.

**Why not a map SDK.** Google Maps needs an API key, a billing-enabled Cloud project, Play
services, and it is Android-only — the shared screen would need a platform slot and iOS would
have nothing. Tiles + Coil are already multiplatform (Coil is the app's photo loader), need no
key, and the whole geometry is testable on the JVM. If pan/zoom is ever wanted, the tile layer
is the piece to swap, not the card.

**Network.** `:app` gains `coil-network-okhttp`, and `SportApplication` becomes Coil's
`SingletonImageLoader.Factory` so the OkHttp client sends a User-Agent naming the app
(`SportRecorder/<version> (Android; +repo URL)`), which OSM's tile policy requires. Coil's disk
cache keeps tiles across sessions, so a place you eat at every day is fetched once. Offline, the
frame stays `surfaceVariant` grey with the markers still drawn on it.

**OSM tile policy, honestly.** OpenStreetMap's servers are volunteer-run and their policy asks
apps to identify themselves and cache, and reserves the right to block heavy use. A small diary
whose map shows a handful of tiles per visit is well inside that, but it is goodwill, not a
contract. The provider URL is a single constant (`TILE_URL_TEMPLATE` in `PlacesMap.kt`); if usage
ever grows, point it at a keyed provider (MapTiler, Stadia, Thunderforest all serve the same
z/x/y scheme) and nothing else changes. Attribution 「© OpenStreetMap contributors」 stays on the
map in every case — it is a licence term, not a courtesy.

**iOS.** `commonMain` compiles unchanged; without a Ktor network fetcher registered on iOS the
tiles simply do not load (grey frame, markers still drawn). Add `coil-network-ktor3` when the
iOS host exists.

## 初衷對照 / North-Star check

**Does this serve the mission?** Yes — it is *Reflect* in the most literal sense: 「這週我在哪裡吃
飯」 becomes something you can see at a glance, not decode. It replaces a card that did not serve
anyone with one that does, and adds nothing to *Capture*.

Against the red flags:

- **評價使用者** — no. Every marker is the same colour; the count is a number, not a heat map
  of "too often". There is no "home vs. out" split, no "you ate out N times" framing.
- **記錄變成負擔** — untouched. Location was already optional at capture time and stays so.
- **外在壓力取代覺察** — none added. Nothing to compare against, nothing to keep up.
- **教練式命令的語氣** — the only copy is a count line and the attribution.

**Where it could drift, and the deliberate line:**

- **Privacy.** Fetching a tile tells the tile server which ~600 m squares (zoom 16) of the world
  the user is looking at, from their IP. That is a real change from today, where nothing about
  location left the device. The previous spec's stance was "if it can't stay on-device, drop the
  card" — the owner has chosen the map knowing this. What keeps it proportionate: only the
  period's places are shown (never the whole history at once), tiles are cached so repeat visits
  send nothing, no coordinate is ever sent (only tile indices), and there is no account or
  identifier in the request beyond the app's name. If this ever needs to be tighter, the
  mission-aligned move is a settings toggle that swaps the map back to the count line, not a
  weaker map.
- **Growing into a map app.** Pan, zoom, "open in Google Maps", place search — each is a small
  ask that would slowly turn a reflection card into a navigation tool. The card is a picture on
  purpose. Tapping a marker to see that place's meals would be the one extension that stays on
  mission (it is B4's "look closer" gesture), and it should reuse B4's sheet when that lands.

## Testing

- **Unit (`shared/src/commonTest`, `:shared:jvmTest`)** — `MapProjectionTest`: projection
  origin / doubling per zoom / orientation / a known Taipei tile / latitude clamp; fit for empty,
  single, identical, two-place and max-zoom-capped inputs; tile enumeration covers the viewport,
  wraps columns and drops rows past the poles.
- **Static** — `:app:detekt`, `:app:lintDebug`.
- **Manual (device, both locales)** — a period with several places shows all of them inside the
  frame; one place is centred at street level; markers grow with count; attribution visible;
  airplane mode shows the grey frame with markers; TalkBack reads the summary line once for the
  map.

## Verification note (2026-09-27)

Same container limits as the 09-21 plan: no Android SDK and Google Maven is blocked, so no
Gradle task runs here. `MapProjection.kt` + `MapProjectionTest.kt` were compiled with a
standalone Kotlin 2.3.10 compiler and the 12 tests pass under JUnit. The Coil 3.4.0 signatures
used in `SportApplication` (`OkHttpNetworkFetcherFactory(Call.Factory)`,
`SingletonImageLoader.Factory.newImageLoader(Context)`) were checked against the published jars.
`PlacesMap.kt` and the screen change are **unverified by build** and gated on CI.
