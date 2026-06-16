package com.crazystudio.sportrecorder.domain.insights

import com.crazystudio.sportrecorder.domain.model.EatRecord
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar
import java.util.concurrent.TimeUnit

class InsightsAggregatorTest {
    private val eatingHours = 8L
    private fun h(n: Long) = TimeUnit.HOURS.toMillis(n)
    private val base = 1_700_000_000_000L

    /** Local-time epoch millis for the given wall-clock moment. `month` is 0-based. */
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

    private fun rec(time: Long) =
        EatRecord(id = 0, time = time, location = null, note = null, photos = emptyList())

    @Test fun monthCells_lengthMatchesDaysInMonth() {
        // March 2026 has 31 days.
        val cells = InsightsAggregator.monthCells(emptyList(), eatingHours, at(2026, 2, 15, 12))
        assertEquals(31, cells.size)
        assertEquals(1, cells.first().dayOfMonth)
        assertEquals(31, cells.last().dayOfMonth)
    }

    @Test fun monthCells_classifiesEachDay() {
        val records = listOf(
            rec(at(2026, 2, 10, 9)),
            rec(at(2026, 2, 10, 14)), // 5h window -> ON
            rec(at(2026, 2, 11, 8)),
            rec(at(2026, 2, 11, 20)), // 12h window -> OFF
        )
        val cells = InsightsAggregator.monthCells(records, eatingHours, at(2026, 2, 1, 0))
        assertEquals(AdherenceState.ON_TARGET, cells[9].state) // day 10
        assertEquals(AdherenceState.OFF_TARGET, cells[10].state) // day 11
        assertEquals(AdherenceState.NO_DATA, cells[0].state) // day 1
    }
}
