package com.crazystudio.sportrecorder.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.crazystudio.sportrecorder.domain.reminder.ReminderPrefs
import com.crazystudio.sportrecorder.domain.reminder.RemindersRescheduler
import com.crazystudio.sportrecorder.domain.repository.ReminderPreferencesRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel constructor(
    private val repository: ReminderPreferencesRepository,
    private val rescheduleReminders: RemindersRescheduler,
) : ViewModel() {

    val uiState: StateFlow<ReminderPrefs> = repository.prefs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), ReminderPrefs())

    fun setWindowClosingEnabled(enabled: Boolean) = applyThenReschedule {
        repository.setWindowClosingEnabled(enabled)
    }

    fun setFastCompleteEnabled(enabled: Boolean) = applyThenReschedule {
        repository.setFastCompleteEnabled(enabled)
    }

    /** Adjust the "window closing" lead time, clamped to a sensible range. */
    fun changeLeadMinutes(delta: Long) = applyThenReschedule {
        val next = (uiState.value.leadMinutes + delta).coerceIn(MIN_LEAD_MINUTES, MAX_LEAD_MINUTES)
        repository.setLeadMinutes(next)
    }

    fun setQuietHoursEnabled(enabled: Boolean) = applyThenReschedule {
        repository.setQuietHoursEnabled(enabled)
    }

    fun setQuietStart(minutesSinceMidnight: Int) = applyThenReschedule {
        repository.setQuietHours(minutesSinceMidnight, uiState.value.quietEndMinutes)
    }

    fun setQuietEnd(minutesSinceMidnight: Int) = applyThenReschedule {
        repository.setQuietHours(uiState.value.quietStartMinutes, minutesSinceMidnight)
    }

    private fun applyThenReschedule(block: suspend () -> Unit) {
        viewModelScope.launch {
            block()
            rescheduleReminders.reschedule()
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5000L
        const val MIN_LEAD_MINUTES = 5L
        const val MAX_LEAD_MINUTES = 120L
    }
}
