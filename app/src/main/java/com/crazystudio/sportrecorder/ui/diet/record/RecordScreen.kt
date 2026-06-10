package com.crazystudio.sportrecorder.ui.diet.record

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.crazystudio.sportrecorder.entity.EatTime
import com.crazystudio.sportrecorder.ui.theme.bg_black
import com.crazystudio.sportrecorder.ui.theme.bg_black2
import com.crazystudio.sportrecorder.ui.theme.white
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val dateFormat = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault())
private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RecordScreen(
    records: List<EatTime>,
    onDelete: (EatTime) -> Unit,
    modifier: Modifier = Modifier,
) {
    var recordToDelete by remember { mutableStateOf<EatTime?>(null) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(bg_black)
    ) {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(records, key = { it.id }) { record ->
                RecordRow(
                    record = record,
                    onLongClick = { recordToDelete = record }
                )
            }
        }
    }

    recordToDelete?.let { record ->
        AlertDialog(
            onDismissRequest = { recordToDelete = null },
            title = { Text(text = "Delete") },
            text = { Text(text = "Do you want to delete this record?") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(record)
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
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecordRow(
    record: EatTime,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {},
                onLongClick = onLongClick,
            )
            .padding(0.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // ID column — fixed 50dp
        Text(
            text = record.id.toString(),
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
            Text(text = " ", fontSize = 14.sp) // gives the divider the same height as the row content
        }

        // Date column — weight 1
        Text(
            text = dateFormat.format(Date(record.time)),
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
            text = timeFormat.format(Date(record.time)),
            fontSize = 14.sp,
            color = white,
            modifier = Modifier
                .weight(1f)
                .padding(10.dp),
        )
    }
}
