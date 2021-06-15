package com.crazystudio.sportrecorder

import android.app.Application
import androidx.room.Room
import com.crazystudio.sportrecorder.database.AppDatabase
import com.crazystudio.sportrecorder.database.Migrations
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class SportApplication : Application()