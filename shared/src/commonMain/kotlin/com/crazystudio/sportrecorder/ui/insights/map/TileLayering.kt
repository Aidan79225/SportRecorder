package com.crazystudio.sportrecorder.ui.insights.map

import kotlin.math.abs

/**
 * How many levels away the last fully loaded level may be and still sit underneath. Past this its
 * tiles are either enormous smears or a huge number of specks, and not worth drawing.
 */
private const val MAX_UNDERLAY_LEVEL_GAP = 3

/**
 * The tiles to draw for [camera], back to front. When the camera's level ([MapViewport.Companion.at])
 * differs from [settledLevel] — the last level whose on-screen tiles all finished loading — the
 * already-[loaded] tiles of that level come first, scaled to the same view, so crossing a zoom level
 * shows the previous (slightly blurry) picture until the new tiles arrive instead of a blank map.
 * Only tiles in [loaded] are underlaid, so this never requests anything new.
 */
fun layeredTiles(
    camera: MapCamera,
    widthPx: Int,
    heightPx: Int,
    tileSizePx: Int,
    settledLevel: Int?,
    loaded: Set<TileKey>,
): List<TilePlacement> {
    val topViewport = MapViewport.at(camera, widthPx, heightPx, tileSizePx)
    val top = topViewport.tiles()
    if (settledLevel == null || settledLevel == topViewport.zoom) return top
    if (abs(settledLevel - topViewport.zoom) > MAX_UNDERLAY_LEVEL_GAP) return top
    val underlay = MapViewport.at(camera, widthPx, heightPx, tileSizePx, level = settledLevel)
        .tiles()
        .filter { it.key in loaded }
    return underlay + top
}
