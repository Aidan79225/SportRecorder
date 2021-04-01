package com.crazystudio.sportrecorder.ui.diet.select

import androidx.lifecycle.ViewModel
import androidx.lifecycle.map
import com.crazystudio.sportrecorder.R
import com.crazystudio.sportrecorder.SportApplication

class SelectFastingTypeViewModel: ViewModel() {
    private val fastingTypeDao = SportApplication.db.getFastingTypeDao()

    val selectFastingItemLiveData = fastingTypeDao.liveLast(10).map {
        return@map mutableListOf(
            FastingItem.TitleFastingItem,
            FastingItem.DefaultFastingItem(
                R.string.diet_fasting_type_trainee,
                14,
                10
            ),
            FastingItem.DefaultFastingItem(
                R.string.diet_fasting_type_normal,
                16,
                8
            ),
            FastingItem.DefaultFastingItem(
                R.string.diet_fasting_type_expert,
                20,
                4
            ),
            FastingItem.DefaultFastingItem(R.string.diet_fasting_type_master,
                23,
                1
            ),
            FastingItem.DefaultFastingItem(R.string.diet_fasting_type_monk,
                47,
                1
            )
        ).apply {
            addAll(it.map {
                FastingItem.CustomFastingItem(it.fastingHours, it.eatingHours)
            })
            add(FastingItem.AddFastingItem)
        }
    }


}