package com.crazystudio.sportrecorder.domain.insights

import com.crazystudio.sportrecorder.domain.model.DietSettings
import com.crazystudio.sportrecorder.domain.model.EatPhoto
import com.crazystudio.sportrecorder.domain.model.EatRecord
import com.crazystudio.sportrecorder.domain.model.Venue
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TastyMemoryTest {
    private val zone = TimeZone.UTC
    private val settings = DietSettings(fastingHours = 16, eatingHours = 8)

    private fun at(year: Int, month: Int, day: Int, hour: Int = 12, minute: Int = 0): Long =
        LocalDateTime(year, month, day, hour, minute).toInstant(zone).toEpochMilliseconds()

    private val today = at(2026, 11, 30, 9)

    @Suppress("LongParameterList")
    private fun meal(
        id: Int,
        time: Long,
        rating: Int? = 5,
        note: String? = "好吃",
        dishes: List<String> = emptyList(),
        venue: String? = null,
        photo: String? = null,
    ) = EatRecord(
        id = id,
        time = time,
        location = null,
        note = note,
        photos = listOfNotNull(photo?.let { EatPhoto(1, it, time) }),
        venue = venue?.let { Venue(id = 1, name = it, lat = null, lng = null, lastUsedAt = time) },
        tasteRating = rating,
        dishes = dishes,
    )

    private fun pick(records: List<EatRecord>, now: Long = today) = TastyMemory.pick(records, settings, now, zone)

    @Test
    fun ratingFourQualifies_ratingThreeDoesNot() {
        assertNotNull(pick(listOf(meal(1, at(2026, 10, 1), rating = 4))))
        assertNull(pick(listOf(meal(1, at(2026, 10, 1), rating = 3))))
    }

    @Test
    fun unratedNeverSurfaces() {
        assertNull(pick(listOf(meal(1, at(2026, 10, 1), rating = null))))
    }

    @Test
    fun thirtyDaysBackQualifies_twentyNineDoesNot() {
        // Today is Nov 30: Oct 31 is 30 days back, Nov 1 is 29.
        assertNotNull(pick(listOf(meal(1, at(2026, 10, 31)))))
        assertNull(pick(listOf(meal(1, at(2026, 11, 1)))))
    }

    @Test
    fun aMealWithNothingToShowIsSkipped() {
        assertNull(pick(listOf(meal(1, at(2026, 10, 1), note = null))))
        assertNull(pick(listOf(meal(1, at(2026, 10, 1), note = "  "))))
        assertNotNull(pick(listOf(meal(1, at(2026, 10, 1), note = null, photo = "a.webp"))))
        assertNotNull(pick(listOf(meal(1, at(2026, 10, 1), note = null, dishes = listOf("滷肉飯")))))
        assertNotNull(pick(listOf(meal(1, at(2026, 10, 1), note = null, venue = "阿財"))))
    }

    @Test
    fun emptyHistoryIsSilent() {
        assertNull(pick(emptyList()))
    }

    @Test
    fun carriesWhatTheCardShows() {
        val records = listOf(
            meal(
                id = 7,
                time = at(2026, 10, 1),
                rating = 4,
                note = null,
                dishes = listOf("滷肉飯", "燙青菜"),
                venue = "阿財",
                photo = "a.webp",
            ),
        )

        val memory = assertNotNull(pick(records))

        assertEquals(at(2026, 10, 1, 0), memory.dayStart)
        assertEquals(7, memory.meal.id)
        assertEquals("a.webp", memory.photoFileName)
        assertEquals("阿財", memory.venueName)
        assertEquals(listOf("滷肉飯", "燙青菜"), memory.dishes)
        assertEquals(4, memory.level)
    }

    @Test
    fun sameDaySamePick_nextDayMayDiffer() {
        val records = (1..6).map { meal(it, at(2026, 9, it)) }

        val morning = assertNotNull(pick(records, at(2026, 11, 30, 8)))
        val evening = assertNotNull(pick(records, at(2026, 11, 30, 22)))
        val firstWeek = (0 until 6).map { assertNotNull(pick(records, at(2026, 11, 30, 8) + it * DAY_MILLIS)).meal.id }

        assertEquals(morning, evening)
        // Six candidates, six consecutive days: each is picked once.
        assertEquals(6, firstWeek.toSet().size)
        assertTrue(firstWeek.all { it in 1..6 })
    }

    @Test
    fun pastMidnightSnackIsDatedToTheEatingDayItFollowed() {
        // 20:00 dinner then a 00:30 snack: one window keyed to Oct 1, so the card opens Oct 1, not Oct 2.
        val records = listOf(
            meal(1, at(2026, 10, 1, 20), rating = null),
            meal(2, at(2026, 10, 2, 0, 30), rating = 5, note = "宵夜"),
        )

        val memory = assertNotNull(pick(records))

        assertEquals(2, memory.meal.id)
        assertEquals(at(2026, 10, 1, 0), memory.dayStart)
    }

    @Test
    fun snackAtTheGapEdgeFollowsItsDinnerDay() {
        // Dinner Oct 30 20:00, snack Oct 31 00:30: the snack's eating day is Oct 30. Today (Nov 29) it is 30 days
        // back by that day, though only 29 by the snack's own clock date.
        val records = listOf(
            meal(1, at(2026, 10, 30, 20), rating = null),
            meal(2, at(2026, 10, 31, 0, 30), rating = 5, note = "宵夜"),
        )

        assertEquals(at(2026, 10, 30, 0), assertNotNull(pick(records, at(2026, 11, 29, 9))).dayStart)
    }

    private companion object {
        const val DAY_MILLIS = 86_400_000L
    }
}
