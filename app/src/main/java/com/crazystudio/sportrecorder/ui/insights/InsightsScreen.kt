package com.crazystudio.sportrecorder.ui.insights

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.crazystudio.sportrecorder.R
import com.crazystudio.sportrecorder.domain.insights.AdherenceState
import com.crazystudio.sportrecorder.domain.insights.DayCell
import com.crazystudio.sportrecorder.domain.insights.InsightsStats
import com.crazystudio.sportrecorder.domain.insights.LocationCount
import com.crazystudio.sportrecorder.domain.insights.Period
import com.crazystudio.sportrecorder.ui.diet.record.FullScreenPhotoViewer
import com.crazystudio.sportrecorder.util.PhotoStorage
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

private const val WEEK_COLUMNS = 7
private const val PHOTO_COLUMNS = 3
private val monthFormat = SimpleDateFormat("yyyy / MM", Locale.getDefault())

@Composable
fun InsightsScreen(
    state: InsightsUiState,
    onSelectPeriod: (Period) -> Unit,
    onShiftMonth: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var fullScreen by remember { mutableStateOf<Pair<List<String>, Int>?>(null) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        PeriodSelector(state.period, onSelectPeriod)
        AdherenceCard(state.result.calendarDays, state.result.streak, state.monthAnchor, onShiftMonth)
        StatsCard(state.result.stats)
        PhotoWallCard(state.result.photoFileNames) { index ->
            fullScreen = state.result.photoFileNames to index
        }
        LocationsCard(state.result.locations)
    }

    fullScreen?.let { (names, index) ->
        FullScreenPhotoViewer(fileNames = names, initialIndex = index, onDismiss = { fullScreen = null })
    }
}

@Composable
private fun PeriodSelector(period: Period, onSelect: (Period) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = period == Period.WEEK,
            onClick = { onSelect(Period.WEEK) },
            label = { Text(stringResource(R.string.insights_period_week)) },
        )
        FilterChip(
            selected = period == Period.MONTH,
            onClick = { onSelect(Period.MONTH) },
            label = { Text(stringResource(R.string.insights_period_month)) },
        )
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

@Composable
private fun AdherenceCard(days: List<DayCell>, streak: Int, monthAnchor: Long, onShiftMonth: (Int) -> Unit) {
    SectionCard(stringResource(R.string.insights_card_adherence)) {
        Text(stringResource(R.string.insights_streak, streak), style = MaterialTheme.typography.bodyLarge)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onShiftMonth(-1) }) {
                Icon(painterResource(R.drawable.ic_arrow_left_24dp), contentDescription = "Previous month")
            }
            Text(
                text = monthFormat.format(monthAnchor),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
            )
            IconButton(onClick = { onShiftMonth(1) }) {
                Icon(painterResource(R.drawable.ic_arrow_right_24dp), contentDescription = "Next month")
            }
        }
        CalendarGrid(days)
    }
}

@Composable
private fun CalendarGrid(days: List<DayCell>) {
    if (days.isEmpty()) return
    val leadingBlanks = remember(days.first().dayStart) {
        Calendar.getInstance().apply { timeInMillis = days.first().dayStart }
            .get(Calendar.DAY_OF_WEEK) - 1
    }
    val cells: List<DayCell?> = List(leadingBlanks) { null } + days
    cells.chunked(WEEK_COLUMNS).forEach { week ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            week.forEach { cell -> DayBox(cell, Modifier.weight(1f)) }
            repeat(WEEK_COLUMNS - week.size) { Box(Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun DayBox(cell: DayCell?, modifier: Modifier) {
    val color = when (cell?.state) {
        AdherenceState.ON_TARGET -> MaterialTheme.colorScheme.primary
        AdherenceState.OFF_TARGET -> MaterialTheme.colorScheme.error
        AdherenceState.NO_DATA -> MaterialTheme.colorScheme.surfaceVariant
        null -> Color.Transparent
    }
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(6.dp))
            .background(color),
        contentAlignment = Alignment.Center,
    ) {
        if (cell != null) {
            Text(
                text = cell.dayOfMonth.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = if (cell.state == AdherenceState.NO_DATA) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onPrimary
                },
            )
        }
    }
}

@Composable
private fun StatsCard(stats: InsightsStats) {
    val noneLabel = stringResource(R.string.insights_value_none)
    fun fmtMinutes(minutes: Int?) =
        if (minutes == null) noneLabel else "%02d:%02d".format(minutes / 60, minutes % 60)

    @Composable
    fun StatRow(label: String, value: String) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        }
    }

    SectionCard(stringResource(R.string.insights_card_stats)) {
        StatRow(stringResource(R.string.insights_stat_meals), stats.mealCount.toString())
        StatRow(stringResource(R.string.insights_stat_first), fmtMinutes(stats.avgFirstMealMinutes))
        StatRow(stringResource(R.string.insights_stat_last), fmtMinutes(stats.avgLastMealMinutes))
        StatRow(stringResource(R.string.insights_stat_late), stats.lateNightDays.toString())
    }
}

@Composable
private fun PhotoWallCard(fileNames: List<String>, onClick: (Int) -> Unit) {
    SectionCard(stringResource(R.string.insights_card_photos)) {
        if (fileNames.isEmpty()) {
            Text(stringResource(R.string.insights_empty_photos), style = MaterialTheme.typography.bodyMedium)
            return@SectionCard
        }
        val context = LocalContext.current
        fileNames.withIndex().chunked(PHOTO_COLUMNS).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                row.forEach { (index, name) ->
                    AsyncImage(
                        model = PhotoStorage.fileFor(context, name),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onClick(index) },
                    )
                }
                repeat(PHOTO_COLUMNS - row.size) { Box(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun LocationsCard(locations: List<LocationCount>) {
    SectionCard(stringResource(R.string.insights_card_locations)) {
        if (locations.isEmpty()) {
            Text(stringResource(R.string.insights_empty_locations), style = MaterialTheme.typography.bodyMedium)
            return@SectionCard
        }
        locations.forEach { loc ->
            Text(
                text = stringResource(R.string.insights_location_count, loc.lat, loc.lng, loc.count),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
