package com.crazystudio.sportrecorder.ui.diet.create.fasting

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

/**
 * The create-fasting-type sheet: its two buttons in the language the app is in, and what a screen reader
 * makes of its hours rows and the pickers they open.
 *
 * The app speaks the device's language, which is the JVM default locale to Compose Resources. Every test
 * starts in English; the `inTraditionalChinese_` ones switch to zh-TW and assert hand-written literals, so
 * a string that was never translated can't pass by being compared with itself.
 */
@RunWith(AndroidJUnit4::class)
class CreateFastingTypeScreenTest {
    @get:Rule val compose = createComposeRule()

    private lateinit var defaultLocale: Locale
    private val confirmed = mutableListOf<Triple<String, String, String>>()

    @Before
    fun pinEnglish() {
        defaultLocale = Locale.getDefault()
        Locale.setDefault(Locale.US)
    }

    @After
    fun restoreLocale() {
        Locale.setDefault(defaultLocale)
    }

    /**
     * Shows the sheet in [locale], presses the buttons labelled [cancel] and [confirm] in that order, and
     * returns which actions ran. A swapped pair would still show both words, so what counts is that each
     * label sits on the button that does the thing. The language is switched first because the sheet reads
     * its strings as it first composes; [restoreLocale] puts the device's back afterwards.
     */
    private fun pressLabelled(locale: Locale, cancel: String, confirm: String): List<String> {
        Locale.setDefault(locale)
        val ran = mutableListOf<String>()
        compose.setContent {
            CreateFastingTypeScreen(
                onDismissRequest = { ran += "cancel" },
                onConfirmRequest = { _, _, _ -> ran += "confirm" },
            )
        }

        compose.onNodeWithText(cancel).performClick()
        compose.onNodeWithText(confirm).performClick()
        return ran
    }

    /** Shows the sheet in the current language, recording what it reports when confirmed. */
    private fun show() {
        compose.setContent {
            CreateFastingTypeScreen(
                onDismissRequest = {},
                onConfirmRequest = { name, fasting, eating -> confirmed += Triple(name, fasting, eating) },
            )
        }
    }

    // What a screen reader reads after a row ("Double tap to choose fasting hours").
    private fun announcedAs(label: String) = SemanticsMatcher("a tap is announced as \"$label\"") {
        it.config.getOrNull(SemanticsActions.OnClick)?.label == label
    }

    // The picker is a dialog of bare numbers, so its name is what tells a screen reader what it is.
    private fun pickerNamed(name: String) = SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, name)

    private fun inThePicker(value: String) = hasText(value) and hasAnyAncestor(isDialog())

    // Picker items carrying a selection state, whatever its value: the current hours, and nothing else.
    private fun pickerItemsWithASelectionState() =
        compose.onAllNodes(hasAnyAncestor(isDialog()) and SemanticsMatcher.keyIsDefined(SemanticsProperties.Selected))

    // The list is taller than its dialog, so the value is scrolled to before it is looked at or pressed.
    // Only the dialog's own list is meant: the sheet behind it may one day scroll too.
    private fun scrollThePickerTo(value: String) {
        compose.onNode(hasScrollAction() and hasAnyAncestor(isDialog())).performScrollToNode(hasText(value))
    }

    private fun chooseInThePicker(value: String) {
        scrollThePickerTo(value)
        compose.onNode(inThePicker(value)).performClick()
    }

    @Test
    fun inEnglish_eachButtonIsLabelledForWhatItDoes() {
        val ran = pressLabelled(Locale.US, cancel = "CANCEL", confirm = "OK")

        assertEquals(listOf("cancel", "confirm"), ran)
    }

    @Test
    fun inTraditionalChinese_eachButtonIsLabelledForWhatItDoes() {
        val ran = pressLabelled(Locale.TRADITIONAL_CHINESE, cancel = "取消", confirm = "新增")

        assertEquals(listOf("cancel", "confirm"), ran)
    }

    // What a screen reader announces: each hours row is ONE button, "title, value". The value used to be a
    // lone clickable Text beside a title that did nothing, so it was read as a bare "16".
    @Test
    fun hoursRows_areEachOneButtonReadingTitleThenValue() {
        show()
        listOf("Fasting hours" to "16", "Eating hours" to "8").forEach { (title, value) ->
            compose.onNode(hasText(title) and hasText(value))
                .assertHasClickAction()
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        }
    }

    @Test
    fun hoursRows_sayWhatATapDoes() {
        show()
        compose.onNodeWithText("Fasting hours").assert(announcedAs("Choose fasting hours"))
        compose.onNodeWithText("Eating hours").assert(announcedAs("Choose eating hours"))
    }

    // The title used to be dead: only the small value at the row's end answered a tap.
    @Test
    fun tappingARowsTitle_opensItsPicker() {
        show()
        compose.onNodeWithText("Fasting hours", useUnmergedTree = true).performClick()

        compose.onNode(isDialog()).assertExists()
    }

    // A description of "" still makes an image a stop for a screen reader, and says nothing. The two icons
    // beside each row are only decoration, which carries null instead.
    @Test
    fun noNodeIsLeftWithAnEmptyDescription() {
        show()
        compose.onAllNodes(hasContentDescription(""), useUnmergedTree = true).assertCountEquals(0)
    }

    // Each picker is named for its row, and only the hours the row shows say "selected": the rest say nothing
    // about it, as in the meal editor's duration menu. Pressing the current value closes the picker unchanged.
    @Test
    fun eachPicker_isNamed_andOnlyTheCurrentHoursAreSelected() {
        show()
        listOf("16" to "Choose fasting hours", "8" to "Choose eating hours").forEach { (current, name) ->
            compose.onNodeWithText(current).performClick()

            compose.onNode(pickerNamed(name)).assertExists()
            scrollThePickerTo(current)
            compose.onNode(inThePicker(current)).assertIsSelected()
            pickerItemsWithASelectionState().assertCountEquals(1)
            chooseInThePicker(current)
        }
    }

    // The pickers themselves are unchanged: what is chosen in them is what the sheet reports when confirmed.
    @Test
    fun choosingHoursInThePickers_isReportedOnConfirm() {
        show()
        compose.onNodeWithText("16").performClick()
        chooseInThePicker("20")
        compose.onNodeWithText("8").performClick()
        chooseInThePicker("6")
        compose.onNodeWithText("OK").performClick()

        assertEquals(listOf(Triple("", "20", "6")), confirmed)
    }

    @Test
    fun inTraditionalChinese_hoursRowsAndPickersAreNamed() {
        Locale.setDefault(Locale.TRADITIONAL_CHINESE)
        show()

        compose.onNodeWithText("斷食時數").assert(announcedAs("選擇斷食時數"))
        compose.onNodeWithText("進食時數").assert(announcedAs("選擇進食時數"))

        compose.onNodeWithText("斷食時數").performClick()
        compose.onNode(pickerNamed("選擇斷食時數")).assertExists()
    }
}
