package com.crazystudio.sportrecorder.ui.diet

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.crazystudio.sportrecorder.shared.resources.Res
import com.crazystudio.sportrecorder.shared.resources.day_today
import com.crazystudio.sportrecorder.shared.resources.day_tomorrow
import com.crazystudio.sportrecorder.shared.resources.day_yesterday
import com.crazystudio.sportrecorder.shared.resources.fast_ending_label
import com.crazystudio.sportrecorder.shared.resources.fast_started_label
import com.crazystudio.sportrecorder.shared.resources.ic_baseline_add_24
import com.crazystudio.sportrecorder.shared.resources.ic_baseline_edit_24
import com.crazystudio.sportrecorder.shared.resources.ic_baseline_settings_24
import com.crazystudio.sportrecorder.shared.resources.settings_title
import com.crazystudio.sportrecorder.ui.component.CircleProgress
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

@Composable
@Suppress("LongMethod") // cohesive single-screen layout; splitting hurts readability
fun DietScreen(
    state: DietUiState,
    onEditFastingType: () -> Unit,
    onAddEatTime: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colorScheme.surface),
    ) {
        // Equal-weight regions above/below the fixed-height ring keep the ring's centre at the
        // vertical centre of the content area (screen top → above the bottom bar).
        Column(modifier = Modifier.fillMaxSize()) {
            // ABOVE the ring — status icon, status text, fasting-type chip; hugs the ring's top edge.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(bottom = 12.dp),
                verticalArrangement = Arrangement.Bottom,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    painter = painterResource(state.statusIcon),
                    contentDescription = null,
                    tint = colorScheme.onSurface,
                    modifier = Modifier.size(36.dp),
                )
                Text(
                    text = stringResource(state.statusText),
                    style = MaterialTheme.typography.headlineMedium,
                    color = colorScheme.primary,
                    modifier = Modifier.padding(top = 4.dp),
                )
                // Edit fasting-type chip — moved under the status text, with a little padding around it.
                Row(
                    modifier = Modifier
                        .padding(top = 12.dp)
                        .clip(RoundedCornerShape(50))
                        .background(colorScheme.surfaceContainer)
                        .clickable { onEditFastingType() }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = state.fastingLabel,
                        style = MaterialTheme.typography.bodyLarge,
                        color = colorScheme.onSurfaceVariant,
                    )
                    Icon(
                        painter = painterResource(Res.drawable.ic_baseline_edit_24),
                        contentDescription = null,
                        tint = colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .padding(start = 6.dp)
                            .size(18.dp),
                    )
                }
            }

            // RING — fixed (square) height; centred between the two weighted regions.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircleProgress(
                    progress = state.progress,
                    modifier = Modifier.fillMaxWidth(),
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${state.progress.roundToInt()}%",
                        style = MaterialTheme.typography.headlineMedium,
                        color = colorScheme.primary,
                    )
                    Text(
                        text = stringResource(state.promptText),
                        style = MaterialTheme.typography.bodyLarge,
                        color = colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = state.elapsedText,
                        style = MaterialTheme.typography.displayMedium,
                        color = colorScheme.primary,
                    )
                }
            }

            // BELOW the ring — fast window blocks; hugs the ring's bottom edge.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.TopCenter,
            ) {
                val fastStart = state.fastStart
                val fastEnd = state.fastEnd
                if (fastStart != null && fastEnd != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp, start = 24.dp, end = 24.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        FastTimeColumn(titleRes = Res.string.fast_started_label, label = fastStart)
                        FastTimeColumn(titleRes = Res.string.fast_ending_label, label = fastEnd)
                    }
                }
            }
        }

        // Floating gear → reminder settings (top-end of the Box, no TopAppBar to match the app style).
        IconButton(
            onClick = onOpenSettings,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = 8.dp, top = 8.dp),
        ) {
            Icon(
                painter = painterResource(Res.drawable.ic_baseline_settings_24),
                contentDescription = stringResource(Res.string.settings_title),
                tint = colorScheme.onSurfaceVariant,
            )
        }

        FloatingActionButton(
            onClick = onAddEatTime,
            containerColor = colorScheme.primary,
            contentColor = colorScheme.onPrimary,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 20.dp),
        ) {
            Icon(
                painter = painterResource(Res.drawable.ic_baseline_add_24),
                contentDescription = null,
            )
        }
    }
}

@Composable
private fun FastTimeColumn(
    titleRes: StringResource,
    label: FastTimeLabel,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    val dayText = when (label.day) {
        RelativeDay.YESTERDAY -> stringResource(Res.string.day_yesterday)
        RelativeDay.TODAY -> stringResource(Res.string.day_today)
        RelativeDay.TOMORROW -> stringResource(Res.string.day_tomorrow)
        RelativeDay.OTHER -> label.date
    }
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(titleRes),
            style = MaterialTheme.typography.bodyLarge,
            color = colorScheme.onSurfaceVariant,
        )
        Text(
            text = "${label.time} $dayText",
            style = MaterialTheme.typography.headlineSmall,
            color = colorScheme.onSurface,
        )
    }
}
