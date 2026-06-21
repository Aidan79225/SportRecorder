package com.crazystudio.sportrecorder.fake

import com.crazystudio.sportrecorder.data.PhotoImageSource

/** Test [PhotoImageSource] that echoes the file name back as its (opaque) Coil model. */
class FakePhotoImageSource : PhotoImageSource {
    override fun modelFor(fileName: String): Any = fileName
}
