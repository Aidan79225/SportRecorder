package com.crazystudio.sportrecorder.fake

import com.crazystudio.sportrecorder.data.PhotoFileStore

/** Test [PhotoFileStore] that records every file name it was asked to delete. */
class FakePhotoFileStore : PhotoFileStore {
    val deleted = mutableListOf<String>()

    override fun delete(fileName: String) {
        deleted.add(fileName)
    }
}
