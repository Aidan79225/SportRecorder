package com.crazystudio.sportrecorder.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.os.Build
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID
import kotlin.math.max
import kotlin.math.roundToInt

private const val MAX_EDGE = 1280
private const val WEBP_QUALITY = 80
private const val DEGREES_90 = 90f
private const val DEGREES_180 = 180f
private const val DEGREES_270 = 270f

/**
 * Decode the stream from [openStream] (opened up to three times: bounds, pixels, EXIF),
 * correct rotation, downscale to MAX_EDGE long edge, encode webp into [photosDir] and
 * return the saved file name.
 */
internal fun decodeScaleEncode(photosDir: File, openStream: () -> InputStream): String {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    openStream().use { BitmapFactory.decodeStream(it, null, bounds) }
    val sample = sampleSizeFor(bounds.outWidth, bounds.outHeight, MAX_EDGE * 2)
    val decoded = openStream().use {
        BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
    } ?: error("Failed to decode image")

    val degrees = openStream().use { exifDegrees(ExifInterface(it)) }
    val rotated = rotateBitmap(decoded, degrees)
    val scaled = scaleToMaxEdge(rotated, MAX_EDGE)

    val name = encodeWebp(photosDir, scaled)
    if (scaled !== decoded) scaled.recycle()
    if (rotated !== decoded) rotated.recycle()
    decoded.recycle()
    return name
}

private fun encodeWebp(photosDir: File, bitmap: Bitmap): String {
    val name = "${UUID.randomUUID()}.webp"
    FileOutputStream(File(photosDir, name)).use { out ->
        val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Bitmap.CompressFormat.WEBP_LOSSY
        } else {
            @Suppress("DEPRECATION")
            Bitmap.CompressFormat.WEBP
        }
        bitmap.compress(format, WEBP_QUALITY, out)
    }
    return name
}

private fun sampleSizeFor(w: Int, h: Int, target: Int): Int {
    var sample = 1
    var longEdge = max(w, h)
    while (longEdge / 2 >= target) {
        longEdge /= 2
        sample *= 2
    }
    return sample
}

private fun scaleToMaxEdge(src: Bitmap, maxEdge: Int): Bitmap {
    val longEdge = max(src.width, src.height)
    if (longEdge <= maxEdge) return src
    val ratio = maxEdge.toFloat() / longEdge
    return Bitmap.createScaledBitmap(src, (src.width * ratio).roundToInt(), (src.height * ratio).roundToInt(), true)
}

private fun exifDegrees(exif: ExifInterface): Float =
    when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
        ExifInterface.ORIENTATION_ROTATE_90 -> DEGREES_90
        ExifInterface.ORIENTATION_ROTATE_180 -> DEGREES_180
        ExifInterface.ORIENTATION_ROTATE_270 -> DEGREES_270
        else -> 0f
    }

private fun rotateBitmap(bitmap: Bitmap, degrees: Float): Bitmap {
    if (degrees == 0f) return bitmap
    val m = Matrix().apply { postRotate(degrees) }
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, m, true)
}
