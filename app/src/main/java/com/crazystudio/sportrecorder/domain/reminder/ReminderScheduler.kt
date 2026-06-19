package com.crazystudio.sportrecorder.domain.reminder

/**
 * Platform boundary: replaces all currently-scheduled reminders with [reminders] (cancelling any
 * types not present). The Android implementation backs this with AlarmManager.
 */
interface ReminderScheduler {
    fun schedule(reminders: List<ScheduledReminder>)
}
