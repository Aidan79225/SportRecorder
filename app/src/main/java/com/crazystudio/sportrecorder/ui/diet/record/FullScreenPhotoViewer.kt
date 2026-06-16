package com.crazystudio.sportrecorder.ui.diet.record

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.crazystudio.sportrecorder.R
import com.crazystudio.sportrecorder.util.PhotoStorage
import kotlinx.coroutines.launch

private const val MAX_ZOOM = 5f
private const val DOUBLE_TAP_ZOOM = 2.5f

/**
 * Full-screen, immersive photo viewer. Supports pinch-to-zoom and pan, double-tap to
 * toggle zoom, plus share and save-to-gallery actions in the top bar.
 */
@Composable
fun FullScreenPhotoViewer(
    fileName: String,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    fun saveToGallery() = scope.launch {
        val ok = PhotoStorage.saveToGallery(context, fileName)
        val msg = if (ok) R.string.photo_saved else R.string.photo_save_failed
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
    }

    val writePermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            saveToGallery()
        } else {
            Toast.makeText(context, R.string.photo_save_failed, Toast.LENGTH_SHORT).show()
        }
    }

    fun onSaveClick() {
        val needsLegacyPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
        val granted = !needsLegacyPermission || ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            saveToGallery()
        } else {
            writePermLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
        ) {
            AsyncImage(
                model = PhotoStorage.fileFor(context, fileName),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, MAX_ZOOM)
                            offset = if (scale > 1f) offset + pan else Offset.Zero
                        }
                    }
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onDoubleTap = {
                                if (scale > 1f) {
                                    scale = 1f
                                    offset = Offset.Zero
                                } else {
                                    scale = DOUBLE_TAP_ZOOM
                                }
                            },
                        )
                    }
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    },
            )

            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                IconButton(onClick = { PhotoStorage.share(context, fileName) }) {
                    Icon(
                        painter = painterResource(R.drawable.ic_baseline_share_24),
                        contentDescription = "Share",
                        tint = Color.White,
                    )
                }
                IconButton(onClick = { onSaveClick() }) {
                    Icon(
                        painter = painterResource(R.drawable.ic_baseline_download_24),
                        contentDescription = "Save",
                        tint = Color.White,
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        painter = painterResource(R.drawable.ic_baseline_close_24),
                        contentDescription = "Close",
                        tint = Color.White,
                    )
                }
            }
        }
    }
}
