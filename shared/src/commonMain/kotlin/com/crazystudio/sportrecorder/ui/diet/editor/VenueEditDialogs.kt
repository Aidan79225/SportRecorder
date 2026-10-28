package com.crazystudio.sportrecorder.ui.diet.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.crazystudio.sportrecorder.domain.model.Venue
import com.crazystudio.sportrecorder.domain.model.VenueName
import com.crazystudio.sportrecorder.shared.resources.Res
import com.crazystudio.sportrecorder.shared.resources.backup_cancel
import com.crazystudio.sportrecorder.shared.resources.editor_delete_body
import com.crazystudio.sportrecorder.shared.resources.editor_delete_title
import com.crazystudio.sportrecorder.shared.resources.editor_move_body
import com.crazystudio.sportrecorder.shared.resources.editor_move_confirm
import com.crazystudio.sportrecorder.shared.resources.editor_move_keep
import com.crazystudio.sportrecorder.shared.resources.editor_move_title
import com.crazystudio.sportrecorder.shared.resources.editor_venue_address
import com.crazystudio.sportrecorder.shared.resources.editor_venue_delete
import com.crazystudio.sportrecorder.shared.resources.editor_venue_edit
import com.crazystudio.sportrecorder.shared.resources.editor_venue_merge_body
import com.crazystudio.sportrecorder.shared.resources.editor_venue_merge_confirm
import com.crazystudio.sportrecorder.shared.resources.editor_venue_merge_keep
import com.crazystudio.sportrecorder.shared.resources.editor_venue_merge_where
import com.crazystudio.sportrecorder.shared.resources.editor_venue_name
import com.crazystudio.sportrecorder.shared.resources.editor_venue_position
import com.crazystudio.sportrecorder.shared.resources.editor_venue_records
import com.crazystudio.sportrecorder.shared.resources.editor_venue_resolving
import com.crazystudio.sportrecorder.shared.resources.editor_venue_same_name_title
import com.crazystudio.sportrecorder.shared.resources.editor_venue_save
import com.crazystudio.sportrecorder.shared.resources.editor_venue_use_here
import com.crazystudio.sportrecorder.shared.resources.editor_venue_use_link
import com.crazystudio.sportrecorder.shared.resources.venue_no_position
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The ✎ dialog (#89). Name and address are drafts that only 儲存 writes; the two position actions
 * apply at once and the position line follows them (取消 does not undo a move). 刪除 asks first.
 * 「貼上地圖連結更新」 also fills the drafts from the link ([draft]); 儲存 waits while it is read, so a
 * save can't drop the move it is about to make.
 */
@Composable
@Suppress("LongParameterList") // Compose state and event slots
internal fun VenueEditDialog(
    venue: Venue,
    canUseThisPosition: Boolean,
    notice: ShareNotice?,
    resolving: Boolean,
    draft: EatTimeEditorUiState.RepositionDraft?,
    actions: VenuePickerActions,
    onClose: () -> Unit,
) {
    var name by rememberSaveable(venue.id) { mutableStateOf(venue.name) }
    // Untouched (null), the field shows the stored address, which a lookup may fill in while the
    // dialog is open. Moving the venue drops a typed draft: it described the old position.
    var typedAddress by rememberSaveable(venue.id, venue.lat, venue.lng) { mutableStateOf<String?>(null) }
    val address = typedAddress ?: venue.address.orEmpty()
    val records by produceState<Int?>(initialValue = null, venue.id) { value = actions.recordCount(venue) }
    val scope = rememberCoroutineScope()
    // Taken once, so a draft the user then edits is never put back (on rotation, say).
    LaunchedEffect(draft) {
        if (draft != null && draft.venueId == venue.id) {
            name = draft.name
            draft.address?.let { typedAddress = it }
            actions.consumeRepositionDraft()
        }
    }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(stringResource(Res.string.editor_venue_edit)) },
        text = {
            VenueEditFields(
                name = name,
                onNameChange = { name = it },
                address = address,
                onAddressChange = { typedAddress = it },
                positionLine = positionLine(venue, records),
                notice = notice,
                resolving = resolving,
                onUseThisPosition = if (canUseThisPosition) {
                    { actions.useThisPositionFor(venue) }
                } else {
                    null
                },
                onUseLink = { scope.launch { actions.repositionFromClipboard(venue) } },
            )
        },
        confirmButton = {
            VenueEditButtons(
                // A name that is blank once normalized is not a name; the view model refuses it too.
                canSave = VenueName.normalize(name).isNotEmpty() && !resolving,
                onDelete = { actions.requestDelete(venue) },
                onCancel = onClose,
                onSave = {
                    actions.saveVenue(venue, name, address)
                    onClose()
                },
            )
        },
    )
}

