package com.crazystudio.sportrecorder.flow

import com.crazystudio.sportrecorder.domain.model.DietSettings
import com.crazystudio.sportrecorder.domain.model.EatRecord
import com.crazystudio.sportrecorder.domain.reminder.ReminderPrefs
import com.crazystudio.sportrecorder.domain.reminder.ReminderType
import com.crazystudio.sportrecorder.domain.usecase.RescheduleRemindersUseCase
import com.crazystudio.sportrecorder.fake.FakeDietSettingsRepository
import com.crazystudio.sportrecorder.fake.FakeEatRecordRepository
import com.crazystudio.sportrecorder.fake.FakeReminderPreferencesRepository
import com.crazystudio.sportrecorder.fake.FakeReminderScheduler
import com.crazystudio.sportrecorder.testutil.MainDispatcherRule
import com.crazystudio.sportrecorder.ui.settings.SettingsViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * 設定 journey at the ViewModel layer: every switch the user flips must reach the preference
 * store *and* re-arm the reminder schedule, computed from the preferences as they now stand.
 *
 * Quiet hours stay off in the scheduling assertions, so the expected trigger times do not depend
 * on the CI machine's timezone.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelFlowTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private val day0 = 1_699_920_000_000L
    private fun h(n: Long) = TimeUnit.HOURS.toMillis(n)
    private fun min(n: Long) = TimeUnit.MINUTES.toMillis(n)

    private class Settings(prefs: ReminderPrefs = ReminderPrefs()) {
        val prefsRepo = FakeReminderPreferencesRepository(prefs)
        val scheduler = FakeReminderScheduler()

        fun build(meals: List<EatRecord>, now: Long): SettingsViewModel {
            val reschedule = RescheduleRemindersUseCase(
                eatRecordRepository = FakeEatRecordRepository(meals),
                dietSettingsRepository = FakeDietSettingsRepository(
                    DietSettings(fastingHours = 16, eatingHours = 8),
                ),
                reminderPreferencesRepository = prefsRepo,
                scheduler = scheduler,
                now = { now },
            )
            return SettingsViewModel(prefsRepo, reschedule)
        }
    }

    private fun meal(time: Long) =
        EatRecord(id = 1, time = time, location = null, note = null, photos = emptyList())

    /**
     * `SettingsViewModel.uiState` is `WhileSubscribed`, and `changeLeadMinutes` reads it — so the
     * screen has to actually be on-screen for the VM to behave. Model that with a live collector.
     */
    private fun CoroutineScope.observe(vm: SettingsViewModel) {
        launch { vm.uiState.collect { } }
    }

    @Test
    fun enablingFastComplete_persistsAndArmsTheCelebration() = runTest(mainRule.testDispatcher.scheduler) {
        val settings = Settings()
        val vm = settings.build(listOf(meal(day0 + h(10))), now = day0 + h(11))
        backgroundScope.observe(vm)
        advanceUntilIdle()

        vm.setFastCompleteEnabled(true)
        advanceUntilIdle()

        assertTrue(settings.prefsRepo.prefs.first().fastCompleteEnabled)
        assertEquals(ReminderType.FAST_COMPLETE, settings.scheduler.lastScheduled.single().type)
        assertEquals(day0 + h(27), settings.scheduler.lastScheduled.single().triggerAtMillis)
    }

    @Test
    fun enablingWindowClosing_persistsAndArmsTheLastCall() = runTest(mainRule.testDispatcher.scheduler) {
        val settings = Settings()
        val vm = settings.build(listOf(meal(day0 + h(10))), now = day0 + h(11))
        backgroundScope.observe(vm)
        advanceUntilIdle()

        vm.setWindowClosingEnabled(true)
        advanceUntilIdle()

        assertEquals(ReminderType.WINDOW_CLOSING, settings.scheduler.lastScheduled.single().type)
        assertEquals(day0 + h(18) - min(30), settings.scheduler.lastScheduled.single().triggerAtMillis)
    }

    @Test
    fun turningAReminderBackOff_disarmsIt() = runTest(mainRule.testDispatcher.scheduler) {
        val settings = Settings(ReminderPrefs(fastCompleteEnabled = true))
        val vm = settings.build(listOf(meal(day0 + h(10))), now = day0 + h(11))
        backgroundScope.observe(vm)
        advanceUntilIdle()

        vm.setFastCompleteEnabled(false)
        advanceUntilIdle()

        assertFalse(settings.prefsRepo.prefs.first().fastCompleteEnabled)
        assertTrue(settings.scheduler.lastScheduled.isEmpty())
    }

    @Test
    fun changingTheLeadTime_movesTheTriggerAndIsClampedUpwards() = runTest(mainRule.testDispatcher.scheduler) {
        val settings = Settings(ReminderPrefs(windowClosingEnabled = true))
        val vm = settings.build(listOf(meal(day0 + h(10))), now = day0 + h(11))
        backgroundScope.observe(vm)
        advanceUntilIdle()

        vm.changeLeadMinutes(15)
        advanceUntilIdle()
        assertEquals(45L, settings.prefsRepo.prefs.first().leadMinutes)
        assertEquals(day0 + h(18) - min(45), settings.scheduler.lastScheduled.single().triggerAtMillis)

        // 45 + 120 saturates at the 120-minute ceiling rather than running away.
        vm.changeLeadMinutes(120)
        advanceUntilIdle()
        vm.changeLeadMinutes(120)
        advanceUntilIdle()

        assertEquals(120L, settings.prefsRepo.prefs.first().leadMinutes)
    }

    @Test
    fun changingTheLeadTime_isClampedDownwards() = runTest(mainRule.testDispatcher.scheduler) {
        val settings = Settings()
        val vm = settings.build(emptyList(), now = day0)
        backgroundScope.observe(vm)
        advanceUntilIdle()

        vm.changeLeadMinutes(-1_000)
        advanceUntilIdle()

        assertEquals(5L, settings.prefsRepo.prefs.first().leadMinutes)
    }

    @Test
    fun quietHoursSettings_persistAndSuppressTheCelebration() = runTest(mainRule.testDispatcher.scheduler) {
        val settings = Settings(ReminderPrefs(fastCompleteEnabled = true))
        val vm = settings.build(listOf(meal(day0 + h(10))), now = day0 + h(11))
        backgroundScope.observe(vm)
        advanceUntilIdle()

        // A quiet range covering the whole day is quiet in every timezone.
        vm.setQuietStart(0)
        advanceUntilIdle()
        vm.setQuietEnd(MINUTES_PER_DAY)
        advanceUntilIdle()
        vm.setQuietHoursEnabled(true)
        advanceUntilIdle()

        val stored = settings.prefsRepo.prefs.first()
        assertEquals(0, stored.quietStartMinutes)
        assertEquals(MINUTES_PER_DAY, stored.quietEndMinutes)
        assertTrue(stored.quietHoursEnabled)
        assertTrue(settings.scheduler.lastScheduled.isEmpty())
    }

    @Test
    fun everyToggle_rearmsTheScheduleExactlyOnce() = runTest(mainRule.testDispatcher.scheduler) {
        val settings = Settings()
        val vm = settings.build(listOf(meal(day0 + h(10))), now = day0 + h(11))
        backgroundScope.observe(vm)
        advanceUntilIdle()

        vm.setWindowClosingEnabled(true)
        advanceUntilIdle()
        vm.setFastCompleteEnabled(true)
        advanceUntilIdle()
        vm.setQuietHoursEnabled(true)
        advanceUntilIdle()

        assertEquals(3, settings.scheduler.scheduleCount)
    }

    private companion object {
        const val MINUTES_PER_DAY = 1440
    }
}
