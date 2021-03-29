package com.crazystudio.sportrecorder.util

import android.content.Context
import android.content.SharedPreferences
import com.crazystudio.sportrecorder.SportApplication

object SharedPreferenceUtils {
    fun getDietPreference(): SharedPreferences {
        return SportApplication.application.getSharedPreferences("diet_preference", Context.MODE_PRIVATE)
    }
}

