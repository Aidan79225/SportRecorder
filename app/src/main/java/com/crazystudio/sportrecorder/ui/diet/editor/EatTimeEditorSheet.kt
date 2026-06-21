package com.crazystudio.sportrecorder.ui.diet.editor

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.crazystudio.sportrecorder.R
import com.crazystudio.sportrecorder.domain.model.EatPhoto
import com.crazystudio.sportrecorder.util.PhotoStorage
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

@Composable
@Suppress("LongMethod", "LongParameterList") // cohesive editor sheet; params are Compose event slots
fun EatTimeEditorSheet(
    state: EatTimeEditorUiState,
    onPickDate: () -> Unit,
    onPickTime: () -> Unit,
    onNoteChange: (String) -> Unit,
    onAddPhoto: () -> Unit,
    onSelectPhoto: () -> Unit,
    onRemovePendingPhoto: (String) -> Unit,
    onRemoveExistingPhoto: (EatPhoto) -> Unit,
    onRecaptureLocation: () -> Unit,
    onClearLocation: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val colorScheme = MaterialTheme.colorScheme
    Column(
        // Insets + scroll keep the whole sheet reachable no matter how tall it grows
        // (location row, photo rows, or the keyboard opening for Note).
        modifier = modifier
            .background(colorScheme.surface)
            .navigationBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        // DATE row
        HeaderRow(
            icon = R.drawable.ic_baseline_date_range_24,
            title = stringResource(id = R.string.diet_create_eating_date_title),
            content = formatDate(state.dateMillis),
            actionIcon = R.drawable.ic_baseline_arrow_drop_down,
            onActionClick = onPickDate,
        )
        // TIME row
        HeaderRow(
            icon = R.drawable.ic_baseline_access_time_24,
            title = stringResource(id = R.string.diet_create_eating_time_title),
            content = formatTime(state.dateMillis),
            actionIcon = R.drawable.ic_baseline_arrow_drop_down,
            onActionClick = onPickTime,
        )
        // NOTE field
        OutlinedTextField(
            value = state.note,
            onValueChange = onNoteChange,
            label = { Text(stringResource(id = R.string.diet_eat_note)) },
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
        // LOCATION row — custom Row with two action icons
        val locationText = when (state.locationStatus) {
            EatTimeEditorUiState.LocationStatus.LOADING -> stringResource(id = R.string.diet_eat_location_loading)
            EatTimeEditorUiState.LocationStatus.AVAILABLE -> state.location?.let {
                String.format(java.util.Locale.ROOT, "%.5f, %.5f", it.lat, it.lng)
            } ?: stringResource(id = R.string.diet_eat_location_none)
            else -> stringResource(id = R.string.diet_eat_location_none)
        }
        Row(
            modifier = Modifier,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_baseline_date_range_24),
                contentDescription = "",
                modifier = Modifier
                    .padding(10.dp)
                    .size(36.dp),
            )
            Text(
                text = stringResource(id = R.string.diet_eat_location),
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
                painter = painterResource(id = R.drawable.ic_baseline_add_24),
                contentDescription = stringResource(id = R.string.diet_eat_recapture_location),
                modifier = Modifier
                    .padding(4.dp)
                    .size(36.dp)
                    .clickable { onRecaptureLocation() },
            )
            // Clear location icon — only shown when location is set
            if (state.location != null) {
                Image(
                    painter = painterResource(id = R.drawable.ic_baseline_delete_24),
                    contentDescription = stringResource(id = R.string.diet_eat_clear_location),
                    modifier = Modifier
                        .padding(4.dp)
                        .size(36.dp)
                        .clickable { onClearLocation() },
                )
            }
        }
        // TAKE PHOTO (camera) row
        HeaderRow(
            icon = R.drawable.ic_baseline_add_24,
            title = stringResource(id = R.string.photo_add),
            content = "",
            actionIcon = R.drawable.ic_baseline_add_24,
            onActionClick = onAddPhoto,
        )
        // SELECT PHOTO (gallery) row
        HeaderRow(
            icon = R.drawable.ic_baseline_photo_library_24,
            title = stringResource(id = R.string.photo_select),
            content = "",
            actionIcon = R.drawable.ic_baseline_photo_library_24,
            onActionClick = onSelectPhoto,
        )
        // EXISTING photos (edit mode)
        if (state.existingPhotos.isNotEmpty()) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
            ) {
                items(state.existingPhotos, key = { it.id }) { photo ->
                    Box(
                        modifier = Modifier
                            .padding(end = 10.dp)
                            .size(80.dp),
                    ) {
                        AsyncImage(
                            model = PhotoStorage.fileFor(context, photo.fileName),
                            contentDescription = photo.fileName,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(80.dp)
                                .clip(RoundedCornerShape(8.dp)),
                        )
                        Image(
                            painter = painterResource(id = R.drawable.ic_baseline_delete_24),
                            contentDescription = stringResource(id = R.string.diet_eat_remove_photo),
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(2.dp)
                                .size(24.dp)
                                .clickable { onRemoveExistingPhoto(photo) },
                        )
                    }
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
                    Box(
                        modifier = Modifier
                            .padding(end = 10.dp)
                            .size(80.dp),
                    ) {
                        AsyncImage(
                            model = PhotoStorage.fileFor(context, name),
                            contentDescription = name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(80.dp)
                                .clip(RoundedCornerShape(8.dp)),
                        )
                        Image(
                            painter = painterResource(id = R.drawable.ic_baseline_delete_24),
                            contentDescription = stringResource(id = R.string.diet_eat_remove_photo),
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(2.dp)
                                .size(24.dp)
                                .clickable { onRemovePendingPhoto(name) },
                        )
                    }
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
                    id = if (state.isEditMode) R.string.diet_eat_save else R.string.diet_eat_create,
                ),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
}

@Composable
private fun HeaderRow(
    @DrawableRes icon: Int,
    title: String,
    content: String,
    @DrawableRes actionIcon: Int,
    onActionClick: () -> Unit,
) {
    Row(
        modifier = Modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(id = icon),
            contentDescription = "",
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
        Image(
            painter = painterResource(id = actionIcon),
            contentDescription = "",
            modifier = Modifier
                .padding(10.dp)
                .size(36.dp)
                .clickable { onActionClick() },
        )
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
