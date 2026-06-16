package com.crazystudio.sportrecorder.domain.insights

import com.crazystudio.sportrecorder.domain.model.DietSettings
import com.crazystudio.sportrecorder.domain.model.EatPhoto
import com.crazystudio.sportrecorder.domain.model.EatRecord
import com.crazystudio.sportrecorder.domain.model.GeoPoint
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

    @Test fun streak_countsConsecutiveOnTargetDays() {
        val records = listOf(
            rec(at(2026, 2, 14, 9)),
            rec(at(2026, 2, 14, 12)),
            rec(at(2026, 2, 15, 9)),
            rec(at(2026, 2, 15, 13)),
        )
        val now = at(2026, 2, 15, 20)
        assertEquals(2, InsightsAggregator.computeStreak(records, eatingHours, now))
    }

    @Test fun streak_emptyTodayDoesNotBreak() {
        // 14th is ON; 15th (today) has no meals yet.
        val records = listOf(
            rec(at(2026, 2, 14, 9)),
            rec(at(2026, 2, 14, 12)),
        )
        val now = at(2026, 2, 15, 10)
        assertEquals(1, InsightsAggregator.computeStreak(records, eatingHours, now))
    }

    @Test fun streak_offTargetBreaks() {
        // 13th ON, 14th OFF (14h window), 15th ON.
        val records = listOf(
            rec(at(2026, 2, 13, 9)),
            rec(at(2026, 2, 13, 12)),
            rec(at(2026, 2, 14, 8)),
            rec(at(2026, 2, 14, 22)),
            rec(at(2026, 2, 15, 9)),
            rec(at(2026, 2, 15, 12)),
        )
        val now = at(2026, 2, 15, 20)
        assertEquals(1, InsightsAggregator.computeStreak(records, eatingHours, now))
    }

    @Test fun streak_gapDayBreaks() {
        // 13th ON, 14th no meals (gap), 15th ON.
        val records = listOf(
            rec(at(2026, 2, 13, 9)),
            rec(at(2026, 2, 13, 12)),
            rec(at(2026, 2, 15, 9)),
            rec(at(2026, 2, 15, 12)),
        )
        val now = at(2026, 2, 15, 20)
        assertEquals(1, InsightsAggregator.computeStreak(records, eatingHours, now))
    }

    @Test fun statsFor_countsAndAverages() {
        val records = listOf(
            rec(at(2026, 2, 10, 8, 0)),
            rec(at(2026, 2, 10, 18, 0)),
            rec(at(2026, 2, 11, 10, 0)),
            rec(at(2026, 2, 11, 20, 0)),
        )
        val stats = InsightsAggregator.statsFor(records)
        assertEquals(4, stats.mealCount)
        assertEquals(9 * 60, stats.avgFirstMealMinutes)
        assertEquals(19 * 60, stats.avgLastMealMinutes)
    }

    @Test fun statsFor_lateNightCountsDistinctDays() {
        val records = listOf(
            rec(at(2026, 2, 10, 23, 0)),
            rec(at(2026, 2, 11, 12, 0)),
            rec(at(2026, 2, 12, 22, 0)),
            rec(at(2026, 2, 12, 22, 30)),
        )
        assertEquals(2, InsightsAggregator.statsFor(records).lateNightDays)
    }

    @Test fun statsFor_empty() {
        val stats = InsightsAggregator.statsFor(emptyList())
        assertEquals(0, stats.mealCount)
        assertEquals(null, stats.avgFirstMealMinutes)
        assertEquals(null, stats.avgLastMealMinutes)
        assertEquals(0, stats.lateNightDays)
    }

    @Test fun periodStart_weekIsSevenDays() {
        val now = at(2026, 2, 15, 12)
        val start = InsightsAggregator.periodStart(now, Period.WEEK)
        assertEquals(InsightsAggregator.dayStart(at(2026, 2, 9, 0)), start)
    }

    private fun recFull(time: Long, photos: List<String>, lat: Double?, lng: Double?) =
        EatRecord(
            id = 0,
            time = time,
            note = null,
            location = if (lat != null && lng != null) GeoPoint(lat, lng) else null,
            photos = photos.mapIndexed { i, name -> EatPhoto(id = i, fileName = name, createdAt = time) },
        )

    @Test fun compute_assemblesAllCards() {
        val settings = DietSettings(fastingHours = 16, eatingHours = 8)
        val now = at(2026, 1, 15, 21)
        val records = listOf(
            recFull(at(2026, 1, 15, 9), listOf("a.webp"), 25.0, 121.0),
            recFull(at(2026, 1, 15, 13), listOf("b.webp"), 25.0, 121.0),
        )
        val result = InsightsAggregator.compute(records, settings, now, Period.MONTH, now)

        assertEquals(28, result.calendarDays.size)
        assertEquals(1, result.streak)
        assertEquals(2, result.stats.mealCount)
        assertEquals(listOf("b.webp", "a.webp"), result.photoFileNames)
        assertEquals(1, result.locations.size)
        assertEquals(2, result.locations.first().count)
    }

    @Test fun compute_weekPeriodFiltersOutOlderRecords() {
        val settings = DietSettings(fastingHours = 16, eatingHours = 8)
        val now = at(2026, 1, 15, 21)
        val records = listOf(
            recFull(at(2026, 1, 15, 9), listOf("recent.webp"), 25.0, 121.0),
            recFull(at(2026, 1, 1, 9), listOf("old.webp"), 25.0, 121.0),
        )
        val result = InsightsAggregator.compute(records, settings, now, Period.WEEK, now)
        assertEquals(1, result.stats.mealCount)
        assertEquals(listOf("recent.webp"), result.photoFileNames)
        assertEquals(1, result.locations.first().count)
    }

    @Test fun compute_empty_returnsZeroedCardsWithFullCalendar() {
        val settings = DietSettings(fastingHours = 16, eatingHours = 8)
        val now = at(2026, 1, 15, 21)
        val result = InsightsAggregator.compute(emptyList(), settings, now, Period.MONTH, now)
        assertEquals(28, result.calendarDays.size)
        assertEquals(0, result.streak)
        assertEquals(0, result.stats.mealCount)
        assertEquals(emptyList<String>(), result.photoFileNames)
        assertEquals(emptyList<LocationCount>(), result.locations)
    }
}
