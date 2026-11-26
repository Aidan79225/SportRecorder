package com.crazystudio.sportrecorder.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** What a meal had on the plate: free text, kept clean, never judged. */
class DishesTest {

    @Test fun aMealHasNoDishesUnlessTheUserSaysSo() {
        assertEquals(emptyList(), EatRecord(0, 0L, null, null, emptyList()).dishes)
    }

    @Test fun normalizeTrimsAndCollapsesEveryKindOfWhitespace() {
        assertEquals("滷肉飯", Dishes.normalize("  滷肉飯  "))
        assertEquals("a b c", Dishes.normalize("a \t b\n\nc"))
        assertEquals("a b", Dishes.normalize("a　　b"))
        assertEquals("a b", Dishes.normalize("　a\r\nb　"))
    }

    @Test fun normalizeDropsOtherControlCharacters() {
        assertEquals("ab", Dishes.normalize("a\u0000\u0007b"))
        assertEquals("ab", Dishes.normalize("a\u007Fb"))
    }

    @Test fun normalizeCutsToThirtyCharacters() {
        val cut = Dishes.normalize("x".repeat(45))
        assertEquals("x".repeat(Dishes.MAX_LENGTH), cut)
        assertEquals(30, Dishes.MAX_LENGTH)
    }

    @Test fun normalizeTrimsAgainAfterTheCut() {
        // The 30th character is a space: the cut must not leave it trailing.
        val name = "x".repeat(29) + " yyy"
        assertEquals("x".repeat(29), Dishes.normalize(name))
    }

    @Test fun aBlankNameIsNoDish() {
        listOf("", "   ", "\n\t", "　", "\u0000").forEach { assertNull(Dishes.normalize(it), "[$it]") }
    }

    @Test fun sanitizeNormalizesDropsBlanksAndKeepsOrder() {
        assertEquals(listOf("b", "a", "c d"), Dishes.sanitize(listOf(" b ", "", "a", "  ", "c\nd")))
    }

    @Test fun sanitizeDropsDuplicatesCaseInsensitively_firstSpellingWins() {
        assertEquals(listOf("Latte", "拿鐵"), Dishes.sanitize(listOf("Latte", "latte", "LATTE ", "拿鐵", "拿鐵")))
    }

    @Test fun sanitizeKeepsTheFirstTen() {
        val many = (1..15).map { "dish$it" }
        assertEquals(many.take(10), Dishes.sanitize(many))
        assertEquals(10, Dishes.MAX_PER_MEAL)
    }

    @Test fun sanitizeCountsOnlyWhatSurvives() {
        // Blanks and repeats do not use up the ten slots.
        val input = List(5) { "" } + List(5) { "same" } + (1..10).map { "d$it" }
        assertEquals(listOf("same") + (1..9).map { "d$it" }, Dishes.sanitize(input))
    }

    @Test fun encodeIsOneDishPerLine_andNullWhenThereIsNothing() {
        assertEquals("a\nb", Dishes.encode(listOf("a", "b")))
        assertNull(Dishes.encode(emptyList()))
        assertNull(Dishes.encode(listOf("  ", "")))
    }

    @Test fun encodeSanitizesSoNoNewlineSurvivesInsideADish() {
        assertEquals("a b\nc", Dishes.encode(listOf("a\nb", "c")))
    }

    @Test fun decodeReadsWhatEncodeWrote() {
        val dishes = listOf("滷肉飯", "Soup", "a b")
        assertEquals(dishes, Dishes.decode(Dishes.encode(dishes)))
    }

    @Test fun decodeOfNothingIsEmpty() {
        assertEquals(emptyList(), Dishes.decode(null))
        assertEquals(emptyList(), Dishes.decode(""))
        assertEquals(emptyList(), Dishes.decode("\n \n"))
    }

    private fun meal(time: Long, vararg dishes: String) =
        EatRecord(0, time, null, null, emptyList(), dishes = dishes.toList())

    @Test fun suggestionsAreMostUsedFirst_thenMostRecent() {
        val records = listOf(
            meal(1, "soup", "rice"),
            meal(2, "rice", "tea"),
            meal(3, "Rice", "soup", "cake"),
            meal(4, "tea"),
        )
        // rice x3, soup x2, tea x2 (tea is newer), cake x1.
        assertEquals(listOf("rice", "tea", "soup", "cake"), Dishes.suggestions(records, "", emptyList()))
    }

    @Test fun suggestionsLeaveOutChosenAndFilterByWhatIsTyped() {
        val records = listOf(meal(1, "Latte", "latte art cake", "tea"))
        assertEquals(listOf("Latte", "latte art cake"), Dishes.suggestions(records, " LAT ", emptyList()))
        assertEquals(listOf("latte art cake"), Dishes.suggestions(records, "lat", listOf("latte")))
        assertEquals(emptyList(), Dishes.suggestions(records, "zzz", emptyList()))
    }

    @Test fun suggestionsAreCappedAtTheLimit() {
        val records = listOf(meal(1, *(1..9).map { "d$it" }.toTypedArray()))
        assertEquals(6, Dishes.suggestions(records, "", emptyList()).size)
        assertEquals(2, Dishes.suggestions(records, "", emptyList(), limit = 2).size)
        assertEquals(emptyList(), Dishes.suggestions(emptyList(), "", emptyList()))
    }

    @Test fun decodeSanitizesAHandEditedOrFutureValue() {
        val stored = "  a  \na\n\n" + (1..12).joinToString("\n") { "d$it" }
        assertEquals(listOf("a") + (1..9).map { "d$it" }, Dishes.decode(stored))
    }
}
