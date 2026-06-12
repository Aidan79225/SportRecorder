package com.crazystudio.sportrecorder.domain.usecase

import com.crazystudio.sportrecorder.domain.model.FastingWindow
import com.crazystudio.sportrecorder.domain.repository.DietSettingsRepository
import javax.inject.Inject

class SaveFastingSelectionUseCase @Inject constructor(
    private val dietSettingsRepository: DietSettingsRepository,
) {
    suspend operator fun invoke(window: FastingWindow) = dietSettingsRepository.setSelection(window)
}
