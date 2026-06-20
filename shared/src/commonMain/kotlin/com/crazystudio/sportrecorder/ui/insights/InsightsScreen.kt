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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.crazystudio.sportrecorder.domain.insights.AdherenceState
import com.crazystudio.sportrecorder.domain.insights.DayCell
import com.crazystudio.sportrecorder.domain.insights.InsightsStats
import com.crazystudio.sportrecorder.domain.insights.LocationCount
import com.crazystudio.sportrecorder.domain.insights.Period
import com.crazystudio.sportrecorder.shared.resources.Res
import com.crazystudio.sportrecorder.shared.resources.ic_arrow_left_24dp
import com.crazystudio.sportrecorder.shared.resources.ic_arrow_right_24dp
import com.crazystudio.sportrecorder.shared.resources.insights_card_adherence
import com.crazystudio.sportrecorder.shared.resources.insights_card_locations
import com.crazystudio.sportrecorder.shared.resources.insights_card_photos
import com.crazystudio.sportrecorder.shared.resources.insights_card_stats
import com.crazystudio.sportrecorder.shared.resources.insights_empty_locations
import com.crazystudio.sportrecorder.shared.resources.insights_empty_photos
import com.crazystudio.sportrecorder.shared.resources.insights_location_count
import com.crazystudio.sportrecorder.shared.resources.insights_period_month
import com.crazystudio.sportrecorder.shared.resources.insights_period_week
import com.crazystudio.sportrecorder.shared.resources.insights_stat_first
import com.crazystudio.sportrecorder.shared.resources.insights_stat_last
import com.crazystudio.sportrecorder.shared.resources.insights_stat_late
import com.crazystudio.sportrecorder.shared.resources.insights_stat_meals
import com.crazystudio.sportrecorder.shared.resources.insights_streak
import com.crazystudio.sportrecorder.shared.resources.insights_value_none
import com.crazystudio.sportrecorder.shared.resources.insights_weekday_initials
import com.crazystudio.sportrecorder.ui.shared.PhotoThumbnail
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringArrayResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.abs
import kotlin.math.roundToLong
import kotlin.time.Instant

private const val WEEK_COLUMNS = 7
private const val PHOTO_COLUMNS = 3

@Composable
fun InsightsScreen(
    state: InsightsUiState,
    onSelectPeriod: (Period) -> Unit,
    onShiftMonth: (Int) -> Unit,
    photoModel: (String) -> Any?,
    onPhotoClick: (List<String>, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
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
        PhotoWallCard(state.result.photoFileNames, photoModel, onPhotoClick)
        LocationsCard(state.result.locations)
    }
}

@Composable
private fun PeriodSelector(period: Period, onSelect: (Period) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = period == Period.WEEK,
            onClick = { onSelect(Period.WEEK) },
            label = { Text(stringResource(Res.string.insights_period_week)) },
        )
        FilterChip(
            selected = period == Period.MONTH,
            onClick = { onSelect(Period.MONTH) },
            label = { Text(stringResource(Res.string.insights_period_month)) },
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
    SectionCard(stringResource(Res.string.insights_card_adherence)) {
        Text(stringResource(Res.string.insights_streak, streak), style = MaterialTheme.typography.bodyLarge)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onShiftMonth(-1) }) {
                Icon(painterResource(Res.drawable.ic_arrow_left_24dp), contentDescription = "Previous month")
            }
            Text(
                text = monthLabel(monthAnchor),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
            )
            IconButton(onClick = { onShiftMonth(1) }) {
                Icon(painterResource(Res.drawable.ic_arrow_right_24dp), contentDescription = "Next month")
            }
        }
        CalendarGrid(days)
    }
}

@Composable
private fun CalendarGrid(days: List<DayCell>) {
    if (days.isEmpty()) return
    val leadingBlanks = remember(days.first().dayStart) {
        // Sunday-first grid: SUNDAY -> 0, MONDAY -> 1, ... SATURDAY -> 6.
        val dow = Instant.fromEpochMilliseconds(days.first().dayStart)
            .toLocalDateTime(TimeZone.currentSystemDefault()).date.dayOfWeek
        dow.isoDayNumber % WEEK_COLUMNS
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        stringArrayResource(Res.array.insights_weekday_initials).forEach { label ->
            Text(
                text = label,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
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
                color = when (cell.state) {
                    AdherenceState.NO_DATA -> MaterialTheme.colorScheme.onSurfaceVariant
                    AdherenceState.OFF_TARGET -> MaterialTheme.colorScheme.onError
                    else -> MaterialTheme.colorScheme.onPrimary
                },
            )
        }
    }
}

@Composable
private fun StatsCard(stats: InsightsStats) {
    val noneLabel = stringResource(Res.string.insights_value_none)
    fun fmtMinutes(minutes: Int?) =
        if (minutes == null) noneLabel else "${pad2(minutes / 60)}:${pad2(minutes % 60)}"

    @Composable
    fun StatRow(label: String, value: String) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        }
    }

    SectionCard(stringResource(Res.string.insights_card_stats)) {
        StatRow(stringResource(Res.string.insights_stat_meals), stats.mealCount.toString())
        StatRow(stringResource(Res.string.insights_stat_first), fmtMinutes(stats.avgFirstMealMinutes))
        StatRow(stringResource(Res.string.insights_stat_last), fmtMinutes(stats.avgLastMealMinutes))
        StatRow(stringResource(Res.string.insights_stat_late), stats.lateNightDays.toString())
    }
}

@Composable
private fun PhotoWallCard(
    fileNames: List<String>,
    photoModel: (String) -> Any?,
    onClick: (List<String>, Int) -> Unit,
) {
    SectionCard(stringResource(Res.string.insights_card_photos)) {
        if (fileNames.isEmpty()) {
            Text(stringResource(Res.string.insights_empty_photos), style = MaterialTheme.typography.bodyMedium)
            return@SectionCard
        }
        fileNames.withIndex().chunked(PHOTO_COLUMNS).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                row.forEach { (index, name) ->
                    PhotoThumbnail(
                        model = photoModel(name),
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onClick(fileNames, index) },
                    )
                }
                repeat(PHOTO_COLUMNS - row.size) { Box(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun LocationsCard(locations: List<LocationCount>) {
    SectionCard(stringResource(Res.string.insights_card_locations)) {
        if (locations.isEmpty()) {
            Text(stringResource(Res.string.insights_empty_locations), style = MaterialTheme.typography.bodyMedium)
            return@SectionCard
        }
        locations.forEach { loc ->
            Text(
                text = stringResource(Res.string.insights_location_count, fmt4(loc.lat), fmt4(loc.lng), loc.count),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

/** "yyyy / MM" month label, kotlinx-datetime (no java.text.SimpleDateFormat on Native). */
private fun monthLabel(millis: Long): String {
    val date = Instant.fromEpochMilliseconds(millis).toLocalDateTime(TimeZone.currentSystemDefault()).date
    return "${date.year} / ${pad2(date.month.ordinal + 1)}"
}

private fun pad2(n: Int): String = n.toString().padStart(2, '0')

/** Formats a coordinate to 4 decimals without java's String.format (unavailable on Native). */
private fun fmt4(value: Double): String {
    val scaled = (value * 10_000).roundToLong()
    val sign = if (scaled < 0) "-" else ""
    val a = abs(scaled)
    return "$sign${a / 10_000}.${(a % 10_000).toString().padStart(4, '0')}"
}
