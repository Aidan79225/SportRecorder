package com.crazystudio.sportrecorder.domain.usecase

import com.crazystudio.sportrecorder.domain.model.FastingWindow
import com.crazystudio.sportrecorder.domain.reminder.RemindersRescheduler
import com.crazystudio.sportrecorder.domain.repository.DietSettingsRepository

class SaveFastingSelectionUseCase constructor(
    private val dietSettingsRepository: DietSettingsRepository,
    private val rescheduleReminders: RemindersRescheduler,
) {
    suspend operator fun invoke(window: FastingWindow) {
        dietSettingsRepository.setSelection(window)
        // Fasting/eating hours changed, so windowEnd / fastTargetAt move too.
        rescheduleReminders.reschedule()
    }
}
