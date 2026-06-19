package com.crazystudio.sportrecorder.fake

import com.crazystudio.sportrecorder.domain.reminder.ReminderPrefs
import com.crazystudio.sportrecorder.domain.repository.ReminderPreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeReminderPreferencesRepository(
    initial: ReminderPrefs = ReminderPrefs(),
) : ReminderPreferencesRepository {
    private val state = MutableStateFlow(initial)

    override val prefs: Flow<ReminderPrefs> = state

    override suspend fun setWindowClosingEnabled(enabled: Boolean) {
        state.value = state.value.copy(windowClosingEnabled = enabled)
    }

    override suspend fun setFastCompleteEnabled(enabled: Boolean) {
        state.value = state.value.copy(fastCompleteEnabled = enabled)
    }

    override suspend fun setLeadMinutes(minutes: Long) {
        state.value = state.value.copy(leadMinutes = minutes)
    }

    override suspend fun setQuietHoursEnabled(enabled: Boolean) {
        state.value = state.value.copy(quietHoursEnabled = enabled)
    }

    override suspend fun setQuietHours(startMinutes: Int, endMinutes: Int) {
        state.value = state.value.copy(quietStartMinutes = startMinutes, quietEndMinutes = endMinutes)
    }
}
