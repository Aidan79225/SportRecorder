package com.crazystudio.sportrecorder.domain.venue

import com.crazystudio.sportrecorder.domain.model.EatRecord
import com.crazystudio.sportrecorder.domain.model.Venue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class VenueSummaryTest {

    private val ramen = Venue(id = 1, name = "Ramen Ya", lat = null, lng = null, lastUsedAt = 0L, address = "Taipei")
    private val sushi = Venue(id = 2, name = "Sushi", lat = null, lng = null, lastUsedAt = 0L)

    private fun record(
        id: Int,
        venue: Venue? = ramen,
        time: Long = id.toLong(),
        dishes: List<String> = emptyList(),
        taste: Int? = null,
    ) = EatRecord(
        id = id, time = time, location = null, note = null, photos = emptyList(),
        venue = venue, tasteRating = taste, dishes = dishes,
    )

    @Test fun aBlankQuery_hasNoSummary() {
        val records = listOf(record(1), record(2))
        assertNull(VenueSummary.of(records, ""))
        assertNull(VenueSummary.of(records, "   "))
    }

    @Test fun oneVisit_hasNoSummary() {
        assertNull(VenueSummary.of(listOf(record(1)), "ramen"))
        assertNull(VenueSummary.of(emptyList(), "ramen"))
    }

    @Test fun mixedOrMissingVenues_haveNoSummary() {
        assertNull(VenueSummary.of(listOf(record(1), record(2, venue = sushi)), "ya"))
        assertNull(VenueSummary.of(listOf(record(1), record(2, venue = null)), "ya"))
        assertNull(VenueSummary.of(listOf(record(1, venue = null), record(2, venue = null)), "ya"))
    }

    @Test fun twoVisitsToOneVenue_giveVisitsAndNewestVisit() {
        val summary = assertNotNull(VenueSummary.of(listOf(record(1, time = 500), record(2, time = 900)), "ramen"))
        assertEquals(ramen, summary.venue)
        assertEquals(2, summary.visits)
        assertEquals(900L, summary.lastVisit)
    }

    @Test fun aDishSearch_summarisesTheWholePlace_notOnlyTheMatchingMeals() {
        val all = listOf(
            record(1, time = 100, dishes = listOf("Rice")),
            record(2, time = 200),
            record(3, time = 300, dishes = listOf("Rice")),
            record(4, time = 400),
        )
        val matched = all.filter { it.dishes.isNotEmpty() }
        val summary = assertNotNull(VenueSummary.of(matched, "rice", all))
        assertEquals(4, summary.visits)
        assertEquals(400L, summary.lastVisit)
    }

    @Test fun otherPlacesInTheDiary_doNotCountTowardThisOne() {
        val all = listOf(record(1), record(2), record(3, venue = sushi))
        assertEquals(2, assertNotNull(VenueSummary.of(all.take(2), "ramen", all)).visits)
    }

    @Test fun twoBranchesWithTheSameName_areNotOnePlace() {
        val other = ramen.copy(id = 9)
        assertNull(VenueSummary.of(listOf(record(1), record(2, venue = other)), "ramen"))
    }

    @Test fun dishesAreRankedByCount_thenByRecency_andCappedAtFive() {
        val records = listOf(
            record(1, time = 100, dishes = listOf("A", "B", "C", "D")),
            record(2, time = 200, dishes = listOf("A", "E", "F")),
            record(3, time = 300, dishes = listOf("A", "B", "G")),
        )
        val dishes = assertNotNull(VenueSummary.of(records, "x")).dishes
        // A x3, B x2, then the rest by how recently they were eaten: G (300), E and F (200, list order), ...
        assertEquals(5, dishes.size)
        assertEquals(listOf("A" to 3, "B" to 2), dishes.take(2))
        assertEquals("G", dishes[2].first)
        assertEquals(setOf("E", "F"), dishes.drop(3).map { it.first }.toSet())
    }

    @Test fun theSameDishInAnotherCase_isCountedOnce_withTheLatestSpelling() {
        val records = listOf(
            record(1, time = 100, dishes = listOf("beef noodle")),
            record(2, time = 200, dishes = listOf("Beef Noodle")),
        )
        assertEquals(listOf("Beef Noodle" to 2), assertNotNull(VenueSummary.of(records, "x")).dishes)
    }

    @Test fun noDishes_giveAnEmptyList() {
        assertEquals(emptyList(), assertNotNull(VenueSummary.of(listOf(record(1), record(2)), "x")).dishes)
    }

    @Test fun tasteCounts_runHighToLow_skipZeroLevels_andIgnoreUnrated() {
        val records = listOf(
            record(1, taste = 4), record(2, taste = 5), record(3, taste = 5), record(4, taste = null),
            record(5, taste = 1),
        )
        assertEquals(
            listOf(5 to 2, 4 to 1, 1 to 1),
            assertNotNull(VenueSummary.of(records, "x")).tasteCounts,
        )
    }

    @Test fun noRatings_giveNoTasteCounts() {
        assertEquals(emptyList(), assertNotNull(VenueSummary.of(listOf(record(1), record(2)), "x")).tasteCounts)
    }
}
