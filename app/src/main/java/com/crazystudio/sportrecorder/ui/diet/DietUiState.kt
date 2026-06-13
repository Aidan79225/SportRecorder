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
    // Fast window blocks below the ring; null hides them (e.g. IDLE / no record).
    val fastStart: FastTimeLabel? = null,
    val fastEnd: FastTimeLabel? = null,
)

/** A fast boundary rendered as a time plus a relative day (or a date when out of range). */
data class FastTimeLabel(
    val time: String, // "HH:mm"
    val day: RelativeDay,
    val date: String, // "M/d", shown when [day] == RelativeDay.OTHER
)

/** Where a fast boundary falls relative to "now"'s calendar day. */
enum class RelativeDay { YESTERDAY, TODAY, TOMORROW, OTHER }
