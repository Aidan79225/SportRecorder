package com.crazystudio.sportrecorder.util

import android.content.Context

class DietPreference(private val applicationContext: Context) {
    val preference = applicationContext.getSharedPreferences("diet_preference", Context.MODE_PRIVATE)
}
