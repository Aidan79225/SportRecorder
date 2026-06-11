package com.crazystudio.sportrecorder.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.math.max
import kotlin.math.roundToInt

/** App-private photo storage: webp files in getExternalFilesDir("photos"). DB stores only file names. */
object PhotoStorage {
    private const val MAX_EDGE = 1280
    private const val WEBP_QUALITY = 80

    fun photosDir(context: Context): File =
        File(context.getExternalFilesDir(null), "photos").apply { mkdirs() }

    fun capturesDir(context: Context): File =
        File(context.getExternalFilesDir(null), "captures").apply { mkdirs() }

    fun fileFor(context: Context, fileName: String): File = File(photosDir(context), fileName)

    /** Create a temp capture file + content Uri for ACTION_IMAGE_CAPTURE EXTRA_OUTPUT. */
    fun newCaptureTarget(context: Context): Pair<File, Uri> {
        val file = File(capturesDir(context), "capture_${UUID.randomUUID()}.jpg")
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        return file to uri
    }

    /**
     * Decode [tempFile], correct EXIF rotation, downscale to MAX_EDGE long edge, encode webp,
     * write into photosDir, delete the temp file, and return the saved file name.
     */
    fun convertToWebp(context: Context, tempFile: File): String {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(tempFile.absolutePath, bounds)
        val sample = sampleSizeFor(bounds.outWidth, bounds.outHeight, MAX_EDGE * 2)
        val decoded = BitmapFactory.decodeFile(
            tempFile.absolutePath,
            BitmapFactory.Options().apply { inSampleSize = sample },
        ) ?: error("Failed to decode capture: ${tempFile.absolutePath}")

        val rotated = applyExifRotation(tempFile, decoded)
        val scaled = scaleToMaxEdge(rotated, MAX_EDGE)

        val name = "${UUID.randomUUID()}.webp"
        FileOutputStream(File(photosDir(context), name)).use { out ->
            val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Bitmap.CompressFormat.WEBP_LOSSY
            } else {
                @Suppress("DEPRECATION")
                Bitmap.CompressFormat.WEBP
            }
            scaled.compress(format, WEBP_QUALITY, out)
        }
        if (scaled !== decoded) scaled.recycle()
        if (rotated !== decoded) rotated.recycle()
        decoded.recycle()
        tempFile.delete()
        return name
    }

    fun deleteByName(context: Context, fileName: String) {
        File(photosDir(context), fileName).delete()
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

    private fun applyExifRotation(file: File, bitmap: Bitmap): Bitmap {
        val orientation = ExifInterface(file.absolutePath)
            .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        val degrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> return bitmap
        }
        val m = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, m, true)
    }
}
