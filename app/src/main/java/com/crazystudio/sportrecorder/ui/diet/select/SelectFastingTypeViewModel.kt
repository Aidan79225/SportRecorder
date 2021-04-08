package com.crazystudio.sportrecorder.ui.diet.select

import androidx.lifecycle.ViewModel
import androidx.lifecycle.map
import com.crazystudio.sportrecorder.R
import com.crazystudio.sportrecorder.SportApplication

class SelectFastingTypeViewModel: ViewModel() {
    private val fastingTypeDao = SportApplication.db.getFastingTypeDao()

    val selectFastingItemLiveData = fastingTypeDao.liveLast(10).map {
        return@map FastingItem.defaultFastingItems.toMutableList().apply {
            addAll(it.map {
                FastingItem.CustomFastingItem(it.fastingHours, it.eatingHours)
            })
            add(FastingItem.AddFastingItem)
        }
    }


}