package com.crazystudio.sportrecorder.platform

import android.content.ClipboardManager
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

/**
 * Android [ClipboardReader]: the first clip item, as text. Read on the main thread, which
 * [ClipboardManager] expects; any failure (no clip, no text, a denied read) is a null.
 */
class AndroidClipboardReader(private val context: Context) : ClipboardReader {
    @Suppress("TooGenericExceptionCaught") // intentional safe-degrade boundary: any failure → null
    override suspend fun text(): String? =
        try {
            withContext(Dispatchers.Main) {
                val clipboard = context.getSystemService(ClipboardManager::class.java)
                clipboard?.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
}
