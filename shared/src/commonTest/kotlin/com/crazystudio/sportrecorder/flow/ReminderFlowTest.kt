package com.crazystudio.sportrecorder.flow

import com.crazystudio.sportrecorder.backup.fakes.FakeDietSettingsRepository
import com.crazystudio.sportrecorder.backup.fakes.FakeEatRecordRepository
import com.crazystudio.sportrecorder.backup.fakes.FakeReminderPreferencesRepository
import com.crazystudio.sportrecorder.backup.fakes.FakeReminderScheduler
import com.crazystudio.sportrecorder.domain.model.DietSettings
import com.crazystudio.sportrecorder.domain.model.EatRecord
import com.crazystudio.sportrecorder.domain.model.FastingWindow
import com.crazystudio.sportrecorder.domain.reminder.ReminderPrefs
import com.crazystudio.sportrecorder.domain.reminder.ReminderType
import com.crazystudio.sportrecorder.domain.usecase.DeleteEatRecordUseCase
import com.crazystudio.sportrecorder.domain.usecase.RescheduleRemindersUseCase
import com.crazystudio.sportrecorder.domain.usecase.SaveEatRecordUseCase
import com.crazystudio.sportrecorder.domain.usecase.SaveFastingSelectionUseCase
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 提醒 journey — every mutation point re-arms the scheduler, and the plan it is armed with
 * follows the current records + fasting window + preferences.
 *
 * `RescheduleRemindersUseCase` calls `ReminderPlanner.plan(...)` without a timezone, so it uses
 * the system zone. Every assertion here is therefore written to be **timezone-independent**:
 * quiet-hours cases use the two degenerate ranges that behave identically everywhere —
 * `[0, 1440)` (always quiet) and `[0, 0)` (never quiet). Per-zone time-of-day behaviour is
 * covered by `ReminderPlannerTest`, which injects `TimeZone.UTC` explicitly.
 */
class ReminderFlowTest {

    // 2023-11-14T00:00:00Z, so day0 + Nh reads as N:00 UTC — but nothing below depends on that.
    private val day0 = 1_699_920_000_000L
    private fun h(n: Long) = n * 3_600_000L
    private fun min(n: Long) = n * 60_000L

    private class Harness(
        meals: List<Long>,
        prefs: ReminderPrefs,
        settings: DietSettings = DietSettings(fastingHours = 16, eatingHours = 8),
        var now: Long,
    ) {
        val eatRepo = FakeEatRecordRepository(
            meals.mapIndexed { index, time ->
                EatRecord(id = index + 1, time = time, location = null, note = null, photos = emptyList())
            },
        )
        val settingsRepo = FakeDietSettingsRepository(settings)
        val prefsRepo = FakeReminderPreferencesRepository(prefs)
        val scheduler = FakeReminderScheduler()
        val reschedule = RescheduleRemindersUseCase(
            eatRecordRepository = eatRepo,
            dietSettingsRepository = settingsRepo,
            reminderPreferencesRepository = prefsRepo,
            scheduler = scheduler,
            now = { now },
        )
        val save = SaveEatRecordUseCase(eatRepo, reschedule)
        val delete = DeleteEatRecordUseCase(eatRepo, reschedule)
        val selectWindow = SaveFastingSelectionUseCase(settingsRepo, reschedule)
    }

    private fun harness(
        meals: List<Long>,
        prefs: ReminderPrefs = ReminderPrefs(),
        settings: DietSettings = DietSettings(fastingHours = 16, eatingHours = 8),
        now: Long,
    ) = Harness(meals, prefs, settings, now)

    private fun triggerOf(app: Harness, type: ReminderType): Long? =
        app.scheduler.lastScheduled.firstOrNull { it.type == type }?.triggerAtMillis

    @Test
    fun remindersOffByDefault_produceNothing() = runTest {
        // 只記錄、不評價: nothing is scheduled until the user opts in.
        val app = harness(meals = listOf(day0 + h(10)), now = day0 + h(11))

        app.reschedule.reschedule()

        assertEquals(1, app.scheduler.scheduleCount)
        assertTrue(app.scheduler.lastScheduled.isEmpty())
    }

