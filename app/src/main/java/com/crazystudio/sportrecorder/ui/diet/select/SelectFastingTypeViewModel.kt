package com.crazystudio.sportrecorder.ui.diet.select

import androidx.lifecycle.ViewModel
import com.crazystudio.sportrecorder.database.AppDatabase
import com.crazystudio.sportrecorder.entity.FastingType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

@HiltViewModel
class SelectFastingTypeViewModel @Inject constructor(val db: AppDatabase): ViewModel() {
    private val fastingTypeDao = db.getFastingTypeDao()

    val fastingItemFlow = fastingTypeDao.flowLast(10).map {
        return@map FastingItem.defaultFastingItems + it.map {
            FastingItem.CustomFastingItem(it.fastingHours, it.eatingHours)
        }.reversed()
    }
}