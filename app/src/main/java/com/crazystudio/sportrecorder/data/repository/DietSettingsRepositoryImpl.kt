package com.crazystudio.sportrecorder.data.repository

import android.content.SharedPreferences
import com.crazystudio.sportrecorder.domain.model.DietSettings
import com.crazystudio.sportrecorder.domain.model.FastingWindow
import com.crazystudio.sportrecorder.domain.repository.DietSettingsRepository
import com.crazystudio.sportrecorder.util.Constants
import com.crazystudio.sportrecorder.util.DietPreference
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject

private const val DEFAULT_FASTING_HOURS = 16L
private const val DEFAULT_EATING_HOURS = 8L

class DietSettingsRepositoryImpl @Inject constructor(
    private val dietPreference: DietPreference,
) : DietSettingsRepository {

    private fun current(): DietSettings = DietSettings(
        fastingHours = dietPreference.preference.getLong(Constants.DIET_FASTING_TIME_INTERVAL, DEFAULT_FASTING_HOURS),
        eatingHours = dietPreference.preference.getLong(Constants.DIET_EATING_TIME_INTERVAL, DEFAULT_EATING_HOURS),
    )

    override val settings: Flow<DietSettings> = callbackFlow {
        trySend(current())
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> trySend(current()) }
        dietPreference.preference.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { dietPreference.preference.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    override suspend fun setSelection(window: FastingWindow) {
        dietPreference.preference.edit()
            .putLong(Constants.DIET_FASTING_TIME_INTERVAL, window.fastingHours)
            .putLong(Constants.DIET_EATING_TIME_INTERVAL, window.eatingHours)
            .apply()
    }
}
