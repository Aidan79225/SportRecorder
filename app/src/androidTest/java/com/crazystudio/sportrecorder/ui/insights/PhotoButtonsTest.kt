package com.crazystudio.sportrecorder.ui.insights

import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.crazystudio.sportrecorder.domain.insights.InsightsAggregator
import com.crazystudio.sportrecorder.domain.insights.InsightsResult
import com.crazystudio.sportrecorder.domain.insights.InsightsStats
import com.crazystudio.sportrecorder.domain.model.EatPhoto
import com.crazystudio.sportrecorder.domain.model.EatRecord
import com.crazystudio.sportrecorder.ui.announcedAs
import com.crazystudio.sportrecorder.ui.isButton
import com.crazystudio.sportrecorder.ui.theme.SportRecorderTheme
import kotlinx.datetime.TimeZone
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

/**
 * What a screen reader makes of the photos that open the viewer on the Insights tab: the photo wall and
 * the day sheet. Both used to be bare clickable images, so a screen reader had no name to give them and
 * did not say they were buttons.
 *
 * Every test starts in English; the `inTraditionalChinese_` ones switch to zh-TW (before `setContent`, as
 * the strings are read when the screen first composes) and assert hand-written literals, so a string that
 * was never translated can't pass by being compared with itself.
 */
@RunWith(AndroidJUnit4::class)
class PhotoButtonsTest {
    @get:Rule val compose = createComposeRule()

    private lateinit var defaultLocale: Locale
    private val opened = mutableListOf<Pair<List<String>, Int>>()

    @Before
    fun pinEnglish() {
        defaultLocale = Locale.getDefault()
        Locale.setDefault(Locale.US)
    }

    @After
    fun restoreLocale() {
        Locale.setDefault(defaultLocale)
    }

    private fun period(photoCount: Int) = List(photoCount) { "p$it.jpg" }

    private fun showWall(photoCount: Int) {
        compose.setContent {
            SportRecorderTheme {
                InsightsScreen(
                    modifier = Modifier.testTag(PAGE),
                    state = InsightsUiState(
                        isLoaded = true,
                        result = InsightsResult.EMPTY.copy(
                            hasAnyRecords = true,
                            stats = InsightsStats.EMPTY.copy(mealCount = photoCount, daysWithRecords = 1),
                            photoFileNames = period(photoCount),
                        ),
                    ),
                    onSelectPeriod = {},
                    onShiftPeriod = {},
                    onDayClick = {},
                    photoModel = { null },
                    onPhotoClick = { names, index -> opened += names to index },
                )
            }
        }
    }

    // The wall is below the fold, and a lazy item that was never composed has no node.
    private fun scrollToThePhotoNamed(label: String) {
        compose.onNodeWithTag(PAGE).performScrollToNode(hasContentDescription(label))
    }

    private fun meal(id: Int, vararg fileNames: String) = EatRecord(
        id = id,
        time = NOON + id * ONE_MINUTE,
        location = null,
        note = null,
        photos = fileNames.mapIndexed { index, name ->
            EatPhoto(id = id * 10 + index, fileName = name, createdAt = 0L)
        },
    )

    private fun showDay(records: List<EatRecord>) {
        compose.setContent {
            SportRecorderTheme {
                DayRecordsSheet(
                    dayStart = InsightsAggregator.dayStart(NOON, TimeZone.currentSystemDefault()),
                    records = records,
                    photoModel = { null },
                    onPhotoClick = { names, index -> opened += names to index },
                )
            }
        }
    }

    @Test
    fun aWallPhoto_isAButtonNamedForItsPlaceInThePeriod_thatOpensTheViewerAtIt() {
        showWall(photoCount = 5)
        scrollToThePhotoNamed("Photo 4 of 5")

        compose.onNodeWithContentDescription("Photo 4 of 5")
            .assertHasClickAction()
            .assert(isButton())
            .assert(announcedAs("See this photo"))
            .performClick()

        assertEquals(listOf(period(5) to 3), opened)
    }

    // The wall previews only the first rows of a long period, but the viewer pages through all of it, so a
    // photo's place is counted against the whole period: the number the footer gives as the period's photos.
    @Test
    fun aLongPeriodsPreview_countsPlacesAgainstTheWholePeriod() {
        showWall(photoCount = 40)
        scrollToThePhotoNamed("Photo 1 of 40")

        compose.onNodeWithContentDescription("Photo 1 of 40").assertHasClickAction().performClick()

        // The name and the viewer agree: it is handed all 40, not only the rows that were previewed.
        assertEquals(listOf(period(40) to 0), opened)
    }

    @Test
    fun aPhotoInTheDaySheet_isNamedForItsPlaceInItsMeal_andOpensThatMeal() {
        showDay(listOf(meal(1, "a.jpg", "b.jpg", "c.jpg"), meal(2, "d.jpg", "e.jpg")))

        compose.onNodeWithContentDescription("Photo 2 of 3")
            .assertHasClickAction()
            .assert(isButton())
            .assert(announcedAs("See this photo"))
            .performClick()
        // Each meal numbers its own photos, as the viewer opens on one meal's photos at a time.
        compose.onNodeWithContentDescription("Photo 1 of 2").assertHasClickAction().performClick()

        assertEquals(listOf(listOf("a.jpg", "b.jpg", "c.jpg") to 1, listOf("d.jpg", "e.jpg") to 0), opened)
    }

    @Test
    fun inTraditionalChinese_aWallPhotoIsNamedInChinese() {
        Locale.setDefault(Locale.TRADITIONAL_CHINESE)
        showWall(photoCount = 5)
        scrollToThePhotoNamed("第 4 張照片，共 5 張")

        compose.onNodeWithContentDescription("第 4 張照片，共 5 張").assert(announcedAs("看這張照片"))
    }

    @Test
    fun inTraditionalChinese_aDaySheetPhotoIsNamedInChinese() {
        Locale.setDefault(Locale.TRADITIONAL_CHINESE)
        showDay(listOf(meal(1, "a.jpg", "b.jpg", "c.jpg")))

        compose.onNodeWithContentDescription("第 2 張照片，共 3 張").assert(announcedAs("看這張照片"))
    }

    private companion object {
        const val PAGE = "insights-page"
        const val NOON = 1_700_000_000_000L
        const val ONE_MINUTE = 60_000L
    }
}
