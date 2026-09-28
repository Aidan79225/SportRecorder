package com.crazystudio.sportrecorder.ui.insights.map

import com.crazystudio.sportrecorder.domain.model.GeoPoint
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sinh
import kotlin.math.tan

/** Native pixel size of a standard slippy-map raster tile. */
const val OSM_TILE_PX = 256

/** Web Mercator's usable latitude range; the projection (and the tiles) stop here. */
private const val MAX_LATITUDE = 85.05112878
private const val HALF_TURN_DEGREES = 180.0
private const val FULL_TURN_DEGREES = 360.0

/** A position in fractional tile units at one zoom level (0..2^zoom on both axes). */
data class TilePoint(val x: Double, val y: Double)

/**
 * Slippy-map projection (Web Mercator, the scheme every OSM-style tile server uses). Pure
 * arithmetic, no platform types, so the map's geometry is unit-tested in `commonTest`.
 */
object WebMercator {

    /** Number of tiles along each axis at [zoom]. */
    fun tilesAcross(zoom: Int): Int = 1 shl zoom

    fun project(lat: Double, lng: Double, zoom: Int): TilePoint {
        val n = tilesAcross(zoom).toDouble()
        val latRad = lat.coerceIn(-MAX_LATITUDE, MAX_LATITUDE) * PI / HALF_TURN_DEGREES
        val x = (lng + HALF_TURN_DEGREES) / FULL_TURN_DEGREES * n
        val y = (1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / PI) / 2.0 * n
        return TilePoint(x, y)
    }

    /** Inverse of [project]: a fractional tile position at [zoom] back to a coordinate. */
    fun unproject(x: Double, y: Double, zoom: Int): GeoPoint {
        val n = tilesAcross(zoom).toDouble()
        val lng = x / n * FULL_TURN_DEGREES - HALF_TURN_DEGREES
        val latRad = atan(sinh(PI * (1 - 2 * y / n)))
        return GeoPoint(latRad * HALF_TURN_DEGREES / PI, lng)
    }
}

/** One raster tile and where its top-left corner lands inside the viewport (px), at [sizePx]. */
data class TilePlacement(val zoom: Int, val x: Int, val y: Int, val leftPx: Int, val topPx: Int, val sizePx: Int)

/** A tile's identity — which image it is, independent of where it is drawn. */
data class TileKey(val zoom: Int, val x: Int, val y: Int)

val TilePlacement.key: TileKey get() = TileKey(zoom, x, y)

/**
 * A fixed, non-interactive map window: a zoom level plus the world pixel that sits at the
 * viewport's top-left. World pixels are tile units × [drawnTilePx]. [renderScale] lets tiles be
 * drawn larger or smaller than their native [tileSizePx] — used to smooth a pinch between integer
 * zoom levels (see `MapViewport.Companion.at`); it defaults to 1, which leaves every result
 * unchanged.
 */
