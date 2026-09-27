package com.crazystudio.sportrecorder.ui.insights.map

import com.crazystudio.sportrecorder.domain.model.GeoPoint
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.roundToInt
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
}

/** One raster tile and where its top-left corner lands inside the viewport (px). */
data class TilePlacement(val zoom: Int, val x: Int, val y: Int, val leftPx: Int, val topPx: Int)

/**
 * A fixed, non-interactive map window: a zoom level plus the world pixel that sits at the
 * viewport's top-left. World pixels are tile units × [tileSizePx].
 */
data class MapViewport(
    val zoom: Int,
    val originX: Double,
    val originY: Double,
    val widthPx: Int,
    val heightPx: Int,
    val tileSizePx: Int,
) {

    /** Viewport-relative pixel position of a coordinate. */
    fun pixelFor(lat: Double, lng: Double): Pair<Float, Float> {
        val p = WebMercator.project(lat, lng, zoom)
        return (p.x * tileSizePx - originX).toFloat() to (p.y * tileSizePx - originY).toFloat()
    }

    /**
     * Every tile that intersects the viewport. Columns wrap around the antimeridian; rows past
     * the poles are simply absent (there is no tile there), leaving the background to show.
     */
    fun tiles(): List<TilePlacement> {
        val n = WebMercator.tilesAcross(zoom)
        val firstX = floor(originX / tileSizePx).toInt()
        val lastX = floor((originX + widthPx - 1) / tileSizePx).toInt()
        val firstY = floor(originY / tileSizePx).toInt().coerceAtLeast(0)
        val lastY = floor((originY + heightPx - 1) / tileSizePx).toInt().coerceAtMost(n - 1)
        if (firstY > lastY) return emptyList()
        return (firstY..lastY).flatMap { ty ->
            (firstX..lastX).map { tx ->
                TilePlacement(
                    zoom = zoom,
                    x = ((tx % n) + n) % n,
                    y = ty,
                    leftPx = (tx.toDouble() * tileSizePx - originX).roundToInt(),
                    topPx = (ty.toDouble() * tileSizePx - originY).roundToInt(),
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
