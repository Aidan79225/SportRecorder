package com.crazystudio.sportrecorder.ui.diet.select

import androidx.lifecycle.ViewModel
import com.crazystudio.sportrecorder.database.AppDatabase
import com.crazystudio.sportrecorder.util.Constants
import com.crazystudio.sportrecorder.util.DietPreference
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.map
import javax.inject.Inject

@HiltViewModel
class SelectFastingTypeViewModel @Inject constructor(
    val db: AppDatabase,
    val dietPreference: DietPreference
) : ViewModel() {
    private val fastingTypeDao = db.getFastingTypeDao()

    val fastingItemFlow = fastingTypeDao.flowLast(10).map {
        return@map FastingItem.defaultFastingItems + it.map {
            FastingItem.CustomFastingItem(it.fastingHours, it.eatingHours)
        }.reversed()
    }

    fun saveSelection(fastingHours: Long, eatingHours: Long) {
        dietPreference.preference.edit()
            .putLong(Constants.DIET_FASTING_TIME_INTERVAL, fastingHours)
            .putLong(Constants.DIET_EATING_TIME_INTERVAL, eatingHours)
            .apply()
    }
}
