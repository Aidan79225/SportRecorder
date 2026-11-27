package com.crazystudio.sportrecorder.ui.shared

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow

private const val DISHES_MAX_LINES = 2

/** What a meal had as one quiet line, `A · B · C`, at most two lines; nothing at all when there are none. */
@Composable
fun DishesLine(dishes: List<String>, modifier: Modifier = Modifier) {
    if (dishes.isEmpty()) return
    Text(
        text = dishes.joinToString(" · "),
        modifier = modifier,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = DISHES_MAX_LINES,
        overflow = TextOverflow.Ellipsis,
    )
}
