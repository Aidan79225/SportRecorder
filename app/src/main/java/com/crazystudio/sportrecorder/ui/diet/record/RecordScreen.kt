package com.crazystudio.sportrecorder.ui.diet.record

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.crazystudio.sportrecorder.R
import com.crazystudio.sportrecorder.entity.EatTime
import com.crazystudio.sportrecorder.entity.EatTimeWithPhotos
import com.crazystudio.sportrecorder.ui.theme.bg_black
import com.crazystudio.sportrecorder.ui.theme.bg_black2
import com.crazystudio.sportrecorder.ui.theme.grey_1
import com.crazystudio.sportrecorder.ui.theme.light_green
import com.crazystudio.sportrecorder.ui.theme.white
import com.crazystudio.sportrecorder.util.PhotoStorage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val dateFormat = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault())
private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

@OptIn(ExperimentalFoundationApi::class)
@Composable
@Suppress("LongMethod") // cohesive screen: list + delete dialog + full-screen photo overlay
fun RecordScreen(
    records: List<EatTimeWithPhotos>,
    onDelete: (EatTime) -> Unit,
    onEditRecord: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var recordToDelete by remember { mutableStateOf<EatTime?>(null) }
    var fullScreenPhoto by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(bg_black)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(12.dp),
        ) {
            items(records, key = { it.eatTime.id }) { record ->
                RecordCard(
                    record = record,
                    onLongClick = { recordToDelete = record.eatTime },
                    onThumbnailClick = { fileName -> fullScreenPhoto = fileName },
                    onEditRecord = onEditRecord,
                )
            }
        }
    }

    recordToDelete?.let { eatTime ->
        AlertDialog(
            onDismissRequest = { recordToDelete = null },
            title = { Text(text = "Delete") },
            text = { Text(text = "Do you want to delete this record?") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(eatTime)
                    recordToDelete = null
                }) {
                    Text(text = "Yes")
                }
            },
            dismissButton = {
                TextButton(onClick = { recordToDelete = null }) {
                    Text(text = "No")
                }
            },
        )
    }

    fullScreenPhoto?.let { fileName ->
        val context = LocalContext.current
        Dialog(onDismissRequest = { fullScreenPhoto = null }) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(bg_black)
                    .clickable { fullScreenPhoto = null },
                contentAlignment = Alignment.Center,
            ) {
                AsyncImage(
                    model = PhotoStorage.fileFor(context, fileName),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
@Suppress("LongMethod") // cohesive card layout: header, note, photo carousel, location
private fun RecordCard(
    record: EatTimeWithPhotos,
    onLongClick: () -> Unit,
    onThumbnailClick: (String) -> Unit,
    onEditRecord: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val eatTime = record.eatTime
    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bg_black2)
            .combinedClickable(
                onClick = {},
                onLongClick = onLongClick,
            )
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Header: date/time on the left, pencil edit on the right
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = dateFormat.format(Date(eatTime.time)),
                    fontSize = 14.sp,
                    color = white,
                )
                Text(
                    text = timeFormat.format(Date(eatTime.time)),
                    fontSize = 14.sp,
                    color = white,
                )
            }
            Icon(
                painter = painterResource(R.drawable.ic_baseline_edit_24),
                contentDescription = "Edit",
                tint = light_green,
                modifier = Modifier
                    .size(24.dp)
                    .clickable { onEditRecord(eatTime.id) },
            )
        }

        // Note
        if (!eatTime.note.isNullOrBlank()) {
            Text(
                text = eatTime.note,
                fontSize = 14.sp,
                color = white,
            )
        }

        // Photo carousel
        if (record.photos.isNotEmpty()) {
            val pagerState = rememberPagerState(pageCount = { record.photos.size })
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxWidth(),
            ) { page ->
                val photo = record.photos[page]
                AsyncImage(
                    model = PhotoStorage.fileFor(context, photo.fileName),
                    contentDescription = null,
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onThumbnailClick(photo.fileName) },
                )
            }

            if (record.photos.size > 1) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    repeat(record.photos.size) { index ->
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 3.dp)
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(
                                    if (index == pagerState.currentPage) light_green else grey_1
                                ),
                        )
                    }
                }
            }
        }

        // Location
        if (eatTime.lat != null && eatTime.lng != null) {
            Text(
                text = "📍 ${String.format(Locale.getDefault(), "%.4f, %.4f", eatTime.lat, eatTime.lng)}",
                fontSize = 12.sp,
                color = white,
            )
        }
    }
}
