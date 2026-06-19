package com.crazystudio.sportrecorder.domain.reminder

import com.crazystudio.sportrecorder.domain.diet.DietPhase
import com.crazystudio.sportrecorder.domain.diet.DietWindow
import com.crazystudio.sportrecorder.domain.diet.DietWindowState
import com.crazystudio.sportrecorder.domain.model.DietSettings
import java.util.Calendar

/**
 * Pure (Android-free) planner that decides which reminders should be scheduled and when, by
 * reusing [DietWindow]. It only ever returns future, not-yet-passed events; quiet hours suppress
 * (drop) the fast-complete reminder rather than deferring it.
 */
object ReminderPlanner {

    private const val MINUTES_PER_HOUR = 60
    private const val MILLIS_PER_MINUTE = 60_000L

    fun plan(
        eatTimesAsc: List<Long>,
        settings: DietSettings,
        prefs: ReminderPrefs,
        now: Long,
    ): List<ScheduledReminder> {
        if (eatTimesAsc.isEmpty()) return emptyList()
        val state = DietWindow.compute(
            eatTimesAsc = eatTimesAsc,
            eatingHours = settings.eatingHours,
            fastingHours = settings.fastingHours,
            now = now,
        )
        return listOfNotNull(
            windowClosing(state, prefs, now),
            fastComplete(state, prefs, now),
        )
    }

    /** "Last call" reminder: only while EATING, [ReminderPrefs.leadMinutes] before windowEnd. */
    private fun windowClosing(state: DietWindowState, prefs: ReminderPrefs, now: Long): ScheduledReminder? {
        val end = state.windowEnd ?: return null
        val trigger = end - prefs.leadMinutes * MILLIS_PER_MINUTE
        val eligible = prefs.windowClosingEnabled && state.phase == DietPhase.EATING && trigger > now
        return if (eligible) ScheduledReminder(ReminderType.WINDOW_CLOSING, trigger) else null
    }

    /** Celebratory reminder at fastTargetAt; skipped if already complete or inside quiet hours. */
    private fun fastComplete(state: DietWindowState, prefs: ReminderPrefs, now: Long): ScheduledReminder? {
        val target = state.fastTargetAt ?: return null
        val eligible = prefs.fastCompleteEnabled &&
            now < target &&
            !suppressedByQuietHours(target, prefs)
        return if (eligible) ScheduledReminder(ReminderType.FAST_COMPLETE, target) else null
    }

    private fun suppressedByQuietHours(triggerAt: Long, prefs: ReminderPrefs): Boolean =
        prefs.quietHoursEnabled && inQuietHours(triggerAt, prefs.quietStartMinutes, prefs.quietEndMinutes)

    /** True if [triggerAt]'s local time-of-day falls in [start, end); the range may span midnight. */
    private fun inQuietHours(triggerAt: Long, start: Int, end: Int): Boolean {
        val minute = minutesOfDay(triggerAt)
        return if (start <= end) minute in start until end else minute >= start || minute < end
    }

    private fun minutesOfDay(millis: Long): Int =
        Calendar.getInstance().apply { timeInMillis = millis }
            .let { it.get(Calendar.HOUR_OF_DAY) * MINUTES_PER_HOUR + it.get(Calendar.MINUTE) }
}
