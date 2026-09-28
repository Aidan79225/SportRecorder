# Insights Map Clusters and Zoom Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** The places card merges overlapping markers into counted clusters, and tapping it opens a full-screen map with pinch-zoom, drag and double-tap, where clusters split apart as you zoom in.

**Architecture:** Pure geometry stays in `commonMain` (`MarkerClusters.kt`, `MapCamera` + `renderScale` in `MapProjection.kt`) with JVM/iOS unit tests. `PlacesMap` renders clusters and takes an `onClick`; `FullScreenPlacesMap` is a `Dialog` (like the photo viewer) driving a `MapCamera` from gestures. `LocationsCard` wires the two.

**Tech Stack:** Compose Multiplatform (`Dialog`, `pointerInput`, `detectTransformGestures`, `detectTapGestures`), Coil `AsyncImage` tiles, kotlin.test.

**Spec:** `docs/superpowers/specs/2026-09-28-insights-map-clusters-and-zoom-design.md`

## Global Constraints

- Branch `claude/insights-map-zoom` off `master`. Commits end with `Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>`.
- `commonMain`/`commonTest` use Kotlin-common APIs only (CI runs `iosSimulatorArm64Test`). `MagicNumber` is not enforced under `ui/`, but name constants anyway (`MAX_ZOOM = 19.0`, etc.). Lines ≤ 120.
- Gate: `.\gradlew.bat assembleDebug testDebugUnitTest :app:detekt :app:lintDebug :shared:jvmTest`. Emulator: `$env:ANDROID_SERIAL="<adb devices>"`, quoted `-P` filter, `JAVA_HOME` set in the same PowerShell call.
- Existing API to preserve: `MapViewport(zoom, originX, originY, widthPx, heightPx, tileSizePx)` and `MapViewport.fit(...)` keep their signatures (new field `renderScale` defaults to `1f`); `PlacesMap(locations, contentDescription, modifier)` gains `onClick: (() -> Unit)? = null` only.
- Strings (both `values/strings.xml` and `values-zh-rTW/strings.xml` in `shared/src/commonMain/composeResources/`): `insights_map_expand_hint` = "Tap to enlarge" / 「點一下可放大」, `insights_map_close` = "Close map" / 「關閉地圖」.
- Copy tone: neutral; markers keep one colour; no marker actions.

---

### Task 1: `MarkerClusters` (commonMain, TDD)

**Files:**
- Create: `shared/src/commonMain/kotlin/com/crazystudio/sportrecorder/ui/insights/map/MarkerClusters.kt`
- Create: `shared/src/commonTest/kotlin/com/crazystudio/sportrecorder/ui/insights/map/MarkerClustersTest.kt`

**Produces:** `data class MapCluster(val lat: Double, val lng: Double, val count: Int, val places: Int)`; `fun clusterPlaces(places: List<LocationCount>, viewport: MapViewport, mergeDistancePx: Float): List<MapCluster>`.

- [ ] **Step 1: Failing tests**

```kotlin
package com.crazystudio.sportrecorder.ui.insights.map

import com.crazystudio.sportrecorder.domain.insights.LocationCount
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MarkerClustersTest {
    // Zoom 16, 256 px tiles, 1000×1000 viewport centred on Taipei 101.
    private val viewport = MapViewport.fit(
        points = listOf(com.crazystudio.sportrecorder.domain.model.GeoPoint(25.0340, 121.5645)),
        widthPx = 1000, heightPx = 1000, tileSizePx = 256, paddingPx = 0, maxZoom = 16,
    )!!

    private fun place(lat: Double, lng: Double, count: Int) = LocationCount(lat, lng, count)

    @Test fun farApart_stayApart() {
        val clusters = clusterPlaces(listOf(place(25.0340, 121.5645, 2), place(25.0400, 121.5700, 1)), viewport, 40f)
        assertEquals(2, clusters.size)
    }

    @Test fun overlapping_merge_withSummedCount_andWeightedCentre() {
        val a = place(25.0340, 121.5645, 3)
        val b = place(25.03401, 121.56451, 1) // ~1.5 m away: same pixel at zoom 16
        val clusters = clusterPlaces(listOf(a, b), viewport, 40f)
        assertEquals(1, clusters.size)
        assertEquals(4, clusters[0].count)
        assertEquals(2, clusters[0].places)
        val expectedLat = (a.lat * 3 + b.lat * 1) / 4
        assertTrue(abs(clusters[0].lat - expectedLat) < 1e-9)
    }

    @Test fun mergeDistance_isInPixels_notDegrees() {
        val a = place(25.0340, 121.5645, 1)
        val b = place(25.0340, 121.5648, 1) // ~30 m: ~26 px apart at zoom 16 with 256 px tiles
        assertEquals(1, clusterPlaces(listOf(a, b), viewport, 40f).size)
        assertEquals(2, clusterPlaces(listOf(a, b), viewport, 10f).size)
    }

    @Test fun outlier_staysAlone_andOrderIsCountDescending() {
        val clusters = clusterPlaces(
            listOf(place(25.0340, 121.5645, 1), place(25.03401, 121.56451, 1), place(25.1, 121.7, 5)),
            viewport, 40f,
        )
        assertEquals(listOf(5, 2), clusters.map { it.count })
    }

    @Test fun empty_isEmpty() = assertEquals(emptyList(), clusterPlaces(emptyList(), viewport, 40f))
}
```