    @Test
    fun windowClosingOnly_firesBeforeTheWindowShuts() = runTest {
        val app = harness(
            meals = listOf(day0 + h(10)),
            prefs = ReminderPrefs(windowClosingEnabled = true),
            now = day0 + h(11),
        )

        app.reschedule.reschedule()

        assertEquals(listOf(ReminderType.WINDOW_CLOSING), app.scheduler.lastScheduled.map { it.type })
        assertEquals(day0 + h(18) - min(30), triggerOf(app, ReminderType.WINDOW_CLOSING))
    }

    @Test
    fun windowClosing_honoursACustomLeadTime() = runTest {
        val app = harness(
            meals = listOf(day0 + h(10)),
            prefs = ReminderPrefs(windowClosingEnabled = true, leadMinutes = 45),
            now = day0 + h(11),
        )

        app.reschedule.reschedule()

        assertEquals(day0 + h(18) - min(45), triggerOf(app, ReminderType.WINDOW_CLOSING))
    }

    @Test
    fun windowClosing_isDroppedOnceTheFastHasStarted() = runTest {
        val app = harness(
            meals = listOf(day0 + h(10)),
            prefs = ReminderPrefs(windowClosingEnabled = true),
            now = day0 + h(20),
        )

        app.reschedule.reschedule()

        assertTrue(app.scheduler.lastScheduled.isEmpty())
    }

    @Test
    fun windowClosing_isNeverScheduledInThePast() = runTest {
        val app = harness(
            meals = listOf(day0 + h(10)),
            prefs = ReminderPrefs(windowClosingEnabled = true),
            now = day0 + h(17) + min(45),
        )

        app.reschedule.reschedule()

        assertTrue(app.scheduler.lastScheduled.isEmpty())
    }

    @Test
    fun fastCompleteOnly_firesAtTheTarget() = runTest {
        val app = harness(
            meals = listOf(day0 + h(10)),
            prefs = ReminderPrefs(fastCompleteEnabled = true),
            now = day0 + h(11),
        )

        app.reschedule.reschedule()

        // Single meal → the fast clock starts at +1h, so the target is meal + 1h + 16h.
        assertEquals(listOf(ReminderType.FAST_COMPLETE), app.scheduler.lastScheduled.map { it.type })
        assertEquals(day0 + h(27), triggerOf(app, ReminderType.FAST_COMPLETE))
    }

    @Test
    fun fastComplete_isDroppedOnceAlreadyReached() = runTest {
        val app = harness(
            meals = listOf(day0 + h(10)),
            prefs = ReminderPrefs(fastCompleteEnabled = true),
            now = day0 + h(27),
        )

        app.reschedule.reschedule()

        assertTrue(app.scheduler.lastScheduled.isEmpty())
    }

    @Test
    fun bothEnabled_armsOneOfEach() = runTest {
        val app = harness(
            meals = listOf(day0 + h(10)),
            prefs = ReminderPrefs(windowClosingEnabled = true, fastCompleteEnabled = true),
            now = day0 + h(11),
        )

        app.reschedule.reschedule()

        assertEquals(
            setOf(ReminderType.WINDOW_CLOSING, ReminderType.FAST_COMPLETE),
            app.scheduler.lastScheduled.map { it.type }.toSet(),
        )
    }

    @Test
    fun quietHoursCoveringTheWholeDay_suppressOnlyTheCelebration() = runTest {
        // [0, 1440) is "quiet" at every minute of every day, in any timezone.
        val app = harness(
            meals = listOf(day0 + h(10)),
            prefs = ReminderPrefs(
                windowClosingEnabled = true,
                fastCompleteEnabled = true,
                quietHoursEnabled = true,
                quietStartMinutes = 0,
                quietEndMinutes = MINUTES_PER_DAY,
            ),
            now = day0 + h(11),
        )

        app.reschedule.reschedule()

        // Quiet hours suppress the celebratory reminder; the "last call" one is unaffected.
        assertEquals(listOf(ReminderType.WINDOW_CLOSING), app.scheduler.lastScheduled.map { it.type })
    }

