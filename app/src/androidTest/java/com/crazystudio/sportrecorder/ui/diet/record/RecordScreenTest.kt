package com.crazystudio.sportrecorder.ui.diet.record

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteractionCollection
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.crazystudio.sportrecorder.domain.model.EatRecord
import com.crazystudio.sportrecorder.domain.model.Venue
import com.crazystudio.sportrecorder.domain.venue.RecordVenueFilter
import com.crazystudio.sportrecorder.shared.resources.Res
import com.crazystudio.sportrecorder.shared.resources.record_search_clear
import com.crazystudio.sportrecorder.shared.resources.record_search_empty
import com.crazystudio.sportrecorder.shared.resources.record_venue_search
import com.crazystudio.sportrecorder.ui.theme.SportRecorderTheme
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Renders [RecordScreen] directly with `createComposeRule`. A small state-hoisting wrapper holds the
 * query and narrows the records with [RecordVenueFilter] — the ViewModel's job in production — so what
 * is under test is the screen's wiring: the venue lines on a card, the search field and its ✕, and
 * tapping a venue to search for it.
 */
@RunWith(AndroidJUnit4::class)
class RecordScreenTest {
    @get:Rule val compose = createComposeRule()

    private fun str(res: StringResource) = runBlocking { getString(res) }

    private fun venue(id: Int, name: String, address: String? = null) =
        Venue(id = id, name = name, lat = null, lng = null, lastUsedAt = 0L, address = address)

    private fun record(id: Int, venue: Venue? = null, note: String? = null) = EatRecord(
        id = id,
        time = 1_700_000_000_000L - id * 60_000L,
        location = null,
        note = note,
        photos = emptyList(),
        venue = venue,
    )

    private fun show(all: List<EatRecord>, showSearch: Boolean = true, initialQuery: String = "") {
        compose.setContent {
            SportRecorderTheme {
                var query by remember { mutableStateOf(initialQuery) }
                RecordScreen(
                    records = RecordVenueFilter.matching(all, query),
                    query = query,
                    showSearch = showSearch,
                    onQueryChange = { query = it },
                    onVenueClick = { query = RecordVenueFilter.queryFor(it) },
                    onDelete = {},
                    onEditRecord = {},
                    photoModel = { null },
                    onPhotoClick = { _, _ -> },
                )
            }
        }
    }

    /** The venue blocks: the only nodes whose click action carries the "show this place's records" label. */
    private fun venueBlocks(): SemanticsNodeInteractionCollection {
        val label = str(Res.string.record_venue_search)
        return compose.onAllNodes(
            SemanticsMatcher("onClickLabel = $label") { it.config.getOrNull(SemanticsActions.OnClick)?.label == label },
        )
    }

    private fun searchField() = compose.onNode(hasSetTextAction())

    @Test
    fun theSearchFieldShowsWhenToldTo_evenIfNoRecordHasAVenue() {
        show(listOf(record(1, note = "just a note")), showSearch = true, initialQuery = "x")
        searchField().assert(hasText("x"))
        compose.onNodeWithText(str(Res.string.record_search_empty)).assertIsDisplayed()
    }

    @Test
    fun aBlankAddressShowsNoAddressLine() {
        show(listOf(record(1, venue(1, "Ramen Ya", "  "))))
        compose.onNodeWithText("Ramen Ya").assertIsDisplayed()
        // The venue block merges its lines' text: only the name is there.
        venueBlocks().assertCountEquals(1).onFirst().assert(
            SemanticsMatcher("only the name") { it.config.getOrNull(SemanticsProperties.Text)?.size == 1 },
        )
    }

    @Test
    fun longPressingTheVenueStillOffersToDeleteTheRecord() {
        show(listOf(record(1, venue(1, "Ramen Ya", "No. 1, Noodle Road"))))
        compose.onNodeWithText("Ramen Ya", useUnmergedTree = true).performTouchInput { longClick() }
        compose.onNodeWithText("Do you want to delete this record?").assertIsDisplayed()
    }

