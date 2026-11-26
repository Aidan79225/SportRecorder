package com.crazystudio.sportrecorder.ui.diet.editor

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SuggestionChip
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.crazystudio.sportrecorder.domain.model.Dishes
import com.crazystudio.sportrecorder.domain.model.EatPhoto
import com.crazystudio.sportrecorder.domain.model.MealDuration
import com.crazystudio.sportrecorder.domain.model.TasteRating
import com.crazystudio.sportrecorder.domain.venue.RoundedDistance
import com.crazystudio.sportrecorder.shared.resources.Res
import com.crazystudio.sportrecorder.shared.resources.diet_create_eating_date_pick
import com.crazystudio.sportrecorder.shared.resources.diet_create_eating_date_title
import com.crazystudio.sportrecorder.shared.resources.diet_create_eating_time_pick
import com.crazystudio.sportrecorder.shared.resources.diet_create_eating_time_title
import com.crazystudio.sportrecorder.shared.resources.diet_eat_clear_location
import com.crazystudio.sportrecorder.shared.resources.diet_eat_create
import com.crazystudio.sportrecorder.shared.resources.diet_eat_duration_pick
import com.crazystudio.sportrecorder.shared.resources.diet_eat_duration_title
import com.crazystudio.sportrecorder.shared.resources.diet_eat_location
import com.crazystudio.sportrecorder.shared.resources.diet_eat_location_loading
import com.crazystudio.sportrecorder.shared.resources.diet_eat_location_none
import com.crazystudio.sportrecorder.shared.resources.diet_eat_note
import com.crazystudio.sportrecorder.shared.resources.diet_eat_recapture_location
import com.crazystudio.sportrecorder.shared.resources.diet_eat_remove_photo
import com.crazystudio.sportrecorder.shared.resources.diet_eat_save
import com.crazystudio.sportrecorder.shared.resources.distance_kilometers
import com.crazystudio.sportrecorder.shared.resources.distance_meters
import com.crazystudio.sportrecorder.shared.resources.duration_hours
import com.crazystudio.sportrecorder.shared.resources.duration_hours_minutes
import com.crazystudio.sportrecorder.shared.resources.duration_minutes
import com.crazystudio.sportrecorder.shared.resources.editor_dish_add
import com.crazystudio.sportrecorder.shared.resources.editor_dish_remove
import com.crazystudio.sportrecorder.shared.resources.editor_dish_suggested
import com.crazystudio.sportrecorder.shared.resources.editor_dishes
import com.crazystudio.sportrecorder.shared.resources.editor_link_unreadable
import com.crazystudio.sportrecorder.shared.resources.editor_not_map_link
import com.crazystudio.sportrecorder.shared.resources.editor_store_body
import com.crazystudio.sportrecorder.shared.resources.editor_store_body_at
import com.crazystudio.sportrecorder.shared.resources.editor_store_branch
import com.crazystudio.sportrecorder.shared.resources.editor_store_keep
import com.crazystudio.sportrecorder.shared.resources.editor_store_move
import com.crazystudio.sportrecorder.shared.resources.editor_store_move_none
import com.crazystudio.sportrecorder.shared.resources.editor_store_title
import com.crazystudio.sportrecorder.shared.resources.editor_taste
import com.crazystudio.sportrecorder.shared.resources.editor_taste_clear
import com.crazystudio.sportrecorder.shared.resources.editor_venue
import com.crazystudio.sportrecorder.shared.resources.editor_venue_clear
import com.crazystudio.sportrecorder.shared.resources.editor_venue_none
import com.crazystudio.sportrecorder.shared.resources.editor_venue_pick
import com.crazystudio.sportrecorder.shared.resources.editor_venue_resolving
import com.crazystudio.sportrecorder.shared.resources.ic_baseline_access_time_24
import com.crazystudio.sportrecorder.shared.resources.ic_baseline_add_24
import com.crazystudio.sportrecorder.shared.resources.ic_baseline_arrow_drop_down
import com.crazystudio.sportrecorder.shared.resources.ic_baseline_check_24
import com.crazystudio.sportrecorder.shared.resources.ic_baseline_close_24
import com.crazystudio.sportrecorder.shared.resources.ic_baseline_date_range_24
import com.crazystudio.sportrecorder.shared.resources.ic_baseline_delete_24
import com.crazystudio.sportrecorder.shared.resources.ic_baseline_photo_camera_24
import com.crazystudio.sportrecorder.shared.resources.ic_baseline_photo_library_24
import com.crazystudio.sportrecorder.shared.resources.photo_add
import com.crazystudio.sportrecorder.shared.resources.photo_select
import com.crazystudio.sportrecorder.ui.shared.tasteLook
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.abs
import kotlin.math.roundToLong
import kotlin.time.Instant

