package com.crazystudio.sportrecorder.ui.insights

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.crazystudio.sportrecorder.domain.insights.DayCell
import com.crazystudio.sportrecorder.domain.insights.DayWindowState
import com.crazystudio.sportrecorder.domain.insights.InsightsAggregator
import com.crazystudio.sportrecorder.domain.insights.InsightsStats
import com.crazystudio.sportrecorder.domain.insights.LocationCount
import com.crazystudio.sportrecorder.domain.insights.MonthSummary
import com.crazystudio.sportrecorder.domain.insights.Period
import com.crazystudio.sportrecorder.shared.resources.Res
import com.crazystudio.sportrecorder.shared.resources.day_today
import com.crazystudio.sportrecorder.shared.resources.ic_arrow_left_24dp
import com.crazystudio.sportrecorder.shared.resources.ic_arrow_right_24dp
import com.crazystudio.sportrecorder.shared.resources.insights_card_locations
import com.crazystudio.sportrecorder.shared.resources.insights_card_photos
import com.crazystudio.sportrecorder.shared.resources.insights_card_rhythm
import com.crazystudio.sportrecorder.shared.resources.insights_card_stats
import com.crazystudio.sportrecorder.shared.resources.insights_date_short
import com.crazystudio.sportrecorder.shared.resources.insights_day_description
import com.crazystudio.sportrecorder.shared.resources.insights_duration_hm
import com.crazystudio.sportrecorder.shared.resources.insights_empty_body
import com.crazystudio.sportrecorder.shared.resources.insights_empty_locations
import com.crazystudio.sportrecorder.shared.resources.insights_empty_photos
import com.crazystudio.sportrecorder.shared.resources.insights_empty_title
import com.crazystudio.sportrecorder.shared.resources.insights_legend_longer
import com.crazystudio.sportrecorder.shared.resources.insights_legend_none
import com.crazystudio.sportrecorder.shared.resources.insights_legend_within
import com.crazystudio.sportrecorder.shared.resources.insights_location_count
import com.crazystudio.sportrecorder.shared.resources.insights_month_summary
import com.crazystudio.sportrecorder.shared.resources.insights_next_month
import com.crazystudio.sportrecorder.shared.resources.insights_period_caption
import com.crazystudio.sportrecorder.shared.resources.insights_period_month
import com.crazystudio.sportrecorder.shared.resources.insights_period_range
import com.crazystudio.sportrecorder.shared.resources.insights_period_week
import com.crazystudio.sportrecorder.shared.resources.insights_photo_count
import com.crazystudio.sportrecorder.shared.resources.insights_prev_month
import com.crazystudio.sportrecorder.shared.resources.insights_stat_days
import com.crazystudio.sportrecorder.shared.resources.insights_stat_first
import com.crazystudio.sportrecorder.shared.resources.insights_stat_last
import com.crazystudio.sportrecorder.shared.resources.insights_stat_late
import com.crazystudio.sportrecorder.shared.resources.insights_stat_meals
import com.crazystudio.sportrecorder.shared.resources.insights_stat_window
import com.crazystudio.sportrecorder.shared.resources.insights_streak
import com.crazystudio.sportrecorder.shared.resources.insights_value_none
import com.crazystudio.sportrecorder.shared.resources.insights_weekday_initials
import com.crazystudio.sportrecorder.ui.shared.PhotoThumbnail
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringArrayResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.abs
import kotlin.math.roundToLong
import kotlin.time.Instant

private const val WEEK_COLUMNS = 7
private const val PHOTO_COLUMNS = 3

/** The wall is a taste of the period, not the archive — the Record tab holds every photo. */
private const val MAX_WALL_PHOTOS = 12

private const val MINUTES_PER_HOUR = 60

