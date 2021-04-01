package com.crazystudio.sportrecorder.ui.diet.select

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.crazystudio.sportrecorder.R

class SelectFastingTypeViewModel: ViewModel() {
    val selectFastingItemLiveData = MutableLiveData<List<FastingItem>>()
    init {
        update()
    }
    private fun update() {
        selectFastingItemLiveData.value = listOf(
            FastingItem.TitleFastingItem,
            FastingItem.DefaultFastingItem(
                R.string.diet_fasting_type_trainee,
                14,
                10,
                R.color.google_green
            ),
            FastingItem.DefaultFastingItem(
                R.string.diet_fasting_type_normal,
                16,
                8,
                R.color.google_blue
            ),
            FastingItem.DefaultFastingItem(
                R.string.diet_fasting_type_expert,
                20,
                4,
                R.color.light_green
            ),
            FastingItem.DefaultFastingItem(R.string.diet_fasting_type_master,
                23,
                1,
                R.color.google_yellow
            ),
            FastingItem.DefaultFastingItem(R.string.diet_fasting_type_monk,
                47,
                1,
                R.color.google_red
            ),
            FastingItem.AddFastingItem
        )
    }

}