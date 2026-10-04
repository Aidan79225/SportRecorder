package com.crazystudio.sportrecorder.ui.insights

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.crazystudio.sportrecorder.domain.insights.DayBand
import com.crazystudio.sportrecorder.shared.resources.Res
import com.crazystudio.sportrecorder.shared.resources.insights_chart_month_summary
import com.crazystudio.sportrecorder.shared.resources.insights_chart_row
import com.crazystudio.sportrecorder.shared.resources.insights_chart_row_empty
import com.crazystudio.sportrecorder.shared.resources.insights_date_short
import com.crazystudio.sportrecorder.shared.resources.insights_day_open
import com.crazystudio.sportrecorder.shared.resources.insights_next_day_time
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Instant

private val LABEL_WIDTH = 44.dp
private val SINGLE_MEAL_MARK = 3.dp
private val GRIDLINE = 1.dp

/**
 * How tightly the chart is drawn. A week is seven rows, so each one can be a labelled, tappable
 * day. A month is thirty-one: drawn that way it is a barcode and a column of unreadable dates, so
 * the rows close up into one block, a date marks every seventh day, and the calendar card keeps
 * owning day taps — a 12dp row is not a touch target anyone can hit.
 */
enum class RhythmDensity(
    val rowHeight: Dp,
    val trackHeight: Dp,
    val gap: Dp,
    /** Draw a date every N rows. */
    val labelEvery: Int,
) {
    WEEK(rowHeight = 22.dp, trackHeight = 12.dp, gap = 4.dp, labelEvery = 1),
    MONTH(rowHeight = 12.dp, trackHeight = 11.dp, gap = 1.dp, labelEvery = 7),
}

/**
 * One row per day, time running left to right, each day's eating window a single band from its
 * first meal to its last. Every band is the same colour: the chart shows the *shape* of the
 * days, it does not colour them against the goal — that would turn it back into a grade sheet.
 *
 * Each row is its own accessibility node (date, meal count, first–last), and a row with meals
 * opens that day via [onDayClick], the same gesture as a calendar cell.
 */
@Composable
fun RhythmChart(
    bands: List<DayBand>,
    onDayClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
    density: RhythmDensity = RhythmDensity.WEEK,
) {
    val domainEnd = remember(bands) { RhythmChartScale.domainEnd(bands) }
    // A month speaks once: thirty-one rows would be thirty-one swipes of a screen reader, and the
    // stats card right below carries the same month as numbers.
    val blockDescription = stringResource(
        Res.string.insights_chart_month_summary,
        bands.count { it.mealCount > 0 },
    ).takeIf { density == RhythmDensity.MONTH }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(
                blockDescription?.let { text ->
                    Modifier.clearAndSetSemantics { contentDescription = text }
                } ?: Modifier
            ),
        verticalArrangement = Arrangement.spacedBy(density.gap),
    ) {
        bands.forEachIndexed { index, band ->
            BandRow(
                band = band,
                domainEnd = domainEnd,
                density = density,
                showLabel = index % density.labelEvery == 0,
                // Only the week's rows are their own targets; see [RhythmDensity].
                onDayClick = onDayClick.takeIf { density == RhythmDensity.WEEK },
            )
        }
        AxisLabels(domainEnd)
    }
}

@Composable
private fun BandRow(
    band: DayBand,
    domainEnd: Int,
    density: RhythmDensity,
    showLabel: Boolean,
    onDayClick: ((Long) -> Unit)?,
) {
    val dayLabel = stringResource(Res.string.insights_date_short, monthNumber(band.dayStart), band.dayOfMonth)
    val description = if (band.mealCount == 0) {
        stringResource(Res.string.insights_chart_row_empty, dayLabel)
    } else {
        stringResource(
            Res.string.insights_chart_row,
            dayLabel,
            band.mealCount,
            clockLabel(band.firstMinutes),
            clockLabel(band.lastMinutes),
        )
    }
    val openLabel = stringResource(Res.string.insights_day_open)
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val bandColor = MaterialTheme.colorScheme.primaryContainer
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    // Not named `density`: that is this row's RhythmDensity.
    val localDensity = LocalDensity.current
    val markPx = with(localDensity) { SINGLE_MEAL_MARK.toPx() }
    val gridPx = with(localDensity) { GRIDLINE.toPx() }
    val ticks = remember(domainEnd) { RhythmChartScale.ticks(domainEnd) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(density.rowHeight)
            .then(
                if (onDayClick != null && band.mealCount > 0) {
                    Modifier.clickable(onClickLabel = openLabel) { onDayClick(band.dayStart) }
                } else {
                    Modifier
                }
            )
            .clearAndSetSemantics { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showLabel) {
            Text(
                text = dayLabel,
                modifier = Modifier.width(LABEL_WIDTH),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Spacer(Modifier.width(LABEL_WIDTH))
        }
        Canvas(Modifier.weight(1f).height(density.trackHeight)) {
            val corner = CornerRadius(size.height / 2f)
            drawRoundRect(color = trackColor, cornerRadius = corner)
            ticks.forEach { tick ->
                val x = size.width * RhythmChartScale.fraction(tick, domainEnd)
                drawLine(gridColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = gridPx)
            }
            if (band.mealCount > 0) {
                val left = size.width * RhythmChartScale.fraction(band.firstMinutes, domainEnd)
                // A single meal has no span; give it a sliver so the day is still visibly there.
                val right = maxOf(size.width * RhythmChartScale.fraction(band.lastMinutes, domainEnd), left + markPx)
                drawRoundRect(
                    color = bandColor,
                    topLeft = Offset(left, 0f),
                    size = Size(right - left, size.height),
                    cornerRadius = corner,
                )
            }
        }
    }
}

/** Hours from midnight: 0 · 6 · 12 · 18 · 24, and on past 24 when a night ran long. */
@Composable
private fun AxisLabels(domainEnd: Int) {
    val ticks = remember(domainEnd) { RhythmChartScale.ticks(domainEnd) }
    Row(Modifier.fillMaxWidth()) {
        Spacer(Modifier.width(LABEL_WIDTH))
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.SpaceBetween) {
            ticks.forEach { tick ->
                Text(
                    text = (tick / RhythmChartScale.MINUTES_PER_HOUR).toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** "HH:mm", or "next day HH:mm" for a meal past the eating day's midnight. */
@Composable
private fun clockLabel(minutes: Int): String {
    val wrapped = minutes % RhythmChartScale.MINUTES_PER_DAY
    val hours = pad2(wrapped / RhythmChartScale.MINUTES_PER_HOUR)
    val text = "$hours:${pad2(wrapped % RhythmChartScale.MINUTES_PER_HOUR)}"
    return if (minutes >= RhythmChartScale.MINUTES_PER_DAY) {
        stringResource(Res.string.insights_next_day_time, text)
    } else {
        text
    }
}

private fun monthNumber(dayStart: Long): Int =
    Instant.fromEpochMilliseconds(dayStart).toLocalDateTime(TimeZone.currentSystemDefault()).month.number

private fun pad2(n: Int): String = n.toString().padStart(2, '0')
