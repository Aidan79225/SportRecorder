package com.crazystudio.sportrecorder.data

/**
 * Resolves a stored photo's file name into a Coil-loadable model (platform-specific: Android
 * returns a [java.io.File] from app-private storage; iOS will return a sandbox path). The shared
 * photo UI passes whatever this returns straight to Coil's AsyncImage, which accepts `Any?`.
 */
interface PhotoImageSource {
    fun modelFor(fileName: String): Any?
}
