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
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.crazystudio.sportrecorder.domain.model.EatRecord
import com.crazystudio.sportrecorder.domain.model.Venue
import com.crazystudio.sportrecorder.shared.resources.Res
import com.crazystudio.sportrecorder.shared.resources.ic_baseline_close_24
import com.crazystudio.sportrecorder.shared.resources.ic_baseline_edit_24
import com.crazystudio.sportrecorder.shared.resources.record_search_clear
import com.crazystudio.sportrecorder.shared.resources.record_search_empty
import com.crazystudio.sportrecorder.shared.resources.record_search_hint
import com.crazystudio.sportrecorder.shared.resources.record_venue_search
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.abs
import kotlin.math.roundToLong
import kotlin.time.Instant

@OptIn(ExperimentalFoundationApi::class)
@Composable
@Suppress("LongMethod") // cohesive screen: search field + list + delete dialog
fun RecordScreen(
    records: List<EatRecord>,
    query: String,
    showSearch: Boolean,
    onQueryChange: (String) -> Unit,
    onVenueClick: (Venue) -> Unit,
    onDelete: (EatRecord) -> Unit,
    onEditRecord: (Int) -> Unit,
    photoModel: (String) -> Any?,
    onPhotoClick: (List<String>, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var recordToDelete by remember { mutableStateOf<EatRecord?>(null) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (showSearch) {
                RecordSearchField(query = query, onQueryChange = onQueryChange)
            }
            if (query.isNotBlank() && records.isEmpty()) {
                Text(
                    text = stringResource(Res.string.record_search_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(12.dp),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(12.dp),
                ) {
                    items(records, key = { it.id }) { record ->
                        RecordCard(
                            record = record,
                            onLongClick = { recordToDelete = record },
                            onVenueClick = onVenueClick,
                            photoModel = photoModel,
                            onThumbnailClick = onPhotoClick,
                            onEditRecord = onEditRecord,
                        )
                    }
                }
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

/**
 * The venue search. Its text lives here, not in the caller's state: a round trip through an async flow
 * would reset the field mid-composition and break Chinese IME input. [query] changing from outside
 * (a tapped venue) is copied in.
 *
 * Every value sent to [onQueryChange] is remembered until [query] echoes it back. The echo can lag:
 * after typing "a" then "b", [query] may still arrive as "a". A value we sent ourselves is an echo, never
 * a change from outside, so it (and every older send it overtook) is dropped instead of rewriting the field.
 */
@Composable
private fun RecordSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var text by remember { mutableStateOf(query) }
    val awaitingEcho = remember { mutableListOf<String>() }
    val send: (String) -> Unit = { value ->
        text = value
        awaitingEcho += value
        onQueryChange(value)
    }
    LaunchedEffect(query) {
        val echoed = awaitingEcho.indexOf(query)
        if (echoed >= 0) {
            awaitingEcho.subList(0, echoed + 1).clear()
        } else {
            awaitingEcho.clear()
            text = query
        }
    }
    OutlinedTextField(
        value = text,
        onValueChange = send,
        // A label rather than a placeholder: it stays the field's accessible name while it holds text.
        label = { Text(stringResource(Res.string.record_search_hint)) },
        singleLine = true,
        trailingIcon = if (text.isNotEmpty()) {
            {
                IconButton(onClick = { send("") }) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_baseline_close_24),
                        contentDescription = stringResource(Res.string.record_search_clear),
                    )
                }
            }
        } else {
            null
        },
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, top = 8.dp),
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
@Suppress("LongMethod") // cohesive card layout: header, note, photo carousel, location
private fun RecordCard(
    record: EatRecord,
    onLongClick: () -> Unit,
    onVenueClick: (Venue) -> Unit,
    photoModel: (String) -> Any?,
    onThumbnailClick: (List<String>, Int) -> Unit,
    onEditRecord: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colorScheme.surfaceContainer)
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
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = formatDate(record.time),
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurface,
                )
                Text(
                    text = formatTime(record.time),
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurface,
                )
                record.venue?.let { venue ->
                    VenueLines(venue = venue, onClick = { onVenueClick(venue) }, onLongClick = onLongClick)
                }
            }
            Icon(
                painter = painterResource(Res.drawable.ic_baseline_edit_24),
                contentDescription = "Edit",
                tint = colorScheme.primary,
                modifier = Modifier
                    .size(24.dp)
                    .clickable { onEditRecord(record.id) },
            )
        }

        // Note (local val: cross-module smart-cast doesn't apply to :shared properties)
        val note = record.note
        if (!note.isNullOrBlank()) {
            Text(
                text = note,
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.onSurface,
            )
        }

        // Photo carousel
        if (record.photos.isNotEmpty()) {
            val photoNames = remember(record.photos) { record.photos.map { it.fileName } }
            val pagerState = rememberPagerState(pageCount = { record.photos.size })
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxWidth(),
            ) { page ->
                val photo = record.photos[page]
                AsyncImage(
                    model = photoModel(photo.fileName),
                    contentDescription = null,
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onThumbnailClick(photoNames, page) },
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
                                    if (index == pagerState.currentPage) {
                                        colorScheme.primary
                                    } else {
                                        colorScheme.onSurfaceVariant
                                    },
                                ),
                        )
                    }
                }
            }
        }

        // Location
        record.location?.let { loc ->
            Text(
                text = "📍 ${fmt4(loc.lat)}, ${fmt4(loc.lng)}",
                style = MaterialTheme.typography.labelSmall,
                color = colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * The record's venue under its time: the name, then the address when there is one. Tapping searches for
 * it; a long press does what it does anywhere else on the card ([onLongClick]), since this block would
 * otherwise take the press from the card.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun VenueLines(venue: Venue, onClick: () -> Unit, onLongClick: () -> Unit) {
    Column(
        modifier = Modifier.combinedClickable(
            onClickLabel = stringResource(Res.string.record_venue_search),
            role = Role.Button,
            onLongClick = onLongClick,
            onClick = onClick,
        ),
    ) {
        Text(
            text = venue.name,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        val address = venue.address
        if (!address.isNullOrBlank()) {
            Text(
                text = address,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun pad2(n: Int): String = n.toString().padStart(2, '0')

/** "yyyy/MM/dd" in the system timezone (kotlinx-datetime; no java SimpleDateFormat on Native). */
private fun formatDate(millis: Long): String {
    val dt = Instant.fromEpochMilliseconds(millis).toLocalDateTime(TimeZone.currentSystemDefault())
    return "${dt.year}/${pad2(dt.month.ordinal + 1)}/${pad2(dt.day)}"
}

/** "HH:mm" in the system timezone. */
private fun formatTime(millis: Long): String {
    val dt = Instant.fromEpochMilliseconds(millis).toLocalDateTime(TimeZone.currentSystemDefault())
    return "${pad2(dt.hour)}:${pad2(dt.minute)}"
}

/** Formats a coordinate to 4 decimals without java's String.format (unavailable on Native). */
private fun fmt4(value: Double): String {
    val scaled = (value * 10_000).roundToLong()
    val sign = if (scaled < 0) "-" else ""
    val a = abs(scaled)
    return "$sign${a / 10_000}.${(a % 10_000).toString().padStart(4, '0')}"
}
