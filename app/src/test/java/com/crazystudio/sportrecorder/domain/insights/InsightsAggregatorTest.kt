package com.crazystudio.sportrecorder.domain.insights

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar
import java.util.concurrent.TimeUnit

class InsightsAggregatorTest {
    private val eatingHours = 8L
    private fun h(n: Long) = TimeUnit.HOURS.toMillis(n)
    private val base = 1_700_000_000_000L

    /** Local-time epoch millis for the given wall-clock moment. `month` is 0-based. */
    @Suppress("UnusedPrivateMember")
    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int = 0): Long =
        Calendar.getInstance().apply {
            clear()
            set(year, month, day, hour, minute)
        }.timeInMillis

    @Test fun adherence_noMeals_isNoData() {
        assertEquals(AdherenceState.NO_DATA, InsightsAggregator.adherenceFor(emptyList(), eatingHours))
    }

    @Test fun adherence_singleMeal_isOnTarget() {
        assertEquals(AdherenceState.ON_TARGET, InsightsAggregator.adherenceFor(listOf(base), eatingHours))
    }

    @Test fun adherence_windowWithinGoal_isOnTarget() {
        val state = InsightsAggregator.adherenceFor(listOf(base, base + h(6)), eatingHours)
        assertEquals(AdherenceState.ON_TARGET, state)
    }

    @Test fun adherence_windowExactlyGoal_isOnTarget() {
        val state = InsightsAggregator.adherenceFor(listOf(base, base + h(8)), eatingHours)
        assertEquals(AdherenceState.ON_TARGET, state)
    }

    @Test fun adherence_windowOverGoal_isOffTarget() {
        val state = InsightsAggregator.adherenceFor(listOf(base, base + h(9)), eatingHours)
        assertEquals(AdherenceState.OFF_TARGET, state)
    }
}