    @Test
    fun anEmptyQuietRange_suppressesNothing() = runTest {
        val app = harness(
            meals = listOf(day0 + h(10)),
            prefs = ReminderPrefs(
                fastCompleteEnabled = true,
                quietHoursEnabled = true,
                quietStartMinutes = 0,
                quietEndMinutes = 0,
            ),
            now = day0 + h(11),
        )

        app.reschedule.reschedule()

        assertEquals(day0 + h(27), triggerOf(app, ReminderType.FAST_COMPLETE))
    }

    @Test
    fun quietHoursDisabled_ignoresTheStoredRange() = runTest {
        val app = harness(
            meals = listOf(day0 + h(10)),
            prefs = ReminderPrefs(
                fastCompleteEnabled = true,
                quietHoursEnabled = false,
                quietStartMinutes = 0,
                quietEndMinutes = MINUTES_PER_DAY,
            ),
            now = day0 + h(11),
        )

        app.reschedule.reschedule()

        assertEquals(day0 + h(27), triggerOf(app, ReminderType.FAST_COMPLETE))
    }

    @Test
    fun noRecords_armsAnEmptyPlan() = runTest {
        val app = harness(
            meals = emptyList(),
            prefs = ReminderPrefs(windowClosingEnabled = true, fastCompleteEnabled = true),
            now = day0 + h(11),
        )

        app.reschedule.reschedule()

        assertEquals(1, app.scheduler.scheduleCount)
        assertTrue(app.scheduler.lastScheduled.isEmpty())
    }

    @Test
    fun savingAMeal_rearmsTheSchedulerWithTheLaterTarget() = runTest {
        val app = harness(
            meals = listOf(day0 + h(10)),
            prefs = ReminderPrefs(fastCompleteEnabled = true),
            now = day0 + h(13),
        )
        app.reschedule.reschedule()
        assertEquals(day0 + h(27), triggerOf(app, ReminderType.FAST_COMPLETE))

        app.save(
            EatRecord(id = 0, time = day0 + h(13), location = null, note = null, photos = emptyList()),
            emptyList(),
            emptyList(),
            now = day0 + h(13),
        )

        // Two meals in the window → no 1h grace; the target moves to the later bite + 16h.
        assertEquals(2, app.scheduler.scheduleCount)
        assertEquals(day0 + h(29), triggerOf(app, ReminderType.FAST_COMPLETE))
    }

    @Test
    fun deletingTheOnlyMeal_collapsesThePlan() = runTest {
        val app = harness(
            meals = listOf(day0 + h(10)),
            prefs = ReminderPrefs(fastCompleteEnabled = true),
            now = day0 + h(11),
        )

        app.delete(app.eatRepo.state.value.single().id)

        assertEquals(1, app.scheduler.scheduleCount)
        assertTrue(app.scheduler.lastScheduled.isEmpty())
    }

    @Test
    fun changingTheFastingWindow_rearmsWithTheNewTarget() = runTest {
        val app = harness(
            meals = listOf(day0 + h(10)),
            prefs = ReminderPrefs(fastCompleteEnabled = true),
            now = day0 + h(11),
        )
        app.reschedule.reschedule()
        assertEquals(day0 + h(27), triggerOf(app, ReminderType.FAST_COMPLETE))

        app.selectWindow(FastingWindow(fastingHours = 20, eatingHours = 4))

        // 20h fast from the same +1h grace start → meal + 1h + 20h.
        assertEquals(day0 + h(31), triggerOf(app, ReminderType.FAST_COMPLETE))
    }

    @Test
    fun aLateMeal_schedulesATargetOnTheFollowingDay() = runTest {
        val app = harness(
            meals = listOf(day0 + h(22)),
            prefs = ReminderPrefs(fastCompleteEnabled = true),
            now = day0 + h(23),
        )

        app.reschedule.reschedule()

        assertEquals(day0 + h(39), triggerOf(app, ReminderType.FAST_COMPLETE))
    }

    private companion object {
        const val MINUTES_PER_DAY = 1440
    }
}
