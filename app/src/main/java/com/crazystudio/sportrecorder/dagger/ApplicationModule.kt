package com.crazystudio.sportrecorder.dagger

import android.content.Context
import com.crazystudio.sportrecorder.util.StringUtils
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
class ApplicationModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext applicationContext: Context): StringUtils {
        return StringUtils(applicationContext)
    }
}
