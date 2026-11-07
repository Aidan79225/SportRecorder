package com.crazystudio.sportrecorder.ui.diet.record

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteractionCollection
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.crazystudio.sportrecorder.domain.model.EatRecord
import com.crazystudio.sportrecorder.domain.model.Venue
import com.crazystudio.sportrecorder.shared.resources.Res
import com.crazystudio.sportrecorder.shared.resources.record_search_clear
import com.crazystudio.sportrecorder.shared.resources.record_search_empty
import com.crazystudio.sportrecorder.shared.resources.record_search_hint
import com.crazystudio.sportrecorder.shared.resources.record_venue_search
import com.crazystudio.sportrecorder.ui.theme.SportRecorderTheme
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Renders [RecordScreen] directly with `createComposeRule`, handing it every record: the screen owns the
 * search text and does the filtering itself, so these tests drive it the way a user does — typing,
 * the ✕, tapping a card's venue, long-pressing to delete.
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

    private fun show(records: List<EatRecord>) {
        compose.setContent {
            SportRecorderTheme {
                RecordScreen(
                    records = records,
                    onDelete = {},
                    onEditRecord = {},
                    photoModel = { null },
                    onPhotoClick = { _, _ -> },
                )
            }
        }
    }

    /** The venue blocks: buttons whose click action carries the "show this place's records" label. */
    private fun venueBlocks(): SemanticsNodeInteractionCollection {
        val label = str(Res.string.record_venue_search)
        return compose.onAllNodes(
            SemanticsMatcher("a Role.Button with onClickLabel = $label") {
                it.config.getOrNull(SemanticsActions.OnClick)?.label == label &&
                    it.config.getOrNull(SemanticsProperties.Role) == Role.Button
            },
        )
    }

    private fun searchField() = compose.onNode(hasSetTextAction())

    @Test
    fun aCardShowsItsVenueNameAndAddress() {
        show(listOf(record(1, venue(1, "Ramen Ya", "No. 1, Noodle Road"))))
        compose.onNodeWithText("Ramen Ya").assertIsDisplayed()
        compose.onNodeWithText("No. 1, Noodle Road").assertIsDisplayed()
        venueBlocks().assertCountEquals(1)
    }

    @Test
    fun aRecordWithoutAVenueShowsNoVenueLines() {
        show(listOf(record(1, note = "just a note")))
        compose.onNodeWithText("just a note").assertIsDisplayed()
        venueBlocks().assertCountEquals(0)
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
    fun theSearchFieldIsHiddenWhenNoRecordHasAVenue() {
        show(listOf(record(1, note = "just a note")))
        compose.onAllNodes(hasSetTextAction()).assertCountEquals(0)
    }

    @Test
    fun anEmptyDiaryDoesNotClaimThatNothingMatched() {
        show(emptyList())
        compose.onAllNodesWithText(str(Res.string.record_search_empty)).assertCountEquals(0)
    }

    @Test
    fun typingNarrowsTheList_andTheClearButtonShowsEverythingAgain() {
        show(listOf(record(1, venue(1, "Ramen Ya")), record(2, venue(2, "Burger Place"))))
        compose.onAllNodesWithContentDescription(str(Res.string.record_search_clear)).assertCountEquals(0)

        searchField().performTextInput("ramen")
        compose.onNodeWithText("Ramen Ya").assertIsDisplayed()
        compose.onAllNodesWithText("Burger Place").assertCountEquals(0)
        // The hint is a label, so it stays the field's accessible name while the field holds text.
        searchField().assert(hasText(str(Res.string.record_search_hint)))

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

    @Test
    fun longPressingTheVenueStillOffersToDeleteTheRecord() {
        show(listOf(record(1, venue(1, "Ramen Ya", "No. 1, Noodle Road"))))
        compose.onNodeWithText("Ramen Ya", useUnmergedTree = true).performTouchInput { longClick() }
        compose.onNodeWithText("Do you want to delete this record?").assertIsDisplayed()
    }

    @Test
    fun theSearchFieldStaysWhileAQueryIsActive_evenWhenTheLastVenueRecordIsDeleted() {
        compose.setContent {
            SportRecorderTheme {
                var records by remember {
                    mutableStateOf(listOf(record(1, venue(1, "Test Shop")), record(2, note = "at home")))
                }
                RecordScreen(
                    records = records,
                    onDelete = { deleted -> records = records - deleted },
                    onEditRecord = {},
                    photoModel = { null },
                    onPhotoClick = { _, _ -> },
                )
            }
        }
        searchField().performTextInput("shop")
        compose.onNodeWithText("Test Shop", useUnmergedTree = true).performTouchInput { longClick() }
        compose.onNodeWithText("Yes").performClick()

        searchField().assert(hasText("shop"))
        compose.onNodeWithText(str(Res.string.record_search_empty)).assertIsDisplayed()
    }

    /**
     * Thirty records, every third from "Ramen Ya". Scrolled far down the full list, a search for "ramen"
     * must open on its newest match rather than keep the old place (which LazyColumn would otherwise do by
     * key, since the card at the top is itself a match).
     */
    @Test
    fun aNewSearchStartsAtTheTopOfTheResults() {
        val ramen = venue(1, "Ramen Ya")
        val burger = venue(2, "Burger Place")
        show((1..RECORD_COUNT).map { id -> record(id, if (id % 3 == 0) ramen else burger, note = "note $id") })

        compose.onNode(hasScrollAction()).performScrollToIndex(FAR_DOWN_INDEX)
        compose.onAllNodesWithText("note 3").assertCountEquals(0)

        searchField().performTextInput("ramen")
        compose.onNodeWithText("note 3").assertIsDisplayed()
    }

    private companion object {
        const val RECORD_COUNT = 30

        /** Record id 27 — a "Ramen Ya" record, so it is still in the results after the search. */
        const val FAR_DOWN_INDEX = 26
    }
}
