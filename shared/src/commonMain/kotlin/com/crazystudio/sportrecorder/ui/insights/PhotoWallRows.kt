package com.crazystudio.sportrecorder.ui.insights

/** One thumbnail on the wall: where it sits in the period's full photo list, and the file to draw. */
internal data class WallPhoto(val index: Int, val fileName: String)

/**
 * Shapes the period's photos into rows of [columns].
 *
 * Collapsed draws the first [previewCount] and the user can ask for the rest; **expanded reaches
 * every photo in the period.** Insights is the one place to look back at a whole period at once, so
 * the preview is a starting point, not a cap. [WallPhoto.index] stays an index into [fileNames] so
 * a tapped thumbnail opens the viewer on the right photo of the period, whichever row it came from.
 */
internal fun photoWallRows(
    fileNames: List<String>,
    expanded: Boolean,
    columns: Int,
    previewCount: Int,
): List<List<WallPhoto>> {
    val shown = if (expanded) fileNames.size else minOf(previewCount, fileNames.size)
    return (0 until shown).map { WallPhoto(it, fileNames[it]) }.chunked(columns)
}