@Composable
@Suppress("LongParameterList") // Compose state and event slots
private fun VenueEditFields(
    name: String,
    onNameChange: (String) -> Unit,
    address: String,
    onAddressChange: (String) -> Unit,
    positionLine: String,
    notice: ShareNotice?,
    resolving: Boolean,
    onUseThisPosition: (() -> Unit)?,
    onUseLink: () -> Unit,
) {
    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = name,
            onValueChange = onNameChange,
            label = { Text(stringResource(Res.string.editor_venue_name)) },
            singleLine = true,
        )
        OutlinedTextField(
            value = address,
            onValueChange = onAddressChange,
            label = { Text(stringResource(Res.string.editor_venue_address)) },
            singleLine = true,
        )
        Text(
            text = positionLine,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // Correcting a venue's position needs a position to correct it with; a record that has no
        // location of its own has nothing to offer.
        onUseThisPosition?.let { onClick ->
            TextButton(onClick = onClick) { Text(stringResource(Res.string.editor_venue_use_here)) }
        }
        TextButton(onClick = onUseLink) { Text(stringResource(Res.string.editor_venue_use_link)) }
        val status = if (resolving) Res.string.editor_venue_resolving else notice?.let { shareNoticeRes(it) }
        status?.let {
            Text(
                text = stringResource(it),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 「位置：25.0335, 121.5301 · 12 筆紀錄」, or 「沒有位置」; the count joins once it is known. */
@Composable
private fun positionLine(venue: Venue, records: Int?): String {
    val lat = venue.lat
    val lng = venue.lng
    if (lat == null || lng == null) return stringResource(Res.string.venue_no_position)
    val position = stringResource(Res.string.editor_venue_position, coordinateText(lat), coordinateText(lng))
    return records?.let { "$position · ${pluralStringResource(Res.plurals.editor_venue_records, it, it)}" }
        ?: position
}

/** 刪除 sits at the start, apart from 取消 / 儲存, in the destructive colour. */
@Composable
private fun VenueEditButtons(canSave: Boolean, onDelete: () -> Unit, onCancel: () -> Unit, onSave: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        DeleteButton(onClick = onDelete)
        Spacer(modifier = Modifier.weight(1f))
        TextButton(onClick = onCancel) { Text(stringResource(Res.string.backup_cancel)) }
        TextButton(onClick = onSave, enabled = canSave) { Text(stringResource(Res.string.editor_venue_save)) }
    }
}

@Composable
private fun DeleteButton(onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
    ) {
        Text(stringResource(Res.string.editor_venue_delete))
    }
}

/** Deleting never deletes a meal: the records stay, without a venue. The count is said when there is one. */
@Composable
internal fun DeleteVenueDialog(
    pending: EatTimeEditorUiState.PendingDelete,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.editor_delete_title, pending.venue.name)) },
        text = if (pending.records > 0) {
            { Text(pluralStringResource(Res.plurals.editor_delete_body, pending.records, pending.records)) }
        } else {
            null
        },
        confirmButton = { DeleteButton(onClick = onConfirm) },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.backup_cancel)) }
        },
    )
}

/**
 * A rename took another venue's name. One place — merging rewrites records the user is not looking
 * at, so it says how many (when any) and where the other one is — or two branches with one name.
 */
@Composable
internal fun MergeOrKeepDialog(
    pending: EatTimeEditorUiState.PendingMerge,
    onMerge: () -> Unit,
    onKeepBoth: () -> Unit,
    onDismiss: () -> Unit,
) {
    val into = pending.into
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.editor_venue_same_name_title, into.name)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // The count is said when there is one, as the delete and which-store questions do.
                if (pending.movedRecords > 0) {
                    Text(
                        pluralStringResource(
                            Res.plurals.editor_venue_merge_body,
                            pending.movedRecords,
                            pending.movedRecords,
                            into.name,
                        ),
                    )
                }
                into.address?.let { Text(stringResource(Res.string.editor_venue_merge_where, it)) }
            }
        },
        confirmButton = {
            Column(horizontalAlignment = Alignment.End) {
                TextButton(onClick = onMerge) { Text(stringResource(Res.string.editor_venue_merge_confirm)) }
                TextButton(onClick = onKeepBoth) { Text(stringResource(Res.string.editor_venue_merge_keep)) }
                TextButton(onClick = onDismiss) { Text(stringResource(Res.string.backup_cancel)) }
            }
        },
    )
}

/** Moving a venue's marker moves it for every past record there, so the dialog says how many. */
@Composable
internal fun MoveDialog(
    pending: EatTimeEditorUiState.PendingMove,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.editor_move_title, pending.venue.name)) },
        text = {
            Text(pluralStringResource(Res.plurals.editor_move_body, pending.movedRecords, pending.movedRecords))
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(Res.string.editor_move_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.editor_move_keep)) }
        },
    )
}
