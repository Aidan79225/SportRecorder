package com.crazystudio.sportrecorder.ui.diet.editor

import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.centerY
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isPopup
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.width
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.crazystudio.sportrecorder.domain.model.EatPhoto
import com.crazystudio.sportrecorder.shared.resources.Res
import com.crazystudio.sportrecorder.shared.resources.diet_create_eating_date_pick
import com.crazystudio.sportrecorder.shared.resources.diet_create_eating_date_title
import com.crazystudio.sportrecorder.shared.resources.diet_create_eating_time_pick
import com.crazystudio.sportrecorder.shared.resources.diet_create_eating_time_title
import com.crazystudio.sportrecorder.shared.resources.diet_eat_clear_location
import com.crazystudio.sportrecorder.shared.resources.diet_eat_create
import com.crazystudio.sportrecorder.shared.resources.diet_eat_duration_pick
import com.crazystudio.sportrecorder.shared.resources.diet_eat_duration_title
import com.crazystudio.sportrecorder.shared.resources.diet_eat_location_loading
import com.crazystudio.sportrecorder.shared.resources.diet_eat_location_none
import com.crazystudio.sportrecorder.shared.resources.diet_eat_note
import com.crazystudio.sportrecorder.shared.resources.diet_eat_recapture_location
import com.crazystudio.sportrecorder.shared.resources.diet_eat_remove_photo
import com.crazystudio.sportrecorder.shared.resources.duration_hours
import com.crazystudio.sportrecorder.shared.resources.duration_hours_minutes
import com.crazystudio.sportrecorder.shared.resources.duration_minutes
import com.crazystudio.sportrecorder.shared.resources.editor_dish_remove
import com.crazystudio.sportrecorder.shared.resources.editor_dish_suggested
import com.crazystudio.sportrecorder.shared.resources.editor_dishes
import com.crazystudio.sportrecorder.shared.resources.editor_taste
import com.crazystudio.sportrecorder.shared.resources.editor_taste_clear
import com.crazystudio.sportrecorder.shared.resources.photo_add
import com.crazystudio.sportrecorder.shared.resources.photo_select
import com.crazystudio.sportrecorder.shared.resources.taste_1
import com.crazystudio.sportrecorder.shared.resources.taste_2
import com.crazystudio.sportrecorder.shared.resources.taste_3
import com.crazystudio.sportrecorder.shared.resources.taste_4
import com.crazystudio.sportrecorder.shared.resources.taste_5
import com.crazystudio.sportrecorder.ui.theme.SportRecorderTheme
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

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
    private val durations = mutableListOf<Int>()
    private val ratings = mutableListOf<Int?>()
    private val dishDrafts = mutableListOf<String>()
    private var dishCommits = 0
    private val addedDishes = mutableListOf<String>()
    private val removedDishes = mutableListOf<String>()
    private val removedPending = mutableListOf<String>()
    private val removedExisting = mutableListOf<EatPhoto>()

    private fun str(res: StringResource, vararg args: Any) = runBlocking { getString(res, *args) }

    private fun show(state: EatTimeEditorUiState) = showSheet { state }

    // [state] is read inside the composition, so a test may hand it a mutable one and change it in place.
    // [around] wraps the sheet, for a test that needs a different density or font scale.
    private fun showSheet(
        modifier: Modifier = Modifier,
        around: @Composable (@Composable () -> Unit) -> Unit = { content -> content() },
        state: () -> EatTimeEditorUiState,
    ) {
        compose.setContent {
            SportRecorderTheme {
                around {
                    EatTimeEditorSheet(
                        state = state(),
                        modifier = modifier,
                        photoModel = { null },
                        onPickDate = { pickDate++ },
                        onPickTime = { pickTime++ },
                        onDurationChange = { durations.add(it) },
                        onTasteRatingChange = { ratings.add(it) },
                        onDishDraftChange = { dishDrafts.add(it) },
                        onCommitDish = { dishCommits++ },
                        onAddDish = { addedDishes.add(it) },
                        onRemoveDish = { removedDishes.add(it) },
                        onNoteChange = { notes.add(it) },
                        onAddPhoto = { addPhoto++ },
                        onSelectPhoto = { selectPhoto++ },
                        onRemovePendingPhoto = { removedPending.add(it) },
                        onRemoveExistingPhoto = { removedExisting.add(it) },
                        onRecaptureLocation = { recapture++ },
                        onClearLocation = { clearLocation++ },
                        onOpenVenuePicker = {},
                        onClearVenue = {},
                        onAnswerAnotherBranch = {},
                        onAnswerSameStoreMove = {},
                        onAnswerSameStoreKeep = {},
                        onConfirm = { confirm++ },
                    )
                }
            }
        }
    }

    // A header row is ONE node (clickable merges its icon, title and value), so it is found by its title.
    // That is the node a screen reader lands on, and tapping it taps the middle of the row.
    private fun row(title: StringResource) = compose.onNodeWithText(str(title))

    // The title's own Text, looked up below the merged row: tapping it is tapping the label, not the arrow.
    private fun label(title: StringResource) = compose.onNodeWithText(str(title), useUnmergedTree = true)

    // The row behind an open menu shows the current value too, so a bare text lookup would find two nodes.
    private fun menuItem(text: String) = compose.onNode(hasText(text) and hasAnyAncestor(isPopup()))

    // Menu items carrying a selection state, whatever its value: the current choice, and nothing else.
    private fun menuItemsWithASelectionState() =
        compose.onAllNodes(hasAnyAncestor(isPopup()) and SemanticsMatcher.keyIsDefined(SemanticsProperties.Selected))

    // Mirrors the sheet's private formatDate / formatTime: "yyyy/MM/dd" and "HH:mm" (zero-padded)
    // in the device's default time zone.
    private fun formatLocal(millis: Long, pattern: String): String =
        Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern(pattern))

    private fun expectedDate(millis: Long) = formatLocal(millis, "uuuu/MM/dd")

    private fun expectedTime(millis: Long) = formatLocal(millis, "HH:mm")

    @Test
    fun dateAndTimeRows_showFormattedDateMillis_andTriggerPickers() {
        val millis = 1_700_000_000_000L
        show(EatTimeEditorUiState(dateMillis = millis))
        compose.onNodeWithText(str(Res.string.diet_create_eating_date_title)).assertIsDisplayed()
        compose.onNodeWithText(expectedDate(millis)).assertIsDisplayed()
        compose.onNodeWithText(str(Res.string.diet_create_eating_time_title)).assertIsDisplayed()
        compose.onNodeWithText(expectedTime(millis)).assertIsDisplayed()

        row(Res.string.diet_create_eating_date_title).performClick()
        assertEquals(1, pickDate)
        row(Res.string.diet_create_eating_time_title).performClick()
        assertEquals(1, pickTime)
    }

    // The label used to be dead: only the small arrow at the row's end answered a tap.
    @Test
    fun tappingARowsLabelActs_notJustItsArrow() {
        show(EatTimeEditorUiState())
        label(Res.string.diet_create_eating_date_title).performClick()
        label(Res.string.diet_create_eating_time_title).performClick()
        label(Res.string.photo_add).performClick()
        label(Res.string.photo_select).performClick()
        assertEquals(listOf(1, 1, 1, 1), listOf(pickDate, pickTime, addPhoto, selectPhoto))
    }

    // What a screen reader announces: the row is one button, "title, value", that says what a tap does.
    @Test
    fun dateTimeAndDurationRows_areEachOneButtonReadingTitleThenValue() {
        val millis = 1_700_000_000_000L
        show(EatTimeEditorUiState(dateMillis = millis))
        listOf(
            Res.string.diet_create_eating_date_title to expectedDate(millis),
            Res.string.diet_create_eating_time_title to expectedTime(millis),
            Res.string.diet_eat_duration_title to str(Res.string.duration_hours, 1),
        ).forEach { (title, value) ->
            compose.onNode(hasText(str(title)) and hasText(value))
                .assertHasClickAction()
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        }
    }

    @Test
    fun dateTimeAndDurationRows_sayWhatATapDoes() {
        show(EatTimeEditorUiState())
        listOf(
            Res.string.diet_create_eating_date_title to Res.string.diet_create_eating_date_pick,
            Res.string.diet_create_eating_time_title to Res.string.diet_create_eating_time_pick,
            Res.string.diet_eat_duration_title to Res.string.diet_eat_duration_pick,
        ).forEach { (title, pick) ->
            val announced = str(pick)
            row(title).assert(
                SemanticsMatcher("a tap is announced as \"$announced\"") {
                    it.config.getOrNull(SemanticsActions.OnClick)?.label == announced
                },
            )
        }
    }

    // A description of "" still makes an image a stop for a screen reader, and says nothing: the arrows were
    // clickable images described that way (controls it can't name), the Location row's icon a silent stop.
    // An image that is only decoration carries null instead.
    @Test
    fun noNodeIsLeftWithAnEmptyDescription() {
        show(EatTimeEditorUiState())
        compose.onAllNodes(hasContentDescription(""), useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun theDurationRowReadsOneHourByDefault() {
        show(EatTimeEditorUiState())
        compose.onNodeWithText(str(Res.string.diet_eat_duration_title)).assertIsDisplayed()
        compose.onNodeWithText(str(Res.string.duration_hours, 1)).assertIsDisplayed()
    }

    @Test
    fun choosingNinetyMinutesFromTheDurationMenu_reportsIt() {
        show(EatTimeEditorUiState())

        row(Res.string.diet_eat_duration_title).performClick() // the row opens its menu
        compose.onNodeWithText(str(Res.string.duration_hours_minutes, 1, 30)).performClick()

        assertEquals(listOf(90), durations)
        // The menu closed with the choice: its items are gone again.
        compose.onNodeWithText(str(Res.string.duration_hours_minutes, 1, 30)).assertDoesNotExist()
    }

    @Test
    fun tappingTheDurationLabel_opensTheMenu() {
        show(EatTimeEditorUiState())
        label(Res.string.diet_eat_duration_title).performClick()
        compose.onNodeWithText(str(Res.string.duration_hours_minutes, 1, 30)).assertIsDisplayed()
    }

    // The arrow is no longer a node of its own; the spot where it sits is still part of the row.
    // (Left-to-right only: the arrow is at the row's right end, so the offset is measured from the right.)
    @Test
    fun tappingWhereTheArrowSits_stillOpensTheDurationMenu() {
        show(EatTimeEditorUiState())
        row(Res.string.diet_eat_duration_title).performTouchInput {
            click(Offset(width - ARROW_CENTER_FROM_END.toPx(), centerY))
        }
        compose.onNodeWithText(str(Res.string.duration_hours_minutes, 1, 30)).assertIsDisplayed()
    }

    // Expected in English: "1 hr 30 min" is selected, and it is the only item with a selection state at all.
    @Test
    fun theDurationMenuMarksTheCurrentChoiceAsSelected() {
        show(EatTimeEditorUiState(durationMinutes = 90))
        label(Res.string.diet_eat_duration_title).performClick()

        menuItem(str(Res.string.duration_hours_minutes, 1, 30)).assertIsSelected()
        menuItemsWithASelectionState().assertCountEquals(1)
    }

    // 75 is not on the menu, so nothing there is marked (the row itself still shows "1 hr 15 min").
    @Test
    fun anOffMenuDuration_marksNothingInTheMenu() {
        show(EatTimeEditorUiState(durationMinutes = 75))
        label(Res.string.diet_eat_duration_title).performClick()

        menuItem(str(Res.string.duration_hours_minutes, 1, 30)).assertIsDisplayed()
        menuItemsWithASelectionState().assertCountEquals(0)
    }

    // Expected in English: "1 hr 15 min" (zh-TW 「1 小時 15 分鐘」) — 75 is not on the menu, shown as stored.
    @Test
    fun anOffMenuDurationIsDisplayedAsStored() {
        show(EatTimeEditorUiState(durationMinutes = 75))
        compose.onNodeWithText(str(Res.string.duration_hours_minutes, 1, 15)).assertIsDisplayed()
    }

    // Expected in English: "15 min" (zh-TW 「15 分鐘」), never "0 hr 15 min".
    @Test
    fun aShortDurationReadsInMinutes() {
        show(EatTimeEditorUiState(durationMinutes = 15))
        compose.onNodeWithText(str(Res.string.duration_minutes, 15)).assertIsDisplayed()
        compose.onNodeWithText(str(Res.string.duration_hours_minutes, 0, 15)).assertDoesNotExist()
    }

    // ---- How the meal tasted: five faces, none chosen until the user taps one ----------------------------------

    private val tasteWords = listOf(
        Res.string.taste_1,
        Res.string.taste_2,
        Res.string.taste_3,
        Res.string.taste_4,
        Res.string.taste_5,
    )

    private fun word(level: Int) = str(tasteWords[level - 1])

    // A face is found by its word: that is its description for a screen reader, and all it exposes.
    private fun face(level: Int) = compose.onNode(hasContentDescription(word(level)))

    private val isRadioButton = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)

    @Test
    fun theTasteRowStartsEmpty_withFiveFacesAndNoneChosen() {
        show(EatTimeEditorUiState())

        compose.onNodeWithText(str(Res.string.editor_taste)).assertIsDisplayed()
        (1..5).forEach { level -> face(level).assertIsDisplayed().assertIsNotSelected() }
        // Nothing is said about being empty, and there is nothing to take back yet.
        (1..5).forEach { level -> compose.onNodeWithText(word(level)).assertDoesNotExist() }
        compose.onNodeWithContentDescription(str(Res.string.editor_taste_clear)).assertDoesNotExist()
    }

    @Test
    fun theFacesAreOneRadioGroup_eachDescribedByItsWord() {
        show(EatTimeEditorUiState())

        compose.onAllNodes(isRadioButton).assertCountEquals(5)
        compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.SelectableGroup)).assertCountEquals(1)
        (1..5).forEach { level -> face(level).assertHasClickAction().assert(isRadioButton) }
    }

    // The emoji is hidden from a screen reader (the word is its description), so no face is announced twice.
    @Test
    fun noEmojiIsAStopOfItsOwn() {
        show(EatTimeEditorUiState(tasteRating = 5))

        val anEmoji = SemanticsMatcher("text made of emoji") { node ->
            val texts = node.config.getOrNull(SemanticsProperties.Text).orEmpty()
            texts.any { text -> text.text.any(Character::isSurrogate) }
        }
        compose.onAllNodes(anEmoji, useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun tappingAFace_reportsItsLevel() {
        show(EatTimeEditorUiState())

        (1..5).forEach { level -> face(level).performClick() }

        assertEquals(listOf<Int?>(1, 2, 3, 4, 5), ratings)
    }

    // The ✕ is the only way to take a rating back. A face that is already chosen is not a toggle: tapping it
    // again chooses it again ("tap again to clear" would also have no name for a screen reader).
    @Test
    fun tappingTheChosenFace_keepsIt_onlyTheClearButtonTakesItBack() {
        show(EatTimeEditorUiState(tasteRating = 4))

        face(4).performClick()

        assertEquals(listOf<Int?>(4), ratings)
    }

    @Test
    fun theChosenFaceIsSelected_andItsWordSitsBesideTheLabel() {
        show(EatTimeEditorUiState(tasteRating = 4))

        face(4).assertIsSelected()
        listOf(1, 2, 3, 5).forEach { level -> face(level).assertIsNotSelected() }
        // The label and the word are one stop, "Taste, Tasty", like the other rows' "title, value".
        compose.onNode(hasText(str(Res.string.editor_taste)) and hasText(word(4))).assertIsDisplayed()
    }

    @Test
    fun theClearButtonTakesTheRatingBack() {
        show(EatTimeEditorUiState(tasteRating = 2))

        compose.onNodeWithContentDescription(str(Res.string.editor_taste_clear)).performClick()

        assertEquals(listOf<Int?>(null), ratings)
    }

    @Test
    fun everyFaceIsAtLeastAFingerTipWide() {
        show(EatTimeEditorUiState())

        (1..5).forEach { level -> face(level).assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp) }
    }

    // Choosing a face puts a word on the label's line and a clear button at the end of the faces' line. Neither
    // changes a height or moves a face, so the faces and everything under them stay where the finger just was.
    @Test
    fun choosingAFace_movesNothing() {
        val state = mutableStateOf(EatTimeEditorUiState())
        showSheet { state.value }
        fun noteTop() = compose.onNodeWithText(str(Res.string.diet_eat_note)).getUnclippedBoundsInRoot().top.value
        fun faceBounds() = face(3).getUnclippedBoundsInRoot()
        val (noteBefore, faceBefore) = noteTop() to faceBounds()

        state.value = EatTimeEditorUiState(tasteRating = 3)
        compose.waitForIdle()

        assertEquals(noteBefore, noteTop(), 0.01f)
        assertEquals(faceBefore.top.value, faceBounds().top.value, 0.01f)
        assertEquals(faceBefore.left.value, faceBounds().left.value, 0.01f)
        assertEquals(faceBefore.right.value, faceBounds().right.value, 0.01f)
    }

    /** The editor [width] wide at twice the normal font, with a face chosen so the word and the ✕ are showing. */
    private fun showNarrow(width: Dp) = showSheet(
        modifier = Modifier.width(width),
        around = { content ->
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale = 2f)) {
                content()
            }
        },
    ) { EatTimeEditorUiState(tasteRating = 5) }

    private fun clearButton() = compose.onNodeWithContentDescription(str(Res.string.editor_taste_clear))

    // JUnit's assertTrue, not Kotlin's assert(): on Android assertions are off, so assert() checks nothing.
    private fun assertWithin(node: SemanticsNodeInteraction, width: Dp, what: String) {
        val bounds = node.getUnclippedBoundsInRoot()
        assertTrue("$what runs past the right edge: ${bounds.right} > $width", bounds.right <= width + 0.5.dp)
        assertTrue("$what starts left of the sheet: ${bounds.left}", bounds.left >= (-0.5).dp)
    }

    // An icon button is drawn 40 dp wide but takes touches over 48 dp, and the second is what a finger meets.
    private fun touchWidth(node: SemanticsNodeInteraction): Float =
        with(compose.density) { node.fetchSemanticsNode().touchBoundsInRoot.width.toDp().value }

    // A 360 dp phone is the smallest in common use, at the largest font setting: every face keeps a fingertip's
    // width and all five stay on the sheet, and the clear button's touch area is a full 48 dp.
    @Test
    fun onASmallPhoneAtALargeFont_everyFaceKeepsItsFingerTip() {
        showNarrow(360.dp)

        (1..5).forEach { level ->
            face(level).assertIsDisplayed().assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
            assertWithin(face(level), 360.dp, "face $level")
        }
        clearButton().assertIsDisplayed()
        assertTrue("the clear button's touch area is under 48 dp", touchWidth(clearButton()) >= 47.5f)
        assertWithin(clearButton(), 360.dp, "the clear button")
    }

    // 320 dp is the narrowest phone there is: the faces share what the clear button leaves and shrink a little,
    // yet none runs off the sheet and the clear button is not pushed out of reach.
    @Test
    fun onTheNarrowestPhone_theFacesShrinkALittle_butAllStayOnTheSheet() {
        showNarrow(320.dp)

        (1..5).forEach { level ->
            face(level).assertIsDisplayed().assertWidthIsAtLeast(44.dp)
            assertWithin(face(level), 320.dp, "face $level")
        }
        clearButton().assertIsDisplayed()
        assertWithin(clearButton(), 320.dp, "the clear button")
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
        compose.onNodeWithText(str(Res.string.photo_add)).assertIsDisplayed()
        compose.onNodeWithText(str(Res.string.photo_select)).assertIsDisplayed()
        row(Res.string.photo_add).performClick()
        assertEquals(1, addPhoto)
        row(Res.string.photo_select).performClick()
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
    fun recaptureLocation_callsBack() {
        show(
            EatTimeEditorUiState(
                location = EatTimeEditorUiState.LatLng(25.03396, 121.56454),
                locationStatus = EatTimeEditorUiState.LocationStatus.AVAILABLE,
            ),
        )
        compose.onNodeWithContentDescription(str(Res.string.diet_eat_recapture_location)).performClick()
        assertEquals(1, recapture)
        assertEquals(0, clearLocation)
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

    // ---- What the meal had: a field, removable chips, suggestions -------------------------------------------------

    @Test
    fun theDishesRowStartsAsJustTheField_nothingElseShown() {
        show(EatTimeEditorUiState())

        compose.onNodeWithText(str(Res.string.editor_dishes)).assertIsDisplayed()
        compose.onAllNodes(hasContentDescription(str(Res.string.editor_dish_remove, ""), substring = true))
            .assertCountEquals(0)
    }

    @Test
    fun typingInTheField_reportsTheText_andDoneAddsTheDish() {
        show(EatTimeEditorUiState())
        val field = compose.onNodeWithText(str(Res.string.editor_dishes))

        field.performTextInput("滷肉飯")
        field.performImeAction()

        assertEquals(listOf("滷肉飯"), dishDrafts)
        assertEquals(1, dishCommits)
    }

    @Test
    fun aChosenDish_isAChipWhoseTapRemovesIt_describedAsRemoving() {
        show(EatTimeEditorUiState(dishes = listOf("滷肉飯", "Soup")))

        compose.onNodeWithContentDescription(str(Res.string.editor_dish_remove, "Soup")).performClick()

        assertEquals(listOf("Soup"), removedDishes)
        compose.onNodeWithContentDescription(str(Res.string.editor_dish_remove, "滷肉飯")).assertIsDisplayed()
    }

    @Test
    fun aSuggestion_isOneTapToAdd() {
        show(EatTimeEditorUiState(dishSuggestions = listOf("Tea", "Rice")))

        compose.onNodeWithContentDescription(str(Res.string.editor_dish_suggested, "Rice")).performClick()

        assertEquals(listOf("Rice"), addedDishes)
    }

    @Test
    fun withTenDishes_theFieldIsFull() {
        show(EatTimeEditorUiState(dishes = (1..10).map { "dish $it" }))

        compose.onNodeWithText(str(Res.string.editor_dishes))
            .assertIsNotEnabled()
    }

    private companion object {
        /** The arrow's cell at a row's trailing end is 56 dp wide (36 dp icon + 10 dp padding each side). */
        val ARROW_CENTER_FROM_END = 28.dp
    }
}
