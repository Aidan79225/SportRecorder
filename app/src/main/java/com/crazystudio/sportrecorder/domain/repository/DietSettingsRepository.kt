package com.crazystudio.sportrecorder.domain.repository

import com.crazystudio.sportrecorder.domain.model.DietSettings
import kotlinx.coroutines.flow.Flow

interface DietSettingsRepository {
    /** Emits current settings and re-emits on every change. */
    val settings: Flow<DietSettings>
}
