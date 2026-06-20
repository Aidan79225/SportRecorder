package com.crazystudio.sportrecorder.ui.diet.select

import com.crazystudio.sportrecorder.shared.resources.Res
import com.crazystudio.sportrecorder.shared.resources.diet_fasting_type_expert
import com.crazystudio.sportrecorder.shared.resources.diet_fasting_type_master
import com.crazystudio.sportrecorder.shared.resources.diet_fasting_type_monk
import com.crazystudio.sportrecorder.shared.resources.diet_fasting_type_normal
import com.crazystudio.sportrecorder.shared.resources.diet_fasting_type_trainee
import org.jetbrains.compose.resources.StringResource

sealed class FastingItem {

    data class DefaultFastingItem(
        val nameRes: StringResource,
        val fastingHours: Long,
        val eatingHours: Long,
    ) : FastingItem()

    data class CustomFastingItem(
        val fastingHours: Long,
        val eatingHours: Long,
        val name: String? = null,
    ) : FastingItem()

    companion object {
        val defaultFastingItems = listOf(
            DefaultFastingItem(Res.string.diet_fasting_type_trainee, 14, 10),
            DefaultFastingItem(Res.string.diet_fasting_type_normal, 16, 8),
            DefaultFastingItem(Res.string.diet_fasting_type_expert, 20, 4),
            DefaultFastingItem(Res.string.diet_fasting_type_master, 23, 1),
            DefaultFastingItem(Res.string.diet_fasting_type_monk, 47, 1),
        )
    }
}
