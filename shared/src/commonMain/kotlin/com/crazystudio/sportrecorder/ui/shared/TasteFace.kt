package com.crazystudio.sportrecorder.ui.shared

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import com.crazystudio.sportrecorder.shared.resources.Res
import com.crazystudio.sportrecorder.shared.resources.taste_1
import com.crazystudio.sportrecorder.shared.resources.taste_2
import com.crazystudio.sportrecorder.shared.resources.taste_3
import com.crazystudio.sportrecorder.shared.resources.taste_4
import com.crazystudio.sportrecorder.shared.resources.taste_5
import com.crazystudio.sportrecorder.shared.resources.taste_described
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * How a `TasteRating` level looks: a face, and the one plain word that says what the face means (the word is
 * what a screen reader reads, and what the editor shows on the label's line). The faces run from unpleasant
 * to delightful and the app gives none of them a colour of its own, so a rating reads as how the meal felt,
 * never as a grade. They are the system's emoji, drawn by each device's own emoji font.
 */
internal class TasteLook(val face: String, val word: StringResource)

private val LOOKS: Map<Int, TasteLook> = mapOf(
    1 to TasteLook("😞", Res.string.taste_1),
    2 to TasteLook("🙁", Res.string.taste_2),
    3 to TasteLook("😐", Res.string.taste_3),
    4 to TasteLook("🙂", Res.string.taste_4),
    5 to TasteLook("😋", Res.string.taste_5),
)

/** The look of [level], or null for an unrated meal or a value off the scale — there is nothing to show then. */
internal fun tasteLook(level: Int?): TasteLook? = level?.let { LOOKS[it] }

/**
 * A rated meal's face, sized by a type role like any other text so it follows the user's font setting. It says
 * nothing for an unrated meal (the card looks exactly as it did before ratings existed) and is described to a
 * screen reader in words, "Taste: Tasty", rather than as an emoji.
 */
@Composable
internal fun TasteFace(level: Int?, modifier: Modifier = Modifier) {
    val look = tasteLook(level) ?: return
    val description = stringResource(Res.string.taste_described, stringResource(look.word))
    Text(
        text = look.face,
        modifier = modifier.clearAndSetSemantics { contentDescription = description },
        style = MaterialTheme.typography.bodyLarge,
    )
}