    /**
     * The ViewModel answers each keystroke later, through a flow. Here the caller does not echo at all
     * until the test says so, then hands back a stale value — the first keystroke — after the second was
     * typed. The field owns its text, so it keeps what the user typed (and an IME's composition with it).
     */
    @Test
    fun aLaggingQueryNeverRewritesWhatTheUserTyped() {
        val query = mutableStateOf("")
        val sent = mutableListOf<String>()
        compose.setContent {
            SportRecorderTheme {
                RecordScreen(
                    records = listOf(record(1, venue(1, "Ramen Ya"))),
                    query = query.value,
                    showSearch = true,
                    onQueryChange = { sent += it },
                    onVenueClick = {},
                    onDelete = {},
                    onEditRecord = {},
                    photoModel = { null },
                    onPhotoClick = { _, _ -> },
                )
            }
        }
        searchField().performTextInput("a")
        searchField().performTextInput("b")
        assertEquals(listOf("a", "ab"), sent)

        compose.runOnIdle { query.value = "a" } // the stale echo of the first keystroke
        searchField().assert(hasText("ab"))
    }

    @Test
    fun aCardShowsItsVenueNameAndAddress() {
        show(listOf(record(1, venue(1, "Ramen Ya", "No. 1, Noodle Road"))))
        compose.onNodeWithText("Ramen Ya").assertIsDisplayed()
        compose.onNodeWithText("No. 1, Noodle Road").assertIsDisplayed()
        venueBlocks().assertCountEquals(1)
    }

    @Test
    fun aRecordWithoutAVenueShowsNoVenueLines() {
        show(listOf(record(1, note = "just a note")), showSearch = false)
        compose.onNodeWithText("just a note").assertIsDisplayed()
        venueBlocks().assertCountEquals(0)
    }

    @Test
    fun theSearchFieldIsHiddenWhenThereIsNothingToSearch() {
        show(listOf(record(1, note = "just a note")), showSearch = false)
        compose.onAllNodes(hasSetTextAction()).assertCountEquals(0)
    }

    @Test
    fun typingNarrowsTheList_andTheClearButtonShowsEverythingAgain() {
        show(listOf(record(1, venue(1, "Ramen Ya")), record(2, venue(2, "Burger Place"))))
        compose.onAllNodesWithContentDescription(str(Res.string.record_search_clear)).assertCountEquals(0)

        searchField().performTextInput("ramen")
        compose.onNodeWithText("Ramen Ya").assertIsDisplayed()
        compose.onAllNodesWithText("Burger Place").assertCountEquals(0)

        compose.onNodeWithContentDescription(str(Res.string.record_search_clear)).performClick()
        compose.onNodeWithText("Ramen Ya").assertIsDisplayed()
        compose.onNodeWithText("Burger Place").assertIsDisplayed()
        searchField().assert(hasText(""))
        compose.onAllNodesWithContentDescription(str(Res.string.record_search_clear)).assertCountEquals(0)
    }

    @Test
    fun tappingTheVenueFillsTheSearchWithItsNameAndAddress_andShowsOnlyThatVenue() {
        show(
            listOf(
                record(1, venue(1, "麥當勞", "台北市信義路1號")),
                record(2, venue(2, "麥當勞", "台北市中山路2號")),
            ),
        )
        venueBlocks().assertCountEquals(2)

        compose.onNodeWithText("台北市信義路1號").performClick()
        searchField().assert(hasText("麥當勞 台北市信義路1號"))
        compose.onNodeWithText("台北市信義路1號").assertIsDisplayed()
        compose.onAllNodesWithText("台北市中山路2號").assertCountEquals(0)
        venueBlocks().assertCountEquals(1)
    }

    @Test
    fun aSearchWithNoMatchSaysSo() {
        show(listOf(record(1, venue(1, "Ramen Ya"))))
        searchField().performTextInput("sushi")
        compose.onNodeWithText(str(Res.string.record_search_empty)).assertIsDisplayed()
        compose.onAllNodesWithText("Ramen Ya").assertCountEquals(0)
    }
}
