package com.crazystudio.sportrecorder.domain.venue

import com.crazystudio.sportrecorder.domain.model.Venue
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The picker's whole job is that the right venue is already near the top, so nobody types.
 * The note suggestion is a plain substring match against names the user created — it guesses
 * nothing, which is exactly why it is allowed to reorder the list.
 */
class VenuePickerTest {

    private fun venue(name: String, lastUsedAt: Long) =
        Venue(id = name.hashCode(), name = name, lat = null, lng = null, lastUsedAt = lastUsedAt)

    private val recent = venue("B", 20L)
    private val older = venue("A", 10L)
    private val oldest = venue("大戶屋", 1L)
    private val all = listOf(recent, older, oldest)

    @Test fun withoutANote_itIsPlainRecency() {
        assertEquals(listOf("B", "A", "大戶屋"), VenuePicker.order(all, note = null).map { it.name })
    }

    @Test fun aNoteNamingAVenueFloatsItToTheTop() {
        val ordered = VenuePicker.order(all, note = "今天在大戶屋吃的")

        assertEquals("大戶屋", ordered.first().name)
        // Everything else keeps its recency order behind it.
        assertEquals(listOf("大戶屋", "B", "A"), ordered.map { it.name })
    }

    @Test fun theMatchIgnoresCase() {
        val starbucks = venue("Starbucks", 1L)
        val ordered = VenuePicker.order(listOf(recent, starbucks), note = "met at STARBUCKS")

        assertEquals("Starbucks", ordered.first().name)
    }

    @Test fun aNoteNamingNothingChangesNothing() {
        assertEquals(
            listOf("B", "A", "大戶屋"),
            VenuePicker.order(all, note = "好吃").map { it.name },
        )
    }

    @Test fun theLongestMatchWinsWhenNamesOverlap() {
        // "大戶屋 信義店" contains "大戶屋"; the more specific one is the better suggestion.
        val broad = venue("大戶屋", 5L)
        val specific = venue("大戶屋 信義店", 1L)
        val ordered = VenuePicker.order(listOf(broad, specific), note = "大戶屋 信義店")

        assertEquals("大戶屋 信義店", ordered.first().name)
    }

    @Test fun emptyInEmptyOut() {
        assertEquals(emptyList(), VenuePicker.order(emptyList(), note = "大戶屋"))
    }
}
