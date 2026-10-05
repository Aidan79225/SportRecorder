package com.crazystudio.sportrecorder.domain.insights

import com.crazystudio.sportrecorder.domain.model.DietSettings
import com.crazystudio.sportrecorder.domain.model.EatPhoto
import com.crazystudio.sportrecorder.domain.model.EatRecord
import com.crazystudio.sportrecorder.domain.model.GeoPoint
import com.crazystudio.sportrecorder.domain.model.Venue
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

// Lives in :shared/commonTest (kotlin.test): runs on JVM locally and on the iosSimulatorArm64
// target in CI. Every case pins [zone] so results never depend on the machine's default zone.
class InsightsAggregatorTest {
    private val zone = TimeZone.UTC
    private val eatingHours = 8L
    private val settings = DietSettings(fastingHours = 16, eatingHours = eatingHours)
    private val base = 1_700_000_000_000L
    private fun h(n: Long) = n * 3_600_000L
    private fun min(n: Long) = n * 60_000L

    /** Epoch millis for a wall-clock moment in [zone]. `month` is 1-based. */
    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int = 0): Long =
        LocalDateTime(year, month, day, hour, minute).toInstant(zone).toEpochMilliseconds()

    private fun rec(time: Long, lat: Double? = null, lng: Double? = null) =
        EatRecord(
            id = 0,
            time = time,
            location = if (lat != null && lng != null) GeoPoint(lat, lng) else null,
            note = null,
            photos = emptyList(),
        )

    private fun recFull(time: Long, photos: List<String>, lat: Double?, lng: Double?) =
        EatRecord(
            id = 0,
            time = time,
            note = null,
            location = if (lat != null && lng != null) GeoPoint(lat, lng) else null,
            photos = photos.mapIndexed { i, name -> EatPhoto(id = i, fileName = name, createdAt = time) },
        )

    private fun byDay(vararg records: EatRecord) = InsightsAggregator.mealsByEatingDay(records.toList(), settings, zone)

    private fun cellsOf(period: Period, anchor: Long, days: Map<Long, List<EatRecord>>, now: Long) =
        InsightsAggregator.dayCells(InsightsRange.of(anchor, period, zone), days, eatingHours, now, zone)

    // --- window state -------------------------------------------------------

    @Test fun windowState_noMeals_isNoRecord() {
        assertEquals(DayWindowState.NO_RECORD, InsightsAggregator.windowStateFor(emptyList(), eatingHours))
    }

    @Test fun windowState_singleMeal_isWithinWindow() {
        assertEquals(DayWindowState.WITHIN_WINDOW, InsightsAggregator.windowStateFor(listOf(base), eatingHours))
    }

    @Test fun windowState_withinGoal_isWithinWindow() {
        val state = InsightsAggregator.windowStateFor(listOf(base, base + h(6)), eatingHours)
        assertEquals(DayWindowState.WITHIN_WINDOW, state)
    }

    @Test fun windowState_exactlyGoal_isWithinWindow() {
        val state = InsightsAggregator.windowStateFor(listOf(base, base + h(8)), eatingHours)
        assertEquals(DayWindowState.WITHIN_WINDOW, state)
    }

    @Test fun windowState_overGoal_isLongerWindow() {
        val state = InsightsAggregator.windowStateFor(listOf(base, base + h(9)), eatingHours)
        assertEquals(DayWindowState.LONGER_WINDOW, state)
    }

    // --- eating days (the Home/Insights agreement) --------------------------

    @Test fun mealsByEatingDay_bucketsByCalendarDateWhenNothingCrossesMidnight() {
        val days = byDay(rec(at(2026, 3, 10, 9)), rec(at(2026, 3, 10, 18)), rec(at(2026, 3, 11, 12)))
        assertEquals(listOf(at(2026, 3, 10, 0), at(2026, 3, 11, 0)), days.keys.toList())
        assertEquals(2, days.getValue(at(2026, 3, 10, 0)).size)
    }

    @Test fun mealsByEatingDay_lateNightSnackStaysWithTheDinnerBeforeIt() {
        // 20:00 dinner, 00:30 snack: same eating window on Home, so the same day here.
        val days = byDay(rec(at(2026, 3, 10, 20)), rec(at(2026, 3, 11, 0, 30)))
        assertEquals(listOf(at(2026, 3, 10, 0)), days.keys.toList())
        assertEquals(2, days.getValue(at(2026, 3, 10, 0)).size)
    }

    @Test fun mealsByEatingDay_aMealAfterARealFastOpensTheNextDay() {
        // 08:00 breakfast, then 00:30 the next night: 16.5h > the 16h merge limit → a new day.
        val days = byDay(rec(at(2026, 3, 10, 8)), rec(at(2026, 3, 11, 0, 30)))
        assertEquals(listOf(at(2026, 3, 10, 0), at(2026, 3, 11, 0)), days.keys.toList())
    }

    @Test fun mealsByEatingDay_valuesAreAscendingEvenWhenInputIsNewestFirst() {
        val days = byDay(rec(at(2026, 3, 10, 18)), rec(at(2026, 3, 10, 9)), rec(at(2026, 3, 10, 13)))
        assertEquals(
            listOf(at(2026, 3, 10, 9), at(2026, 3, 10, 13), at(2026, 3, 10, 18)),
            days.getValue(at(2026, 3, 10, 0)).map { it.time },
        )
    }

    // --- calendar -----------------------------------------------------------

    @Test fun dayCells_oneCellPerDayOfTheRange() {
        val now = at(2026, 3, 15, 12)
        val month = cellsOf(Period.MONTH, now, emptyMap(), now)
        assertEquals(31, month.size)
        assertEquals(1, month.first().dayOfMonth)
        assertEquals(31, month.last().dayOfMonth)
        val week = cellsOf(Period.WEEK, now, emptyMap(), now)
        assertEquals((9..15).toList(), week.map { it.dayOfMonth })
    }

    @Test fun dayCells_classifiesEachDay() {
        val days = byDay(
            rec(at(2026, 3, 10, 9)),
            rec(at(2026, 3, 10, 15)), // 6h window → within
            rec(at(2026, 3, 11, 8)),
            rec(at(2026, 3, 11, 20)), // 12h window → longer
            rec(at(2026, 3, 12, 20)),
            rec(at(2026, 3, 13, 0, 30)), // 4.5h across midnight → within, on the 12th
        )
        val now = at(2026, 3, 15, 12)
        val cells = cellsOf(Period.MONTH, now, days, now)
        assertEquals(DayWindowState.WITHIN_WINDOW, cells[9].state)
        assertEquals(DayWindowState.LONGER_WINDOW, cells[10].state)
        assertEquals(DayWindowState.WITHIN_WINDOW, cells[11].state)
        assertEquals(DayWindowState.NO_RECORD, cells[12].state)
    }

    @Test fun dayCells_marksTodayAndWhatIsStillAhead() {
        val now = at(2026, 3, 15, 12)
        val cells = cellsOf(Period.MONTH, now, emptyMap(), now)
        assertEquals(listOf(15), cells.filter { it.isToday }.map { it.dayOfMonth })
        assertEquals((16..31).toList(), cells.filter { it.isFuture }.map { it.dayOfMonth })
        assertFalse(cells[14].isFuture)
    }

    @Test fun dayCells_otherMonthHasNoToday() {
        val now = at(2026, 3, 15, 12)
        val cells = cellsOf(Period.MONTH, at(2026, 2, 1, 0), emptyMap(), now)
        assertTrue(cells.none { it.isToday })
        assertTrue(cells.none { it.isFuture })
    }

    @Test fun periodSummary_countsRecordedAndWithinWindowDays() {
        val cells = listOf(
            DayCell(0L, 1, DayWindowState.WITHIN_WINDOW),
            DayCell(0L, 2, DayWindowState.LONGER_WINDOW),
            DayCell(0L, 3, DayWindowState.NO_RECORD),
            DayCell(0L, 4, DayWindowState.WITHIN_WINDOW),
        )
        assertEquals(PeriodSummary(recordedDays = 3, withinWindowDays = 2), PeriodSummary.of(cells))
    }

    // --- stats --------------------------------------------------------------

    @Test fun statsFor_countsAndAverages() {
        val days = byDay(
            rec(at(2026, 3, 10, 8)),
            rec(at(2026, 3, 10, 18)),
            rec(at(2026, 3, 11, 10)),
            rec(at(2026, 3, 11, 20)),
        )
        val stats = InsightsAggregator.statsFor(days.values, zone)
        assertEquals(4, stats.mealCount)
        assertEquals(2, stats.daysWithRecords)
        assertEquals(9 * 60, stats.avgFirstMealMinutes)
        assertEquals(19 * 60, stats.avgLastMealMinutes)
        assertEquals(10 * 60, stats.avgWindowMinutes)
    }

    @Test fun statsFor_averagesRoundInsteadOfTruncating() {
        val days = byDay(rec(at(2026, 3, 10, 8, 0)), rec(at(2026, 3, 11, 8, 1)), rec(at(2026, 3, 12, 8, 1)))
        // (480 + 481 + 481) / 3 = 480.67 → 481, not 480.
        assertEquals(481, InsightsAggregator.statsFor(days.values, zone).avgFirstMealMinutes)
    }

    @Test fun statsFor_avgWindowIgnoresSingleMealDays() {
        val days = byDay(rec(at(2026, 3, 10, 8)), rec(at(2026, 3, 10, 14)), rec(at(2026, 3, 11, 12)))
        assertEquals(6 * 60, InsightsAggregator.statsFor(days.values, zone).avgWindowMinutes)
    }

    @Test fun statsFor_avgWindowNullWhenEveryDayHasOneMeal() {
        val days = byDay(rec(at(2026, 3, 10, 8)), rec(at(2026, 3, 11, 12)))
        assertNull(InsightsAggregator.statsFor(days.values, zone).avgWindowMinutes)
    }

    @Test fun statsFor_lastMealAfterMidnightRunsPastTwentyFourHours() {
        // 20:00 → 00:30 is one eating day: last meal at 24h30m, window 4h30m.
        val days = byDay(rec(at(2026, 3, 10, 20)), rec(at(2026, 3, 11, 0, 30)))
        val stats = InsightsAggregator.statsFor(days.values, zone)
        assertEquals(20 * 60, stats.avgFirstMealMinutes)
        assertEquals(24 * 60 + 30, stats.avgLastMealMinutes)
        assertEquals(4 * 60 + 30, stats.avgWindowMinutes)
    }

    @Test fun statsFor_empty() {
        assertEquals(InsightsStats.EMPTY, InsightsAggregator.statsFor(emptyList(), zone))
    }

    // --- bands --------------------------------------------------------------

    @Test fun bandsFor_oneRowPerDayEmptyDaysIncluded() {
        val now = at(2026, 3, 15, 12)
        val days = byDay(rec(at(2026, 3, 10, 9)), rec(at(2026, 3, 10, 17, 30)))
        val bands = InsightsAggregator.bandsFor(InsightsRange.of(now, Period.WEEK, zone), days, zone)
        assertEquals((9..15).toList(), bands.map { it.dayOfMonth })
        val tenth = bands[1]
        assertEquals(2, tenth.mealCount)
        assertEquals(9 * 60, tenth.firstMinutes)
        assertEquals(17 * 60 + 30, tenth.lastMinutes)
        assertEquals(0, bands[0].mealCount)
        assertEquals(at(2026, 3, 9, 0), bands[0].dayStart)
    }

    @Test fun bandsFor_singleMealHasNoWidth() {
        val now = at(2026, 3, 15, 12)
        val days = byDay(rec(at(2026, 3, 12, 13)))
        val bands = InsightsAggregator.bandsFor(InsightsRange.of(now, Period.WEEK, zone), days, zone)
        val twelfth = bands.single { it.dayOfMonth == 12 }
        assertEquals(1, twelfth.mealCount)
        assertEquals(twelfth.firstMinutes, twelfth.lastMinutes)
    }

    @Test fun bandsFor_windowCrossingMidnightKeepsGoingPastTheDay() {
        val now = at(2026, 3, 15, 12)
        val days = byDay(rec(at(2026, 3, 12, 22)), rec(at(2026, 3, 13, 1)))
        val bands = InsightsAggregator.bandsFor(InsightsRange.of(now, Period.WEEK, zone), days, zone)
        assertEquals(25 * 60, bands.single { it.dayOfMonth == 12 }.lastMinutes)
        assertEquals(0, bands.single { it.dayOfMonth == 13 }.mealCount)
    }

    @Test fun bandsFor_coversEveryDayOfAMonth() {
        // The month chart draws one row per band, so a missing day would silently shift the block.
        val now = at(2026, 3, 15, 12)
        val days = byDay(rec(at(2026, 3, 1, 8)), rec(at(2026, 3, 31, 20)))
        val bands = InsightsAggregator.bandsFor(InsightsRange.of(now, Period.MONTH, zone), days, zone)
        assertEquals(31, bands.size)
        assertEquals((1..31).toList(), bands.map { it.dayOfMonth })
        assertEquals(1, bands.first().mealCount)
        assertEquals(1, bands.last().mealCount)
        assertEquals(29, bands.count { it.mealCount == 0 })
    }

    @Test fun bandsFor_aShortMonthHasItsOwnLength() {
        val now = at(2026, 2, 10, 12)
        val bands = InsightsAggregator.bandsFor(InsightsRange.of(now, Period.MONTH, zone), emptyMap(), zone)
        assertEquals(28, bands.size) // 2026 is not a leap year
        assertEquals(28, bands.last().dayOfMonth)
    }

    // --- compute ------------------------------------------------------------

    @Test fun compute_assemblesEveryCardOverTheSameRange() {
        val now = at(2026, 3, 15, 12)
        val records = listOf(
            recFull(at(2026, 3, 14, 9), listOf("a.jpg"), 25.0330, 121.5654),
            recFull(at(2026, 3, 14, 18), listOf("b.jpg", "c.jpg"), 25.0331, 121.5654), // same ~100m cell
            recFull(at(2026, 3, 10, 12), emptyList(), 24.1477, 120.6736),
        )
        val result = InsightsAggregator.compute(records, settings, now, Period.WEEK, now, zone)

        assertTrue(result.hasAnyRecords)
        assertTrue(result.isCurrentPeriod)
        assertEquals(7, result.calendarDays.size)
        assertEquals(7, result.bands.size)
        assertEquals(3, result.stats.mealCount)
        assertEquals(2, result.stats.daysWithRecords)
        assertEquals(PeriodSummary(recordedDays = 2, withinWindowDays = 1), result.summary)
        assertEquals(listOf("b.jpg", "c.jpg", "a.jpg"), result.photoFileNames) // newest record first
        assertEquals(2, result.locations.size)
        assertEquals(2, result.locations.first().count)
    }

    @Test fun compute_weekRangeLeavesOlderRecordsOut() {
        val now = at(2026, 3, 15, 12)
        val records = listOf(rec(at(2026, 3, 14, 9)), rec(at(2026, 3, 8, 9)))
        val week = InsightsAggregator.compute(records, settings, now, Period.WEEK, now, zone)
        assertEquals(1, week.stats.mealCount)
        val month = InsightsAggregator.compute(records, settings, now, Period.MONTH, now, zone)
        assertEquals(2, month.stats.mealCount)
    }

    @Test fun compute_pastMonthIsNotTheCurrentPeriod() {
        val now = at(2026, 3, 15, 12)
        val result = InsightsAggregator.compute(emptyList(), settings, now, Period.MONTH, at(2026, 2, 1, 0), zone)
        assertFalse(result.isCurrentPeriod)
        assertEquals(28, result.calendarDays.size)
    }

    @Test fun compute_futureDatedRecordShowsOnTheCalendarButNotInTheNumbers() {
        val now = at(2026, 3, 15, 12)
        val records = listOf(rec(at(2026, 3, 20, 9)))
        val result = InsightsAggregator.compute(records, settings, now, Period.MONTH, now, zone)
        assertEquals(DayWindowState.WITHIN_WINDOW, result.calendarDays[19].state)
        assertEquals(0, result.stats.mealCount)
        assertEquals(0, result.bands[19].mealCount)
    }

    @Test fun compute_empty_returnsNoRecordsWithAFullCalendar() {
        val now = at(2026, 3, 15, 12)
        val result = InsightsAggregator.compute(emptyList(), settings, now, Period.MONTH, now, zone)
        assertFalse(result.hasAnyRecords)
        assertEquals(31, result.calendarDays.size)
        assertEquals(InsightsStats.EMPTY, result.stats)
        assertTrue(result.photoFileNames.isEmpty())
        assertTrue(result.locations.isEmpty())
        assertEquals(PeriodSummary.EMPTY, result.summary)
    }

    // ---- the map prefers venues, but never at the cost of a record ----

    @Test fun locations_namesVenuesAndKeepsVenuelessRecordsAsPoints() {
        val now = at(2026, 3, 15, 12)
        val venue = Venue(id = 1, name = "大戶屋", lat = 25.05, lng = 121.55, lastUsedAt = 1L)
        val days = byDay(
            rec(at(2026, 3, 10, 12), lat = 25.0, lng = 121.0).copy(venue = venue),
            rec(at(2026, 3, 11, 12), lat = 24.0, lng = 120.0), // no venue
        )
        val result = InsightsAggregator.compute(days.values.flatten(), settings, now, Period.MONTH, now, zone)

        val named = result.locations.single { it.name != null }
        assertEquals("大戶屋", named.name)
        // The marker sits at the VENUE's position, not at the record's.
        assertEquals(25.05, named.lat, 0.0001)
        assertEquals(121.55, named.lng, 0.0001)
        // The venue-less record is still on the map, as a plain point.
        assertEquals(1, result.locations.count { it.name == null })
    }

    @Test fun locations_groupOneVenueAcrossManyVisits() {
        val now = at(2026, 3, 15, 12)
        val venue = Venue(id = 1, name = "大戶屋", lat = 25.05, lng = 121.55, lastUsedAt = 1L)
        val days = byDay(
            rec(at(2026, 3, 10, 12), lat = 25.0, lng = 121.0).copy(venue = venue),
            rec(at(2026, 3, 12, 12), lat = 1.0, lng = 1.0).copy(venue = venue),
        )
        val result = InsightsAggregator.compute(days.values.flatten(), settings, now, Period.MONTH, now, zone)

        // One marker, counted twice — even though the two visits were recorded miles apart.
        assertEquals(1, result.locations.size)
        assertEquals(2, result.locations.single().count)
    }

    /** A venue that has never learned where it is cannot be drawn; the record's own point is used. */
    @Test fun locations_fallBackWhenTheVenueHasNoPosition() {
        val now = at(2026, 3, 15, 12)
        val venue = Venue(id = 1, name = "路邊攤", lat = null, lng = null, lastUsedAt = 1L)
        val days = byDay(rec(at(2026, 3, 10, 12), lat = 25.0, lng = 121.0).copy(venue = venue))
        val result = InsightsAggregator.compute(days.values.flatten(), settings, now, Period.MONTH, now, zone)

        assertEquals(1, result.locations.size)
        assertEquals(null, result.locations.single().name)
        assertEquals(25.0, result.locations.single().lat, 0.0001)
    }

    /** Someone who never names a venue sees exactly the map they always had: rounded, anonymous, nothing missing. */
    @Test fun locations_withoutAnyVenueAreUnchanged() {
        val now = at(2026, 3, 15, 12)
        val result = InsightsAggregator.compute(
            listOf(
                rec(at(2026, 3, 10, 12), lat = 25.0330, lng = 121.5654),
                rec(at(2026, 3, 11, 12), lat = 25.0331, lng = 121.5654), // same ~100 m cell
                rec(at(2026, 3, 12, 12), lat = 24.1477, lng = 120.6736),
                rec(at(2026, 3, 13, 12)), // no location at all: not drawable, as before
            ),
            settings, now, Period.MONTH, now, zone,
        )

        assertTrue(result.locations.all { it.name == null })
        assertEquals(listOf(2, 1), result.locations.map { it.count })
        assertEquals(25.033, result.locations.first().lat, 0.0001)
        assertEquals(121.565, result.locations.first().lng, 0.0001)
    }

    /** A venue that knows where it is is drawn even if the record itself carries no coordinates. */
    @Test fun locations_aVenueWithAPositionNeedsNoRecordCoordinates() {
        val now = at(2026, 3, 15, 12)
        val venue = Venue(id = 1, name = "大戶屋", lat = 25.05, lng = 121.55, lastUsedAt = 1L)
        val result = InsightsAggregator.compute(
            listOf(rec(at(2026, 3, 10, 12)).copy(venue = venue)),
            settings, now, Period.MONTH, now, zone,
        )

        assertEquals(listOf(LocationCount(25.05, 121.55, 1, "大戶屋")), result.locations)
    }

    /** Two different venues never merge into one marker, however close they are. */
    @Test fun locations_differentVenuesStaySeparate() {
        val now = at(2026, 3, 15, 12)
        val a = Venue(id = 1, name = "A", lat = 25.05, lng = 121.55, lastUsedAt = 1L)
        val b = Venue(id = 2, name = "B", lat = 25.05, lng = 121.55, lastUsedAt = 1L)
        val result = InsightsAggregator.compute(
            listOf(rec(at(2026, 3, 10, 12)).copy(venue = a), rec(at(2026, 3, 11, 12)).copy(venue = b)),
            settings, now, Period.MONTH, now, zone,
        )

        assertEquals(setOf("A", "B"), result.locations.map { it.name }.toSet())
    }

    @Test fun compute_lateNightMealIsOneDayEverywhere() {
        val now = at(2026, 3, 15, 12)
        val records = listOf(rec(at(2026, 3, 12, 20)), rec(at(2026, 3, 13, 0, 30)))
        val result = InsightsAggregator.compute(records, settings, now, Period.WEEK, now, zone)
        assertEquals(1, result.stats.daysWithRecords)
        assertEquals(1, result.summary.recordedDays)
        assertEquals(DayWindowState.WITHIN_WINDOW, result.calendarDays.single { it.dayOfMonth == 12 }.state)
        assertEquals(DayWindowState.NO_RECORD, result.calendarDays.single { it.dayOfMonth == 13 }.state)
        assertEquals(2, result.bands.single { it.dayOfMonth == 12 }.mealCount)
    }
}
