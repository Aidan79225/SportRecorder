package com.crazystudio.sportrecorder.ui.insights

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.crazystudio.sportrecorder.domain.insights.InsightsResult
import com.crazystudio.sportrecorder.domain.insights.InsightsStats
import com.crazystudio.sportrecorder.domain.insights.OnThisDayMemory
import com.crazystudio.sportrecorder.domain.insights.TastyMemoryPick
import com.crazystudio.sportrecorder.domain.model.EatRecord
import com.crazystudio.sportrecorder.shared.resources.Res
import com.crazystudio.sportrecorder.shared.resources.insights_on_this_day_title
import com.crazystudio.sportrecorder.shared.resources.insights_on_this_day_title_years
import com.crazystudio.sportrecorder.shared.resources.insights_tasty_title
import com.crazystudio.sportrecorder.ui.theme.SportRecorderTheme
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The two memory cards above the period control: the years-ago title, the tasty card's body, and its tap. */
@RunWith(AndroidJUnit4::class)
class MemoryCardsTest {
    @get:Rule val compose = createComposeRule()

    private val tastyDay = 1_000L

    private fun onThisDay(yearsAgo: Int) =
        OnThisDayMemory(dayStart = 2_000L, yearsAgo = yearsAgo, photoFileName = null, note = null)

    private fun tasty(venue: String? = "阿財", dishes: List<String> = listOf("滷肉飯", "燙青菜"), note: String? = null) =
        TastyMemoryPick(
            meal = EatRecord(id = 1, time = tastyDay, location = null, note = note, photos = emptyList()),
            dayStart = tastyDay,
            photoFileName = null,
            venueName = venue,
            dishes = dishes,
            level = 5,
        )

    private fun show(
        onThisDay: OnThisDayMemory? = null,
        tastyMemory: TastyMemoryPick? = null,
        onDayClick: (Long) -> Unit = {},
    ) {
        compose.setContent {
            SportRecorderTheme {
                InsightsScreen(
                    state = InsightsUiState(
                        isLoaded = true,
                        result = InsightsResult.EMPTY.copy(
                            hasAnyRecords = true,
                            stats = InsightsStats.EMPTY.copy(mealCount = 1, daysWithRecords = 1),
                        ),
                        onThisDay = onThisDay,
                        tastyMemory = tastyMemory,
                    ),
                    onSelectPeriod = {},
                    onShiftPeriod = {},
                    onDayClick = onDayClick,
                    photoModel = { null },
                    onPhotoClick = { _, _ -> },
                )
            }
        }
    }

    private fun string(res: StringResource, vararg args: Any) = runBlocking { getString(res, *args) }

    @Test
    fun oneYearBack_usesTheASingularTitle() {
        show(onThisDay = onThisDay(yearsAgo = 1))

        compose.onNodeWithText(string(Res.string.insights_on_this_day_title)).assertIsDisplayed()
    }

    @Test
    fun severalYearsBack_namesHowMany() {
        show(onThisDay = onThisDay(yearsAgo = 3))

        compose.onNodeWithText(string(Res.string.insights_on_this_day_title_years, 3)).assertIsDisplayed()
    }

    @Test
    fun tastyCard_showsVenueAndDishes_andOpensItsDay() {
        var opened: Long? = null
        show(tastyMemory = tasty(), onDayClick = { opened = it })

        compose.onNodeWithText("阿財 · 滷肉飯 · 燙青菜").assertIsDisplayed()
        compose.onNodeWithText(string(Res.string.insights_tasty_title)).performClick()

        assertEquals(tastyDay, opened)
    }

    @Test
    fun tastyCard_fallsBackToTheNote() {
        show(tastyMemory = tasty(venue = null, dishes = emptyList(), note = "和家人吃火鍋"))

        compose.onNodeWithText("和家人吃火鍋").assertIsDisplayed()
    }

    @Test
    fun noTastyMemory_leavesNoCard() {
        show()

        compose.onNodeWithText(string(Res.string.insights_tasty_title)).assertDoesNotExist()
    }
}
