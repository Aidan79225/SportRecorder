package com.crazystudio.sportrecorder.ui.diet.editor

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.crazystudio.sportrecorder.R
import com.crazystudio.sportrecorder.ui.theme.white
import com.crazystudio.sportrecorder.util.PhotoStorage
import java.text.SimpleDateFormat

@Composable
fun EatTimeEditorSheet(
    state: EatTimeEditorUiState,
    onPickDate: () -> Unit,
    onPickTime: () -> Unit,
    onAddPhoto: () -> Unit,
    onRemovePhoto: (String) -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Column(
        modifier = modifier
            .background(colorResource(id = R.color.bg_black))
            .padding(20.dp)
    ) {
        // DATE row
        HeaderRow(
            icon = R.drawable.ic_baseline_date_range_24,
            title = stringResource(id = R.string.diet_create_eating_date_title),
            content = SimpleDateFormat("yyyy/MM/dd").format(state.date.time),
            actionIcon = R.drawable.ic_baseline_arrow_drop_down,
            onActionClick = onPickDate,
        )
        // TIME row
        HeaderRow(
            icon = R.drawable.ic_baseline_access_time_24,
            title = stringResource(id = R.string.diet_create_eating_time_title),
            content = SimpleDateFormat("HH:mm").format(state.date.time),
            actionIcon = R.drawable.ic_baseline_arrow_drop_down,
            onActionClick = onPickTime,
        )
        // LOCATION row
        val locationText = when (state.locationStatus) {
            EatTimeEditorUiState.LocationStatus.LOADING -> "Locating…"
            EatTimeEditorUiState.LocationStatus.AVAILABLE -> state.location?.let {
                String.format("%.5f, %.5f", it.lat, it.lng)
            } ?: "No location"
            else -> "No location"
        }
        HeaderRow(
            // No dedicated location drawable exists; reuse the date icon per the plan.
            icon = R.drawable.ic_baseline_date_range_24,
            title = "Location",
            content = locationText,
            actionIcon = R.drawable.ic_baseline_arrow_drop_down,
            onActionClick = {},
        )
        // ADD PHOTO (+) row
        HeaderRow(
            icon = R.drawable.ic_baseline_add_24,
            title = "Add photo",
            content = "",
            actionIcon = R.drawable.ic_baseline_add_24,
            onActionClick = onAddPhoto,
        )
        // PHOTO thumbnails
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
                            contentDescription = "Remove photo",
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(2.dp)
                                .size(24.dp)
                                .clickable { onRemovePhoto(name) },
                        )
                    }
                }
            }
        }
        // CREATE button
        Button(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 30.dp),
            onClick = onConfirm,
        ) {
            Text(
                text = "CREATE",
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
            color = white,
            fontSize = 18.sp,
        )
        Text(
            text = content,
            color = white,
            fontSize = 18.sp,
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