@Composable
@Suppress("LongMethod", "LongParameterList") // cohesive editor sheet; params are Compose event slots
fun EatTimeEditorSheet(
    state: EatTimeEditorUiState,
    photoModel: (String) -> Any?,
    onPickDate: () -> Unit,
    onPickTime: () -> Unit,
    onDurationChange: (Int) -> Unit,
    onTasteRatingChange: (Int?) -> Unit,
    onDishDraftChange: (String) -> Unit,
    onCommitDish: () -> Unit,
    onAddDish: (String) -> Unit,
    onRemoveDish: (String) -> Unit,
    onNoteChange: (String) -> Unit,
    onAddPhoto: () -> Unit,
    onSelectPhoto: () -> Unit,
    onRemovePendingPhoto: (String) -> Unit,
    onRemoveExistingPhoto: (EatPhoto) -> Unit,
    onRecaptureLocation: () -> Unit,
    onClearLocation: () -> Unit,
    onOpenVenuePicker: () -> Unit,
    onClearVenue: () -> Unit,
    onAnswerAnotherBranch: () -> Unit,
    onAnswerSameStoreMove: () -> Unit,
    onAnswerSameStoreKeep: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    Column(
        // Insets + scroll keep the whole sheet reachable no matter how tall it grows
        // (location row, photo rows, or the keyboard opening for Note).
        modifier = modifier
            .background(colorScheme.surface)
            // Inside the M3 sheet these are no-ops (M3 already consumes the insets); kept so this
            // sheet still lays out correctly if it is ever hosted outside a sheet.
            .navigationBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
    ) {
        // DATE row
        HeaderRow(
            icon = Res.drawable.ic_baseline_date_range_24,
            title = stringResource(Res.string.diet_create_eating_date_title),
            content = formatDate(state.dateMillis),
            trailingIcon = Res.drawable.ic_baseline_arrow_drop_down,
            onClick = onPickDate,
            onClickLabel = stringResource(Res.string.diet_create_eating_date_pick),
        )
        // TIME row
        HeaderRow(
            icon = Res.drawable.ic_baseline_access_time_24,
            title = stringResource(Res.string.diet_create_eating_time_title),
            content = formatTime(state.dateMillis),
            trailingIcon = Res.drawable.ic_baseline_arrow_drop_down,
            onClick = onPickTime,
            onClickLabel = stringResource(Res.string.diet_create_eating_time_pick),
        )
        // DURATION row: already reads 1 小時, so a quick capture needs no tap here.
        DurationRow(minutes = state.durationMinutes, onDurationChange = onDurationChange)
        // TASTE row: how the meal tasted, in the moment. Nothing is chosen until the user taps a face.
        TasteRatingRow(rating = state.tasteRating, onRatingChange = onTasteRatingChange)
        // DISHES row: what the meal had. Optional, starts empty, never blocks the confirm button.
        DishesRow(
            dishes = state.dishes,
            draft = state.dishDraft,
            suggestions = state.dishSuggestions,
            onDraftChange = onDishDraftChange,
            onCommit = onCommitDish,
            onAdd = onAddDish,
            onRemove = onRemoveDish,
        )
        // NOTE field
        OutlinedTextField(
            value = state.note,
            onValueChange = onNoteChange,
            label = { Text(stringResource(Res.string.diet_eat_note)) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            minLines = 2,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = colorScheme.onSurface,
                unfocusedTextColor = colorScheme.onSurface,
                focusedBorderColor = colorScheme.primary,
                unfocusedBorderColor = colorScheme.onSurfaceVariant,
                focusedLabelColor = colorScheme.primary,
                unfocusedLabelColor = colorScheme.onSurfaceVariant,
                cursorColor = colorScheme.primary,
            ),
        )
        // VENUE row — the place the food came from; never where the user was (that is LOCATION).
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // Announced as a button that chooses a place, not just "Place, Not set".
                .clickable(onClickLabel = stringResource(Res.string.editor_venue_pick), role = Role.Button) {
                    onOpenVenuePicker()
                }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(Res.string.editor_venue),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                // A shared place is being read: say so rather than show "Not set" for a beat.
                text = if (state.resolvingLink) {
                    stringResource(Res.string.editor_venue_resolving)
                } else {
                    state.venue?.name ?: stringResource(Res.string.editor_venue_none)
                },
                modifier = Modifier.weight(1f).padding(start = 12.dp),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (state.venue != null) {
                IconButton(onClick = onClearVenue) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_baseline_close_24),
                        contentDescription = stringResource(Res.string.editor_venue_clear),
                    )
                }
            }
        }
        // Why a shared text left the venue empty; stays until the user picks a venue themselves.
        state.shareNotice?.let { notice ->
            Text(
                text = stringResource(shareNoticeRes(notice)),
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.onSurfaceVariant,
            )
        }
        // LOCATION row — custom Row with two action icons
        val locationText = when (state.locationStatus) {
            EatTimeEditorUiState.LocationStatus.LOADING -> stringResource(Res.string.diet_eat_location_loading)
            EatTimeEditorUiState.LocationStatus.AVAILABLE -> state.location?.let {
                "${fmt5(it.lat)}, ${fmt5(it.lng)}"
            } ?: stringResource(Res.string.diet_eat_location_none)
            else -> stringResource(Res.string.diet_eat_location_none)
        }
        Row(
            modifier = Modifier,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = painterResource(Res.drawable.ic_baseline_date_range_24),
                contentDescription = null, // Decoration: the title beside it names the row.
                modifier = Modifier
                    .padding(10.dp)
                    .size(36.dp),
            )
            Text(
                text = stringResource(Res.string.diet_eat_location),
                style = MaterialTheme.typography.bodyLarge,
                color = colorScheme.onSurface,
            )
            Text(
                text = locationText,
                style = MaterialTheme.typography.bodyLarge,
                color = colorScheme.onSurface,
                textAlign = TextAlign.End,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 10.dp),
            )
            // Re-capture location icon (ic_baseline_add_24 — no refresh drawable available)
            Image(
                painter = painterResource(Res.drawable.ic_baseline_add_24),
                contentDescription = stringResource(Res.string.diet_eat_recapture_location),
                modifier = Modifier
                    .padding(4.dp)
                    .size(36.dp)
                    .clickable { onRecaptureLocation() },
            )
            // Clear location icon — only shown when location is set
            if (state.location != null) {
                Image(
                    painter = painterResource(Res.drawable.ic_baseline_delete_24),
                    contentDescription = stringResource(Res.string.diet_eat_clear_location),
                    modifier = Modifier
                        .padding(4.dp)
                        .size(36.dp)
                        .clickable { onClearLocation() },
                )
            }
        }
        // TAKE PHOTO (camera) row
        HeaderRow(
            icon = Res.drawable.ic_baseline_photo_camera_24,
            title = stringResource(Res.string.photo_add),
            content = "",
            trailingIcon = Res.drawable.ic_baseline_add_24,
            onClick = onAddPhoto,
        )
        // SELECT PHOTO (gallery) row
        HeaderRow(
            icon = Res.drawable.ic_baseline_photo_library_24,
            title = stringResource(Res.string.photo_select),
            content = "",
            trailingIcon = Res.drawable.ic_baseline_add_24,
            onClick = onSelectPhoto,
        )
        // EXISTING photos (edit mode)
        if (state.existingPhotos.isNotEmpty()) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
            ) {
                items(state.existingPhotos, key = { it.id }) { photo ->
                    PhotoTile(
                        model = photoModel(photo.fileName),
                        contentDescription = photo.fileName,
                        onRemove = { onRemoveExistingPhoto(photo) },
                    )
                }
            }
        }
        // PENDING photos (newly captured)
        if (state.pendingPhotos.isNotEmpty()) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
            ) {
                items(state.pendingPhotos, key = { it }) { name ->
                    PhotoTile(
                        model = photoModel(name),
                        contentDescription = name,
                        onRemove = { onRemovePendingPhoto(name) },
                    )
                }
            }
        }
        // CONFIRM button
        Button(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 30.dp),
            onClick = onConfirm,
        ) {
            Text(
                text = stringResource(
                    if (state.isEditMode) Res.string.diet_eat_save else Res.string.diet_eat_create,
                ),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
    state.pendingStore?.let { pending ->
        StoreDialog(
            pending = pending,
            onAnotherBranch = onAnswerAnotherBranch,
            onMove = onAnswerSameStoreMove,
            onKeep = onAnswerSameStoreKeep,
        )
    }
}

internal fun shareNoticeRes(notice: ShareNotice) = when (notice) {
    ShareNotice.MapLinkUnreadable -> Res.string.editor_link_unreadable
    ShareNotice.NotAMapLink -> Res.string.editor_not_map_link
}

/**
 * A shared place has the name of a saved venue that is far away: another branch, or the same store
 * mis-placed? Only the user knows. The three answers are stacked; dismissing keeps everything as is.
 */
@Composable
private fun StoreDialog(
    pending: EatTimeEditorUiState.PendingStore,
    onAnotherBranch: () -> Unit,
    onMove: () -> Unit,
    onKeep: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onKeep,
        title = { Text(stringResource(Res.string.editor_store_title, pending.venue.name)) },
        text = { Text(storeBody(pending)) },
        confirmButton = {
            Column(horizontalAlignment = Alignment.End) {
                TextButton(onClick = onAnotherBranch) { Text(stringResource(Res.string.editor_store_branch)) }
                TextButton(onClick = onMove) { Text(storeMoveLabel(pending.movedRecords)) }
                TextButton(onClick = onKeep) { Text(stringResource(Res.string.editor_store_keep)) }
            }
        },
    )
}

