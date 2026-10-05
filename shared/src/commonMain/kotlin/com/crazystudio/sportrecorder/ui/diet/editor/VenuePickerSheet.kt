package com.crazystudio.sportrecorder.ui.diet.editor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.crazystudio.sportrecorder.domain.model.Venue
import com.crazystudio.sportrecorder.domain.model.VenueName
import com.crazystudio.sportrecorder.shared.resources.Res
import com.crazystudio.sportrecorder.shared.resources.backup_cancel
import com.crazystudio.sportrecorder.shared.resources.editor_venue_merge_body
import com.crazystudio.sportrecorder.shared.resources.editor_venue_merge_confirm
import com.crazystudio.sportrecorder.shared.resources.editor_venue_merge_title
import com.crazystudio.sportrecorder.shared.resources.editor_venue_more
import com.crazystudio.sportrecorder.shared.resources.editor_venue_new
import com.crazystudio.sportrecorder.shared.resources.editor_venue_pick
import com.crazystudio.sportrecorder.shared.resources.editor_venue_rename
import com.crazystudio.sportrecorder.shared.resources.editor_venue_use_here
import com.crazystudio.sportrecorder.shared.resources.ic_baseline_more_vert_24
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Choose, create or rename the venue of the record being edited.
 *
 * Choosing a venue only sets [EatTimeEditorUiState.venue]; it never copies the venue's position
 * onto the record, because the record's location is where the user was and the venue is where the
 * food came from. Everything here is optional — the editor can be saved without ever opening it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("LongParameterList") // Compose event slots
fun VenuePickerSheet(
    state: EatTimeEditorUiState,
    onSelect: (Venue) -> Unit,
    onCreate: (String) -> Unit,
    onRename: (Venue, String) -> Unit,
    onUseThisPosition: (Venue) -> Unit,
    onConfirmMerge: () -> Unit,
    onCancelMerge: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var renaming by remember { mutableStateOf<Venue?>(null) }

    // Slide out before the host drops the sheet, like the editor sheet itself does.
    fun finish(action: () -> Unit) {
        action()
        scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
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
            val matches = remember(state.venueOptions, query) { venuesMatching(state.venueOptions, query) }
            val typed = VenueName.normalize(query)
            val canCreate = typed.isNotEmpty() && state.venueOptions.none { VenueName.sameAs(it.name, typed) }
            LazyColumn {
                if (canCreate) {
                    item(key = "create") {
                        VenueRow(
                            text = stringResource(Res.string.editor_venue_new, typed),
                            onClick = { finish { onCreate(typed) } },
                        )
                    }
                }
                items(matches, key = { it.id }) { venue ->
                    VenueRow(
                        text = venue.name,
                        onClick = { finish { onSelect(venue) } },
                    ) {
                        VenueOverflow(
                            canUseThisPosition = state.location != null,
                            onRename = { renaming = venue },
                            onUseThisPosition = { onUseThisPosition(venue) },
                        )
                    }
                }
            }
        }
    }

    renaming?.let { venue ->
        RenameDialog(
            venue = venue,
            onConfirm = { newName ->
                renaming = null
                onRename(venue, newName)
            },
            onDismiss = { renaming = null },
        )
    }
    state.pendingMerge?.let { pending ->
        MergeDialog(pending = pending, onConfirm = onConfirmMerge, onDismiss = onCancelMerge)
    }
}

/** Venues whose name contains what was typed, in the order given (the picker's suggested order). */
private fun venuesMatching(venues: List<Venue>, query: String): List<Venue> {
    val typed = VenueName.normalize(query)
    return if (typed.isEmpty()) venues else venues.filter { it.name.contains(typed, ignoreCase = true) }
}

@Composable
private fun VenueRow(text: String, onClick: () -> Unit, trailing: @Composable () -> Unit = {}) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
        )
        trailing()
    }
}

@Composable
private fun VenueOverflow(
    canUseThisPosition: Boolean,
    onRename: () -> Unit,
    onUseThisPosition: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                painter = painterResource(Res.drawable.ic_baseline_more_vert_24),
                contentDescription = stringResource(Res.string.editor_venue_more),
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.editor_venue_rename)) },
                onClick = {
                    expanded = false
                    onRename()
                },
            )
            // Correcting a venue's position needs a position to correct it with; a record that
            // has no location of its own has nothing to offer.
            if (canUseThisPosition) {
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.editor_venue_use_here)) },
                    onClick = {
                        expanded = false
                        onUseThisPosition()
                    },
                )
            }
        }
    }
}

@Composable
private fun RenameDialog(venue: Venue, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember(venue.id) { mutableStateOf(venue.name) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.editor_venue_rename)) },
        text = {
            OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true)
        },
        confirmButton = {
            // A name that is blank once normalized is not a name; the view model refuses it too.
            TextButton(
                onClick = { onConfirm(name) },
                enabled = VenueName.normalize(name).isNotEmpty(),
            ) {
                Text(stringResource(Res.string.editor_venue_rename))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.backup_cancel)) }
        },
    )
}

/** A merge rewrites records the user is not looking at, so the dialog says how many move. */
@Composable
private fun MergeDialog(
    pending: EatTimeEditorUiState.PendingMerge,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.editor_venue_merge_title, pending.intoName)) },
        text = {
            Text(stringResource(Res.string.editor_venue_merge_body, pending.movedRecords, pending.intoName))
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(Res.string.editor_venue_merge_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.backup_cancel)) }
        },
    )
}
