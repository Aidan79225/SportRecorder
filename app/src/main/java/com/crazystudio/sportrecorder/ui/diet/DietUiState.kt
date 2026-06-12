package com.crazystudio.sportrecorder.ui.diet

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.crazystudio.sportrecorder.R
import com.crazystudio.sportrecorder.ui.diet.select.FastingItem

data class DietUiState(
    val elapsedText: String = "00:00:00",
    val progress: Float = 0f, // 0..100 for CircleProgress
    val fastingLabel: String = "", // e.g. "16 : 8"
    val selectedFastingItem: FastingItem.DefaultFastingItem? = null,
    @DrawableRes val statusIcon: Int = R.drawable.ic_baseline_fastfood_24,
    @StringRes val statusTextRes: Int = R.string.diet_status_fasting,
    @StringRes val promptTextRes: Int = R.string.diet_fasting_time,
    // Fast window shown below the ring; empty [fastStart] hides the line.
    val fastStart: String = "",
    val fastEnd: String = "",
    val fastEndsNextDay: Boolean = false,
)
