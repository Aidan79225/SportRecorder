package com.crazystudio.sportrecorder.util

import android.content.Context
import android.util.DisplayMetrics

fun Context.dpToPx(dp: Float): Float {
    return dp * resources.displayMetrics.densityDpi / DisplayMetrics.DENSITY_DEFAULT
}