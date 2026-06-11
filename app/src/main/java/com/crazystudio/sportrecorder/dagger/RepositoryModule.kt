package com.crazystudio.sportrecorder.dagger

import com.crazystudio.sportrecorder.data.repository.DietSettingsRepositoryImpl
import com.crazystudio.sportrecorder.data.repository.EatRecordRepositoryImpl
import com.crazystudio.sportrecorder.domain.repository.DietSettingsRepository
import com.crazystudio.sportrecorder.domain.repository.EatRecordRepository
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
}
