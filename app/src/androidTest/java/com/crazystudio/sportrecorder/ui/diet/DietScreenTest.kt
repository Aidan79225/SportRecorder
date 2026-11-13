package com.crazystudio.sportrecorder.ui.diet

import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.crazystudio.sportrecorder.ui.announcedAs
import com.crazystudio.sportrecorder.ui.isButton
import com.crazystudio.sportrecorder.ui.theme.SportRecorderTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

/**
 * What a screen reader makes of the Home screen's two controls that are not plain text: the "+" button,
 * which is only an icon, and the chip that opens the fasting-type picker.
 *
 * Every test starts in English; the `inTraditionalChinese_` one switches to zh-TW (before `setContent`, as
 * the strings are read when the screen first composes) and asserts hand-written literals, so a string that
 * was never translated can't pass by being compared with itself.
 */
@RunWith(AndroidJUnit4::class)
class DietScreenTest {
    @get:Rule val compose = createComposeRule()

    private lateinit var defaultLocale: Locale
    private var added = 0
    private var edited = 0

    @Before
    fun pinEnglish() {
        defaultLocale = Locale.getDefault()
        Locale.setDefault(Locale.US)
    }

    @After
    fun restoreLocale() {
        Locale.setDefault(defaultLocale)
    }

    private fun show() {
        compose.setContent {
            SportRecorderTheme {
                DietScreen(
                    state = DietUiState(fastingLabel = "16 : 8"),
                    onEditFastingType = { edited++ },
                    onAddEatTime = { added++ },
                    onOpenSettings = {},
                )
            }
        }
    }

    // The main way to record a meal is an icon, so its label is all a screen reader has to name it by.
    @Test
    fun theAddMealButton_isNamed_andAddsAMeal() {
        show()

        compose.onNodeWithContentDescription("Add meal").assertHasClickAction().performClick()

        assertEquals(1, added)
    }

    // The chip reads as its text ("16 : 8"); that it is a button, and that a tap opens the fasting-type
    // picker, is what the role and the click label add.
    @Test
    fun theFastingTypeChip_isAButton_thatSaysWhatATapDoes() {
        show()

        compose.onNodeWithText("16 : 8")
            .assertHasClickAction()
            .assert(isButton())
            .assert(announcedAs("Choose a fasting type"))
            .performClick()

        assertEquals(1, edited)
    }

    @Test
    fun inTraditionalChinese_theAddMealButtonAndTheChipAreNamed() {
        Locale.setDefault(Locale.TRADITIONAL_CHINESE)
        show()

        compose.onNodeWithContentDescription("新增一餐").assertHasClickAction()
        compose.onNodeWithText("16 : 8").assert(announcedAs("選擇斷食類型"))
    }
}
