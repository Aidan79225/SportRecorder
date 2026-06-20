package com.crazystudio.sportrecorder.data

/**
 * Deletes a stored photo file by name. Platform-specific (Android: app-private storage; iOS: the
 * app sandbox), so the shared repository depends on this abstraction rather than platform file APIs.
 */
interface PhotoFileStore {
    fun delete(fileName: String)
}
