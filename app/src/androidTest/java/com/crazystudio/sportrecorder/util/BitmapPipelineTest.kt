package com.crazystudio.sportrecorder.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.exifinterface.media.ExifInterface
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class BitmapPipelineTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var dir: File

    @Before fun setUp() { dir = File(context.cacheDir, "pipeline-${UUID.randomUUID()}").apply { mkdirs() } }
    @After fun tearDown() { dir.deleteRecursively() }

    /** A solid JPEG of [w]×[h] with an optional EXIF orientation tag. */
    private fun jpeg(w: Int, h: Int, orientation: Int? = null): File {
        val file = File(dir, "src-${UUID.randomUUID()}.jpg")
        Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }.let { bmp ->
            FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            bmp.recycle()
        }
        if (orientation != null) {
            ExifInterface(file.absolutePath).apply {
                setAttribute(ExifInterface.TAG_ORIENTATION, orientation.toString())
                saveAttributes()
            }
        }
        return file
    }

    private fun decode(name: String): Triple<Int, Int, String?> {
        val opts = BitmapFactory.Options()
        val bmp = BitmapFactory.decodeFile(File(dir, name).absolutePath, opts)!!
        return Triple(bmp.width, bmp.height, opts.outMimeType).also { bmp.recycle() }
    }

    @Test fun rotate90_isAppliedThenScaledToLongEdge1280() {
        val src = jpeg(3000, 1500, ExifInterface.ORIENTATION_ROTATE_90)
        val name = decodeScaleEncode(dir) { src.inputStream() }
        val (w, h, mime) = decode(name)
        assertEquals(640, w); assertEquals(1280, h)
        assertEquals("image/webp", mime)
        assertTrue(name.endsWith(".webp"))
    }

    @Test fun smallImage_isNotUpscaled() {
        val src = jpeg(800, 600)
        val (w, h, _) = decode(decodeScaleEncode(dir) { src.inputStream() })
        assertEquals(800, w); assertEquals(600, h)
    }

    @Test fun rotate180_keepsAspect_andScales() {
        val src = jpeg(1500, 3000, ExifInterface.ORIENTATION_ROTATE_180)
        val (w, h, _) = decode(decodeScaleEncode(dir) { src.inputStream() })
        assertEquals(640, w); assertEquals(1280, h)
    }

    @Test fun outputLandsInGivenDirectory() {
        val src = jpeg(100, 100)
        val name = decodeScaleEncode(dir) { src.inputStream() }
        assertTrue(File(dir, name).let { it.exists() && it.length() > 0 })
    }
}
