package com.crazystudio.sportrecorder.fake

import com.crazystudio.sportrecorder.platform.ClipboardReader

/** Test [ClipboardReader]: answers with [text] (null models an empty clipboard) and counts the reads. */
class FakeClipboardReader(var text: String? = null) : ClipboardReader {
    var reads = 0
        private set

    override suspend fun text(): String? {
        reads++
        return text
    }
}
