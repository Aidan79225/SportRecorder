package com.crazystudio.sportrecorder.util

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/** App-private photo storage: webp files in getExternalFilesDir("photos"). DB stores only file names. */
object PhotoStorage {

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
        val name = decodeScaleEncode(photosDir(context)) { tempFile.inputStream() }
        tempFile.delete()
        return name
    }

    /**
     * Import an existing image picked from [uri] (e.g. the gallery): correct EXIF rotation,
     * downscale, encode webp into photosDir, and return the saved file name. The source file
     * referenced by [uri] is NOT modified or deleted.
     */
    fun importFromUri(context: Context, uri: Uri): String =
        decodeScaleEncode(photosDir(context)) {
            context.contentResolver.openInputStream(uri) ?: error("Cannot open uri: $uri")
        }

    fun deleteByName(context: Context, fileName: String) {
        File(photosDir(context), fileName).delete()
    }

    /** Launch the system share sheet for the photo [fileName]. */
    fun share(context: Context, fileName: String) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            fileFor(context, fileName),
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/webp"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(
            Intent.createChooser(intent, null).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) },
        )
    }

    /** Copy the photo [fileName] into the device's public gallery. Returns true on success. */
    suspend fun saveToGallery(context: Context, fileName: String): Boolean = withContext(Dispatchers.IO) {
        val source = fileFor(context, fileName)
        if (!source.exists()) return@withContext false
        val resolver = context.contentResolver
        val displayName = "SportRecorder_${System.currentTimeMillis()}.webp"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/webp")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/SportRecorder")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: return@withContext false
        val written = runCatching {
            resolver.openOutputStream(uri)?.use { out ->
                source.inputStream().use { it.copyTo(out) }
            } ?: error("Cannot open output stream for $uri")
        }.isSuccess
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        }
        if (!written) resolver.delete(uri, null, null)
        written
    }
}
