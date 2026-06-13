package com.crazystudio.sportrecorder.ui.diet

import androidx.annotation.StringRes
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.crazystudio.sportrecorder.R
import com.crazystudio.sportrecorder.ui.component.CircleProgress
import com.crazystudio.sportrecorder.ui.theme.SportRecorderTheme
import kotlin.math.roundToInt

@Composable
@Suppress("LongMethod") // cohesive single-screen layout; splitting hurts readability
fun DietScreen(
    state: DietUiState,
    onEditFastingType: () -> Unit,
    onAddEatTime: () -> Unit,
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
                    painter = painterResource(id = state.statusIcon),
                    contentDescription = null,
                    tint = colorScheme.onSurface,
                    modifier = Modifier.size(36.dp),
                )
                Text(
                    text = stringResource(id = state.statusTextRes),
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
                        painter = painterResource(id = R.drawable.ic_baseline_edit_24),
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
                        text = stringResource(id = state.promptTextRes),
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
                        FastTimeColumn(titleRes = R.string.fast_started_label, label = fastStart)
                        FastTimeColumn(titleRes = R.string.fast_ending_label, label = fastEnd)
                    }
                }
            }
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
                painter = painterResource(id = R.drawable.ic_baseline_add_24),
                contentDescription = null,
            )
        }
    }
}

@Composable
private fun FastTimeColumn(
    @StringRes titleRes: Int,
    label: FastTimeLabel,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    val dayText = when (label.day) {
        RelativeDay.YESTERDAY -> stringResource(id = R.string.day_yesterday)
        RelativeDay.TODAY -> stringResource(id = R.string.day_today)
        RelativeDay.TOMORROW -> stringResource(id = R.string.day_tomorrow)
        RelativeDay.OTHER -> label.date
    }
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(id = titleRes),
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

// showBackground gives layoutlib a window/theme context so drawable + Window_* styleable
// resolution doesn't fail in the IDE preview; the dark backgroundColor matches the app surface.
@Preview(showBackground = true, backgroundColor = 0xFF2B2B2B)
@Composable
@Suppress("UnusedPrivateMember") // @Preview entry point used by the IDE preview tooling
private fun DietScreenPreview() {
    SportRecorderTheme {
        DietScreen(
            state = DietUiState(
                elapsedText = "02:21:18",
                progress = 75f,
                fastingLabel = "16 : 8",
                statusIcon = R.drawable.ic_baseline_no_food_24,
                statusTextRes = R.string.diet_status_fasting,
                promptTextRes = R.string.diet_fasting_time,
                fastStart = FastTimeLabel(time = "23:00", day = RelativeDay.TODAY, date = ""),
                fastEnd = FastTimeLabel(time = "15:00", day = RelativeDay.TOMORROW, date = ""),
            ),
            onEditFastingType = {},
            onAddEatTime = {},
        )
    }
}
