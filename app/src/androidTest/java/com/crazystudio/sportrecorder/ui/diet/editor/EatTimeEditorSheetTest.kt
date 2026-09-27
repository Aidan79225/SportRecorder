package com.crazystudio.sportrecorder.ui.diet.editor

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.crazystudio.sportrecorder.domain.model.EatPhoto
import com.crazystudio.sportrecorder.shared.resources.Res
import com.crazystudio.sportrecorder.shared.resources.diet_create_eating_date_title
import com.crazystudio.sportrecorder.shared.resources.diet_create_eating_time_title
import com.crazystudio.sportrecorder.shared.resources.diet_eat_clear_location
import com.crazystudio.sportrecorder.shared.resources.diet_eat_create
import com.crazystudio.sportrecorder.shared.resources.diet_eat_location_loading
import com.crazystudio.sportrecorder.shared.resources.diet_eat_location_none
import com.crazystudio.sportrecorder.shared.resources.diet_eat_note
import com.crazystudio.sportrecorder.shared.resources.diet_eat_remove_photo
import com.crazystudio.sportrecorder.shared.resources.photo_add
import com.crazystudio.sportrecorder.shared.resources.photo_select
import com.crazystudio.sportrecorder.ui.theme.SportRecorderTheme
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Renders [EatTimeEditorSheet] directly (no host screen) with `createComposeRule`, per the
 * pattern in `BackupScreenTest`. `setContent` may only be called once per test, so any case
 * that needs two distinct states (e.g. the location row's loading -> available transition)
 * is split across two tests instead of calling `show()` twice.
 */
@RunWith(AndroidJUnit4::class)
class EatTimeEditorSheetTest {
    @get:Rule val compose = createComposeRule()

    private var pickDate = 0
    private var pickTime = 0
    private var addPhoto = 0
    private var selectPhoto = 0
    private var confirm = 0
    private var clearLocation = 0
    private var recapture = 0
    private val notes = mutableListOf<String>()
    private val removedPending = mutableListOf<String>()
    private val removedExisting = mutableListOf<EatPhoto>()

    private fun str(res: StringResource, vararg args: Any) = runBlocking { getString(res, *args) }

    private fun show(state: EatTimeEditorUiState) {
        compose.setContent {
            SportRecorderTheme {
                EatTimeEditorSheet(
                    state = state,
                    photoModel = { null },
                    onPickDate = { pickDate++ },
                    onPickTime = { pickTime++ },
                    onNoteChange = { notes.add(it) },
                    onAddPhoto = { addPhoto++ },
                    onSelectPhoto = { selectPhoto++ },
                    onRemovePendingPhoto = { removedPending.add(it) },
                    onRemoveExistingPhoto = { removedExisting.add(it) },
                    onRecaptureLocation = { recapture++ },
                    onClearLocation = { clearLocation++ },
                    onConfirm = { confirm++ },
                )
            }
        }
    }

    // HeaderRow's action icon carries no text and the same empty contentDescription ("") as its
    // own leading icon, so it can't be found by text or content description alone. `HeaderRow`'s
    // `Row` itself sets no semantics config, so it is NOT a semantics node at all -- confirmed by
    // dumping `onRoot(useUnmergedTree = true).printToLog(...)`: every row's 4 children (leading
    // icon, title, content, action icon) are flattened as direct children of the sheet's
    // scrollable Column, alongside every other row. That ruled out the brief's suggested
    // `onNodeWithText(title).onParent().onChildren().onLast()` -- its "onParent()" resolves to
    // the whole Column, not the row, so "onLast()" always hit the CONFIRM button instead (it is
    // the Column's last child), leaving pickDate/addPhoto at 0 (see report for the RED run).
    // The action icons are the only nodes that are both clickable AND have contentDescription =
    // "" (the leading icons share the empty description but aren't clickable), so they're
    // selected by that filter and taken by fixed document order: date, time, take-photo,
    // choose-from-gallery.
    private fun headerRowActionIcons() =
        compose.onAllNodes(hasContentDescription("") and hasClickAction(), useUnmergedTree = true)

    @Test
    fun dateAndTimeRows_triggerPickers() {
        show(EatTimeEditorUiState(dateMillis = 1_700_000_000_000L))
        headerRowActionIcons()[0].performClick() // date row
        assertEquals(1, pickDate)
        headerRowActionIcons()[1].performClick() // time row
        assertEquals(1, pickTime)
    }

    @Test
    fun noteField_emitsChanges() {
        show(EatTimeEditorUiState())
        compose.onNodeWithText(str(Res.string.diet_eat_note)).performTextInput("ramen")
        assertEquals("ramen", notes.last())
    }

    @Test
    fun photoRows_triggerCaptureAndPick() {
        show(EatTimeEditorUiState())
        headerRowActionIcons()[2].performClick() // take-photo row
        assertEquals(1, addPhoto)
        headerRowActionIcons()[3].performClick() // choose-from-gallery row
        assertEquals(1, selectPhoto)
    }

    // Sheet source order: existingPhotos LazyRow (line ~198) renders before pendingPhotos
    // LazyRow (line ~214), so the remove icons come back existing-first, pending-second.
    @Test
    fun photoTiles_removeTheRightPhoto() {
        val existing = EatPhoto(7, "old.webp", 1L)
        show(EatTimeEditorUiState(existingPhotos = listOf(existing), pendingPhotos = listOf("new.webp")))
        compose.onNodeWithContentDescription("old.webp").assertIsDisplayed()
        compose.onNodeWithContentDescription("new.webp").assertIsDisplayed()
        val removes = compose.onAllNodesWithContentDescription(str(Res.string.diet_eat_remove_photo))
        removes[0].performClick()
        removes[1].performClick()
        assertEquals(listOf(existing), removedExisting)
        assertEquals(listOf("new.webp"), removedPending)
    }

    @Test
    fun locationRow_loading_showsLoadingCopy() {
        show(EatTimeEditorUiState(locationStatus = EatTimeEditorUiState.LocationStatus.LOADING))
        compose.onNodeWithText(str(Res.string.diet_eat_location_loading)).assertIsDisplayed()
    }

    // fmt5 (sheet, private) rounds to 5 decimals and joins as "lat, lng"; 25.03396 and
    // 121.56454 both round-trip exactly through that formatter (no rounding ambiguity).
    @Test
    fun locationRow_available_showsCoordinatesAndClears() {
        show(
            EatTimeEditorUiState(
                location = EatTimeEditorUiState.LatLng(25.03396, 121.56454),
                locationStatus = EatTimeEditorUiState.LocationStatus.AVAILABLE,
            ),
        )
        compose.onNodeWithText("25.03396, 121.56454", substring = true).assertIsDisplayed()
        compose.onNodeWithContentDescription(str(Res.string.diet_eat_clear_location)).performClick()
        assertEquals(1, clearLocation)
    }

    @Test
    fun noLocation_showsNoneCopy() {
        show(EatTimeEditorUiState(location = null, locationStatus = EatTimeEditorUiState.LocationStatus.IDLE))
        compose.onNodeWithText(str(Res.string.diet_eat_location_none)).assertIsDisplayed()
    }

    // The Button's onClick == onConfirm and Compose merges the child Text's semantics into
    // the Button node, so the button's own label text locates and clicks it directly; no
    // content description or position-based lookup (and no production testTag) is needed.
    @Test
    fun confirm_callsBack() {
        show(EatTimeEditorUiState())
        compose.onNodeWithText(str(Res.string.diet_eat_create)).performClick()
        assertEquals(1, confirm)
    }
}
