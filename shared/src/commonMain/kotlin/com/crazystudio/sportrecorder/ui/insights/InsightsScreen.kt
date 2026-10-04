package com.crazystudio.sportrecorder.ui.insights

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.crazystudio.sportrecorder.domain.insights.DayBand
import com.crazystudio.sportrecorder.domain.insights.DayCell
import com.crazystudio.sportrecorder.domain.insights.DayRange
import com.crazystudio.sportrecorder.domain.insights.DayWindowState
import com.crazystudio.sportrecorder.domain.insights.InsightsStats
import com.crazystudio.sportrecorder.domain.insights.LocationCount
import com.crazystudio.sportrecorder.domain.insights.OnThisDayMemory
import com.crazystudio.sportrecorder.domain.insights.Period
import com.crazystudio.sportrecorder.domain.insights.PeriodSummary
import com.crazystudio.sportrecorder.shared.resources.Res
import com.crazystudio.sportrecorder.shared.resources.day_today
import com.crazystudio.sportrecorder.shared.resources.ic_arrow_left_24dp
import com.crazystudio.sportrecorder.shared.resources.ic_arrow_right_24dp
import com.crazystudio.sportrecorder.shared.resources.insights_card_chart
import com.crazystudio.sportrecorder.shared.resources.insights_card_locations
import com.crazystudio.sportrecorder.shared.resources.insights_card_photos
import com.crazystudio.sportrecorder.shared.resources.insights_card_rhythm
import com.crazystudio.sportrecorder.shared.resources.insights_card_stats
import com.crazystudio.sportrecorder.shared.resources.insights_date_short
import com.crazystudio.sportrecorder.shared.resources.insights_day_description
import com.crazystudio.sportrecorder.shared.resources.insights_day_open
import com.crazystudio.sportrecorder.shared.resources.insights_duration_hm
import com.crazystudio.sportrecorder.shared.resources.insights_empty_body
import com.crazystudio.sportrecorder.shared.resources.insights_empty_locations
import com.crazystudio.sportrecorder.shared.resources.insights_empty_photos
import com.crazystudio.sportrecorder.shared.resources.insights_empty_title
import com.crazystudio.sportrecorder.shared.resources.insights_legend_longer
import com.crazystudio.sportrecorder.shared.resources.insights_legend_none
import com.crazystudio.sportrecorder.shared.resources.insights_legend_within
import com.crazystudio.sportrecorder.shared.resources.insights_map_summary
import com.crazystudio.sportrecorder.shared.resources.insights_next_period
import com.crazystudio.sportrecorder.shared.resources.insights_on_this_day_hint
import com.crazystudio.sportrecorder.shared.resources.insights_on_this_day_title
import com.crazystudio.sportrecorder.shared.resources.insights_period_month
import com.crazystudio.sportrecorder.shared.resources.insights_period_range
import com.crazystudio.sportrecorder.shared.resources.insights_period_summary
import com.crazystudio.sportrecorder.shared.resources.insights_period_week
import com.crazystudio.sportrecorder.shared.resources.insights_photo_count
import com.crazystudio.sportrecorder.shared.resources.insights_photos_collapse
import com.crazystudio.sportrecorder.shared.resources.insights_photos_show_all
import com.crazystudio.sportrecorder.shared.resources.insights_prev_period
import com.crazystudio.sportrecorder.shared.resources.insights_stat_days
import com.crazystudio.sportrecorder.shared.resources.insights_stat_first
import com.crazystudio.sportrecorder.shared.resources.insights_stat_last
import com.crazystudio.sportrecorder.shared.resources.insights_stat_meals
import com.crazystudio.sportrecorder.shared.resources.insights_stat_window
import com.crazystudio.sportrecorder.shared.resources.insights_value_none
import com.crazystudio.sportrecorder.shared.resources.insights_weekday_initials
import com.crazystudio.sportrecorder.ui.insights.map.FullScreenPlacesMap
import com.crazystudio.sportrecorder.ui.insights.map.PlacesMap
import com.crazystudio.sportrecorder.ui.shared.PhotoThumbnail
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringArrayResource
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Instant

private const val WEEK_COLUMNS = 7
private const val PHOTO_COLUMNS = 3

/**
 * How much of the wall is drawn before the user asks for the rest. This is a preview, **not a cap**:
 * Insights is the one place to look back at a whole period at once, so truncating it would take the
 * page's purpose away. "Show all" reaches every photo in the period (see [photoWallRows]), and the
 * rows are lazy items so the ones off screen cost nothing.
 */
private const val WALL_PREVIEW_PHOTOS = 12