@Composable
private fun storeBody(pending: EatTimeEditorUiState.PendingStore): String {
    val name = pending.venue.name
    val distance = distanceText(pending.distance)
    val address = pending.venue.address
    return if (address != null) {
        stringResource(Res.string.editor_store_body_at, name, address, distance)
    } else {
        stringResource(Res.string.editor_store_body, name, distance)
    }
}

// Moving it moves the marker of every record at it, so the answer says how many — when there are any.
@Composable
private fun storeMoveLabel(movedRecords: Int): String = if (movedRecords > 0) {
    pluralStringResource(Res.plurals.editor_store_move, movedRecords, movedRecords)
} else {
    stringResource(Res.string.editor_store_move_none)
}

private const val TENTHS_PER_KILOMETER = 10

@Composable
private fun distanceText(distance: RoundedDistance): String = when (distance) {
    is RoundedDistance.Meters -> stringResource(Res.string.distance_meters, distance.value)
    // One decimal, built by hand: no String.format in common code.
    is RoundedDistance.Kilometers -> stringResource(
        Res.string.distance_kilometers,
        "${distance.tenths / TENTHS_PER_KILOMETER}.${distance.tenths % TENTHS_PER_KILOMETER}",
    )
}

@Composable
private fun PhotoTile(model: Any?, contentDescription: String, onRemove: () -> Unit) {
    Box(
        modifier = Modifier
            .padding(end = 10.dp)
            .size(80.dp),
    ) {
        AsyncImage(
            model = model,
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(80.dp)
                .clip(RoundedCornerShape(8.dp)),
        )
        Image(
            painter = painterResource(Res.drawable.ic_baseline_delete_24),
            contentDescription = stringResource(Res.string.diet_eat_remove_photo),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(2.dp)
                .size(24.dp)
                .clickable { onRemove() },
        )
    }
}

