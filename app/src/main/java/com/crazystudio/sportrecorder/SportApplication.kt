package com.crazystudio.sportrecorder

import android.app.Application
import com.crazystudio.sportrecorder.di.appModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class SportApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@SportApplication)
            modules(appModule)
        }
    }
}
