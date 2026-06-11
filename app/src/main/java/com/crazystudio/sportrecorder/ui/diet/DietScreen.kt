package com.crazystudio.sportrecorder.ui.diet

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import com.crazystudio.sportrecorder.ui.component.VerticalProgress
import com.crazystudio.sportrecorder.ui.theme.SportRecorderTheme

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
            // Circular progress with overlaid icon / status / prompt / timer.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp, start = 24.dp, end = 24.dp),
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
                        text = stringResource(id = state.statusTextRes),
                        color = colorScheme.onSurface,
                        fontSize = 24.sp,
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = stringResource(id = state.promptTextRes),
                        color = colorScheme.onSurfaceVariant,
                        fontSize = 24.sp,
                    )
                    Text(
                        text = state.elapsedText,
                        color = colorScheme.primary,
                        fontSize = 48.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    if (state.timeInfoRes != 0) {
                        Text(
                            text = stringResource(state.timeInfoRes, state.timeInfoArg1, state.timeInfoArg2),
                            fontSize = 14.sp,
                            color = colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }

            // Fasting-type chip with edit pencil.
            Row(
                modifier = Modifier
                    .padding(top = 10.dp)
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

            // Row of 5 history bars with date labels.
            Row(
                modifier = Modifier
                    .padding(top = 10.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                state.history.forEach { bar ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        VerticalProgress(
                            progress = bar.ratio,
                            modifier = Modifier
                                .padding(horizontal = 20.dp, vertical = 10.dp)
                                .width(20.dp)
                                .height(170.dp),
                        )
                        Text(
                            text = DietViewModel.formatHistoryDate(bar.dateMillis),
                            color = colorScheme.onSurface,
                        )
                    }
                }
            }

            // Bottom spacer mirrors the original 100dp filler so FAB never overlaps content.
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
                elapsedText = "12:34:56",
                progress = 70f,
                fastingLabel = "16 : 8",
                history = List(5) { i ->
                    DietUiState.HistoryBar(
                        dateMillis = System.currentTimeMillis() - i * 86_400_000L,
                        ratio = (i + 1) / 5f,
                    )
                },
            ),
            onEditFastingType = {},
            onAddEatTime = {},
        )
    }
}