- [ ] **Step 2: Implement**

```kotlin
package com.crazystudio.sportrecorder.ui.insights.map

import com.crazystudio.sportrecorder.domain.insights.LocationCount
import kotlin.math.hypot

/** One marker on the map: a place, or several places whose markers would overlap. */
data class MapCluster(val lat: Double, val lng: Double, val count: Int, val places: Int)

/**
 * Merge places whose markers would overlap at this [viewport] (centres closer than
 * [mergeDistancePx]) into single markers with summed counts. Greedy and deterministic: biggest
 * places seed clusters first; a cluster's centre is the count-weighted mean of its members.
 */
fun clusterPlaces(places: List<LocationCount>, viewport: MapViewport, mergeDistancePx: Float): List<MapCluster> {
    class Working(var lat: Double, var lng: Double, var count: Int, var places: Int)
    val clusters = mutableListOf<Working>()
    val ordered = places.sortedWith(compareByDescending<LocationCount> { it.count }.thenBy { it.lat }.thenBy { it.lng })
    for (place in ordered) {
        val (px, py) = viewport.pixelFor(place.lat, place.lng)
        val target = clusters.firstOrNull { c ->
            val (cx, cy) = viewport.pixelFor(c.lat, c.lng)
            hypot((cx - px).toDouble(), (cy - py).toDouble()) < mergeDistancePx
        }
        if (target == null) {
            clusters += Working(place.lat, place.lng, place.count, 1)
        } else {
            val total = target.count + place.count
            target.lat = (target.lat * target.count + place.lat * place.count) / total
            target.lng = (target.lng * target.count + place.lng * place.count) / total
            target.count = total
            target.places += 1
        }
    }
    return clusters.map { MapCluster(it.lat, it.lng, it.count, it.places) }.sortedByDescending { it.count }
}
```

- [ ] **Step 3:** `:shared:jvmTest` green → commit `feat(insights): cluster overlapping map markers (pure logic)`.

---

### Task 2: `MapCamera`, fractional zoom, `renderScale` (commonMain, TDD)

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/crazystudio/sportrecorder/ui/insights/map/MapProjection.kt`
- Modify: `shared/src/commonTest/kotlin/com/crazystudio/sportrecorder/ui/insights/map/MapProjectionTest.kt` (add cases)
- Create: `shared/src/commonTest/kotlin/com/crazystudio/sportrecorder/ui/insights/map/MapCameraTest.kt`

**Produces:**
- `WebMercator.unproject(x: Double, y: Double, zoom: Int): GeoPoint`
- `MapViewport.renderScale: Float = 1f`, `MapViewport.drawnTilePx: Double`, `TilePlacement.sizePx: Int`
- `data class MapCamera(val zoom: Double, val centerLat: Double, val centerLng: Double)` with `companion { MAX_ZOOM = 19.0; fun fromViewport(v: MapViewport): MapCamera }`, `fun zoomedBy(factor: Float, minZoom: Double, maxZoom: Double = MAX_ZOOM): MapCamera`, `fun pannedBy(dxPx: Float, dyPx: Float, viewport: MapViewport): MapCamera`, `fun zoomedAround(factor: Float, focusXPx: Float, focusYPx: Float, viewport: MapViewport, minZoom: Double, maxZoom: Double = MAX_ZOOM): MapCamera`
- `fun MapViewport.Companion.at(camera: MapCamera, widthPx: Int, heightPx: Int, tileSizePx: Int): MapViewport`

- [ ] **Step 1: Tests** (`MapCameraTest`; add `unproject` round-trip and `renderScale` cases to `MapProjectionTest`)

```kotlin
class MapCameraTest {
    private val taipei = GeoPoint(25.0340, 121.5645)

