package com.crazystudio.sportrecorder.domain.reminder

/** A single reminder the scheduler should fire at [triggerAtMillis] (epoch millis). */
data class ScheduledReminder(val type: ReminderType, val triggerAtMillis: Long)
