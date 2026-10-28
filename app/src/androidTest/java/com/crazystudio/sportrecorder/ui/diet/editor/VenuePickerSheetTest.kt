package com.crazystudio.sportrecorder.ui.diet.editor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.crazystudio.sportrecorder.domain.model.Venue
import com.crazystudio.sportrecorder.shared.resources.Res
import com.crazystudio.sportrecorder.shared.resources.editor_delete_body
import com.crazystudio.sportrecorder.shared.resources.editor_delete_title
import com.crazystudio.sportrecorder.shared.resources.editor_link_no_position
import com.crazystudio.sportrecorder.shared.resources.editor_link_no_position_unchanged
import com.crazystudio.sportrecorder.shared.resources.editor_venue_address
import com.crazystudio.sportrecorder.shared.resources.editor_venue_delete
import com.crazystudio.sportrecorder.shared.resources.editor_venue_edit
import com.crazystudio.sportrecorder.shared.resources.editor_venue_edit_named
import com.crazystudio.sportrecorder.shared.resources.editor_venue_merge_body
import com.crazystudio.sportrecorder.shared.resources.editor_venue_merge_keep
import com.crazystudio.sportrecorder.shared.resources.editor_venue_merge_where
import com.crazystudio.sportrecorder.shared.resources.editor_venue_name
import com.crazystudio.sportrecorder.shared.resources.editor_venue_position
import com.crazystudio.sportrecorder.shared.resources.editor_venue_records
import com.crazystudio.sportrecorder.shared.resources.editor_venue_resolving
import com.crazystudio.sportrecorder.shared.resources.editor_venue_same_name_title
import com.crazystudio.sportrecorder.shared.resources.editor_venue_save
import com.crazystudio.sportrecorder.shared.resources.editor_venue_use_link
import com.crazystudio.sportrecorder.shared.resources.venue_no_position
import com.crazystudio.sportrecorder.ui.theme.SportRecorderTheme
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.PluralStringResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getPluralString
import org.jetbrains.compose.resources.getString
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Renders [VenuePickerSheet] directly over a recording [VenuePickerActions]: the rows' address
 * lines, the ✎ edit dialog, the delete confirmation and the merge-or-keep question (#89).
 */
@RunWith(AndroidJUnit4::class)
class VenuePickerSheetTest {
    @get:Rule val compose = createComposeRule()

    private val actions = RecordingActions()

    private val placed = Venue(
        id = 1, name = "鼎泰豐 信義店", lat = 25.0335035, lng = 121.530118, lastUsedAt = 3L, address = "信義路二段 194 號",
    )
    private val unplaced = Venue(id = 2, name = "大戶屋", lat = null, lng = null, lastUsedAt = 2L)
    private val waiting = Venue(id = 3, name = "一蘭", lat = 25.0418, lng = 121.5503, lastUsedAt = 1L)

    private var state by mutableStateOf(EatTimeEditorUiState(venueOptions = listOf(placed, unplaced, waiting)))

    private fun str(res: StringResource, vararg args: Any) = runBlocking { getString(res, *args) }

    private fun plural(res: PluralStringResource, quantity: Int, vararg more: Any) =
        runBlocking { getPluralString(res, quantity, quantity, *more) }

    private fun show() {
        compose.setContent {
            SportRecorderTheme {
                VenuePickerSheet(state = state, actions = actions, onDismiss = {})
            }
        }
    }

    @Test
    fun aRowShowsTheAddress_orNoPosition() {
        show()

        compose.onNodeWithText(placed.name).assertIsDisplayed()
        compose.onNodeWithText("信義路二段 194 號").assertIsDisplayed()
        // Neither address nor position says so; one still waiting for its lookup shows its name alone.
        compose.onAllNodesWithText(str(Res.string.venue_no_position)).assertCountEquals(1)
        compose.onNodeWithText(unplaced.name).assertIsDisplayed()
        compose.onNodeWithText(waiting.name).assertIsDisplayed()
    }

    @Test
    fun thePencilOpensTheEditDialog() {
        actions.records = 12
        show()

        openEditDialog(placed)

        compose.onNodeWithText(str(Res.string.editor_venue_edit)).assertIsDisplayed()
        val position = str(Res.string.editor_venue_position, "25.0335", "121.5301")
        compose.onNodeWithText("$position · ${plural(Res.plurals.editor_venue_records, 12)}").assertIsDisplayed()
        // The drafts: a blank name cannot be saved, a real one is handed over with the address.
        nameField().assert(hasText(placed.name))
        nameField().performTextReplacement("  ")
        saveButton().assertIsNotEnabled()
        nameField().performTextReplacement("鼎泰豐 101 店")
        saveButton().performClick()

        assertEquals(listOf(Triple(placed, "鼎泰豐 101 店", "信義路二段 194 號")), actions.saved)
        compose.onAllNodesWithText(str(Res.string.editor_venue_edit)).assertCountEquals(0)
    }

    @Test
    fun deleteConfirmation_mentionsTheRecordCount() {
        show()
        openEditDialog(unplaced)
        compose.onNodeWithText(str(Res.string.editor_venue_delete)).performClick()
        assertEquals(listOf(unplaced), actions.deleteRequests)

        state = state.copy(pendingDelete = EatTimeEditorUiState.PendingDelete(unplaced, records = 12))
        compose.onNodeWithText(str(Res.string.editor_delete_title, unplaced.name)).assertIsDisplayed()
        compose.onNodeWithText(plural(Res.plurals.editor_delete_body, 12)).assertIsDisplayed()

        // With no record at it, only the question is asked.
        state = state.copy(pendingDelete = EatTimeEditorUiState.PendingDelete(unplaced, records = 0))
        compose.onNodeWithText(str(Res.string.editor_delete_title, unplaced.name)).assertIsDisplayed()
        compose.onAllNodesWithText(plural(Res.plurals.editor_delete_body, 0)).assertCountEquals(0)
    }

    @Test
    fun mergeOrKeep_namesTheOtherOne_andWhereItIs() {
        val pending = EatTimeEditorUiState.PendingMerge(
            from = unplaced,
            into = placed,
            movedRecords = 0,
            newName = placed.name,
            newAddress = null,
        )
        state = state.copy(pendingMerge = pending)
        show()

        compose.onNodeWithText(str(Res.string.editor_venue_same_name_title, placed.name)).assertIsDisplayed()
        compose.onNodeWithText(str(Res.string.editor_venue_merge_where, "信義路二段 194 號")).assertIsDisplayed()
        // No record would move: the count is not said.
        compose.onAllNodesWithText(plural(Res.plurals.editor_venue_merge_body, 0, placed.name)).assertCountEquals(0)
        compose.onNodeWithText(str(Res.string.editor_venue_merge_keep)).performClick()
        assertEquals(1, actions.keptBoth)

        state = state.copy(pendingMerge = pending.copy(movedRecords = 4))
        compose.onNodeWithText(plural(Res.plurals.editor_venue_merge_body, 4, placed.name)).assertIsDisplayed()
    }

    @Test
    fun updatingFromALink_fillsTheNameDraft() {
        // What the view model does once the link is read: offer its name to that venue's dialog.
        actions.onReposition = { venue ->
            state = state.copy(repositionDraft = EatTimeEditorUiState.RepositionDraft(venue.id, "鼎泰豐 101 店"))
        }
        show()
        openEditDialog(placed)

        tapUpdateFromLink()

        nameField().assert(hasText("鼎泰豐 101 店"))
        assertEquals(1, actions.draftsTaken)
        // Only 儲存 writes it; the address is the stored one (a move writes the link's own).
        saveButton().performClick()
        assertEquals(listOf(Triple(placed, "鼎泰豐 101 店", "信義路二段 194 號")), actions.saved)
    }

    @Test
    fun whileALinkIsRead_saveIsDisabled() {
        actions.onReposition = { state = state.copy(repositioning = true) }
        show()
        openEditDialog(placed)

        tapUpdateFromLink()

        // A save now would drop the move the link is about to make.
        compose.onNodeWithText(str(Res.string.editor_venue_resolving)).assertIsDisplayed()
        saveButton().assertIsNotEnabled()
        state = state.copy(repositioning = false)
        saveButton().assertIsEnabled()
    }

    @Test
    fun aLinkThatCantBeLocated_fillsNameAndAddress_andSaysSo() {
        val linkAddress = "臺北市大安區福住里信義路二段194號"
        actions.onReposition = { venue ->
            state = state.copy(
                repositionDraft = EatTimeEditorUiState.RepositionDraft(venue.id, "鼎泰豐 信義店", linkAddress),
                repositionNotice = RepositionNotice.NotLocated,
            )
        }
        show()
        openEditDialog(unplaced)

        tapUpdateFromLink()

        nameField().assert(hasText("鼎泰豐 信義店"))
        addressField().assert(hasText(linkAddress))
        compose.onNodeWithText(str(Res.string.editor_link_no_position)).assertIsDisplayed()
        saveButton().performClick()
        assertEquals(listOf(Triple(unplaced, "鼎泰豐 信義店", linkAddress)), actions.saved)
    }

    @Test
    fun aWholeTitleThatCantBeLocated_keepsTheDrafts_andSaysNothingChanged() {
        // What the view model does for a title the split left whole and the geocoder can't place.
        actions.onReposition = { state = state.copy(repositionNotice = RepositionNotice.NotLocatedUnchanged) }
        show()
        openEditDialog(placed)

        tapUpdateFromLink()

        compose.onNodeWithText(str(Res.string.editor_link_no_position_unchanged)).assertIsDisplayed()
        nameField().assert(hasText(placed.name))
        addressField().assert(hasText("信義路二段 194 號"))
        assertEquals(0, actions.draftsTaken)
    }

    /** Taps [venue]'s ✎, which opens its edit dialog. */
    private fun openEditDialog(venue: Venue) {
        compose.onNodeWithContentDescription(str(Res.string.editor_venue_edit_named, venue.name)).performClick()
    }

    /** Taps the open edit dialog's 「貼上地圖連結更新」. */
    private fun tapUpdateFromLink() {
        compose.onNodeWithText(str(Res.string.editor_venue_use_link)).performClick()
    }

    private fun saveButton() = compose.onNode(hasText(str(Res.string.editor_venue_save)) and hasClickAction())

    private fun nameField() = compose.onNode(hasText(str(Res.string.editor_venue_name)) and hasSetTextAction())

    private fun addressField() = compose.onNode(hasText(str(Res.string.editor_venue_address)) and hasSetTextAction())

    /**
     * Records what the picker asks for; answers [records] for every venue's record count. Updating
     * from a link runs [onReposition], which stands in for the view model reading it.
     *
     * Unlike the view model, [consumeRepositionDraft] only counts: `state.repositionDraft` stays set.
     * What stops the dialog taking that leftover draft twice is then its `LaunchedEffect(draft)` key,
     * not a cleared draft, so [draftsTaken] counts how often the dialog took it.
     */
    private class RecordingActions : VenuePickerActions {
        var records = 0
        val saved = mutableListOf<Triple<Venue, String, String?>>()
        val deleteRequests = mutableListOf<Venue>()
        var keptBoth = 0
        var onReposition: (Venue) -> Unit = {}
        var draftsTaken = 0

        override fun selectVenue(venue: Venue) = Unit
        override fun createVenue(name: String) = Unit
        override suspend fun pasteMapsLink() = false
        override fun pasteText(text: String) = Unit
        override fun dismissPasteDialog() = Unit
        override fun consumeMessage() = Unit
        override suspend fun recordCount(venue: Venue) = records
        override fun useThisPositionFor(venue: Venue) = Unit
        override suspend fun repositionFromClipboard(venue: Venue): Boolean {
            onReposition(venue)
            return true
        }
        override fun consumeRepositionDraft() {
            draftsTaken++
        }
        override fun closeVenueEdit() = Unit
        override fun confirmMove() = Unit
        override fun cancelMove() = Unit
        override fun saveVenue(venue: Venue, name: String, address: String?) {
            saved += Triple(venue, name, address)
        }
        override fun mergeVenues() = Unit
        override fun keepBothVenues() {
            keptBoth++
        }
        override fun cancelMerge() = Unit
        override fun requestDelete(venue: Venue) {
            deleteRequests += venue
        }
        override fun confirmDelete() = Unit
        override fun cancelDelete() = Unit
    }
}
