package com.crazystudio.sportrecorder.ui.diet.select

import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.crazystudio.sportrecorder.ui.isButton
import com.crazystudio.sportrecorder.ui.theme.SportRecorderTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

/**
 * What a screen reader makes of the fasting-type picker's tiles: the "+" tile, an icon-only button whose
 * label is all there is to go on, and the tiles of the fasting types themselves.
 *
 * Every test starts in English; the `inTraditionalChinese_` one switches to zh-TW (before `setContent`, as
 * the strings are read when the screen first composes) and asserts a hand-written literal, so a string that
 * was never translated can't pass by being compared with itself.
 */
@RunWith(AndroidJUnit4::class)
class SelectFastingTypeScreenTest {
    @get:Rule val compose = createComposeRule()

    private lateinit var defaultLocale: Locale
    private var added = 0
    private val chosen = mutableListOf<Pair<Long, Long>>()

    @Before
    fun pinEnglish() {
        defaultLocale = Locale.getDefault()
        Locale.setDefault(Locale.US)
    }

    @After
    fun restoreLocale() {
        Locale.setDefault(defaultLocale)
    }

    // Two tiles are enough to have a custom one beside a built-in one, and keep the "+" tile on screen.
    private fun show() {
        compose.setContent {
            SportRecorderTheme {
                SelectFastingTypeScreen(
                    itemList = listOf(
                        FastingItem.defaultFastingItems.first(),
                        FastingItem.CustomFastingItem(fastingHours = 18, eatingHours = 6, name = "Brunch"),
                    ),
                    onAddClick = { added++ },
                    onItemClick = { fasting, eating -> chosen += fasting to eating },
                )
            }
        }
    }

    private fun pressTheAddTileNamed(label: String) {
        compose.onNodeWithContentDescription(label).assertHasClickAction().performClick()
        assertEquals("the \"$label\" button adds a fasting type", 1, added)
        assertTrue("and chooses no existing one", chosen.isEmpty())
    }

    @Test
    fun theAddTile_isANamedButton() {
        show()

        pressTheAddTileNamed("Add fasting type")
    }

    @Test
    fun inTraditionalChinese_theAddTileIsNamed() {
        Locale.setDefault(Locale.TRADITIONAL_CHINESE)
        show()

        pressTheAddTileNamed("新增斷食類型")
    }

    // A tile reads as its name and hours ("Trainee, 14 : 10"); the role is what says it is a button, as it
    // already does for the "+" tile beside them.
    @Test
    fun theFastingTypeTiles_areButtons_thatChooseTheirHours() {
        show()

        compose.onNodeWithText("Trainee").assertHasClickAction().assert(isButton()).performClick()
        compose.onNodeWithText("Brunch").assertHasClickAction().assert(isButton()).performClick()

        assertEquals(listOf(14L to 10L, 18L to 6L), chosen)
        assertEquals("and neither adds a fasting type", 0, added)
    }
}
