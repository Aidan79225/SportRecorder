package com.crazystudio.sportrecorder.domain.usecase

import com.crazystudio.sportrecorder.domain.model.DietSettings
import com.crazystudio.sportrecorder.domain.model.EatRecord
import com.crazystudio.sportrecorder.domain.reminder.ReminderPrefs
import com.crazystudio.sportrecorder.domain.reminder.ReminderType
import com.crazystudio.sportrecorder.fake.FakeDietSettingsRepository
import com.crazystudio.sportrecorder.fake.FakeEatRecordRepository
import com.crazystudio.sportrecorder.fake.FakeReminderPreferencesRepository
import com.crazystudio.sportrecorder.fake.FakeReminderScheduler
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.TimeUnit

class RescheduleRemindersUseCaseTest {

    private val day0 = 1_699_920_000_000L
    private fun h(n: Long) = TimeUnit.HOURS.toMillis(n)
    private fun eat(time: Long) =
        EatRecord(id = 1, time = time, location = null, note = null, photos = emptyList())

    private fun useCase(
        eats: List<EatRecord>,
        prefs: ReminderPrefs,
        scheduler: FakeReminderScheduler,
        now: () -> Long,
    ) = RescheduleRemindersUseCase(
        eatRecordRepository = FakeEatRecordRepository(initial = eats),
        dietSettingsRepository = FakeDietSettingsRepository(DietSettings(fastingHours = 16, eatingHours = 8)),
        reminderPreferencesRepository = FakeReminderPreferencesRepository(prefs),
        scheduler = scheduler,
        now = now,
    )

    @Test
    fun reschedule_passesPlannedRemindersToScheduler() = runTest {
        val scheduler = FakeReminderScheduler()
        val prefs = ReminderPrefs(fastCompleteEnabled = true)
        // Eat 10:00 → fastTargetAt 02:00 next day. now 11:00.
        useCase(listOf(eat(day0 + h(10))), prefs, scheduler) { day0 + h(11) }.reschedule()

        assertEquals(1, scheduler.scheduleCount)
        assertEquals(1, scheduler.lastScheduled.size)
        assertEquals(ReminderType.FAST_COMPLETE, scheduler.lastScheduled[0].type)
        assertEquals(day0 + h(26), scheduler.lastScheduled[0].triggerAtMillis)
    }

    @Test
    fun reschedule_withNoData_schedulesEmptyPlan() = runTest {
        val scheduler = FakeReminderScheduler()
        useCase(emptyList(), ReminderPrefs(fastCompleteEnabled = true), scheduler) { day0 }.reschedule()

        assertEquals(1, scheduler.scheduleCount)
        assertEquals(emptyList<Any>(), scheduler.lastScheduled)
    }
}
