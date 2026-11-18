package com.crazystudio.sportrecorder.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** How a meal tasted: five levels, and "not rated" is the absence of one, never a middle value. */
class TasteRatingTest {

    @Test fun aMealIsUnratedUnlessTheUserSaysOtherwise() {
        assertNull(EatRecord(0, 0L, null, null, emptyList()).tasteRating)
    }

    @Test fun theLevelsAreOneToFive_lowestFirst() {
        assertEquals(listOf(1, 2, 3, 4, 5), TasteRating.LEVELS)
        assertEquals(1, TasteRating.MIN)
        assertEquals(5, TasteRating.MAX)
    }

    @Test fun sanitizeKeepsEveryLevel() {
        TasteRating.LEVELS.forEach { level -> assertEquals(level, TasteRating.sanitize(level), "level $level") }
    }

    /** A stored row or a snapshot is data from outside: anything that is not a level reads as "not rated". */
    @Test fun sanitizeTurnsAnythingElseIntoUnrated() {
        listOf(null, 0, 6, -1, 100, Int.MIN_VALUE, Int.MAX_VALUE).forEach { value ->
            assertNull(TasteRating.sanitize(value), "value $value")
        }
    }
}
