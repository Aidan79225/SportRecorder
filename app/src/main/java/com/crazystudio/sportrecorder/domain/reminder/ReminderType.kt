package com.crazystudio.sportrecorder.domain.reminder

/** The two fasting reminders this app can schedule. Order/identity is stable for persistence. */
enum class ReminderType {
    /** "進食視窗即將關閉" — fires at windowEnd − leadMinutes, only while EATING. */
    WINDOW_CLOSING,

    /** "斷食達標 🎉" — fires at fastTargetAt (lastEat + fastingHours). */
    FAST_COMPLETE,
}
