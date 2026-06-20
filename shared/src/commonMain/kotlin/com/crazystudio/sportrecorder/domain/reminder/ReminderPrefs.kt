package com.crazystudio.sportrecorder.domain.reminder

/**
 * User-configurable reminder preferences. Quiet hours are stored as minutes-since-midnight and
 * may span midnight (start > end). Reminders default to OFF so we only request notification
 * permission once the user opts in.
 */
data class ReminderPrefs(
    val windowClosingEnabled: Boolean = false,
    val fastCompleteEnabled: Boolean = false,
    val leadMinutes: Long = DEFAULT_LEAD_MINUTES,
    val quietHoursEnabled: Boolean = false,
    val quietStartMinutes: Int = DEFAULT_QUIET_START,
    val quietEndMinutes: Int = DEFAULT_QUIET_END,
) {
    companion object {
        const val DEFAULT_LEAD_MINUTES = 30L
        const val DEFAULT_QUIET_START = 1320 // 22:00
        const val DEFAULT_QUIET_END = 480 // 08:00
    }
}
