package com.crazystudio.sportrecorder.platform

import android.content.Context
import android.net.Uri
import com.crazystudio.sportrecorder.util.PhotoStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Android [PhotoImporter]: runs the decode/EXIF/downscale/webp pipeline on [Dispatchers.IO] via
 * [PhotoStorage]. Failures safe-degrade to null rather than throwing.
 */
class AndroidPhotoImporter(private val context: Context) : PhotoImporter {

    override suspend fun importCapture(sourcePath: String): String? = withContext(Dispatchers.IO) {
        runCatching { PhotoStorage.convertToWebp(context, File(sourcePath)) }.getOrNull()
    }

    override suspend fun importPicked(sourceUri: String): String? = withContext(Dispatchers.IO) {
        runCatching { PhotoStorage.importFromUri(context, Uri.parse(sourceUri)) }.getOrNull()
    }
}
