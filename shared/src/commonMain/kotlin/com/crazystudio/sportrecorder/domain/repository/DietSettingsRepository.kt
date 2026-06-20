package com.crazystudio.sportrecorder.domain.repository

import com.crazystudio.sportrecorder.domain.model.DietSettings
import com.crazystudio.sportrecorder.domain.model.FastingWindow
import kotlinx.coroutines.flow.Flow

interface DietSettingsRepository {
    /** Emits current settings and re-emits on every change. */
    val settings: Flow<DietSettings>

    /** Persist the selected fasting/eating window (triggers a [settings] re-emit). */
    suspend fun setSelection(window: FastingWindow)
}