data class MapViewport(
    val zoom: Int,
    val originX: Double,
    val originY: Double,
    val widthPx: Int,
    val heightPx: Int,
    val tileSizePx: Int,
    val renderScale: Double = 1.0,
) {

    /** The on-screen size of one tile: [tileSizePx] scaled by [renderScale]. */
    val drawnTilePx: Double get() = tileSizePx * renderScale

    /** Viewport-relative pixel position of a coordinate. */
    fun pixelFor(lat: Double, lng: Double): Pair<Float, Float> {
        val p = WebMercator.project(lat, lng, zoom)
        return (p.x * drawnTilePx - originX).toFloat() to (p.y * drawnTilePx - originY).toFloat()
    }

    /**
     * Every tile that intersects the viewport. Columns wrap around the antimeridian; rows past
     * the poles are simply absent (there is no tile there), leaving the background to show.
     */
    fun tiles(): List<TilePlacement> {
        val n = WebMercator.tilesAcross(zoom)
        val drawn = drawnTilePx
        val firstX = floor(originX / drawn).toInt()
        val lastX = floor((originX + widthPx - 1) / drawn).toInt()
        val firstY = floor(originY / drawn).toInt().coerceAtLeast(0)
        val lastY = floor((originY + heightPx - 1) / drawn).toInt().coerceAtMost(n - 1)
        if (firstY > lastY) return emptyList()
        return (firstY..lastY).flatMap { ty ->
            (firstX..lastX).map { tx ->
                TilePlacement(
                    zoom = zoom,
                    x = ((tx % n) + n) % n,
                    y = ty,
                    leftPx = (tx.toDouble() * drawn - originX).roundToInt(),
                    topPx = (ty.toDouble() * drawn - originY).roundToInt(),
                    sizePx = drawn.roundToInt(),
                )
            }
        }
    }

    companion object {
        /** Street level; close enough to tell two places apart, far enough that ~100 m grouping isn't a lie. */
        const val DEFAULT_MAX_ZOOM = 16

        /** A lone place has no extent to fit, so it gets a fixed neighbourhood view. */
        const val SINGLE_PLACE_ZOOM = 15

        /**
         * The largest zoom at which every point fits inside the viewport minus [paddingPx] on
         * each side, centred on the points' bounding box. `null` when there is nothing to show.
         */
        fun fit(
            points: List<GeoPoint>,
            widthPx: Int,
            heightPx: Int,
            tileSizePx: Int,
            paddingPx: Int,
            maxZoom: Int = DEFAULT_MAX_ZOOM,
        ): MapViewport? {
            if (points.isEmpty() || widthPx <= 0 || heightPx <= 0) return null
            val usableW = (widthPx - 2 * paddingPx).coerceAtLeast(1)
            val usableH = (heightPx - 2 * paddingPx).coerceAtLeast(1)

            var zoom = maxZoom
            var bounds = boundsAt(points, zoom, tileSizePx)
            if (bounds.width == 0.0 && bounds.height == 0.0) {
                zoom = minOf(SINGLE_PLACE_ZOOM, maxZoom)
                bounds = boundsAt(points, zoom, tileSizePx)
            } else {
                while (zoom > 0 && (bounds.width > usableW || bounds.height > usableH)) {
                    zoom--
                    bounds = boundsAt(points, zoom, tileSizePx)
                }
            }
            return MapViewport(
                zoom = zoom,
                originX = bounds.centerX - widthPx / 2.0,
                originY = bounds.centerY - heightPx / 2.0,
                widthPx = widthPx,
                heightPx = heightPx,
                tileSizePx = tileSizePx,
            )
        }

        private class Bounds(val minX: Double, val minY: Double, val maxX: Double, val maxY: Double) {
            val width get() = maxX - minX
            val height get() = maxY - minY
            val centerX get() = (minX + maxX) / 2
            val centerY get() = (minY + maxY) / 2
        }

        /** Bounding box of [points] in world pixels at [zoom]. */
        private fun boundsAt(points: List<GeoPoint>, zoom: Int, tileSizePx: Int): Bounds {
            val projected = points.map { WebMercator.project(it.lat, it.lng, zoom) }
            return Bounds(
                minX = projected.minOf { it.x } * tileSizePx,
                minY = projected.minOf { it.y } * tileSizePx,
                maxX = projected.maxOf { it.x } * tileSizePx,
                maxY = projected.maxOf { it.y } * tileSizePx,
            )
        }
    }
}

/** The deepest tile level [MapViewport.Companion.at] will draw. */
private val MAX_LEVEL = MapCamera.MAX_ZOOM.toInt()

/**
 * Builds the [MapViewport] for [camera]: by default the nearest integer zoom level (clamped to the
 * world's range), with [MapViewport.renderScale] covering the fractional remainder so a pinch between
 * levels scales the current tiles smoothly instead of jumping when the level changes. Passing another
 * [level] draws that level's tiles at whatever scale shows the same view — how the last loaded level
 * stays underneath while the new one arrives (see [layeredTiles]).
 */
