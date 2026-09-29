package com.crazystudio.sportrecorder.flow

import app.cash.turbine.test
import com.crazystudio.sportrecorder.domain.diet.TaglineMood
import com.crazystudio.sportrecorder.domain.model.DietSettings
import com.crazystudio.sportrecorder.domain.model.EatRecord
import com.crazystudio.sportrecorder.domain.model.FastingWindow
import com.crazystudio.sportrecorder.domain.usecase.ObserveDietStateUseCase
import com.crazystudio.sportrecorder.domain.usecase.SaveEatRecordUseCase
import com.crazystudio.sportrecorder.domain.usecase.SaveFastingSelectionUseCase
import com.crazystudio.sportrecorder.fake.FakeDietSettingsRepository
import com.crazystudio.sportrecorder.fake.FakeEatRecordRepository
import com.crazystudio.sportrecorder.fake.FakeRemindersRescheduler
import com.crazystudio.sportrecorder.shared.resources.Res
import com.crazystudio.sportrecorder.shared.resources.diet_fasting_time
import com.crazystudio.sportrecorder.shared.resources.diet_no_record
import com.crazystudio.sportrecorder.shared.resources.diet_remaining_time
import com.crazystudio.sportrecorder.testutil.MainDispatcherRule
import com.crazystudio.sportrecorder.ui.diet.DietUiState
import com.crazystudio.sportrecorder.ui.diet.DietViewModel
import com.crazystudio.sportrecorder.ui.diet.RelativeDay
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

/**
 * Home / 斷食視窗 journey at the ViewModel layer: a user opens the app with nothing recorded,
 * logs a meal, watches the window close, reaches the fast target, then changes their fasting
 * window — each step asserted on the state the screen actually renders.
 *
 * Phase transitions are observed through a fresh [DietViewModel] per step (which is what
 * navigating back to Home does), plus one live re-emission test.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DietHomeFlowTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    // 2023-11-14T22:13:20Z, so the eating window and the fast target both cross midnight in UTC.
    private val f = 1_700_000_000_000L
    private fun h(n: Long) = TimeUnit.HOURS.toMillis(n)

    private lateinit var defaultTz: TimeZone
    private lateinit var defaultLocale: Locale

    @Before
    fun pinTimeZoneAndLocale() {
        defaultTz = TimeZone.getDefault()
        defaultLocale = Locale.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        Locale.setDefault(Locale.US)
    }

    @After
    fun restoreTimeZoneAndLocale() {
        TimeZone.setDefault(defaultTz)
        Locale.setDefault(defaultLocale)
    }

    private class Home {
        val eatRepo = FakeEatRecordRepository()
        val settingsRepo = FakeDietSettingsRepository()
        val rescheduler = FakeRemindersRescheduler()
        val save = SaveEatRecordUseCase(eatRepo, rescheduler)
        val selectWindow = SaveFastingSelectionUseCase(settingsRepo, rescheduler)

        fun viewModel(now: () -> Long) =
            DietViewModel(ObserveDietStateUseCase(eatRepo, settingsRepo), now)
    }

    private fun meal(time: Long) =
        EatRecord(id = 0, time = time, location = null, note = null, photos = emptyList())

    /** The first computed state a freshly-opened Home screen shows at [now]. */
    private suspend fun Home.stateAt(now: Long): DietUiState {
        lateinit var state: DietUiState
        viewModel { now }.uiState.test {
            skipItems(1) // the DietUiState() placeholder before the first computation
            state = awaitItem()
            cancelAndIgnoreRemainingEvents()
        }
        return state
    }

    @Test
    fun noRecordsYet_showsTheIdlePrompt() = runTest(mainRule.testDispatcher.scheduler) {
        val home = Home()

        val state = home.stateAt(f)

        assertEquals(TaglineMood.IDLE_NIGHT, state.tagline.mood)
        assertEquals(Res.string.diet_no_record, state.promptText)
        assertEquals("00:00:00", state.elapsedText)
        assertNull(state.fastStart)
        assertNull(state.fastEnd)
    }

    @Test
    fun loggingAMeal_opensTheEatingWindow() = runTest(mainRule.testDispatcher.scheduler) {
        val home = Home()
        assertEquals(TaglineMood.IDLE_NIGHT, home.stateAt(f).tagline.mood)

        home.save(meal(f), emptyList(), emptyList(), now = f)

        val state = home.stateAt(f + h(1))
        assertEquals(TaglineMood.EATING, state.tagline.mood)
        assertEquals(Res.string.diet_remaining_time, state.promptText)
        assertEquals("07:00:00", state.elapsedText) // 8h window, 1h in
        assertEquals("16 : 8", state.fastingLabel)
    }

    @Test
    fun theWindowClosesAndTheFastRunsToTarget() = runTest(mainRule.testDispatcher.scheduler) {
        val home = Home()
        home.save(meal(f), emptyList(), emptyList(), now = f)

        val fasting = home.stateAt(f + h(12))
        assertEquals(TaglineMood.FASTING, fasting.tagline.mood)
        assertEquals(Res.string.diet_fasting_time, fasting.promptText)

        val success = home.stateAt(f + h(17))
        assertEquals(TaglineMood.SUCCESS, success.tagline.mood)
        assertEquals(100f, success.progress, 0.01f)
    }

    @Test
    fun aWindowCrossingMidnightIsLabelledAsTomorrow() = runTest(mainRule.testDispatcher.scheduler) {
        // Meal at 22:13 UTC → the window closes at 06:13 and the fast target lands at 15:13,
        // both on the following calendar day.
        val home = Home()
        home.save(meal(f), emptyList(), emptyList(), now = f)

        val state = home.stateAt(f + h(1))

        assertEquals("06:13", state.fastStart?.time)
        assertEquals(RelativeDay.TOMORROW, state.fastStart?.day)
        assertEquals("15:13", state.fastEnd?.time)
        assertEquals(RelativeDay.TOMORROW, state.fastEnd?.day)
    }

    @Test
    fun changingTheFastingWindow_rerendersTheHomeState() = runTest(mainRule.testDispatcher.scheduler) {
        val home = Home()
        home.save(meal(f), emptyList(), emptyList(), now = f)
        assertEquals(TaglineMood.EATING, home.stateAt(f + h(6)).tagline.mood)

        home.selectWindow(FastingWindow(fastingHours = 20, eatingHours = 4))

        // The 4h window already closed 6h in, so the very same moment now reads as a fast.
        val state = home.stateAt(f + h(6))
        assertEquals(TaglineMood.FASTING, state.tagline.mood)
        assertEquals("20 : 4", state.fastingLabel)
        assertEquals(
            DietSettings(fastingHours = 20, eatingHours = 4),
            home.settingsRepo.settings.first(),
        )
    }

    @Test
    fun aMealSavedWhileHomeIsOpen_updatesTheStateLive() = runTest(mainRule.testDispatcher.scheduler) {
        val home = Home()
        val vm = home.viewModel { f }

        vm.uiState.test {
            skipItems(1)
            assertEquals(TaglineMood.IDLE_NIGHT, awaitItem().tagline.mood)

            home.save(meal(f), emptyList(), emptyList(), now = f)

            assertEquals(TaglineMood.EATING, awaitItem().tagline.mood)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
