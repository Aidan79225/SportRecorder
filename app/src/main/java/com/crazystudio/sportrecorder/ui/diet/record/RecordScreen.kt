package com.crazystudio.sportrecorder.ui.diet.record

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Divider
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.crazystudio.sportrecorder.entity.EatTime
import com.crazystudio.sportrecorder.entity.EatTimeWithPhotos
import com.crazystudio.sportrecorder.ui.theme.bg_black
import com.crazystudio.sportrecorder.ui.theme.bg_black2
import com.crazystudio.sportrecorder.ui.theme.white
import com.crazystudio.sportrecorder.util.PhotoStorage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val dateFormat = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault())
private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RecordScreen(
    records: List<EatTimeWithPhotos>,
    onDelete: (EatTime) -> Unit,
    modifier: Modifier = Modifier,
) {
    var recordToDelete by remember { mutableStateOf<EatTime?>(null) }
    var fullScreenPhoto by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(bg_black)
    ) {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(records, key = { it.eatTime.id }) { record ->
                RecordRow(
                    record = record,
                    onLongClick = { recordToDelete = record.eatTime },
                    onThumbnailClick = { fileName -> fullScreenPhoto = fileName },
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
private fun RecordRow(
    record: EatTimeWithPhotos,
    onLongClick: () -> Unit,
    onThumbnailClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val eatTime = record.eatTime
    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {},
                onLongClick = onLongClick,
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(0.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // ID column — fixed 50dp
            Text(
                text = eatTime.id.toString(),
                fontSize = 14.sp,
                color = white,
                modifier = Modifier
                    .width(50.dp)
                    .padding(10.dp),
            )

            // Divider
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .padding(vertical = 0.dp)
                    .background(bg_black2)
                    .align(Alignment.CenterVertically)
            ) {
                Text(text = " ", fontSize = 14.sp)
            }

            // Date column — weight 1
            Text(
                text = dateFormat.format(Date(eatTime.time)),
                fontSize = 14.sp,
                color = white,
                modifier = Modifier
                    .weight(1f)
                    .padding(10.dp),
            )

            // Divider
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .background(bg_black2)
                    .align(Alignment.CenterVertically)
            ) {
                Text(text = " ", fontSize = 14.sp)
            }

            // Time column — weight 1
            Text(
                text = timeFormat.format(Date(eatTime.time)),
                fontSize = 14.sp,
                color = white,
                modifier = Modifier
                    .weight(1f)
                    .padding(10.dp),
            )
        }

        // Photo thumbnails
        if (record.photos.isNotEmpty()) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            ) {
                items(record.photos) { photo ->
                    AsyncImage(
                        model = PhotoStorage.fileFor(context, photo.fileName),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .padding(end = 6.dp)
                            .size(64.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onThumbnailClick(photo.fileName) },
                    )
                }
            }
        }

        // GPS coordinates
        if (eatTime.lat != null && eatTime.lng != null) {
            Text(
                text = "📍 ${String.format("%.4f, %.4f", eatTime.lat, eatTime.lng)}",
                fontSize = 12.sp,
                color = white,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp),
            )
        }

        Divider(color = bg_black2, thickness = 1.dp)
    }
}