/** Gap between the page's cards. Set per item, because the wall's own rows must sit flush. */
private val CARD_GAP = 16.dp

/** Matches `CardDefaults.shape` (`shapes.medium`), so a sliced card still looks like a card. */
private val WALL_CORNER = 12.dp

private const val MINUTES_PER_HOUR = 60
private const val HOURS_PER_DAY = 24

/** 「去年的今天」 thumbnail: big enough to recognise the meal, small enough to stay a footnote. */
private val MEMORY_THUMB = 56.dp

/** Days of the current month that have not happened yet are drawn, but quietly. */
private const val FUTURE_DAY_ALPHA = 0.4f

/**
 * 回顧 / Insights. Reflects the user's eating back to them — a rhythm, never a grade. One period
 * control (Week / Month, paged) scopes every card below it. See
 * `docs/superpowers/specs/2026-09-21-insights-improvements-design.md` for the tone rules that
 * shape the copy and colours here.
 */
@Composable
@Suppress("LongParameterList") // Compose event slots for the host, as the other shared screens
fun InsightsScreen(
    state: InsightsUiState,
    onSelectPeriod: (Period) -> Unit,
    onShiftPeriod: (Int) -> Unit,
    onDayClick: (Long) -> Unit,
    photoModel: (String) -> Any?,
    onPhotoClick: (List<String>, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Nothing is drawn before the first real state: painting the empty card for that frame
    // would greet every returning user with 「這裡還空著」.
    if (!state.isLoaded) return
    if (!state.result.hasAnyRecords) {
        Box(modifier.fillMaxWidth().padding(16.dp)) { NothingYetCard() }
        return
    }
    // Kept outside the LazyColumn: an item's own state dies when it scrolls out of view.
    var photosExpanded by rememberSaveable { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(16.dp),
    ) {
        // Above the period control on purpose: this is one fixed day, not part of the period
        // that scopes every card below. Absent entirely when that day holds nothing.
        state.onThisDay?.let { memory ->
            item { Spaced { OnThisDayCard(memory, photoModel, onDayClick) } }
        }
        item {
            Spaced {
                PeriodHeader(
                    period = state.period,
                    range = state.result.range,
                    canGoForward = !state.result.isCurrentPeriod,
                    onSelectPeriod = onSelectPeriod,
                    onShiftPeriod = onShiftPeriod,
                )
            }
        }
        item {
            Spaced { RhythmCard(state.period, state.result.calendarDays, state.result.summary, onDayClick) }
        }
        // Seven rows read as a shape; thirty-one read as a barcode. The chart is a week thing.
        if (state.period == Period.WEEK) {
            item { Spaced { RhythmChartCard(state.result.bands, onDayClick) } }
        }
        item { Spaced { StatsCard(state.result.stats) } }
        photoWall(
            fileNames = state.result.photoFileNames,
            expanded = photosExpanded,
            onToggleExpanded = { photosExpanded = !photosExpanded },
            photoModel = photoModel,
            onPhotoClick = onPhotoClick,
        )
        item { LocationsCard(state.result.locations) }
    }
}

/** A card plus the gap that follows it. The wall sets its own spacing, so this is per item. */
@Composable
private fun Spaced(content: @Composable () -> Unit) {
    Box(Modifier.padding(bottom = CARD_GAP)) { content() }
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

/**
 * 「去年的今天」. The day's own photo and note if it kept any, and the whole day when tapped —
 * it reuses [onDayClick], so this card needed no new navigation. An invitation to look back,
 * never a comparison between then and now.
 */
@Composable
private fun OnThisDayCard(
    memory: OnThisDayMemory,
    photoModel: (String) -> Any?,
    onDayClick: (Long) -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onDayClick(memory.dayStart) },
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            memory.photoFileName?.let { fileName ->
                PhotoThumbnail(model = photoModel(fileName), modifier = Modifier.size(MEMORY_THUMB))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(Res.string.insights_on_this_day_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = memory.note ?: stringResource(Res.string.insights_on_this_day_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Week / Month chips plus the pager: the one control every card below answers to. */
@Composable
private fun PeriodHeader(
    period: Period,
    range: DayRange,
    canGoForward: Boolean,
    onSelectPeriod: (Period) -> Unit,
    onShiftPeriod: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = period == Period.WEEK,
                onClick = { onSelectPeriod(Period.WEEK) },
                label = { Text(stringResource(Res.string.insights_period_week)) },
            )
            FilterChip(
                selected = period == Period.MONTH,
                onClick = { onSelectPeriod(Period.MONTH) },
                label = { Text(stringResource(Res.string.insights_period_month)) },
            )
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onShiftPeriod(-1) }) {
                Icon(
                    painter = painterResource(Res.drawable.ic_arrow_left_24dp),
                    contentDescription = stringResource(Res.string.insights_prev_period),
                )
            }
            Text(
                text = rangeLabel(period, range),
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.titleMedium,
            )
            IconButton(onClick = { onShiftPeriod(1) }, enabled = canGoForward) {
                Icon(
                    painter = painterResource(Res.drawable.ic_arrow_right_24dp),
                    contentDescription = stringResource(Res.string.insights_next_period),
                )
            }
        }
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
    period: Period,
    days: List<DayCell>,
    summary: PeriodSummary,
    onDayClick: (Long) -> Unit,
) {
    SectionCard(stringResource(Res.string.insights_card_rhythm)) {
        CalendarGrid(period, days, onDayClick)
        CalendarLegend()
        Text(
            text = stringResource(
                Res.string.insights_period_summary,
                summary.recordedDays,
                summary.withinWindowDays,
            ),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun RhythmChartCard(bands: List<DayBand>, onDayClick: (Long) -> Unit) {
    SectionCard(stringResource(Res.string.insights_card_chart)) {
        RhythmChart(bands = bands, onDayClick = onDayClick)
    }
}

/**
 * Month: a Sunday-first grid with leading blanks. Week: one row of the seven days, each column
 * headed by that day's own initial, so the header always tells the truth about the cell below.
 */
@Composable
private fun CalendarGrid(period: Period, days: List<DayCell>, onDayClick: (Long) -> Unit) {
    if (days.isEmpty()) return
    val initials = stringArrayResource(Res.array.insights_weekday_initials)
    val zone = TimeZone.currentSystemDefault()
    val columns: List<Int> = when (period) {
        Period.WEEK -> days.map { weekdayIndex(it.dayStart, zone) }
        Period.MONTH -> (0 until WEEK_COLUMNS).toList()
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        columns.forEach { index ->
            Text(
                text = initials[index],
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    val leadingBlanks = if (period == Period.MONTH) weekdayIndex(days.first().dayStart, zone) else 0
    val cells: List<DayCell?> = List(leadingBlanks) { null } + days
    cells.chunked(WEEK_COLUMNS).forEach { week ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            week.forEach { cell -> DayBox(cell, onDayClick, Modifier.weight(1f)) }
            repeat(WEEK_COLUMNS - week.size) { Box(Modifier.weight(1f)) }
        }
    }
}

/** Sunday-first column index: SUNDAY -> 0, MONDAY -> 1, ... SATURDAY -> 6. */
private fun weekdayIndex(dayStart: Long, zone: TimeZone): Int =
    Instant.fromEpochMilliseconds(dayStart).toLocalDateTime(zone).date.dayOfWeek.isoDayNumber % WEEK_COLUMNS

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
private fun DayBox(cell: DayCell?, onDayClick: (Long) -> Unit, modifier: Modifier) {
    if (cell == null) {
        Box(modifier.aspectRatio(1f).background(Color.Transparent))
        return
    }
    val todayLabel = stringResource(Res.string.day_today)
    val openLabel = stringResource(Res.string.insights_day_open)
    val hasRecord = cell.state != DayWindowState.NO_RECORD
    val description = stringResource(Res.string.insights_day_description, cell.dayOfMonth, stateLabel(cell.state))
        .let { if (cell.isToday) "$it · $todayLabel" else it }
    val shape = RoundedCornerShape(6.dp)
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .alpha(if (cell.isFuture) FUTURE_DAY_ALPHA else 1f)
            .clip(shape)
            .background(cellColor(cell.state))
            .then(
                if (cell.isToday) {
                    Modifier.border(1.5.dp, MaterialTheme.colorScheme.onSurface, shape)
                } else {
                    Modifier
                }
            )
            // Only a day with something to show is a button; empty days stay inert — an empty
            // sheet is a dead end, and "log a meal for this day" would turn a mirror into a nag.
            .then(
                if (hasRecord) {
                    Modifier.clickable(onClickLabel = openLabel) { onDayClick(cell.dayStart) }
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
    }
}

/**
 * The wall, as a run of lazy items rather than one card: a period can hold hundreds of photos, and
 * only the rows on screen should be composed (or ask Coil for an image). The pieces carry the card's
 * corners between them — rounded at the two ends, square in the middle — so the seam does not show.
 */
private fun LazyListScope.photoWall(
    fileNames: List<String>,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    photoModel: (String) -> Any?,
    onPhotoClick: (List<String>, Int) -> Unit,
) {
    if (fileNames.isEmpty()) {
        item {
            Spaced {
                SectionCard(stringResource(Res.string.insights_card_photos)) {
                    Text(
                        text = stringResource(Res.string.insights_empty_photos),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
        return
    }
    val rows = photoWallRows(
        fileNames = fileNames,
        expanded = expanded,
        columns = PHOTO_COLUMNS,
        previewCount = WALL_PREVIEW_PHOTOS,
    )
    item {
        WallPiece(shape = RoundedCornerShape(topStart = WALL_CORNER, topEnd = WALL_CORNER)) {
            Text(
                text = stringResource(Res.string.insights_card_photos),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 16.dp, bottom = 12.dp),
            )
        }
    }
    items(rows, key = { row -> "wall-${row.first().index}" }) { row ->
        WallPiece(shape = RectangleShape) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                row.forEach { photo ->
                    PhotoThumbnail(
                        model = photoModel(photo.fileName),
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onPhotoClick(fileNames, photo.index) },
                    )
                }
                repeat(PHOTO_COLUMNS - row.size) { Box(Modifier.weight(1f)) }
            }
        }
    }
    item {
        WallPiece(shape = RoundedCornerShape(bottomStart = WALL_CORNER, bottomEnd = WALL_CORNER)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(Res.string.insights_photo_count, fileNames.size),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (fileNames.size > WALL_PREVIEW_PHOTOS) {
                    TextButton(onClick = onToggleExpanded) {
                        Text(
                            if (expanded) {
                                stringResource(Res.string.insights_photos_collapse)
                            } else {
                                stringResource(Res.string.insights_photos_show_all, fileNames.size)
                            },
                        )
                    }
                }
            }
        }
    }
    item { Spacer(Modifier.height(CARD_GAP)) }
}

/**
 * One slice of the wall's card. [shape] rounds only the ends of the run; every slice shares the card
 * colour and sits flush against its neighbours, so the run reads as a single card.
 */
@Composable
private fun WallPiece(shape: Shape, content: @Composable () -> Unit) {
    Card(shape = shape, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 16.dp)) { content() }
    }
}

@Composable
private fun LocationsCard(locations: List<LocationCount>) {
    SectionCard(stringResource(Res.string.insights_card_locations)) {
        if (locations.isEmpty()) {
            Text(stringResource(Res.string.insights_empty_locations), style = MaterialTheme.typography.bodyMedium)
            return@SectionCard
        }
        // The map is one picture; the line under it is its text equivalent for screen readers.
        val summary = stringResource(
            Res.string.insights_map_summary,
            locations.size,
            locations.sumOf { it.count },
        )
        // The card stays a still picture; a tap opens the closer, zoomable look (kept open across
        // rotation and other recreation).
        var expanded by rememberSaveable { mutableStateOf(false) }
        PlacesMap(locations = locations, contentDescription = summary, onClick = { expanded = true })
        if (expanded) {
            FullScreenPlacesMap(locations = locations, contentDescription = summary, onDismiss = { expanded = false })
        }
        Text(
            text = summary,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * "HH:mm" for minutes since the eating day's midnight, wrapped to the clock (an average last
 * meal of 24 h 30 m reads 00:30), or [none] when the period holds no data.
 */
private fun clockLabel(minutes: Int?, none: String): String {
    if (minutes == null) return none
    val hour = (minutes / MINUTES_PER_HOUR) % HOURS_PER_DAY
    return "${pad2(hour)}:${pad2(minutes % MINUTES_PER_HOUR)}"
}

/** Month: "yyyy / MM"; Week: "m/d – m/d". kotlinx-datetime only (no SimpleDateFormat on Native). */
@Composable
private fun rangeLabel(period: Period, range: DayRange): String = when (period) {
    Period.MONTH -> {
        val date = Instant.fromEpochMilliseconds(range.start).toLocalDateTime(TimeZone.currentSystemDefault()).date
        "${date.year} / ${pad2(date.month.number)}"
    }
    Period.WEEK -> stringResource(
        Res.string.insights_period_range,
        shortDate(range.start),
        shortDate(range.endInclusive),
    )
}

@Composable
private fun shortDate(millis: Long): String {
    val date = Instant.fromEpochMilliseconds(millis).toLocalDateTime(TimeZone.currentSystemDefault()).date
    return stringResource(Res.string.insights_date_short, date.month.number, date.day)
}

private fun pad2(n: Int): String = n.toString().padStart(2, '0')
