package com.crazystudio.sportrecorder.ui.insights

import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.crazystudio.sportrecorder.domain.insights.InsightsResult
import com.crazystudio.sportrecorder.domain.insights.InsightsStats
import com.crazystudio.sportrecorder.shared.resources.Res
import com.crazystudio.sportrecorder.shared.resources.insights_photo_count
import com.crazystudio.sportrecorder.shared.resources.insights_photos_collapse
import com.crazystudio.sportrecorder.shared.resources.insights_photos_show_all
import com.crazystudio.sportrecorder.ui.theme.SportRecorderTheme
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The photo wall's expand / collapse control. The wall shows a preview of the period and the user
 * can open it up to the whole period — Insights is the one place to look back at a period at once,
 * so nothing is permanently truncated. The row shaping itself is unit-tested in `:shared`
 * (`PhotoWallRowsTest`); this test covers the control the user actually touches.
 */
@RunWith(AndroidJUnit4::class)
class PhotoWallExpandTest {
    @get:Rule val compose = createComposeRule()

    private val PAGE = "insights-page"

    private fun stateWith(photoCount: Int) = InsightsUiState(
        isLoaded = true,
        result = InsightsResult.EMPTY.copy(
            hasAnyRecords = true,
            stats = InsightsStats.EMPTY.copy(mealCount = photoCount, daysWithRecords = 1),
            photoFileNames = List(photoCount) { "p$it.jpg" },
        ),
    )

    private fun show(photoCount: Int) {
        compose.setContent {
            SportRecorderTheme {
                InsightsScreen(
                    modifier = Modifier.testTag(PAGE),
                    state = stateWith(photoCount),
                    onSelectPeriod = {},
                    onShiftPeriod = {},
                    onDayClick = {},
                    photoModel = { null },
                    onPhotoClick = { _, _ -> },
                )
            }
        }
    }

    private fun string(res: StringResource, vararg args: Any) =
        runBlocking { getString(res, *args) }

    /** The wall's footer is below the fold, and a lazy item that was never composed has no node. */
    private fun scrollToFooter(photoCount: Int) {
        compose.onNodeWithTag(PAGE)
            .performScrollToNode(hasText(string(Res.string.insights_photo_count, photoCount)))
    }

    @Test
    fun aPeriodLongerThanThePreview_offersTheWholePeriod() {
        show(photoCount = 40)

        scrollToFooter(photoCount = 40)
        compose.onNodeWithText(string(Res.string.insights_photos_show_all, 40))
            .assertIsDisplayed()
            .performClick()

        // Expanding inserts the rest of the period's rows *above* the footer, so the control the
        // user just pressed is now further down the page — scroll back to it.
        scrollToFooter(photoCount = 40)
        compose.onNodeWithText(string(Res.string.insights_photos_collapse)).assertIsDisplayed()
    }

    @Test
    fun collapsingAgain_returnsToThePreview() {
        show(photoCount = 40)

        scrollToFooter(photoCount = 40)
        compose.onNodeWithText(string(Res.string.insights_photos_show_all, 40)).performClick()
        scrollToFooter(photoCount = 40)
        compose.onNodeWithText(string(Res.string.insights_photos_collapse)).performClick()

        scrollToFooter(photoCount = 40)
        compose.onNodeWithText(string(Res.string.insights_photos_show_all, 40)).assertIsDisplayed()
    }

    /** A period that fits in the preview has nothing to open, so the control stays away. */
    @Test
    fun aShortPeriod_hasNoControl() {
        show(photoCount = 5)

        scrollToFooter(photoCount = 5)
        compose.onNodeWithText(string(Res.string.insights_photos_show_all, 5)).assertDoesNotExist()
        compose.onNodeWithText(string(Res.string.insights_photos_collapse)).assertDoesNotExist()
    }
}
