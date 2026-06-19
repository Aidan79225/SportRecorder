package com.crazystudio.sportrecorder.dagger

import com.crazystudio.sportrecorder.domain.reminder.ReminderScheduler
import com.crazystudio.sportrecorder.domain.reminder.RemindersRescheduler
import com.crazystudio.sportrecorder.domain.usecase.RescheduleRemindersUseCase
import com.crazystudio.sportrecorder.reminder.AlarmReminderScheduler
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ReminderModule {
    @Binds
    @Singleton
    abstract fun bindReminderScheduler(impl: AlarmReminderScheduler): ReminderScheduler

    @Binds
    @Singleton
    abstract fun bindRemindersRescheduler(impl: RescheduleRemindersUseCase): RemindersRescheduler
}
