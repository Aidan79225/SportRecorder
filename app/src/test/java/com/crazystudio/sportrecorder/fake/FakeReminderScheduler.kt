package com.crazystudio.sportrecorder.fake

import com.crazystudio.sportrecorder.domain.reminder.ReminderScheduler
import com.crazystudio.sportrecorder.domain.reminder.ScheduledReminder

/** Captures the last plan handed to the scheduler. */
class FakeReminderScheduler : ReminderScheduler {
    var lastScheduled: List<ScheduledReminder> = emptyList()
        private set
    var scheduleCount = 0
        private set

    override fun schedule(reminders: List<ScheduledReminder>) {
        lastScheduled = reminders
        scheduleCount++
    }
}
