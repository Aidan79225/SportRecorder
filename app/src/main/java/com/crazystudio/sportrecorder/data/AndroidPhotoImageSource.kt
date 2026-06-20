package com.crazystudio.sportrecorder.data

import android.content.Context
import com.crazystudio.sportrecorder.util.PhotoStorage

/** Android [PhotoImageSource]: resolves a file name to a [java.io.File] Coil can load directly. */
class AndroidPhotoImageSource(private val context: Context) : PhotoImageSource {
    override fun modelFor(fileName: String): Any = PhotoStorage.fileFor(context, fileName)
}
