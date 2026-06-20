package com.crazystudio.sportrecorder.data

import android.content.Context
import com.crazystudio.sportrecorder.util.PhotoStorage

/** Android [PhotoFileStore]: deletes from the app's private photo storage via [PhotoStorage]. */
class AndroidPhotoFileStore(private val context: Context) : PhotoFileStore {
    override fun delete(fileName: String) {
        PhotoStorage.deleteByName(context, fileName)
    }
}