/** Built like the Date and Time rows; tapping it opens a menu of [MealDuration.CHOICES] at its arrow. */
@Composable
private fun DurationRow(minutes: Int, onDurationChange: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    HeaderRow(
        icon = Res.drawable.ic_baseline_access_time_24,
        title = stringResource(Res.string.diet_eat_duration_title),
        // A stored length the menu doesn't offer is shown as it is.
        content = durationText(minutes),
        trailingIcon = Res.drawable.ic_baseline_arrow_drop_down,
        onClick = { expanded = true },
        onClickLabel = stringResource(Res.string.diet_eat_duration_pick),
    ) {
        // Anchored to the arrow, so the menu drops from it at the row's right edge.
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            MealDuration.CHOICES.forEach { choice ->
                DurationMenuItem(
                    minutes = choice,
                    current = choice == minutes,
                    onChoose = {
                        expanded = false
                        onDurationChange(choice)
                    },
                )
            }
        }
    }
}

/** One choice of the duration menu; the current one is ticked, and announced as selected. */
@Composable
private fun DurationMenuItem(minutes: Int, current: Boolean, onChoose: () -> Unit) {
    DropdownMenuItem(
        text = { Text(durationText(minutes)) },
        onClick = onChoose,
        // Only the current choice carries a state, so a screen reader says nothing about the others.
        modifier = if (current) Modifier.semantics { selected = true } else Modifier,
        // Decorative: "selected" is what a screen reader reports, so the tick needs no description.
        trailingIcon = if (current) {
            { Icon(painter = painterResource(Res.drawable.ic_baseline_check_24), contentDescription = null) }
        } else {
            null
        },
    )
}

