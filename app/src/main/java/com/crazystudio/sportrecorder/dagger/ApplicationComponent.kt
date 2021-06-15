package com.crazystudio.sportrecorder.dagger

import com.crazystudio.sportrecorder.database.AppDatabase
import dagger.Component
import javax.inject.Singleton

@Singleton
//@Component(modules = [DatabaseModule::class])
interface ApplicationComponent {
    fun appDatabase(): AppDatabase
}