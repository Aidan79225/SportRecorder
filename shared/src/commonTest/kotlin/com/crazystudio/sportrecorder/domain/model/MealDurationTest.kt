package com.crazystudio.sportrecorder.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A meal lasts an hour unless the user says otherwise; the default lives here so every path gets it. */
class MealDurationTest {

    @Test fun defaultIsOneHour() {
        assertEquals(60, MealDuration.DEFAULT_MINUTES)
        assertEquals(60, EatRecord(0, 0L, null, null, emptyList()).durationMinutes)
    }

    /** The exact list already fixes the order; the default check still catches a DEFAULT_MINUTES change. */
    @Test fun choicesAreExactlyTheMenu_andContainTheDefault() {
        assertEquals(listOf(15, 30, 45, 60, 90, 120, 180), MealDuration.CHOICES)
        assertTrue(MealDuration.DEFAULT_MINUTES in MealDuration.CHOICES)
    }

    @Test fun partsSplitsHoursAndMinutes() {
        assertEquals(1 to 30, MealDuration.parts(90))
        assertEquals(1 to 0, MealDuration.parts(60))
        assertEquals(0 to 45, MealDuration.parts(45))
        assertEquals(3 to 0, MealDuration.parts(180))
    }

    @Test fun endTimeIsStartPlusDuration() {
        val record = EatRecord(0, 1_000L, null, null, emptyList(), durationMinutes = 90)
        assertEquals(1_000L + 5_400_000L, record.endTime)
        assertEquals(1_000L + 3_600_000L, EatRecord(0, 1_000L, null, null, emptyList()).endTime)
    }

    /** 40_000 minutes is legal restore input; times 60_000 it overflows Int, so the end must be a Long sum. */
    @Test fun endTimeOfAVeryLongMeal_doesNotOverflow() {
        assertEquals(2_400_001_000L, EatRecord(0, 1_000L, null, null, emptyList(), durationMinutes = 40_000).endTime)
    }
}
