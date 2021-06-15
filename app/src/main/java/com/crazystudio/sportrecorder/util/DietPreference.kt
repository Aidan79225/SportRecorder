package com.crazystudio.sportrecorder.util

import android.content.Context
import android.content.SharedPreferences

class DietPreference(private val applicationContext: Context) {
    val preference = applicationContext.getSharedPreferences("diet_preference", Context.MODE_PRIVATE)
}

