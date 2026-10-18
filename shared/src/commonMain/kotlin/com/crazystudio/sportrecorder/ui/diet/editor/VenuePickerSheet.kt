package com.crazystudio.sportrecorder.ui.diet.editor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.crazystudio.sportrecorder.domain.model.Venue
import com.crazystudio.sportrecorder.domain.model.VenueName
import com.crazystudio.sportrecorder.domain.venue.VenuePicker
import com.crazystudio.sportrecorder.shared.resources.Res
import com.crazystudio.sportrecorder.shared.resources.backup_cancel
import com.crazystudio.sportrecorder.shared.resources.editor_paste_hint
import com.crazystudio.sportrecorder.shared.resources.editor_paste_title
import com.crazystudio.sportrecorder.shared.resources.editor_paste_use
import com.crazystudio.sportrecorder.shared.resources.editor_venue_edit_named
import com.crazystudio.sportrecorder.shared.resources.editor_venue_new
import com.crazystudio.sportrecorder.shared.resources.editor_venue_paste
import com.crazystudio.sportrecorder.shared.resources.editor_venue_pick
import com.crazystudio.sportrecorder.shared.resources.editor_venue_position_updated
import com.crazystudio.sportrecorder.shared.resources.ic_baseline_content_paste_24
import com.crazystudio.sportrecorder.shared.resources.ic_outline_edit_24
import com.crazystudio.sportrecorder.shared.resources.venue_no_position
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Choose or create the venue of the record being edited, or edit one with ✎.
 *
 * Choosing a venue only sets [EatTimeEditorUiState.venue]; it never copies the venue's position
 * onto the record, because the record's location is where the user was and the venue is where the
 * food came from. Everything here is optional — the editor can be saved without ever opening it.
 *
 * Each row shows the venue's address under its name, so two branches with one name can be told
 * apart. The paste row reads the clipboard only when tapped: when it held a Maps link the place is
 * being opened and the sheet closes; otherwise [EatTimeEditorUiState.pasteDialog] asks for one.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VenuePickerSheet(
    state: EatTimeEditorUiState,
    actions: VenuePickerActions,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    // By id, so the dialog always shows the venue as stored now (a move updates its position line).
    var editingId by rememberSaveable { mutableStateOf<Int?>(null) }

    // A question left pending, or a snackbar message cut off by closing the sheet (showSnackbar is
    // cancelled before it can consume), must not outlive the sheet and reappear the next time.
    val dismiss = {
        actions.cancelMerge()
        actions.cancelDelete()
        actions.cancelMove()
        actions.dismissPasteDialog()
        actions.closeVenueEdit()
        actions.consumeMessage()
        onDismiss()
    }

    // Slide out before the host drops the sheet, like the editor sheet itself does.
    fun finish(action: () -> Unit) {
        action()
        scope.launch { sheetState.hide() }.invokeOnCompletion { dismiss() }
    }

    // The sheet is its own window, so a snackbar hosted behind it would never be seen.
    val snackbarHostState = remember { SnackbarHostState() }
    val messageText = state.message?.let { stringResource(messageRes(it)) }
    LaunchedEffect(state.message) {
        if (messageText != null) {
            snackbarHostState.showSnackbar(messageText)
            actions.consumeMessage()
        }
    }

    ModalBottomSheet(onDismissRequest = dismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 20.dp),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text(stringResource(Res.string.editor_venue_pick)) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
            )
            VenueList(
                venues = state.venueOptions,
                query = query,
                onPaste = { scope.launch { if (actions.pasteMapsLink()) finish {} } },
                onCreate = { name -> finish { actions.createVenue(name) } },
                onSelect = { venue -> finish { actions.selectVenue(venue) } },
                onEdit = { venue ->
                    // Whatever an earlier edit left behind goes before this one starts.
                    actions.closeVenueEdit()
                    editingId = venue.id
                },
            )
            SnackbarHost(hostState = snackbarHostState)
        }
    }

    VenuePickerDialogs(
        state = state,
        actions = actions,
        editing = editingId?.let { id -> state.venueOptions.firstOrNull { it.id == id } },
        closeEditing = {
            editingId = null
            actions.closeVenueEdit()
        },
        pasteToSelect = { text -> finish { actions.pasteText(text) } },
    )
}

