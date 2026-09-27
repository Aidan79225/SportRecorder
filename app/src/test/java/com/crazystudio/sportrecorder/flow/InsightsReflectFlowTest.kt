package com.crazystudio.sportrecorder.flow

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.crazystudio.sportrecorder.domain.insights.DayWindowState
import com.crazystudio.sportrecorder.domain.insights.Period
import com.crazystudio.sportrecorder.domain.model.EatRecord
import com.crazystudio.sportrecorder.domain.usecase.ObserveEatRecordsUseCase
import com.crazystudio.sportrecorder.domain.usecase.SaveEatRecordUseCase
import com.crazystudio.sportrecorder.fake.FakeDietSettingsRepository
import com.crazystudio.sportrecorder.fake.FakeEatRecordRepository
import com.crazystudio.sportrecorder.fake.FakePhotoImageSource
import com.crazystudio.sportrecorder.fake.FakeRemindersRescheduler
import com.crazystudio.sportrecorder.testutil.MainDispatcherRule
import com.crazystudio.sportrecorder.ui.insights.DayRecordsViewModel
import com.crazystudio.sportrecorder.ui.insights.InsightsUiState
import com.crazystudio.sportrecorder.ui.insights.InsightsViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toInstant
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.TimeZone

/**
 * 回顧 / Reflect journey at the ViewModel layer: a user with an empty diary opens Insights, records
 * a few days of meals (one of them a late-night snack), reads the month back, taps a day to see
 * exactly those meals, then switches to Week and sees the same days as bands — each step asserted
 * on the state the screen actually renders, and each step agreeing with the others about which
 * day a meal belongs to.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class InsightsReflectFlowTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private lateinit var defaultTz: TimeZone

    @Before
    fun pinTimeZone() {
        defaultTz = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    @After
    fun restoreTimeZone() {
        TimeZone.setDefault(defaultTz)
    }

    private val zone = kotlinx.datetime.TimeZone.UTC

    /** Epoch millis for a wall-clock moment in March 2026, UTC. */
    private fun at(day: Int, hour: Int, minute: Int = 0): Long =
        LocalDateTime(2026, 3, day, hour, minute).toInstant(zone).toEpochMilliseconds()

    /** "Today" for the whole journey: the 15th at noon. */
    private val now = at(15, 12)

    private class Diary {
        val eatRepo = FakeEatRecordRepository()
        val settingsRepo = FakeDietSettingsRepository() // 16:8
        val save = SaveEatRecordUseCase(eatRepo, FakeRemindersRescheduler())

        fun insights(now: () -> Long) = InsightsViewModel(
            observeEatRecords = ObserveEatRecordsUseCase(eatRepo),
            dietSettingsRepository = settingsRepo,
            photoImageSource = FakePhotoImageSource(),
            now = now,
        )

        fun day(dayStart: Long) = DayRecordsViewModel(
            observeEatRecords = ObserveEatRecordsUseCase(eatRepo),
            dietSettingsRepository = settingsRepo,
            photoImageSource = FakePhotoImageSource(),
            savedStateHandle = SavedStateHandle(mapOf("dayStart" to dayStart)),
        )
    }

    private fun meal(time: Long, note: String? = null) =
        EatRecord(id = 0, time = time, location = null, note = note, photos = emptyList())

    /** The first loaded state a freshly-opened Insights tab shows. */
    private suspend fun InsightsViewModel.loaded(): InsightsUiState {
        lateinit var state: InsightsUiState
        uiState.test {
            skipItems(1) // the not-yet-loaded seed
            state = awaitItem()
            cancelAndIgnoreRemainingEvents()
        }
        return state
    }

    private suspend fun Diary.recordAFewDays() {
        save(meal(at(10, 9), "breakfast"), emptyList(), emptyList(), now = now)
        save(meal(at(10, 15)), emptyList(), emptyList(), now = now)
        save(meal(at(12, 20), "dinner"), emptyList(), emptyList(), now = now)
        save(meal(at(13, 0, 30), "late snack"), emptyList(), emptyList(), now = now)
        save(meal(at(14, 8)), emptyList(), emptyList(), now = now)
        save(meal(at(14, 21)), emptyList(), emptyList(), now = now)
    }

    @Test
    fun anEmptyDiary_showsTheInvitationOnceLoaded() = runTest(mainRule.testDispatcher.scheduler) {
        val diary = Diary()
        val vm = diary.insights { now }

        assertFalse(vm.uiState.value.isLoaded)
        val state = vm.loaded()
        assertTrue(state.isLoaded)
        assertFalse(state.result.hasAnyRecords)
    }

    @Test
    fun recordingMeals_fillsTheMonthAndItsNumbers() = runTest(mainRule.testDispatcher.scheduler) {
        val diary = Diary()
        diary.recordAFewDays()

        val state = diary.insights { now }.loaded()

        assertEquals(Period.MONTH, state.period)
        assertTrue(state.result.isCurrentPeriod)
        assertEquals(31, state.result.calendarDays.size)
        val byDay = state.result.calendarDays.associate { it.dayOfMonth to it.state }
        assertEquals(DayWindowState.WITHIN_WINDOW, byDay[10]) // 09:00–15:00
        assertEquals(DayWindowState.WITHIN_WINDOW, byDay[12]) // 20:00 + the 00:30 snack
        assertEquals(DayWindowState.NO_RECORD, byDay[13]) // the snack was the 12th's, not a new day
        assertEquals(DayWindowState.LONGER_WINDOW, byDay[14]) // 08:00–21:00
        assertEquals(3, state.result.summary.recordedDays)
        assertEquals(2, state.result.summary.withinWindowDays)
        assertEquals(6, state.result.stats.mealCount)
        assertEquals(3, state.result.stats.daysWithRecords)
        assertTrue(state.result.calendarDays.single { it.dayOfMonth == 15 }.isToday)
    }

    @Test
    fun tappingADay_opensExactlyThatDaysMeals() = runTest(mainRule.testDispatcher.scheduler) {
        val diary = Diary()
        diary.recordAFewDays()
        val month = diary.insights { now }.loaded()
        val twelfth = month.result.calendarDays.single { it.dayOfMonth == 12 }

        val sheet = diary.day(twelfth.dayStart)

        sheet.records.test {
            skipItems(1)
            val meals = awaitItem()
            assertEquals(listOf("dinner", "late snack"), meals.map { it.note })
            assertEquals(listOf(at(12, 20), at(13, 0, 30)), meals.map { it.time })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun switchingToWeek_drawsTheSameDaysAsBands() = runTest(mainRule.testDispatcher.scheduler) {
        val diary = Diary()
        diary.recordAFewDays()
        val vm = diary.insights { now }

        vm.uiState.test {
            skipItems(2)
            vm.setPeriod(Period.WEEK)
            val week = awaitItem()

            assertEquals((9..15).toList(), week.result.calendarDays.map { it.dayOfMonth })
            assertEquals(7, week.result.bands.size)
            val twelfth = week.result.bands.single { it.dayOfMonth == 12 }
            assertEquals(2, twelfth.mealCount)
            assertEquals(20 * 60, twelfth.firstMinutes)
            assertEquals(24 * 60 + 30, twelfth.lastMinutes) // the band runs past midnight
            assertEquals(0, week.result.bands.single { it.dayOfMonth == 13 }.mealCount)
            assertEquals(3, week.result.stats.daysWithRecords)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun pagingBackAWeek_leavesTheNumbersBehind() = runTest(mainRule.testDispatcher.scheduler) {
        val diary = Diary()
        diary.recordAFewDays()
        val vm = diary.insights { now }

        vm.uiState.test {
            skipItems(2)
            vm.setPeriod(Period.WEEK)
            awaitItem()
            vm.shiftPeriod(-1)
            val lastWeek = awaitItem()

            assertEquals((2..8).toList(), lastWeek.result.calendarDays.map { it.dayOfMonth })
            assertEquals(0, lastWeek.result.stats.mealCount)
            assertFalse(lastWeek.result.isCurrentPeriod)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
