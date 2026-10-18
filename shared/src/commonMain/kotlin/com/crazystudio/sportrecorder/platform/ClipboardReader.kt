package com.crazystudio.sportrecorder.platform

/**
 * Reads what the user copied, so a Maps link can be pasted into the venue picker (#89).
 * Platform-specific (Android: ClipboardManager). Called only in direct response to a tap.
 */
interface ClipboardReader {
    /** The clipboard's text, or null when it is empty, holds no text, or cannot be read. Never throws. */
    suspend fun text(): String?
}
