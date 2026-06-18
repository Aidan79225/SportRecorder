package com.crazystudio.sportrecorder.ui.diet.select

import com.crazystudio.sportrecorder.R
sealed class FastingItem {

    data class DefaultFastingItem(
        val nameResId: Int,
        val fastingHours: Long,
        val eatingHours: Long
    ) : FastingItem()

    data class CustomFastingItem(
        val fastingHours: Long,
        val eatingHours: Long,
        val name: String? = null
    ) : FastingItem()

    companion object {
        val defaultFastingItems = listOf(
            DefaultFastingItem(
                R.string.diet_fasting_type_trainee,
                14,
                10
            ),
            DefaultFastingItem(
                R.string.diet_fasting_type_normal,
                16,
                8
            ),
            DefaultFastingItem(
                R.string.diet_fasting_type_expert,
                20,
                4
            ),
            DefaultFastingItem(
                R.string.diet_fasting_type_master,
                23,
                1
            ),
            DefaultFastingItem(
                R.string.diet_fasting_type_monk,
                47,
                1
            )
        )
    }
}
