package com.crazystudio.sportrecorder.ui.diet.select

import androidx.lifecycle.ViewModel
import androidx.lifecycle.map
import com.crazystudio.sportrecorder.database.AppDatabase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class SelectFastingTypeViewModel @Inject constructor(val db: AppDatabase): ViewModel() {
    private val fastingTypeDao = db.getFastingTypeDao()

    val selectFastingItemLiveData = fastingTypeDao.liveLast(10).map {
        return@map FastingItem.defaultFastingItems.toMutableList().apply {
            addAll(it.map {
                FastingItem.CustomFastingItem(it.fastingHours, it.eatingHours)
            })
            add(FastingItem.AddFastingItem)
        }
    }


}