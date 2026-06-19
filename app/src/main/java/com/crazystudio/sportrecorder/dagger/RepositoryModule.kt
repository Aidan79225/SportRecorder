package com.crazystudio.sportrecorder.dagger

import com.crazystudio.sportrecorder.data.repository.DietSettingsRepositoryImpl
import com.crazystudio.sportrecorder.data.repository.EatRecordRepositoryImpl
import com.crazystudio.sportrecorder.data.repository.FastingTypeRepositoryImpl
import com.crazystudio.sportrecorder.data.repository.ReminderPreferencesRepositoryImpl
import com.crazystudio.sportrecorder.domain.repository.DietSettingsRepository
import com.crazystudio.sportrecorder.domain.repository.EatRecordRepository
import com.crazystudio.sportrecorder.domain.repository.FastingTypeRepository
import com.crazystudio.sportrecorder.domain.repository.ReminderPreferencesRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun bindEatRecordRepository(impl: EatRecordRepositoryImpl): EatRecordRepository

    @Binds
    @Singleton
    abstract fun bindDietSettingsRepository(impl: DietSettingsRepositoryImpl): DietSettingsRepository

    @Binds
    @Singleton
    abstract fun bindFastingTypeRepository(impl: FastingTypeRepositoryImpl): FastingTypeRepository

    @Binds
    @Singleton
    abstract fun bindReminderPreferencesRepository(
        impl: ReminderPreferencesRepositoryImpl,
    ): ReminderPreferencesRepository
}
