package com.crazystudio.sportrecorder.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Normalization exists so that 「大戶屋」 typed twice is one venue, not two. It deliberately does
 * NOT change what the user sees — only how two names are compared and stored.
 */
class VenueNameTest {

    @Test fun trimsSurroundingWhitespace() {
        assertEquals("大戶屋", VenueName.normalize("  大戶屋 "))
    }

    @Test fun collapsesRunsOfWhitespace() {
        assertEquals("Burger King", VenueName.normalize("Burger   King"))
        assertEquals("Burger King", VenueName.normalize("Burger\tKing"))
    }

    @Test fun keepsTheCaseTheUserTyped() {
        assertEquals("Starbucks", VenueName.normalize("Starbucks"))
    }

    /** Uniqueness ignores case, so one careless capital does not create a second venue. */
    @Test fun comparesWithoutCase() {
        assertTrue(VenueName.sameAs("Starbucks", "starbucks"))
        assertTrue(VenueName.sameAs(" Starbucks ", "STARBUCKS"))
        assertFalse(VenueName.sameAs("Starbucks", "Starbuck"))
    }

    @Test fun blankIsNotAName() {
        assertEquals("", VenueName.normalize("   "))
    }
}
