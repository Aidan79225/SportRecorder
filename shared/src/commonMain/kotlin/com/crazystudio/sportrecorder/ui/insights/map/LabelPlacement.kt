package com.crazystudio.sportrecorder.ui.insights.map

/** A marker's circle in canvas pixels. */
internal data class MarkerCircle(val x: Float, val y: Float, val radius: Float)

/** Where a name label goes: [left] edge and [width], in canvas pixels. */
internal data class LabelSlot(val left: Float, val width: Float)

private const val HALF = 2f

/** Free horizontal room beside a marker: the label would start (or end) at [edge] and may be [room] wide. */
private class Side(val edge: Float, val room: Float)

/**
 * Where to draw a name label of [naturalWidth] x [height] beside [marker]: to its right if it fits
 * before the canvas edge or the next marker's circle, else to its left, else (when neither side has
 * room for the whole name) the roomier side with the label narrowed — which the caller ellipsizes.
 * Null when even that would leave less than [minWidth]: a sliver of a name tells no one anything,
 * and the name is still in the map's text description. Never overlaps another marker's circle.
 * This is not a label-collision solver; it only keeps labels on the canvas and off other markers.
 */
internal fun placeLabel(
    marker: MarkerCircle,
    others: List<MarkerCircle>,
    naturalWidth: Float,
    height: Float,
    canvasWidth: Float,
    gap: Float,
    minWidth: Float,
): LabelSlot? {
    val inBand = others.filter { it.y + it.radius > marker.y - height / HALF && it.y - it.radius < marker.y + height / HALF }
    val right = roomRight(marker, inBand, canvasWidth, gap)
    val left = roomLeft(marker, inBand, gap)
    val toRight = right.room >= naturalWidth || (left.room < naturalWidth && right.room >= left.room)
    val side = if (toRight) right else left
    val width = minOf(naturalWidth, side.room)
    return when {
        width < minWidth -> null
        toRight -> LabelSlot(left = side.edge, width = width)
        else -> LabelSlot(left = side.edge - width, width = width)
    }
}

private fun roomRight(marker: MarkerCircle, inBand: List<MarkerCircle>, canvasWidth: Float, gap: Float): Side {
    val start = marker.x + marker.radius + gap
    val limit = inBand
        .filter { it.x + it.radius > start }
        .minOfOrNull { it.x - it.radius - gap }
        ?.coerceAtMost(canvasWidth) ?: canvasWidth
    return Side(edge = start, room = (limit - start).coerceAtLeast(0f))
}

private fun roomLeft(marker: MarkerCircle, inBand: List<MarkerCircle>, gap: Float): Side {
    val end = marker.x - marker.radius - gap
    val limit = inBand
        .filter { it.x - it.radius < end }
        .maxOfOrNull { it.x + it.radius + gap }
        ?.coerceAtLeast(0f) ?: 0f
    return Side(edge = end, room = (end - limit).coerceAtLeast(0f))
}
