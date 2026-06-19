package com.crazystudio.sportrecorder.domain.reminder

/**
 * Recomputes the reminder plan from current data and re-arms the scheduler. Invoked from every
 * mutation point (meal saved/edited/deleted, settings/prefs changed, boot, after an alarm fires)
 * because fastTargetAt shifts with each new meal.
 */
interface RemindersRescheduler {
    suspend fun reschedule()
}
