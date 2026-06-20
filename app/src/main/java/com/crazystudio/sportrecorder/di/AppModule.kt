package com.crazystudio.sportrecorder.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import com.crazystudio.sportrecorder.data.AndroidPhotoFileStore
import com.crazystudio.sportrecorder.data.PhotoFileStore
import com.crazystudio.sportrecorder.data.repository.DietSettingsRepositoryImpl
import com.crazystudio.sportrecorder.data.repository.EatRecordRepositoryImpl
import com.crazystudio.sportrecorder.data.repository.FastingTypeRepositoryImpl
import com.crazystudio.sportrecorder.data.repository.ReminderPreferencesRepositoryImpl
import com.crazystudio.sportrecorder.database.AppDatabase
import com.crazystudio.sportrecorder.database.Migrations
import com.crazystudio.sportrecorder.domain.reminder.ReminderScheduler
import com.crazystudio.sportrecorder.domain.reminder.RemindersRescheduler
import com.crazystudio.sportrecorder.domain.repository.DietSettingsRepository
import com.crazystudio.sportrecorder.domain.repository.EatRecordRepository
import com.crazystudio.sportrecorder.domain.repository.FastingTypeRepository
import com.crazystudio.sportrecorder.domain.repository.ReminderPreferencesRepository
import com.crazystudio.sportrecorder.domain.usecase.CreateCustomFastingTypeUseCase
import com.crazystudio.sportrecorder.domain.usecase.DeleteEatRecordUseCase
import com.crazystudio.sportrecorder.domain.usecase.LoadEatRecordUseCase
import com.crazystudio.sportrecorder.domain.usecase.ObserveCustomFastingTypesUseCase
import com.crazystudio.sportrecorder.domain.usecase.ObserveDietStateUseCase
import com.crazystudio.sportrecorder.domain.usecase.ObserveEatRecordsUseCase
import com.crazystudio.sportrecorder.domain.usecase.RescheduleRemindersUseCase
import com.crazystudio.sportrecorder.domain.usecase.SaveEatRecordUseCase
import com.crazystudio.sportrecorder.domain.usecase.SaveFastingSelectionUseCase
import com.crazystudio.sportrecorder.reminder.AlarmReminderScheduler
import com.crazystudio.sportrecorder.reminder.ReminderNotifier
import com.crazystudio.sportrecorder.ui.diet.DietViewModel
import com.crazystudio.sportrecorder.ui.diet.create.fasting.CreateFastingTypeViewModel
import com.crazystudio.sportrecorder.ui.diet.editor.EatTimeEditorViewModel
import com.crazystudio.sportrecorder.ui.diet.record.DietRecordViewModel
import com.crazystudio.sportrecorder.ui.diet.select.SelectFastingTypeViewModel
import com.crazystudio.sportrecorder.ui.insights.InsightsViewModel
import com.crazystudio.sportrecorder.ui.settings.SettingsViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

// Must match the legacy SharedPreferences file name so SharedPreferencesMigration finds it.
private const val DIET_PREFERENCES_NAME = "diet_preference"

val appModule = module {
    // Persistence
    single<DataStore<Preferences>> {
        PreferenceDataStoreFactory.create(
            migrations = listOf(SharedPreferencesMigration(androidContext(), DIET_PREFERENCES_NAME)),
            produceFile = { androidContext().preferencesDataStoreFile(DIET_PREFERENCES_NAME) },
        )
    }
    single {
        Room.databaseBuilder(androidContext(), AppDatabase::class.java, "database-name").apply {
            Migrations.getMigrations().forEach { addMigrations(it) }
        }.build()
    }
    single { get<AppDatabase>().getEatTimeDao() }
    single { get<AppDatabase>().getFastingTypeDao() }
    single { get<AppDatabase>().getPhotoDao() }

    // Repositories
    single<PhotoFileStore> { AndroidPhotoFileStore(androidContext()) }
    single<EatRecordRepository> { EatRecordRepositoryImpl(get(), get(), get(), get()) }
    single<DietSettingsRepository> { DietSettingsRepositoryImpl(get()) }
    single<FastingTypeRepository> { FastingTypeRepositoryImpl(get()) }
    single<ReminderPreferencesRepository> { ReminderPreferencesRepositoryImpl(get()) }

    // Reminders (Android side) + rescheduler
    single { ReminderNotifier(androidContext()) }
    single<ReminderScheduler> { AlarmReminderScheduler(androidContext(), get()) }
    single<RemindersRescheduler> { RescheduleRemindersUseCase(get(), get(), get(), get()) }

    // Use cases
    factory { ObserveDietStateUseCase(get(), get()) }
    factory { ObserveEatRecordsUseCase(get()) }
    factory { ObserveCustomFastingTypesUseCase(get()) }
    factory { CreateCustomFastingTypeUseCase(get()) }
    factory { LoadEatRecordUseCase(get()) }
    factory { SaveEatRecordUseCase(get(), get()) }
    factory { DeleteEatRecordUseCase(get(), get()) }
    factory { SaveFastingSelectionUseCase(get(), get()) }

    // ViewModels
    viewModel { DietViewModel(get()) }
    viewModel { DietRecordViewModel(get(), get()) }
    viewModel { InsightsViewModel(get(), get()) }
    viewModel { SettingsViewModel(get(), get()) }
    viewModel { SelectFastingTypeViewModel(get(), get()) }
    viewModel { CreateFastingTypeViewModel(get()) }
    viewModel { EatTimeEditorViewModel(androidContext(), get(), get(), get()) }
}
