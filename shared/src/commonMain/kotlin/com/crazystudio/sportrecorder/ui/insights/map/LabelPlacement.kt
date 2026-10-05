package com.crazystudio.sportrecorder.ui.insights.map

/** A marker's circle in canvas pixels. */
internal data class MarkerCircle(val x: Float, val y: Float, val radius: Float)

/** An axis-aligned rectangle a label must stay clear of: a marker's circle, or a label already placed. */
internal data class Block(val left: Float, val top: Float, val right: Float, val bottom: Float)

internal fun MarkerCircle.asBlock() = Block(x - radius, y - radius, x + radius, y + radius)

/** Whether any part of this circle lies on a [canvasWidth] x [canvasHeight] canvas. */
internal fun MarkerCircle.intersectsCanvas(canvasWidth: Float, canvasHeight: Float): Boolean =
    x + radius > 0f && x - radius < canvasWidth && y + radius > 0f && y - radius < canvasHeight

/** Where a name label goes: [left] edge and [width], in canvas pixels. */
internal data class LabelSlot(val left: Float, val width: Float)

/** The rectangle a placed label takes up, for the labels placed after it to keep clear of. */
internal fun LabelSlot.asBlock(centreY: Float, height: Float) =
    Block(left, centreY - height / HALF, left + width, centreY + height / HALF)

private const val HALF = 2f

/** Free horizontal room beside a marker: the label would start (or end) at [edge] and may be [room] wide. */
private class Side(val edge: Float, val room: Float)

/**
 * Where to draw a name label of [naturalWidth] x [height] beside [marker]: to its right if it fits
 * before the canvas edge or the next [others] block (other markers' circles and labels already
 * placed), else to its left, else (when neither side has room for the whole name) the roomier side
 * with the label narrowed, which the caller ellipsizes. Null when even that would leave less than
 * [minWidth] (a sliver of a name tells no one anything, and the name is still in the map's text
 * description); a short name that fits whole is never dropped. Never overlaps an [others] block.
 * This is not a full label-collision solver: it only keeps labels on the canvas and off what is
 * already drawn, in the order the caller places them.
 */
internal fun placeLabel(
    marker: MarkerCircle,
    others: List<Block>,
    naturalWidth: Float,
    height: Float,
    canvasWidth: Float,
    gap: Float,
    minWidth: Float,
): LabelSlot? {
    val inBand = others.filter { it.bottom > marker.y - height / HALF && it.top < marker.y + height / HALF }
    val right = roomRight(marker, inBand, canvasWidth, gap)
    val left = roomLeft(marker, inBand, gap)
    val toRight = right.room >= naturalWidth || (left.room < naturalWidth && right.room >= left.room)
    val side = if (toRight) right else left
    val width = minOf(naturalWidth, side.room)
    // The minimum only suppresses a truncated sliver; a short name that fits whole is always drawn.
    return when {
        width < minOf(minWidth, naturalWidth) -> null
        toRight -> LabelSlot(left = side.edge, width = width)
        else -> LabelSlot(left = side.edge - width, width = width)
    }
}

private fun roomRight(marker: MarkerCircle, inBand: List<Block>, canvasWidth: Float, gap: Float): Side {
    val start = marker.x + marker.radius + gap
    val limit = inBand
        .filter { it.right > start }
        .minOfOrNull { it.left - gap }
        ?.coerceAtMost(canvasWidth) ?: canvasWidth
    return Side(edge = start, room = (limit - start).coerceAtLeast(0f))
}

private fun roomLeft(marker: MarkerCircle, inBand: List<Block>, gap: Float): Side {
    val end = marker.x - marker.radius - gap
    val limit = inBand
        .filter { it.left < end }
        .maxOfOrNull { it.right + gap }
        ?.coerceAtLeast(0f) ?: 0f
    return Side(edge = end, room = (end - limit).coerceAtLeast(0f))
}