@Composable
private fun durationText(minutes: Int): String {
    val (hours, rest) = MealDuration.parts(minutes)
    return when {
        hours == 0 -> stringResource(Res.string.duration_minutes, minutes)
        rest == 0 -> stringResource(Res.string.duration_hours, hours)
        else -> stringResource(Res.string.duration_hours_minutes, hours, rest)
    }
}

private val TASTE_TARGET = 48.dp

/**
 * What the meal had: a one-line field (its label names the row, the keyboard's Done adds the dish), the chosen
 * dishes as chips that go away on a tap, and a few earlier dishes one tap away. Chips wrap, so the row is as
 * tall as the dishes need and no taller: with none yet it is just the field. The field is full at
 * [Dishes.MAX_PER_MEAL] dishes.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
@Suppress("LongParameterList") // Compose event slots
private fun DishesRow(
    dishes: List<String>,
    draft: String,
    suggestions: List<String>,
    onDraftChange: (String) -> Unit,
    onCommit: () -> Unit,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        OutlinedTextField(
            value = draft,
            onValueChange = { onDraftChange(it.take(Dishes.MAX_LENGTH)) },
            modifier = Modifier.fillMaxWidth(),
            enabled = dishes.size < Dishes.MAX_PER_MEAL,
            label = { Text(stringResource(Res.string.editor_dishes)) },
            placeholder = { Text(stringResource(Res.string.editor_dish_add)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onCommit() }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = colorScheme.onSurface,
                unfocusedTextColor = colorScheme.onSurface,
                focusedBorderColor = colorScheme.primary,
                unfocusedBorderColor = colorScheme.onSurfaceVariant,
                focusedLabelColor = colorScheme.primary,
                unfocusedLabelColor = colorScheme.onSurfaceVariant,
                cursorColor = colorScheme.primary,
            ),
        )
        if (dishes.isNotEmpty() || suggestions.isNotEmpty()) {
            FlowRow(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                dishes.forEach { dish ->
                    val remove = stringResource(Res.string.editor_dish_remove, dish)
                    InputChip(
                        selected = true,
                        onClick = { onRemove(dish) },
                        label = { Text(dish, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        trailingIcon = {
                            Icon(
                                painter = painterResource(Res.drawable.ic_baseline_close_24),
                                contentDescription = null,
                                modifier = Modifier.size(InputChipDefaults.IconSize),
                            )
                        },
                        // One stop that says what a tap does ("Remove 滷肉飯"), not the dish and an unnamed icon.
                        modifier = Modifier.clearAndSetSemantics {
                            contentDescription = remove
                            role = Role.Button
                            onClick {
                                onRemove(dish)
                                true
                            }
                        },
                    )
                }
                suggestions.forEach { dish ->
                    val add = stringResource(Res.string.editor_dish_suggested, dish)
                    SuggestionChip(
                        onClick = { onAdd(dish) },
                        label = { Text(dish, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        modifier = Modifier.clearAndSetSemantics {
                            contentDescription = add
                            role = Role.Button
                            onClick {
                                onAdd(dish)
                                true
                            }
                        },
                    )
                }
            }
        }
    }
}

/**
 * How the meal tasted: the label, and five faces to choose from. It starts empty and says nothing about
 * being empty (no "Not set", no hint). A chosen face puts its word on the label's line and a ✕ at the end of
 * the faces' line to take it back. Neither changes a height, and the ✕ has its place whether or not it is
 * shown, so choosing a face moves nothing: not what is below it, not the faces under the finger. (The one
 * exception is the largest font on the narrowest phones: the word wraps rather than clips, and a word that no
 * longer fits on the label's line takes a second one, moving everything below it, the faces included, down.)
 * For a screen reader the label and the word are one stop ("Taste, Very tasty"), the faces are one radio group
 * each described by its word, and the ✕ is a button after them.
 *
 * It is two lines, no more: the editor is one scrolling column, and a taller row here pushes the confirm
 * button off a phone-sized screen.
 */
