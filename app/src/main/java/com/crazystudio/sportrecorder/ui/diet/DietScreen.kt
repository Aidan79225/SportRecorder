package com.crazystudio.sportrecorder.ui.diet

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Status string — moved above the ring.
            Text(
                text = stringResource(id = state.statusTextRes),
                color = colorScheme.primary,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 16.dp),
            )

            // Ring with overlaid icon / percentage / prompt / countdown.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, start = 24.dp, end = 24.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircleProgress(
                    progress = state.progress,
                    modifier = Modifier.fillMaxWidth(),
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        painter = painterResource(id = state.statusIcon),
                        contentDescription = null,
                        tint = colorScheme.onSurface,
                        modifier = Modifier.size(36.dp),
                    )
                    Text(
                        text = "${state.progress.roundToInt()}%",
                        color = colorScheme.primary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = stringResource(id = state.promptTextRes),
                        color = colorScheme.onSurfaceVariant,
                        fontSize = 16.sp,
                    )
                    Text(
                        text = state.elapsedText,
                        color = colorScheme.primary,
                        fontSize = 44.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            // Fast window (start → end), below the ring. Hidden when no record yet.
            if (state.fastStart.isNotEmpty()) {
                val endText = if (state.fastEndsNextDay) {
                    stringResource(id = R.string.diet_next_day, state.fastEnd)
                } else {
                    state.fastEnd
                }
                Text(
                    text = stringResource(id = R.string.diet_fast_window, state.fastStart, endText),
                    color = colorScheme.onSurfaceVariant,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }

            // Fasting-type chip with edit pencil.
            Row(
                modifier = Modifier
                    .padding(top = 12.dp)
                    .clip(RoundedCornerShape(50))
                    .background(colorScheme.surfaceContainer)
                    .clickable { onEditFastingType() }
                    .padding(horizontal = 12.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = state.fastingLabel,
                    color = colorScheme.onSurfaceVariant,
                    fontSize = 18.sp,
                )
                Icon(
                    painter = painterResource(id = R.drawable.ic_baseline_edit_24),
                    contentDescription = null,
                    tint = colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(start = 5.dp)
                        .size(18.dp),
                )
            }

            Spacer(modifier = Modifier.height(100.dp))
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

@Preview
@Composable
@Suppress("UnusedPrivateMember") // @Preview entry point used by the IDE preview tooling
private fun DietScreenPreview() {
    SportRecorderTheme {
        DietScreen(
            state = DietUiState(
                elapsedText = "02:21:18",
                progress = 75f,
                fastingLabel = "16 : 8",
                statusTextRes = R.string.diet_status_fasting,
                promptTextRes = R.string.diet_fasting_time,
                fastStart = "23:00",
                fastEnd = "15:00",
                fastEndsNextDay = true,
            ),
            onEditFastingType = {},
            onAddEatTime = {},
        )
    }
}
