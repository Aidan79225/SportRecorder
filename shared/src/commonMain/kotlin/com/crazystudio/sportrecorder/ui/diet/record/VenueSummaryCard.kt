package com.crazystudio.sportrecorder.ui.diet.record

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.crazystudio.sportrecorder.domain.model.VenueAddress
import com.crazystudio.sportrecorder.domain.venue.VenueSummary
import com.crazystudio.sportrecorder.shared.resources.Res
import com.crazystudio.sportrecorder.shared.resources.venue_summary_dish
import com.crazystudio.sportrecorder.shared.resources.venue_summary_taste
import com.crazystudio.sportrecorder.shared.resources.venue_summary_visits
import com.crazystudio.sportrecorder.ui.shared.tasteLook
import org.jetbrains.compose.resources.stringResource

/**
 * What one place has been for the user, at the top of the Record list while the search narrows to it: its name
 * and address, how many times and when last, the dishes eaten there and the faces given. Plain history only —
 * nothing is averaged, ranked or praised.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun VenueSummaryCard(summary: VenueSummary, modifier: Modifier = Modifier) {
    val colorScheme = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colorScheme.surfaceContainerHigh)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column {
            Text(text = summary.venue.name, style = MaterialTheme.typography.titleMedium, color = colorScheme.onSurface)
            VenueAddress.clean(summary.venue.address)?.let { address ->
                Text(text = address, style = MaterialTheme.typography.labelSmall, color = colorScheme.onSurfaceVariant)
            }
        }
        Text(
            text = stringResource(Res.string.venue_summary_visits, summary.visits, formatDate(summary.lastVisit)),
            style = MaterialTheme.typography.bodySmall,
            color = colorScheme.onSurface,
        )
        if (summary.dishes.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                summary.dishes.forEach { (dish, count) ->
                    // Not a button: a quiet tag that only carries the words.
                    Text(
                        text = stringResource(Res.string.venue_summary_dish, dish, count),
                        style = MaterialTheme.typography.labelLarge,
                        color = colorScheme.onSurface,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(colorScheme.surfaceContainerHighest)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
        }
        if (summary.tasteCounts.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                summary.tasteCounts.forEach { (level, count) ->
                    val look = tasteLook(level) ?: return@forEach
                    // The emoji is for the eye; a screen reader gets the taste word and the count.
                    val spoken = stringResource(Res.string.venue_summary_taste, stringResource(look.word), count)
                    Text(
                        text = "${look.face}×$count",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.clearAndSetSemantics { contentDescription = spoken },
                    )
                }
            }
        }
    }
}
