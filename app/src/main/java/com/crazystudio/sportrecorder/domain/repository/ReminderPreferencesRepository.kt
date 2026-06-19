package com.crazystudio.sportrecorder.domain.repository

import com.crazystudio.sportrecorder.domain.reminder.ReminderPrefs
import kotlinx.coroutines.flow.Flow

interface ReminderPreferencesRepository {
    /** Emits current preferences and re-emits on every change. */
    val prefs: Flow<ReminderPrefs>

    suspend fun setWindowClosingEnabled(enabled: Boolean)

    suspend fun setFastCompleteEnabled(enabled: Boolean)

    suspend fun setLeadMinutes(minutes: Long)

    suspend fun setQuietHoursEnabled(enabled: Boolean)

    /** Persist the quiet-hours range as minutes-since-midnight (may span midnight). */
    suspend fun setQuietHours(startMinutes: Int, endMinutes: Int)
}
