package com.crazystudio.sportrecorder.fake

import com.crazystudio.sportrecorder.domain.model.DietSettings
import com.crazystudio.sportrecorder.domain.model.FastingWindow
import com.crazystudio.sportrecorder.domain.repository.DietSettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeDietSettingsRepository(
    initial: DietSettings = DietSettings(fastingHours = 16, eatingHours = 8),
) : DietSettingsRepository {
    private val state = MutableStateFlow(initial)

    override val settings: Flow<DietSettings> = state

    override suspend fun setSelection(window: FastingWindow) {
        state.value = DietSettings(
            fastingHours = window.fastingHours,
            eatingHours = window.eatingHours,
        )
    }
}