@Composable
private fun TasteRatingRow(rating: Int?, onRatingChange: (Int?) -> Unit) {
    val chosenLook = tasteLook(rating)
    Column(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(Res.string.editor_taste),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (chosenLook != null) {
                // Wraps rather than clips at a large font: the word is the meaning.
                Text(
                    text = stringResource(chosenLook.word),
                    modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.End,
                )
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            // The faces share the width that is left, so on the narrowest phones they shrink a little rather
            // than run off the sheet or push the ✕ away.
            Row(modifier = Modifier.weight(1f).selectableGroup()) {
                TasteRating.LEVELS.forEach { level ->
                    TasteFaceChoice(level = level, chosen = level == rating, onChoose = { onRatingChange(level) })
                }
            }
            Box(modifier = Modifier.width(TASTE_TARGET), contentAlignment = Alignment.Center) {
                if (chosenLook != null) {
                    IconButton(onClick = { onRatingChange(null) }) {
                        Icon(
                            painter = painterResource(Res.drawable.ic_baseline_close_24),
                            contentDescription = stringResource(Res.string.editor_taste_clear),
                        )
                    }
                }
            }
        }
    }
}

/**
 * One face of the row: a radio button described by its word, at least 48 dp wide and tall wherever the row has
 * the width for it. The emoji alone is hidden from the screen reader (not the button, which keeps its role,
 * state and action) so the word is heard once. Its size is a type role, so it follows the font setting inside
 * the fixed target, and the chosen face sits on a soft container.
 */
@Composable
private fun RowScope.TasteFaceChoice(level: Int, chosen: Boolean, onChoose: () -> Unit) {
    val look = tasteLook(level) ?: return
    val word = stringResource(look.word)
    Box(
        modifier = Modifier
            .weight(1f)
            .height(TASTE_TARGET)
            .selectable(selected = chosen, role = Role.RadioButton, onClick = onChoose)
            .semantics { contentDescription = word },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(TASTE_TARGET)
                .clip(CircleShape)
                .background(if (chosen) MaterialTheme.colorScheme.primaryContainer else Color.Transparent),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = look.face,
                modifier = Modifier.clearAndSetSemantics {},
                style = MaterialTheme.typography.headlineMedium,
            )
        }
    }
}

/**
 * Icon, [title], [content] and a small trailing icon (an arrow, or a plus) as one button: a tap anywhere
 * on the row acts, and a screen reader reads "[title], [content]" as a single stop. The trailing icon only
 * hints that something opens or can be added, so it has no description of its own.
 */
@Composable
private fun HeaderRow(
    icon: DrawableResource,
    title: String,
    content: String,
    trailingIcon: DrawableResource,
    onClick: () -> Unit,
    // What a tap does, read after the row ("Double tap to choose a date"); null where the title says it.
    onClickLabel: String? = null,
    // Composed in a Box around the trailing icon: whatever pops up from it (a menu) anchors to it.
    anchor: @Composable BoxScope.() -> Unit = {},
) {
    Row(
        // clickable also merges the icon and both texts into this one node, and gives it the button role.
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = onClickLabel, role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(icon),
            contentDescription = null,
            modifier = Modifier
                .padding(10.dp)
                .size(36.dp),
        )
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = content,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.End,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 10.dp),
        )
        Box {
            Image(
                painter = painterResource(trailingIcon),
                contentDescription = null,
                modifier = Modifier
                    .padding(10.dp)
                    .size(36.dp),
            )
            anchor()
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

/** Formats a coordinate to 5 decimals without java's String.format (unavailable on Native). */
private fun fmt5(value: Double): String {
    val scaled = (value * 100_000).roundToLong()
    val sign = if (scaled < 0) "-" else ""
    val a = abs(scaled)
    return "$sign${a / 100_000}.${(a % 100_000).toString().padStart(5, '0')}"
}
