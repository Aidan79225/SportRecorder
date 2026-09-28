# Insights map: clustered markers + full-screen zoom — Design

**Status:** implemented in PR #70 (approved 2026-09-28; adds a last-loaded-level tile underlay so level changes never draw blank, see §3) · **Builds on:** `2026-09-27-insights-places-map-design.md`

## Problem

The places card fits every place of the period into a 220 dp still map at the largest zoom that
keeps them all inside (max 16). When places sit within a few hundred metres of each other, or a
few close places share the map with one far one, their markers overlap and the card reads as a
blob. The owner asked for zoom, or something better.

## Decisions

1. **Cluster overlapping markers on the card.** Still a picture, but an honest one: markers that
   would overlap at the fitted zoom merge into one marker carrying the summed meal count.
2. **Tap the card to open a full-screen, pinch-zoomable map.** The card stays a reflection; the
   full-screen view is where you look closer. Same tiles, same neutral markers, clusters that split
   apart as you zoom in. No marker tap action (the map spec names "tap a marker to see that
   place's meals" as the one mission-aligned extension; it is not in this change).

## 1. Clustering — `MarkerClusters.kt` (commonMain, pure)

```kotlin
data class MapCluster(val lat: Double, val lng: Double, val count: Int, val places: Int)

fun clusterPlaces(places: List<LocationCount>, viewport: MapViewport, mergeDistancePx: Float): List<MapCluster>
```

Greedy and deterministic: sort places by count descending, then by lat/lng; each place joins the
first cluster whose current centre is closer than `mergeDistancePx` in viewport pixels (centre =
count-weighted mean of its members, recomputed on merge); otherwise it starts a cluster. Output
order: count descending. `mergeDistancePx` is the marker diameter at the largest marker size, so
two markers never overlap. Tests: no merge when far apart; merge when overlapping; weighted centre;
counts summed; `places` counted; a far outlier stays alone; stable order; empty input.

The card's caption keeps counting **places**, not clusters (「N 個地點 · M 餐」), so merging never
under-reports where the user has been.

## 2. Camera and fractional zoom — `MapProjection.kt`

```kotlin
data class MapCamera(val zoom: Double, val centerLat: Double, val centerLng: Double) {
    fun zoomedBy(factor: Float, minZoom: Double, maxZoom: Double): MapCamera   // log2 space, clamped
    fun pannedBy(dxPx: Float, dyPx: Float, viewport: MapViewport): MapCamera    // moves the centre by screen pixels
    fun zoomedAround(factor, focusPx, viewport): MapCamera                     // keeps the point under the fingers fixed
}

fun MapViewport.Companion.at(camera: MapCamera, widthPx: Int, heightPx: Int, tileSizePx: Int): MapViewport
```

`MapViewport` gains `renderScale: Float = 1f`: tiles are laid out and drawn at
`tileSizePx * renderScale`, and `pixelFor` uses the same. `at(camera)` picks the integer level
`z = round(camera.zoom)` and `renderScale = 2^(zoom − z)`, so a pinch between levels scales the
current tiles smoothly and the next level's tiles are requested when `z` changes. `fit(...)` is
unchanged (scale 1). Zoom range for the full screen: `[fittedZoom − 2, 19]` (OSM's max level).
`MapCamera.fromViewport(viewport)` converts the card's fit into the initial camera.

## 3. Full-screen map — `FullScreenPlacesMap.kt` (commonMain UI)

`@Composable fun FullScreenPlacesMap(locations, onDismiss)`: a `Dialog(properties =
DialogProperties(usePlatformDefaultWidth = false))` filling the screen; `BoxWithConstraints` →
initial camera from `MapViewport.fit`; `pointerInput` with `detectTransformGestures` (pan + pinch
around the centroid) and a second `pointerInput` with `detectTapGestures(onDoubleTap = zoom in ×2
around the tap)`; `TileLayer` and cluster markers from `clusterPlaces(locations, viewport,
diameter)` recomputed per camera; a close button (top-end, `IconButton` with a content
description) and the OSM attribution (bottom-end). Whole map is one semantics node with the same
summary as the card, plus the close button. Colours and marker style identical to the card.

**Tile underlay (added in review).** `TileLayering.kt` `layeredTiles(camera, w, h, tileSizePx,
settledLevel, loaded)` draws the last fully-loaded level's tiles (only those in `loaded`, at most
3 levels away, at the current camera) under the new level's tiles until every new tile has
loaded, so a level change never shows blank tiles. `TileLayer` reports each successful load via
`onLoaded(TileKey)`; the full-screen map settles a level when all of its tiles are loaded and
prunes `loaded` to that level.

The card (`PlacesMap`) gets `onClick: (() -> Unit)?`; `LocationsCard` holds `var expanded` and
renders `FullScreenPlacesMap` when true. The card's content description gains 「點一下可放大」.

## Privacy / tile-usage line

Zooming in asks OSM for tiles at up to zoom 19 (~75 m squares) for the area the user pans over —
more precise than the card's 16, still only tile indices, only for the period's places, cached by
Coil. This stays within the stance the map spec set; the User-Agent identification and disk cache
remain. If tile traffic ever matters, the template constant is still the single switch.

## Testing

- commonTest: `MarkerClustersTest`, `MapCameraTest` (zoom clamp, log2 zoom, pan by pixels moves
  the centre by the right degrees at a given zoom, zoom-around keeps the focus point fixed within
  1 px, `at()` scale between levels, `fromViewport` round-trip), `MapViewport.renderScale` in tiles
  and `pixelFor`.
- androidTest: `PlacesMapInteractionTest` — the card with `onClick` opens `FullScreenPlacesMap`
  (close button visible) and the close button dismisses it; clusters render fewer markers than
  places for two overlapping places (marker count via semantics? markers are not semantics nodes —
  assert through `clusterPlaces` in JVM instead; the UI test covers open/close only).
- Manual: pinch, drag, double-tap on the emulator; clusters split when zooming in.

## 初衷對照 / North-Star check

Both changes make the reflection more truthful (no blob, no hidden places) without turning the
card into a navigation tool: the card stays still; the full-screen view has no search, no routing,
no "open in Maps", and markers stay one neutral colour. Drift to avoid: adding marker actions
beyond "see that place's meals" later.

## Docs

`docs/DEVELOPMENT.md` §6 row; a status line on the 2026-09-27 map spec pointing here.
