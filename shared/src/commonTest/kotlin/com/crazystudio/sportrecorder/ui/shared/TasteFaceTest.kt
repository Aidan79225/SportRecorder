package com.crazystudio.sportrecorder.ui.shared

import com.crazystudio.sportrecorder.domain.model.TasteRating
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The faces stay in step with the scale: each stored level has its own face and word, in the order shown. */
class TasteFaceTest {

    // The one table that gives the stored 1..5 their meaning. An exact list, so swapping two entries (or only
    // their pictures) fails here: nothing else can notice, since a screen reader never sees the picture.
    @Test fun theFacesRunFromUnpleasantToDelightful_eachWithItsOwnWord() {
        assertEquals(
            listOf("😞" to "taste_1", "🙁" to "taste_2", "😐" to "taste_3", "🙂" to "taste_4", "😋" to "taste_5"),
            TasteRating.LEVELS.map { level -> tasteLook(level)?.let { it.face to it.word.key } },
        )
    }

    @Test fun nothingIsShownForAnUnratedMealOrAValueOffTheScale() {
        listOf(null, 0, 6, -1, Int.MAX_VALUE).forEach { value ->
            assertNull(tasteLook(value), "value $value")
        }
    }
}