@Composable
@Suppress("LongParameterList") // Compose event slots
private fun VenueList(
    venues: List<Venue>,
    query: String,
    onPaste: () -> Unit,
    onCreate: (String) -> Unit,
    onSelect: (Venue) -> Unit,
    onEdit: (Venue) -> Unit,
) {
    val matches = remember(venues, query) { VenuePicker.matching(venues, query) }
    val canCreate = VenuePicker.canCreate(venues, query)
    val typed = VenueName.normalize(query)
    LazyColumn {
        item(key = "paste") {
            PasteRow(onClick = onPaste)
        }
        if (canCreate) {
            item(key = "create") {
                PickerRow(onClick = { onCreate(typed) }) {
                    Text(
                        text = stringResource(Res.string.editor_venue_new, typed),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }
        items(matches, key = { it.id }) { venue ->
            VenueRow(venue = venue, onClick = { onSelect(venue) }, onEdit = { onEdit(venue) })
        }
    }
}

/** Every dialog the picker opens; each sits above the one that led to it. */
@Composable
private fun VenuePickerDialogs(
    state: EatTimeEditorUiState,
    actions: VenuePickerActions,
    editing: Venue?,
    closeEditing: () -> Unit,
    pasteToSelect: (String) -> Unit,
) {
    editing?.let { venue ->
        VenueEditDialog(
            venue = venue,
            canUseThisPosition = state.location != null,
            notice = state.repositionNotice,
            resolving = state.repositioning,
            actions = actions,
            onClose = closeEditing,
        )
    }
    state.pendingDelete?.let { pending ->
        DeleteVenueDialog(
            pending = pending,
            onConfirm = {
                closeEditing()
                actions.confirmDelete()
            },
            onDismiss = actions::cancelDelete,
        )
    }
    state.pendingMerge?.let { pending ->
        MergeOrKeepDialog(
            pending = pending,
            onMerge = actions::mergeVenues,
            onKeepBoth = actions::keepBothVenues,
            onDismiss = actions::cancelMerge,
        )
    }
    state.pendingMove?.let { pending ->
        MoveDialog(pending = pending, onConfirm = actions::confirmMove, onDismiss = actions::cancelMove)
    }
    when (state.pasteDialog) {
        // A place for this meal: the sheet closes, as when the clipboard held the link.
        PasteTarget.Select -> PasteDialog(onConfirm = pasteToSelect, onDismiss = actions::dismissPasteDialog)
        // A new position for the venue being edited: back to its edit dialog.
        is PasteTarget.Reposition -> PasteDialog(onConfirm = actions::pasteText, onDismiss = actions::dismissPasteDialog)
        null -> Unit
    }
}

private fun messageRes(message: EditorMessage) = when (message) {
    EditorMessage.VenuePositionUpdated -> Res.string.editor_venue_position_updated
}

@Composable
private fun PickerRow(onClick: () -> Unit, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/** The name, and under it where the venue is: its address, 「沒有位置」, or nothing while a lookup runs. */
@Composable
private fun VenueRow(venue: Venue, onClick: () -> Unit, onEdit: () -> Unit) {
    PickerRow(onClick = onClick) {
        Column(modifier = Modifier.weight(1f).padding(vertical = 4.dp)) {
            Text(text = venue.name, style = MaterialTheme.typography.bodyLarge)
            venueSecondLine(venue)?.let { line ->
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        IconButton(onClick = onEdit) {
            Icon(
                painter = painterResource(Res.drawable.ic_outline_edit_24),
                contentDescription = stringResource(Res.string.editor_venue_edit_named, venue.name),
            )
        }
    }
}

@Composable
private fun venueSecondLine(venue: Venue): String? = when {
    venue.address != null -> venue.address
    venue.lat == null || venue.lng == null -> stringResource(Res.string.venue_no_position)
    else -> null
}

@Composable
private fun PasteRow(onClick: () -> Unit) {
    PickerRow(onClick = onClick) {
        Icon(
            painter = painterResource(Res.drawable.ic_baseline_content_paste_24),
            contentDescription = null, // the text beside it says what the row does
            modifier = Modifier
                .padding(end = 12.dp)
                .size(24.dp),
        )
        Text(
            text = stringResource(Res.string.editor_venue_paste),
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

/** Asked when the clipboard held no Maps link: the user pastes (or types) one here. */
@Composable
private fun PasteDialog(onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    // Saveable, so a link pasted in survives rotation.
    var text by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.editor_paste_title)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text(stringResource(Res.string.editor_paste_hint)) },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }, enabled = text.isNotBlank()) {
                Text(stringResource(Res.string.editor_paste_use))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.backup_cancel)) }
        },
    )
}
