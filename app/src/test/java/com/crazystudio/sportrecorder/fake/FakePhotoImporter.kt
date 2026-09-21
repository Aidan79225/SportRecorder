package com.crazystudio.sportrecorder.fake

import com.crazystudio.sportrecorder.platform.PhotoImporter

/**
 * Test [PhotoImporter]. Each successful import returns a generated `.webp` name; set [succeeds]
 * to false to model a failed decode/encode, which the editor must tolerate without crashing.
 */
class FakePhotoImporter(
    var succeeds: Boolean = true,
) : PhotoImporter {
    val importedSources = mutableListOf<String>()
    private var next = 1

    override suspend fun importCapture(sourcePath: String): String? = importFrom(sourcePath, "capture")

    override suspend fun importPicked(sourceUri: String): String? = importFrom(sourceUri, "picked")

    private fun importFrom(source: String, prefix: String): String? {
        importedSources.add(source)
        return if (succeeds) "$prefix-${next++}.webp" else null
    }
}
