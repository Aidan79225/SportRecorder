package com.crazystudio.sportrecorder.ui.diet.record

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import coil.compose.AsyncImage
import com.crazystudio.sportrecorder.R
import com.crazystudio.sportrecorder.util.PhotoStorage
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs

private const val MAX_ZOOM = 5f
private const val DOUBLE_TAP_ZOOM = 2.5f
private const val ZOOM_ANIM_MS = 250

/**
 * Full-screen, immersive photo viewer. Supports horizontal swipe between [fileNames]
 * (starting at [initialIndex]), focal pinch-to-zoom with bounded pan, animated double-tap
 * zoom, single-tap to toggle the toolbar (and system bars), swipe-down to dismiss, plus
 * share and save-to-gallery actions.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
@Suppress("LongMethod", "CyclomaticComplexMethod") // cohesive viewer: pager + gestures + chrome
fun FullScreenPhotoViewer(
    fileNames: List<String>,
    initialIndex: Int,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val view = LocalView.current

    val pagerState = rememberPagerState(
        initialPage = initialIndex.coerceIn(0, (fileNames.size - 1).coerceAtLeast(0)),
        pageCount = { fileNames.size },
    )

    // Zoom/pan state for the currently visible page; reset whenever the page changes.
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var animJob by remember { mutableStateOf<Job?>(null) }
    LaunchedEffect(pagerState.currentPage) {
        animJob?.cancel()
        scale = 1f
        offset = Offset.Zero
    }

    // Toolbar + system-bar visibility, toggled by a single tap. Drives true immersive mode.
    var chromeVisible by remember { mutableStateOf(true) }
    LaunchedEffect(chromeVisible) {
        val window = (view.parent as? DialogWindowProvider)?.window ?: return@LaunchedEffect
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, view)
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (chromeVisible) {
            controller.show(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    // Swipe-down-to-dismiss drag state (only active while not zoomed in).
    val dismissThresholdPx = with(density) { 180.dp.toPx() }
    var dismissDragY by remember { mutableFloatStateOf(0f) }
    val dragProgress = (abs(dismissDragY) / dismissThresholdPx).coerceIn(0f, 1f)
    val backdropAlpha = 1f - dragProgress * 0.6f

    val currentFileName = fileNames[pagerState.currentPage]

    fun saveToGallery() = scope.launch {
        val ok = PhotoStorage.saveToGallery(context, currentFileName)
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
                .background(Color.Black.copy(alpha = backdropAlpha)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    // Only translate for the dismiss drag while not zoomed; otherwise the
                    // zoomed image must stay put.
                    .graphicsLayer { translationY = if (scale <= 1f) dismissDragY else 0f }
                    // Stable key: a keyed pointerInput would be disposed mid-pinch (when scale
                    // crosses 1) and leave dismissDragY stuck, hiding the image. Guard by zoom
                    // state inside the callbacks instead.
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onVerticalDrag = { change, dragAmount ->
                                if (scale <= 1f) {
                                    dismissDragY += dragAmount
                                    change.consume()
                                }
                            },
                            onDragEnd = {
                                if (scale <= 1f && abs(dismissDragY) > dismissThresholdPx) {
                                    onDismiss()
                                } else if (dismissDragY != 0f) {
                                    scope.launch { animate(dismissDragY, 0f) { v, _ -> dismissDragY = v } }
                                }
                            },
                            onDragCancel = {
                                if (dismissDragY != 0f) {
                                    scope.launch { animate(dismissDragY, 0f) { v, _ -> dismissDragY = v } }
                                }
                            },
                        )
                    },
            ) {
                // Swipe between pages only while not zoomed; when zoomed, drags pan the image.
                HorizontalPager(
                    state = pagerState,
                    userScrollEnabled = scale <= 1f,
                    modifier = Modifier.fillMaxSize(),
                ) { page ->
                    val isCurrent = page == pagerState.currentPage
                    AsyncImage(
                        model = PhotoStorage.fileFor(context, fileNames[page]),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .then(
                                if (isCurrent) {
                                    Modifier
                                        .pointerInput(Unit) {
                                            // Custom transform loop: only act on a real pinch
                                            // (2+ pointers) or while already zoomed in, so single
                                            // finger drags stay free for the pager / dismiss.
                                            awaitEachGesture {
                                                awaitFirstDown(requireUnconsumed = false)
                                                animJob?.cancel()
                                                do {
                                                    val event = awaitPointerEvent()
                                                    val pressed = event.changes.count { it.pressed }
                                                    val pinching = pressed >= 2
                                                    // Require a pointer still down: on the release
                                                    // event calculateCentroid() is Unspecified and
                                                    // would feed NaN into the transform.
                                                    if ((pinching || scale > 1f) && pressed > 0) {
                                                        // A pinch may have started after a brief
                                                        // one-finger move; drop any dismiss drag.
                                                        if (pinching) dismissDragY = 0f
                                                        val oldScale = scale
                                                        val newScale = (oldScale * event.calculateZoom())
                                                            .coerceIn(1f, MAX_ZOOM)
                                                        scale = newScale
                                                        offset = if (newScale > 1f) {
                                                            focalOffset(
                                                                centroid = event.calculateCentroid(),
                                                                pan = event.calculatePan(),
                                                                from = ZoomState(oldScale, offset),
                                                                newScale = newScale,
                                                                size = size,
                                                            )
                                                        } else {
                                                            Offset.Zero
                                                        }
                                                        event.changes.forEach { it.consume() }
                                                    }
                                                } while (event.changes.any { it.pressed })
                                            }
                                        }
                                        .pointerInput(Unit) {
                                            detectTapGestures(
                                                onTap = { chromeVisible = !chromeVisible },
                                                onDoubleTap = { tapPos ->
                                                    val target = if (scale > 1f) 1f else DOUBLE_TAP_ZOOM
                                                    val viewSize = size
                                                    animJob?.cancel()
                                                    val from = ZoomState(scale, offset)
                                                    animJob = scope.launch {
                                                        animateZoom(target, tapPos, viewSize, from) { state ->
                                                            scale = state.scale
                                                            offset = state.offset
                                                        }
                                                    }
                                                },
                                            )
                                        }
                                        .graphicsLayer {
                                            scaleX = scale
                                            scaleY = scale
                                            translationX = offset.x
                                            translationY = offset.y
                                        }
                                } else {
                                    Modifier
                                },
                            ),
                    )
                }
            }

            AnimatedVisibility(
                visible = chromeVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.TopEnd),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    IconButton(onClick = { PhotoStorage.share(context, currentFileName) }) {
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

            AnimatedVisibility(
                visible = chromeVisible && fileNames.size > 1,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                Text(
                    text = "${pagerState.currentPage + 1} / ${fileNames.size}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                    modifier = Modifier
                        .navigationBarsPadding()
                        .padding(bottom = 16.dp),
                )
            }
        }
    }
}

/** Scale + pan offset of the visible photo. */
internal data class ZoomState(val scale: Float, val offset: Offset)