    @Test fun fromViewport_roundTripsTheCentre() {
        val v = MapViewport.fit(listOf(taipei), 800, 600, 256, 0, 16)!!
        val cam = MapCamera.fromViewport(v)
        assertTrue(abs(cam.centerLat - taipei.lat) < 1e-6 && abs(cam.centerLng - taipei.lng) < 1e-6)
        assertEquals(16.0, cam.zoom)
    }

    @Test fun at_usesRoundedLevel_andScaleBetweenLevels() {
        val v = MapViewport.at(MapCamera(16.5, taipei.lat, taipei.lng), 800, 600, 256)
        assertEquals(17, v.zoom) // 16.5 rounds to 17
        assertTrue(abs(v.renderScale - 2f.pow(-0.5f)) < 1e-6)
        assertEquals(800, v.widthPx)
    }

    @Test fun zoomedBy_isLog2_andClamped() {
        val cam = MapCamera(16.0, taipei.lat, taipei.lng)
        assertEquals(17.0, cam.zoomedBy(2f, minZoom = 10.0).zoom)
        assertEquals(19.0, cam.zoomedBy(64f, minZoom = 10.0).zoom)
        assertEquals(10.0, cam.zoomedBy(0.001f, minZoom = 10.0).zoom)
    }

    @Test fun pannedBy_movesTheCentreByScreenPixels() {
        val cam = MapCamera(16.0, taipei.lat, taipei.lng)
        val v = MapViewport.at(cam, 800, 600, 256)
        val moved = cam.pannedBy(dxPx = -100f, dyPx = 0f, viewport = v) // drag left → centre moves east
        val v2 = MapViewport.at(moved, 800, 600, 256)
        val (x, _) = v2.pixelFor(taipei.lat, taipei.lng)
        assertTrue(abs(x - 300f) < 1f) // the old centre (400) is now 100 px left
    }

    @Test fun zoomedAround_keepsTheFocusPointFixed() {
        val cam = MapCamera(15.0, taipei.lat, taipei.lng)
        val v = MapViewport.at(cam, 800, 600, 256)
        val focus = GeoPoint(25.0300, 121.5600)
        val (fx, fy) = v.pixelFor(focus.lat, focus.lng)
        val zoomed = cam.zoomedAround(2f, fx, fy, v, minZoom = 10.0)
        val v2 = MapViewport.at(zoomed, 800, 600, 256)
        val (fx2, fy2) = v2.pixelFor(focus.lat, focus.lng)
        assertTrue(abs(fx2 - fx) < 1f && abs(fy2 - fy) < 1f)
    }
}
```

- [ ] **Step 2: Implement** in `MapProjection.kt`

```kotlin
// WebMercator
fun unproject(x: Double, y: Double, zoom: Int): GeoPoint {
    val n = tilesAcross(zoom).toDouble()
    val lng = x / n * FULL_TURN_DEGREES - HALF_TURN_DEGREES
    val latRad = atan(sinh(PI * (1 - 2 * y / n)))
    return GeoPoint(latRad * HALF_TURN_DEGREES / PI, lng)
}

// MapViewport: add `val renderScale: Float = 1f` as the last constructor param.
val drawnTilePx: Double get() = tileSizePx * renderScale.toDouble()
// pixelFor and tiles() use drawnTilePx instead of tileSizePx; TilePlacement gains sizePx = drawnTilePx.roundToInt().
// Companion:
fun at(camera: MapCamera, widthPx: Int, heightPx: Int, tileSizePx: Int): MapViewport {
    val level = camera.zoom.roundToInt().coerceIn(0, MapCamera.MAX_ZOOM.toInt())
    val scale = 2.0.pow(camera.zoom - level).toFloat()
    val drawn = tileSizePx * scale
    val c = WebMercator.project(camera.centerLat, camera.centerLng, level)
    return MapViewport(level, c.x * drawn - widthPx / 2.0, c.y * drawn - heightPx / 2.0, widthPx, heightPx, tileSizePx, scale)
}

data class MapCamera(val zoom: Double, val centerLat: Double, val centerLng: Double) {
    fun zoomedBy(factor: Float, minZoom: Double, maxZoom: Double = MAX_ZOOM) =
        copy(zoom = (zoom + log2(factor.toDouble())).coerceIn(minZoom, maxZoom))

