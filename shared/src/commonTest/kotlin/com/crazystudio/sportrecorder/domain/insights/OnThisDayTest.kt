package com.crazystudio.sportrecorder.domain.insights

import com.crazystudio.sportrecorder.domain.model.DietSettings
import com.crazystudio.sportrecorder.domain.model.EatPhoto
import com.crazystudio.sportrecorder.domain.model.EatRecord
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

// Every case pins [zone], so results never depend on the machine's default zone.
class OnThisDayTest {
    private val zone = TimeZone.UTC
    private val settings = DietSettings(fastingHours = 16, eatingHours = 8)

    /** Epoch millis for a wall-clock moment in [zone]. `month` is 1-based. */
    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int = 0): Long =
        LocalDateTime(year, month, day, hour, minute).toInstant(zone).toEpochMilliseconds()

    private fun rec(time: Long, note: String? = null, photos: List<String> = emptyList()) =
        EatRecord(
            id = 0,
            time = time,
            location = null,
            note = note,
            photos = photos.mapIndexed { i, name -> EatPhoto(id = i, fileName = name, createdAt = time) },
        )

    private fun find(records: List<EatRecord>, now: Long, yearsAgo: Int = 1) =
        OnThisDay.find(records, settings, now, yearsAgo, zone)

    @Test
    fun findsLastYearsSameDay_withItsPhotoAndNote() {
        val records = listOf(rec(at(2025, 10, 4, 12), note = "和家人吃火鍋", photos = listOf("a.webp")))

        val memory = assertNotNull(find(records, now = at(2026, 10, 4, 9)))

        assertEquals(at(2025, 10, 4, 0), memory.dayStart)
        assertEquals("a.webp", memory.photoFileName)
        assertEquals("和家人吃火鍋", memory.note)
    }

    @Test
    fun isSilentWhenThatDayHoldsNothing() {
        // Records exist, just not on the anniversary — the card must not appear at all.
        val records = listOf(rec(at(2025, 10, 3, 12)), rec(at(2026, 10, 4, 12)))

        assertNull(find(records, now = at(2026, 10, 4, 9)))
    }

    @Test
    fun noPhotoOrNote_stillSurfacesTheDay() {
        val records = listOf(rec(at(2025, 10, 4, 12)))

        val memory = assertNotNull(find(records, now = at(2026, 10, 4, 9)))

        assertNull(memory.photoFileName)
        assertNull(memory.note)
    }

    @Test
    fun blankNoteCountsAsNoNote() {
        val records = listOf(rec(at(2025, 10, 4, 12), note = "   "))

        assertNull(assertNotNull(find(records, now = at(2026, 10, 4, 9))).note)
    }

    @Test
    fun pastMidnightMealBelongsToTheEatingDayItFollowed() {
        // 20:00 dinner then a 00:30 snack: one eating window, keyed to the 4th. The snack's photo
        // and note belong to the memory of the 4th, same as the sheet it opens.
        val records = listOf(
            rec(at(2025, 10, 4, 20)),
            rec(at(2025, 10, 5, 0, 30), note = "宵夜", photos = listOf("late.webp")),
        )

        val memory = assertNotNull(find(records, now = at(2026, 10, 4, 9)))

        assertEquals(at(2025, 10, 4, 0), memory.dayStart)
        assertEquals("late.webp", memory.photoFileName)
        assertEquals("宵夜", memory.note)
    }

    @Test
    fun february29ClampsToFebruary28() {
        // 2028 is a leap year, 2027 is not. The nearest real "same day" is the 28th.
        val records = listOf(rec(at(2027, 2, 28, 12), photos = listOf("leap.webp")))

        val memory = assertNotNull(find(records, now = at(2028, 2, 29, 9)))

        assertEquals(at(2027, 2, 28, 0), memory.dayStart)
    }

    @Test
    fun yearsAgoReachesFurtherBack() {
        val records = listOf(rec(at(2024, 10, 4, 12), photos = listOf("old.webp")))

        assertNull(find(records, now = at(2026, 10, 4, 9)))
        assertEquals(
            at(2024, 10, 4, 0),
            assertNotNull(find(records, now = at(2026, 10, 4, 9), yearsAgo = 2)).dayStart,
        )
    }

    @Test
    fun yearsAgoBelowOneIsNotAMemory() {
        val records = listOf(rec(at(2026, 10, 4, 12)))

        assertNull(find(records, now = at(2026, 10, 4, 15), yearsAgo = 0))
    }
}
