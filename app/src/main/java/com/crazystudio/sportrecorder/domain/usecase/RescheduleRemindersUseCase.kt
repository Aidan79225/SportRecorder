package com.crazystudio.sportrecorder.domain.usecase

import com.crazystudio.sportrecorder.domain.reminder.ReminderPlanner
import com.crazystudio.sportrecorder.domain.reminder.ReminderScheduler
import com.crazystudio.sportrecorder.domain.reminder.RemindersRescheduler
import com.crazystudio.sportrecorder.domain.repository.DietSettingsRepository
import com.crazystudio.sportrecorder.domain.repository.EatRecordRepository
import com.crazystudio.sportrecorder.domain.repository.ReminderPreferencesRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Reads the current records + settings + reminder prefs once, runs the pure [ReminderPlanner],
 * and re-arms the [ReminderScheduler]. Schedules only future events, so it is safe to call on
 * every data change.
 */
class RescheduleRemindersUseCase(
    private val eatRecordRepository: EatRecordRepository,
    private val dietSettingsRepository: DietSettingsRepository,
    private val reminderPreferencesRepository: ReminderPreferencesRepository,
    private val scheduler: ReminderScheduler,
    private val now: () -> Long,
) : RemindersRescheduler {

    @Inject
    constructor(
        eatRecordRepository: EatRecordRepository,
        dietSettingsRepository: DietSettingsRepository,
        reminderPreferencesRepository: ReminderPreferencesRepository,
        scheduler: ReminderScheduler,
    ) : this(
        eatRecordRepository,
        dietSettingsRepository,
        reminderPreferencesRepository,
        scheduler,
        System::currentTimeMillis,
    )

    override suspend fun reschedule() {
        val eatTimesAsc = eatRecordRepository.observeAll().first().map { it.time }.sorted()
        val settings = dietSettingsRepository.settings.first()
        val prefs = reminderPreferencesRepository.prefs.first()
        scheduler.schedule(ReminderPlanner.plan(eatTimesAsc, settings, prefs, now()))
    }
}
