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
import androidx.compose.ui.test.hasClickAction
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
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

/**
 * Renders [RecordScreen] directly with `createComposeRule`, handing it every record: the screen owns the
 * search text and does the filtering itself, so these tests drive it the way a user does — typing,
 * the ✕, tapping a card's venue, long-pressing to delete.
 *
 * The app speaks the device's language, which is the JVM default locale to Compose Resources. Every test
 * starts in English; the `inTraditionalChinese_` ones switch to zh-TW and assert hand-written literals, so
 * a string that was never translated can't pass by being compared with itself.
 */
@RunWith(AndroidJUnit4::class)
class RecordScreenTest {
    @get:Rule val compose = createComposeRule()

    private lateinit var defaultLocale: Locale

    @Before
    fun pinEnglish() {
        defaultLocale = Locale.getDefault()
        Locale.setDefault(Locale.US)
    }

    @After
    fun restoreLocale() {
        Locale.setDefault(defaultLocale)
    }

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

    private fun show(records: List<EatRecord>, onDelete: (EatRecord) -> Unit = {}) {
        compose.setContent {
            SportRecorderTheme {
                RecordScreen(
                    records = records,
                    onDelete = onDelete,
                    onEditRecord = {},
                    photoModel = { null },
                    onPhotoClick = { _, _ -> },
                )
            }
        }
    }

    /**
     * [show] in Traditional Chinese. The language is switched first because the screen reads its strings as
     * it first composes; [restoreLocale] puts the device's back afterwards.
     */
    private fun showInTraditionalChinese(records: List<EatRecord>, onDelete: (EatRecord) -> Unit = {}) {
        Locale.setDefault(Locale.TRADITIONAL_CHINESE)
        show(records, onDelete)
    }

    /** Long-presses the card that shows [text], which is how a record's delete dialog opens. */
    private fun longPress(text: String) {
        compose.onNodeWithText(text).performTouchInput { longClick() }
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

    /**
     * After process death the saved query comes back before the records load. A query over an empty list
     * has nothing to miss, so the screen must not flash (and announce) "no matching records".
     */
    @Test
    fun aQueryOnAnEmptyListDoesNotClaimNoMatch() {
        val records = mutableStateOf(listOf(record(1, venue(1, "Ramen Ya"))))
        compose.setContent {
            SportRecorderTheme {
                RecordScreen(
                    records = records.value,
                    onDelete = {},
                    onEditRecord = {},
                    photoModel = { null },
                    onPhotoClick = { _, _ -> },
                )
            }
        }
        searchField().performTextInput("sushi")
        compose.onNodeWithText(str(Res.string.record_search_empty)).assertIsDisplayed()

        compose.runOnIdle { records.value = emptyList() }
        searchField().assert(hasText("sushi"))
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

    @Test
    fun theEditButtonIsLabelledEdit() {
        show(listOf(record(1, note = "lunch")))
        compose.onNodeWithContentDescription("Edit").assertIsDisplayed()
    }

    @Test
    fun inTraditionalChinese_theEditButtonIsLabelledInChinese() {
        showInTraditionalChinese(listOf(record(1, note = "lunch")))
        compose.onNodeWithContentDescription("編輯").assertIsDisplayed()
    }

    @Test
    fun theDeleteDialogReadsInEnglish() {
        show(listOf(record(1, note = "lunch")))
        longPress("lunch")
        compose.onNodeWithText("Delete").assertIsDisplayed()
        compose.onNodeWithText("Do you want to delete this record?").assertIsDisplayed()
        compose.onNodeWithText("Yes").assertIsDisplayed()
        compose.onNodeWithText("No").assertIsDisplayed()
    }

    @Test
    fun inTraditionalChinese_theDeleteDialogReadsInChinese() {
        showInTraditionalChinese(listOf(record(1, note = "lunch")))
        longPress("lunch")
        // 「刪除」 is both the title and the confirm button; only the button has a click action.
        compose.onNode(hasText("刪除") and !hasClickAction()).assertIsDisplayed()
        compose.onNodeWithText("要刪除這筆紀錄嗎？").assertIsDisplayed()
        compose.onNode(hasText("刪除") and hasClickAction()).assertIsDisplayed()
        compose.onNodeWithText("取消").assertIsDisplayed()
    }

    /**
     * The translated buttons must keep their meaning: swapping the two Chinese labels would still show
     * every word of the dialog, yet make "取消" delete the record.
     */
    @Test
    fun inTraditionalChinese_cancelKeepsTheRecord_andDeleteDeletesIt() {
        val lunch = record(1, note = "lunch")
        val deleted = mutableListOf<EatRecord>()
        showInTraditionalChinese(listOf(lunch), onDelete = { deleted += it })

        longPress("lunch")
        compose.onNodeWithText("取消").performClick()
        compose.onAllNodesWithText("要刪除這筆紀錄嗎？").assertCountEquals(0)
        assertEquals(emptyList<EatRecord>(), deleted)

        longPress("lunch")
        compose.onNode(hasText("刪除") and hasClickAction()).performClick()
        assertEquals(listOf(lunch), deleted)
    }

    private companion object {
        const val RECORD_COUNT = 30

        /** Record id 27 — a "Ramen Ya" record, so it is still in the results after the search. */
        const val FAR_DOWN_INDEX = 26
    }
}
