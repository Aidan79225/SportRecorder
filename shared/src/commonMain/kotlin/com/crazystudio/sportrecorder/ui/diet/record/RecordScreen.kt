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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.crazystudio.sportrecorder.domain.model.EatRecord
import com.crazystudio.sportrecorder.domain.model.Venue
import com.crazystudio.sportrecorder.domain.model.VenueAddress
import com.crazystudio.sportrecorder.domain.venue.RecordVenueFilter
import com.crazystudio.sportrecorder.shared.resources.Res
import com.crazystudio.sportrecorder.shared.resources.ic_baseline_close_24
import com.crazystudio.sportrecorder.shared.resources.ic_baseline_edit_24
import com.crazystudio.sportrecorder.shared.resources.record_delete_confirm
import com.crazystudio.sportrecorder.shared.resources.record_delete_dismiss
import com.crazystudio.sportrecorder.shared.resources.record_delete_message
import com.crazystudio.sportrecorder.shared.resources.record_delete_title
import com.crazystudio.sportrecorder.shared.resources.record_edit
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

/**
 * The Record tab: every record, newest first, with a venue search above them.
 *
 * The search text is this screen's own state — the one place it lives. Typing, the ✕ and tapping a card's
 * venue all go through one `search` path, so the field and the list can never disagree, and a new
 * search always starts at the newest match.
 */
@Composable
fun RecordScreen(
    records: List<EatRecord>,
    onDelete: (EatRecord) -> Unit,
    onEditRecord: (Int) -> Unit,
    photoModel: (String) -> Any?,
    onPhotoClick: (List<String>, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var recordToDelete by remember { mutableStateOf<EatRecord?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    val shown = remember(records, query) { RecordVenueFilter.matching(records, query) }
    val showSearch = remember(records, query) { RecordVenueFilter.canSearch(records, query) }
    // Outside the branch below, so the list keeps its place while the no-match line stands in for it.
    val listState = rememberLazyListState()
    val search: (String) -> Unit = { value ->
        query = value
        listState.requestScrollToItem(0)
    }

    // imePadding: edge-to-edge, so without it the keyboard would cover the last results.
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .imePadding(),
    ) {
        if (showSearch) {
            RecordSearchField(text = query, onTextChange = search)
        }
        // Only when there are records to miss: after process death the query comes back before the
        // records do, and an empty list then must not flash (and announce) "no matching records".
        if (records.isNotEmpty() && shown.isEmpty() && query.isNotBlank()) {
            NoMatchingRecords()
        } else {
            RecordList(
                records = shown,
                listState = listState,
                onLongClick = { recordToDelete = it },
                onVenueClick = { venue -> search(RecordVenueFilter.queryFor(venue)) },
                photoModel = photoModel,
                onPhotoClick = onPhotoClick,
                onEditRecord = onEditRecord,
            )
        }
    }

    recordToDelete?.let { record ->
        DeleteRecordDialog(
            record = record,
            onConfirm = {
                onDelete(it)
                recordToDelete = null
            },
            onDismiss = { recordToDelete = null },
        )
    }
}

/** The venue search field; [RecordScreen] owns its text. The ✕ clears it through [onTextChange]. */
@Composable
private fun RecordSearchField(
    text: String,
    onTextChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = text,
        onValueChange = onTextChange,
        // A label rather than a placeholder: it stays the field's accessible name while it holds text.
        label = { Text(stringResource(Res.string.record_search_hint)) },
        singleLine = true,
        trailingIcon = if (text.isNotEmpty()) {
            {
                IconButton(onClick = { onTextChange("") }) {
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

@Composable
private fun RecordList(
    records: List<EatRecord>,
    listState: LazyListState,
    onLongClick: (EatRecord) -> Unit,
    onVenueClick: (Venue) -> Unit,
    photoModel: (String) -> Any?,
    onPhotoClick: (List<String>, Int) -> Unit,
    onEditRecord: (Int) -> Unit,
) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(12.dp),
    ) {
        items(records, key = { it.id }) { record ->
            RecordCard(
                record = record,
                onLongClick = { onLongClick(record) },
                onVenueClick = onVenueClick,
                photoModel = photoModel,
                onThumbnailClick = onPhotoClick,
                onEditRecord = onEditRecord,
            )
        }
    }
}

/** Stands in for the list when a search matches nothing; announced politely when it appears. */
@Composable
private fun NoMatchingRecords() {
    Text(
        text = stringResource(Res.string.record_search_empty),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .padding(12.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
    )
}

@Composable
private fun DeleteRecordDialog(record: EatRecord, onConfirm: (EatRecord) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(Res.string.record_delete_title)) },
        text = { Text(text = stringResource(Res.string.record_delete_message)) },
        confirmButton = {
            TextButton(onClick = { onConfirm(record) }) {
                Text(text = stringResource(Res.string.record_delete_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(Res.string.record_delete_dismiss))
            }
        },
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
@Suppress("LongMethod") // cohesive card layout: header (date, time, venue), note, photo carousel, location
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
                contentDescription = stringResource(Res.string.record_edit),
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
        VenueAddress.clean(venue.address)?.let { address ->
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