    fun pannedBy(dxPx: Float, dyPx: Float, viewport: MapViewport): MapCamera {
        val cx = (viewport.originX + viewport.widthPx / 2.0 - dxPx) / viewport.drawnTilePx
        val cy = (viewport.originY + viewport.heightPx / 2.0 - dyPx) / viewport.drawnTilePx
        val p = WebMercator.unproject(cx, cy, viewport.zoom)
        return copy(centerLat = p.lat, centerLng = p.lng)
    }

    fun zoomedAround(factor: Float, focusXPx: Float, focusYPx: Float, viewport: MapViewport, minZoom: Double, maxZoom: Double = MAX_ZOOM): MapCamera {
        val focus = WebMercator.unproject((viewport.originX + focusXPx) / viewport.drawnTilePx, (viewport.originY + focusYPx) / viewport.drawnTilePx, viewport.zoom)
        val zoomed = zoomedBy(factor, minZoom, maxZoom)
        val next = MapViewport.at(zoomed, viewport.widthPx, viewport.heightPx, viewport.tileSizePx)
        val (fx, fy) = next.pixelFor(focus.lat, focus.lng)
        return zoomed.pannedBy(dxPx = focusXPx - fx, dyPx = focusYPx - fy, viewport = next)
    }

    companion object {
        const val MAX_ZOOM = 19.0
        fun fromViewport(v: MapViewport): MapCamera {
            val c = WebMercator.unproject((v.originX + v.widthPx / 2.0) / v.drawnTilePx, (v.originY + v.heightPx / 2.0) / v.drawnTilePx, v.zoom)
            return MapCamera(v.zoom + log2(v.renderScale.toDouble()), c.lat, c.lng)
        }
    }
}
```
Sign convention for `pannedBy`: a drag by `(dx, dy)` moves the content with the finger, so the centre moves by `(−dx, −dy)` in pixels — the code above subtracts. `PlacesMap`'s `TileLayer` switches to `tile.sizePx`. Keep existing `MapProjectionTest` cases green (default scale 1).

- [ ] **Step 3:** `:shared:jvmTest` green → commit `feat(insights): map camera with fractional zoom and pan`.

---

### Task 3: `PlacesMap` clusters + `onClick`; `FullScreenPlacesMap`; `LocationsCard` wiring; strings

**Files:**
- Modify: `shared/.../ui/insights/map/PlacesMap.kt`
- Create: `shared/.../ui/insights/map/FullScreenPlacesMap.kt`
- Modify: `shared/.../ui/insights/InsightsScreen.kt` (`LocationsCard`)
- Modify: both `strings.xml`

- [ ] **Step 1: `PlacesMap`** — add `onClick: (() -> Unit)? = null`; when non-null, `.clickable(onClick = onClick)` on the outer box and append 「。」 + `insights_map_expand_hint` to the content description; replace `locations.forEach { PlaceMarker(viewport, it) }` with `clusterPlaces(locations, viewport, mergeDistancePx).forEach { ClusterMarker(viewport, it) }` where `mergeDistancePx = with(density) { (MARKER_RADIUS + MARKER_GROWTH_PER_MEAL * MARKER_GROWTH_CAP).toPx() * 2 }`; `ClusterMarker` is `PlaceMarker` taking `MapCluster` (radius grows with `count`, text = `count`). `TileLayer` uses `tile.sizePx`. Make `TileLayer`, the marker and the attribution `internal` so the full-screen map reuses them (same file or a small `MapLayers.kt`).
- [ ] **Step 2: `FullScreenPlacesMap`**

```kotlin
@Composable
fun FullScreenPlacesMap(locations: List<LocationCount>, contentDescription: String, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val density = LocalDensity.current
        BoxWithConstraints(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
            val widthPx = with(density) { maxWidth.roundToPx() }; val heightPx = with(density) { maxHeight.roundToPx() }
            val tileSizePx = (OSM_TILE_PX * maxOf(TILE_SCALE_MIN, density.density / TILE_SCALE_DENSITY_DIVISOR)).roundToInt()
            val paddingPx = with(density) { FIT_PADDING.roundToPx() }
            val fitted = remember(locations, widthPx, heightPx) {
                MapViewport.fit(locations.map { GeoPoint(it.lat, it.lng) }, widthPx, heightPx, tileSizePx, paddingPx)
            } ?: return@BoxWithConstraints
            val minZoom = (fitted.zoom - ZOOM_OUT_ALLOWANCE).coerceAtLeast(0.0)
            var camera by remember(fitted) { mutableStateOf(MapCamera.fromViewport(fitted)) }
            val viewport = MapViewport.at(camera, widthPx, heightPx, tileSizePx)
            val mergePx = /* same formula as the card */
            Box(
                Modifier.fillMaxSize()
                    .clearAndSetSemantics { this.contentDescription = contentDescription }
                    .pointerInput(fitted) {
                        detectTransformGestures { centroid, pan, zoom, _ ->
                            camera = camera.zoomedAround(zoom, centroid.x, centroid.y, viewportFor(camera), minZoom)
                                .pannedBy(pan.x, pan.y, viewportFor(camera))
                        }
                    }
                    .pointerInput(fitted) {
                        detectTapGestures(onDoubleTap = { tap -> camera = camera.zoomedAround(2f, tap.x, tap.y, viewportFor(camera), minZoom) })
                    },
            ) {
                TileLayer(viewport)
                clusterPlaces(locations, viewport, mergePx).forEach { ClusterMarker(viewport, it) }
            }
            IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(8.dp)) {
                Icon(painterResource(Res.drawable.ic_baseline_close_24), contentDescription = stringResource(Res.string.insights_map_close))
            }
            Attribution(Modifier.align(Alignment.BottomEnd).navigationBarsPadding())
        }
    }
}
```
`viewportFor(camera)` = `MapViewport.at(camera, widthPx, heightPx, tileSizePx)` computed inside the gesture lambda from the latest `camera` (gesture lambdas capture `camera` by reference via the `by remember` delegate). `ZOOM_OUT_ALLOWANCE = 2.0`. `Res.drawable.ic_baseline_close_24` must exist in shared resources — if it is only in `:app`, add the vector to `shared/src/commonMain/composeResources/drawable/` (copy the XML). The dialog closes on system back via `onDismissRequest`.
- [ ] **Step 3: `LocationsCard`** — `var expanded by remember { mutableStateOf(false) }`; `PlacesMap(locations, summary, onClick = { expanded = true })`; `if (expanded) FullScreenPlacesMap(locations, summary, onDismiss = { expanded = false })`.
- [ ] **Step 4:** strings in both locales; `:shared:jvmTest` + gate; commit `feat(insights): tap the places card for a full-screen zoomable map with clustered markers`.

---

### Task 4: Instrumented open/close test, device check, docs

**Files:**
- Create: `app/src/androidTest/java/com/crazystudio/sportrecorder/ui/insights/PlacesMapInteractionTest.kt`
- Modify: `docs/DEVELOPMENT.md`, `docs/superpowers/specs/2026-09-27-insights-places-map-design.md` (status line → this spec)

- [ ] **Step 1: Test** (`createComposeRule`): render a small host that mirrors `LocationsCard`'s wiring (`PlacesMap(onClick = { expanded = true })` + `FullScreenPlacesMap` when expanded) with two Taipei places; `onNodeWithContentDescription(summary + hint)` click → `onNodeWithContentDescription(getString(insights_map_close)).assertIsDisplayed()` → click → the close button is gone. Tiles come from the network and may not load in the test — the assertions must not depend on them.
- [ ] **Step 2: Device check** — Insights tab with real records (add 3 meals at nearby locations via the editor with location on), screenshot the card (one cluster with count 3), tap → full screen, pinch/drag (`adb shell input swipe` for drag; pinch via two `input` events is unreliable — use double-tap to zoom and confirm the cluster splits), screenshot, back closes. Record in the report.
- [ ] **Step 3: Docs** — §6 row `| 09-28 | 回顧地圖:marker 合併 + 全螢幕縮放 | ✓ | ✓ | 已完成 |`; status line at the top of the 2026-09-27 map spec: 「2026-09-28:marker 合併與全螢幕縮放見 `2026-09-28-insights-map-clusters-and-zoom-design.md`」. Commit `docs: record map clustering and full-screen zoom`.

## Self-review
Spec §1 → T1; §2 → T2; §3 → T3 (+strings); tests → T1/T2 (JVM) and T4 (instrumented); docs → T4. Consistent names: `MapCluster`, `clusterPlaces`, `MapCamera` (`zoomedBy`, `pannedBy`, `zoomedAround`, `fromViewport`, `MAX_ZOOM`), `MapViewport.at`, `renderScale`, `drawnTilePx`, `TilePlacement.sizePx`, `FullScreenPlacesMap`, `insights_map_expand_hint`, `insights_map_close`.
