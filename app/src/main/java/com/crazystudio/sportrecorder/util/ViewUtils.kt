package com.crazystudio.sportrecorder.util

import android.content.Context
import android.util.DisplayMetrics

fun Context.dpToPx(dp: Float): Float {
    return dp * resources.displayMetrics.densityDpi / DisplayMetrics.DENSITY_DEFAULT
}

fun Context.spToPx(sp: Float): Float {
    return sp * resources.displayMetrics.scaledDensity / DisplayMetrics.DENSITY_DEFAULT
}