/** Animate scale + focal-anchored offset toward [target], reporting each frame via [onUpdate]. */
private suspend fun animateZoom(
    target: Float,
    tapPosition: Offset,
    size: IntSize,
    from: ZoomState,
    onUpdate: (ZoomState) -> Unit,
) {
    val endOffset = if (target > 1f) {
        focalOffset(tapPosition, Offset.Zero, from, target, size)
    } else {
        Offset.Zero
    }
    animate(0f, 1f, animationSpec = tween(ZOOM_ANIM_MS)) { t, _ ->
        onUpdate(ZoomState(from.scale + (target - from.scale) * t, lerp(from.offset, endOffset, t)))
    }
}

/**
 * Compute the new pan offset for a zoom step that keeps the content point under [centroid]
 * fixed, then clamp it so the (container-sized) content can't be panned past its edges.
 * [from] is the pre-step scale + offset; [newScale] the post-step scale.
 */
internal fun focalOffset(
    centroid: Offset,
    pan: Offset,
    from: ZoomState,
    newScale: Float,
    size: IntSize,
): Offset {
    // On the pointer-release event no pointer is down, so calculateCentroid() yields
    // Offset.Unspecified (NaN). Don't let that NaN reach the graphicsLayer translation,
    // which would blank the image; keep the last offset instead.
    if (!centroid.isSpecified) return from.offset
    val center = Offset(size.width / 2f, size.height / 2f)
    val raw = (centroid - center) + pan - (centroid - center - from.offset) * (newScale / from.scale)
    return clampOffset(raw, newScale, size)
}

private fun clampOffset(offset: Offset, scale: Float, size: IntSize): Offset {
    val maxX = (size.width * (scale - 1f)) / 2f
    val maxY = (size.height * (scale - 1f)) / 2f
    return Offset(
        offset.x.coerceIn(-maxX, maxX),
        offset.y.coerceIn(-maxY, maxY),
    )
}