fun MapViewport.Companion.at(
    camera: MapCamera,
    widthPx: Int,
    heightPx: Int,
    tileSizePx: Int,
    level: Int = camera.zoom.roundToInt(),
): MapViewport {
    val tileLevel = level.coerceIn(0, MAX_LEVEL)
    val scale = 2.0.pow(camera.zoom - tileLevel)
    val drawn = tileSizePx * scale
    val center = WebMercator.project(camera.centerLat, camera.centerLng, tileLevel)
    return MapViewport(
        zoom = tileLevel,
        originX = center.x * drawn - widthPx / 2.0,
        originY = center.y * drawn - heightPx / 2.0,
        widthPx = widthPx,
        heightPx = heightPx,
        tileSizePx = tileSizePx,
        renderScale = scale,
    )
}

/**
 * The interactive full-screen map's state: a fractional [zoom] (rounded to the nearest integer for
 * the tile level, the fractional remainder becomes [MapViewport.renderScale] via [MapViewport.Companion.at])
 * and the geographic point at the centre of the viewport. Pure data and arithmetic; gestures in
 * `FullScreenPlacesMap` turn into calls on this type.
 */
data class MapCamera(val zoom: Double, val centerLat: Double, val centerLng: Double) {

    /** Applies [factor] in log2 space (doubling the visual scale is +1 zoom), then clamps. */
    fun zoomedBy(factor: Float, minZoom: Double, maxZoom: Double = MAX_ZOOM): MapCamera =
        copy(zoom = (zoom + log2(factor.toDouble())).coerceIn(minZoom, maxZoom))

    /** Moves the centre so the content appears dragged by ([dxPx], [dyPx]) screen pixels. */
    fun pannedBy(dxPx: Float, dyPx: Float, viewport: MapViewport): MapCamera {
        val centerTileX = (viewport.originX + viewport.widthPx / 2.0 - dxPx) / viewport.drawnTilePx
        val centerTileY = (viewport.originY + viewport.heightPx / 2.0 - dyPx) / viewport.drawnTilePx
        val center = WebMercator.unproject(centerTileX, centerTileY, viewport.zoom)
        return copy(centerLat = center.lat, centerLng = center.lng)
    }

    /**
     * Zooms by [factor] while keeping the point under ([focusXPx], [focusYPx]) in [viewport] fixed
     * on screen — the point under the fingers during a pinch, or under the tap for a double-tap.
     */
    fun zoomedAround(
        factor: Float,
        focusXPx: Float,
        focusYPx: Float,
        viewport: MapViewport,
        minZoom: Double,
        maxZoom: Double = MAX_ZOOM,
    ): MapCamera {
        val focus = WebMercator.unproject(
            (viewport.originX + focusXPx) / viewport.drawnTilePx,
            (viewport.originY + focusYPx) / viewport.drawnTilePx,
            viewport.zoom,
        )
        val zoomed = zoomedBy(factor, minZoom, maxZoom)
        val zoomedViewport = MapViewport.at(zoomed, viewport.widthPx, viewport.heightPx, viewport.tileSizePx)
        val (focusXAfter, focusYAfter) = zoomedViewport.pixelFor(focus.lat, focus.lng)
        val remainderDx = focusXPx - focusXAfter
        val remainderDy = focusYPx - focusYAfter
        return zoomed.pannedBy(dxPx = remainderDx, dyPx = remainderDy, viewport = zoomedViewport)
    }

    companion object {
        /** OSM's largest published raster zoom level. */
        const val MAX_ZOOM = 19.0

        /** The camera that reproduces [v] exactly: same centre, same effective (fractional) zoom. */
        fun fromViewport(v: MapViewport): MapCamera {
            val center = WebMercator.unproject(
                (v.originX + v.widthPx / 2.0) / v.drawnTilePx,
                (v.originY + v.heightPx / 2.0) / v.drawnTilePx,
                v.zoom,
            )
            return MapCamera(v.zoom + log2(v.renderScale), center.lat, center.lng)
        }
    }
}