/**
 * 回顧 / Insights. Reflects the user's eating back to them — a rhythm, never a grade. See
 * `docs/superpowers/specs/2026-09-21-insights-improvements-design.md` for the tone rules that
 * shape the copy and colours here.
 */
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
        if (!state.result.hasAnyRecords) {
            NothingYetCard()
            return@Column
        }
        RhythmCard(
            days = state.result.calendarDays,
            summary = state.result.monthSummary,
            streak = state.result.streak,
            monthAnchor = state.monthAnchor,
            canShowNextMonth = !state.result.isAnchorCurrentMonth,
            onShiftMonth = onShiftMonth,
        )
        PeriodSelector(state.period, state.result.periodStart, state.result.periodEnd, onSelectPeriod)
        StatsCard(state.result.stats)
        PhotoWallCard(state.result.photoFileNames, photoModel, onPhotoClick)
        LocationsCard(state.result.locations)
    }
}

/**
 * Shown before the very first meal is recorded. An invitation, not a scoreboard of zeroes.
 */
@Composable
private fun NothingYetCard() {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(Res.string.insights_empty_title), style = MaterialTheme.typography.titleMedium)
            Text(
                text = stringResource(Res.string.insights_empty_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PeriodSelector(period: Period, from: Long, to: Long, onSelect: (Period) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(Res.string.insights_period_caption),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
        Text(
            text = stringResource(Res.string.insights_period_range, shortDate(from), shortDate(to)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // titleMedium already carries the weight for this role (see theme/Type.kt).
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun RhythmCard(
    days: List<DayCell>,
    summary: MonthSummary,
    streak: Int,
    monthAnchor: Long,
    canShowNextMonth: Boolean,
    onShiftMonth: (Int) -> Unit,
) {
    SectionCard(stringResource(Res.string.insights_card_rhythm)) {
        MonthPager(monthAnchor, canShowNextMonth, onShiftMonth)
        CalendarGrid(days)
        CalendarLegend()
        Text(
            text = stringResource(
                Res.string.insights_month_summary,
                summary.recordedDays,
                summary.withinWindowDays,
            ),
            style = MaterialTheme.typography.bodyMedium,
        )
        // A count that cannot break is the headline; the run is a quiet aside, and 0 is not news.
        if (streak > 0) {
            Text(
                text = stringResource(Res.string.insights_streak, streak),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MonthPager(monthAnchor: Long, canShowNextMonth: Boolean, onShiftMonth: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onShiftMonth(-1) }) {
            Icon(
                painter = painterResource(Res.drawable.ic_arrow_left_24dp),
                contentDescription = stringResource(Res.string.insights_prev_month),
            )
        }
        Text(
            text = monthLabel(monthAnchor),
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
        )
        IconButton(onClick = { onShiftMonth(1) }, enabled = canShowNextMonth) {
            Icon(
                painter = painterResource(Res.drawable.ic_arrow_right_24dp),
                contentDescription = stringResource(Res.string.insights_next_month),
            )
        }
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

/** Without this the calendar's colours are an unexplained verdict; with it they are a key. */
@Composable
private fun CalendarLegend() {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        DayWindowState.entries.forEach { state ->
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(10.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(cellColor(state)),
                )
                Text(
                    text = stateLabel(state),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun DayBox(cell: DayCell?, modifier: Modifier) {
    if (cell == null) {
        Box(modifier.aspectRatio(1f).background(Color.Transparent))
        return
    }
    val todayLabel = stringResource(Res.string.day_today)
    val description = stringResource(Res.string.insights_day_description, cell.dayOfMonth, stateLabel(cell.state))
        .let { if (cell.isToday) "$it · $todayLabel" else it }
    val shape = RoundedCornerShape(6.dp)
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(shape)
            .background(cellColor(cell.state))
            .then(
                if (cell.isToday) {
                    Modifier.border(1.5.dp, MaterialTheme.colorScheme.onSurface, shape)
                } else {
                    Modifier
                }
            )
            .clearAndSetSemantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = cell.dayOfMonth.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = onCellColor(cell.state),
        )
    }
}

/**
 * Container tones on purpose: the three states carry equal visual weight, so a longer day reads
 * as a different colour, not as a fault. Never `colorScheme.error` here.
 */
@Composable
private fun cellColor(state: DayWindowState): Color = when (state) {
    DayWindowState.WITHIN_WINDOW -> MaterialTheme.colorScheme.primaryContainer
    DayWindowState.LONGER_WINDOW -> MaterialTheme.colorScheme.tertiaryContainer
    DayWindowState.NO_RECORD -> MaterialTheme.colorScheme.surfaceVariant
}

@Composable
private fun onCellColor(state: DayWindowState): Color = when (state) {
    DayWindowState.WITHIN_WINDOW -> MaterialTheme.colorScheme.onPrimaryContainer
    DayWindowState.LONGER_WINDOW -> MaterialTheme.colorScheme.onTertiaryContainer
    DayWindowState.NO_RECORD -> MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
private fun stateLabel(state: DayWindowState): String = when (state) {
    DayWindowState.WITHIN_WINDOW -> stringResource(Res.string.insights_legend_within)
    DayWindowState.LONGER_WINDOW -> stringResource(Res.string.insights_legend_longer)
    DayWindowState.NO_RECORD -> stringResource(Res.string.insights_legend_none)
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun StatsCard(stats: InsightsStats) {
    val none = stringResource(Res.string.insights_value_none)
    val hours = stats.avgWindowMinutes?.let { it / MINUTES_PER_HOUR } ?: 0
    val minutes = stats.avgWindowMinutes?.let { it % MINUTES_PER_HOUR } ?: 0
    val window = if (stats.avgWindowMinutes == null) {
        none
    } else {
        stringResource(Res.string.insights_duration_hm, hours, minutes)
    }

    SectionCard(stringResource(Res.string.insights_card_stats)) {
        StatRow(stringResource(Res.string.insights_stat_meals), stats.mealCount.toString())
        StatRow(stringResource(Res.string.insights_stat_days), stats.daysWithRecords.toString())
        StatRow(stringResource(Res.string.insights_stat_first), clockLabel(stats.avgFirstMealMinutes, none))
        StatRow(stringResource(Res.string.insights_stat_last), clockLabel(stats.avgLastMealMinutes, none))
        StatRow(stringResource(Res.string.insights_stat_window), window)
        StatRow(
            stringResource(Res.string.insights_stat_late, InsightsAggregator.LATE_HOUR),
            stats.lateHourDays.toString(),
        )
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
        // Only a bounded prefix is composed; tapping still opens the whole period in the viewer.
        fileNames.take(MAX_WALL_PHOTOS).withIndex().chunked(PHOTO_COLUMNS).forEach { row ->
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
        Text(
            text = stringResource(Res.string.insights_photo_count, fileNames.size),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
                text = stringResource(Res.string.insights_location_count, coord(loc.lat), coord(loc.lng), loc.count),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

/** "HH:mm" for minutes-since-midnight, or [none] when the period holds no data. */
private fun clockLabel(minutes: Int?, none: String): String =
    if (minutes == null) none else "${pad2(minutes / MINUTES_PER_HOUR)}:${pad2(minutes % MINUTES_PER_HOUR)}"

/** "yyyy / MM" month label, kotlinx-datetime (no java.text.SimpleDateFormat on Native). */
private fun monthLabel(millis: Long): String {
    val date = Instant.fromEpochMilliseconds(millis).toLocalDateTime(TimeZone.currentSystemDefault()).date
    return "${date.year} / ${pad2(date.month.number)}"
}

@Composable
private fun shortDate(millis: Long): String {
    val date = Instant.fromEpochMilliseconds(millis).toLocalDateTime(TimeZone.currentSystemDefault()).date
    return stringResource(Res.string.insights_date_short, date.month.number, date.day)
}

private fun pad2(n: Int): String = n.toString().padStart(2, '0')

/**
 * Formats a coordinate to 3 decimals — the precision places are actually grouped at — without
 * java's String.format (unavailable on Native).
 */
private fun coord(value: Double): String {
    val scaled = (value * 1_000).roundToLong()
    val sign = if (scaled < 0) "-" else ""
    val a = abs(scaled)
    return "$sign${a / 1_000}.${(a % 1_000).toString().padStart(3, '0')}"
}
