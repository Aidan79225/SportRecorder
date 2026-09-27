package com.crazystudio.sportrecorder.ui.insights

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.crazystudio.sportrecorder.domain.insights.InsightsAggregator
import com.crazystudio.sportrecorder.domain.model.EatRecord
import com.crazystudio.sportrecorder.shared.resources.Res
import com.crazystudio.sportrecorder.shared.resources.insights_day_empty
import com.crazystudio.sportrecorder.shared.resources.insights_day_meals
import com.crazystudio.sportrecorder.shared.resources.insights_day_title
import com.crazystudio.sportrecorder.shared.resources.insights_next_day_time
import com.crazystudio.sportrecorder.ui.shared.PhotoThumbnail
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Instant

private val MAX_SHEET_HEIGHT = 560.dp
private const val PHOTO_COLUMNS = 3

/**
 * One eating day's meals, read-only: time, note, photos. Insights is for looking; there is no
 * delete and no edit here on purpose — the Record tab keeps those. A meal that fell after
 * midnight but belongs to this day (see [InsightsAggregator.mealsByEatingDay]) is marked
 * 「隔天」 so the time reads right.
 */
@Composable
fun DayRecordsSheet(
    dayStart: Long,
    records: List<EatRecord>,
    photoModel: (String) -> Any?,
    onPhotoClick: (List<String>, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val zone = TimeZone.currentSystemDefault()
    val date = Instant.fromEpochMilliseconds(dayStart).toLocalDateTime(zone).date
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface)
            .navigationBarsPadding()
            .heightIn(max = MAX_SHEET_HEIGHT)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(Res.string.insights_day_title, date.year, date.month.number, date.day),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = if (records.isEmpty()) {
                stringResource(Res.string.insights_day_empty)
            } else {
                stringResource(Res.string.insights_day_meals, records.size)
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        records.forEach { record -> MealRow(record, dayStart, zone, photoModel, onPhotoClick) }
    }
}

@Composable
private fun MealRow(
    record: EatRecord,
    dayStart: Long,
    zone: TimeZone,
    photoModel: (String) -> Any?,
    onPhotoClick: (List<String>, Int) -> Unit,
) {
    val time = Instant.fromEpochMilliseconds(record.time).toLocalDateTime(zone)
    val clock = "${pad2(time.hour)}:${pad2(time.minute)}"
    val afterMidnight = InsightsAggregator.dayStart(record.time, zone) != dayStart
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = if (afterMidnight) stringResource(Res.string.insights_next_day_time, clock) else clock,
            style = MaterialTheme.typography.bodyMedium,
        )
        // Local val: cross-module smart-cast doesn't apply to :shared properties.
        val note = record.note
        if (!note.isNullOrBlank()) {
            Text(text = note, style = MaterialTheme.typography.bodySmall)
        }
        if (record.photos.isNotEmpty()) {
            val photoNames = remember(record.photos) { record.photos.map { it.fileName } }
            photoNames.withIndex().chunked(PHOTO_COLUMNS).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    row.forEach { (index, name) ->
                        PhotoThumbnail(
                            model = photoModel(name),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onPhotoClick(photoNames, index) },
                        )
                    }
                    repeat(PHOTO_COLUMNS - row.size) { Box(Modifier.weight(1f)) }
                }
            }
        }
    }
}

private fun pad2(n: Int): String = n.toString().padStart(2, '0')
