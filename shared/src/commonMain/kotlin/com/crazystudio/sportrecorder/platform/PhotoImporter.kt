package com.crazystudio.sportrecorder.platform

/**
 * Imports a photo into the app's private photo store and returns the saved file name (or null on
 * failure). Platform-specific (Android: decode/EXIF-rotate/downscale/webp-encode via PhotoStorage;
 * iOS: the CoreImage/ImageIO equivalent). Inputs are platform-neutral string handles so shared
 * ViewModels can drive capture/pick without depending on platform file/URI types:
 * a filesystem path for a just-captured photo, a URI string for a picked one.
 */
interface PhotoImporter {
    /** Import a just-captured photo at [sourcePath] (e.g. a camera temp file). */
    suspend fun importCapture(sourcePath: String): String?

    /** Import a photo identified by [sourceUri] (e.g. a gallery pick); the source is left intact. */
    suspend fun importPicked(sourceUri: String): String?
}
