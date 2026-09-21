package com.crazystudio.sportrecorder.flow

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.crazystudio.sportrecorder.data.repository.DietSettingsRepositoryImpl
import com.crazystudio.sportrecorder.data.repository.ReminderPreferencesRepositoryImpl
import com.crazystudio.sportrecorder.domain.model.DietSettings
import com.crazystudio.sportrecorder.domain.model.EatRecord
import com.crazystudio.sportrecorder.domain.model.FastingWindow
import com.crazystudio.sportrecorder.domain.reminder.ReminderPrefs
import com.crazystudio.sportrecorder.domain.reminder.ReminderType
import com.crazystudio.sportrecorder.domain.usecase.RescheduleRemindersUseCase
import com.crazystudio.sportrecorder.domain.usecase.SaveFastingSelectionUseCase
import com.crazystudio.sportrecorder.fake.FakeEatRecordRepository
import com.crazystudio.sportrecorder.fake.FakeReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * 設定 journey over a **real on-disk DataStore** — the only place in this suite where the actual
 * persistence layer runs. Pins that a changed fasting window / reminder preference is still there
 * after the store is torn down and rebuilt (i.e. across an app restart), and that changing either
 * re-arms the reminder schedule.
 *
 * Only quiet-hours-disabled plans are asserted, so nothing here depends on the CI machine's
 * timezone; quiet-hours behaviour is covered by `ReminderPlannerTest` and `ReminderFlowTest`.
 */
class SettingsPersistenceFlowTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val day0 = 1_699_920_000_000L
    private fun h(n: Long) = TimeUnit.HOURS.toMillis(n)

    private fun storeFile(dir: File) = File(dir, "diet.preferences_pb")

    /**
     * Runs [block] against a DataStore over [dir], then tears the store down. DataStore refuses a
     * second instance over a file that is still active, so the scope is cancelled before
     * returning — which is exactly what makes "reopen the app" testable here.
     */
    private suspend fun <T> withStore(dir: File, block: suspend (DataStore<Preferences>) -> T): T {
        val job = SupervisorJob()
        val scope = CoroutineScope(currentCoroutineContext() + job)
        try {
            val store = PreferenceDataStoreFactory.create(scope = scope, produceFile = { storeFile(dir) })
            return block(store)
        } finally {
            job.cancelAndJoin()
        }
    }

    @Test
    fun emptyStore_hasTheDocumentedDefaults() = runTest {
        val dir = tmp.newFolder()

        withStore(dir) { store ->
            assertEquals(
                DietSettings(fastingHours = 16, eatingHours = 8),
                DietSettingsRepositoryImpl(store).settings.first(),
            )
            val prefs = ReminderPreferencesRepositoryImpl(store).prefs.first()
            // Reminders are opt-in: nothing nags until the user asks for it.
            assertFalse(prefs.windowClosingEnabled)
            assertFalse(prefs.fastCompleteEnabled)
            assertEquals(ReminderPrefs(), prefs)
        }
    }

    @Test
    fun fastingWindow_survivesAStoreRestart() = runTest {
        val dir = tmp.newFolder()

        withStore(dir) { store ->
            DietSettingsRepositoryImpl(store).setSelection(FastingWindow(fastingHours = 20, eatingHours = 4))
        }

        withStore(dir) { store ->
            assertEquals(
                DietSettings(fastingHours = 20, eatingHours = 4),
                DietSettingsRepositoryImpl(store).settings.first(),
            )
        }
    }

    @Test
    fun everyReminderPreference_survivesAStoreRestart() = runTest {
        val dir = tmp.newFolder()

        withStore(dir) { store ->
            val repo = ReminderPreferencesRepositoryImpl(store)
            repo.setWindowClosingEnabled(true)
            repo.setFastCompleteEnabled(true)
            repo.setLeadMinutes(45)
            repo.setQuietHoursEnabled(true)
            repo.setQuietHours(startMinutes = 1_290, endMinutes = 450)
        }

        withStore(dir) { store ->
            assertEquals(
                ReminderPrefs(
                    windowClosingEnabled = true,
                    fastCompleteEnabled = true,
                    leadMinutes = 45,
                    quietHoursEnabled = true,
                    quietStartMinutes = 1_290,
                    quietEndMinutes = 450,
                ),
                ReminderPreferencesRepositoryImpl(store).prefs.first(),
            )
        }
    }

    @Test
    fun changingTheFastingWindow_persistsAndRearmsTheSchedule() = runTest {
        val dir = tmp.newFolder()
        val scheduler = FakeReminderScheduler()

        withStore(dir) { store ->
            val settingsRepo = DietSettingsRepositoryImpl(store)
            val prefsRepo = ReminderPreferencesRepositoryImpl(store)
            prefsRepo.setFastCompleteEnabled(true)
            val reschedule = RescheduleRemindersUseCase(
                eatRecordRepository = FakeEatRecordRepository(listOf(meal(day0 + h(10)))),
                dietSettingsRepository = settingsRepo,
                reminderPreferencesRepository = prefsRepo,
                scheduler = scheduler,
                now = { day0 + h(11) },
            )

            SaveFastingSelectionUseCase(settingsRepo, reschedule)(
                FastingWindow(fastingHours = 20, eatingHours = 4),
            )
        }

        // Single meal at +10h → the fast clock starts at +11h; a 20h fast targets +31h.
        assertEquals(1, scheduler.scheduleCount)
        assertEquals(ReminderType.FAST_COMPLETE, scheduler.lastScheduled.single().type)
        assertEquals(day0 + h(31), scheduler.lastScheduled.single().triggerAtMillis)

        withStore(dir) { store ->
            assertEquals(
                DietSettings(fastingHours = 20, eatingHours = 4),
                DietSettingsRepositoryImpl(store).settings.first(),
            )
        }
    }

    @Test
    fun enablingAReminder_persistsAndArmsTheSchedule() = runTest {
        val dir = tmp.newFolder()
        val scheduler = FakeReminderScheduler()

        withStore(dir) { store ->
            val prefsRepo = ReminderPreferencesRepositoryImpl(store)
            val reschedule = RescheduleRemindersUseCase(
                eatRecordRepository = FakeEatRecordRepository(listOf(meal(day0 + h(10)))),
                dietSettingsRepository = DietSettingsRepositoryImpl(store),
                reminderPreferencesRepository = prefsRepo,
                scheduler = scheduler,
                now = { day0 + h(11) },
            )

            reschedule.reschedule()
            assertTrue(scheduler.lastScheduled.isEmpty()) // still opted out

            prefsRepo.setWindowClosingEnabled(true)
            prefsRepo.setLeadMinutes(45)
            reschedule.reschedule()
        }

        // 16:8 → the window closes at +18h; a 45-minute lead fires at +17h15m.
        val armed = scheduler.lastScheduled.single()
        assertEquals(ReminderType.WINDOW_CLOSING, armed.type)
        assertEquals(day0 + h(18) - TimeUnit.MINUTES.toMillis(45), armed.triggerAtMillis)
    }

    private fun meal(time: Long) =
        EatRecord(id = 1, time = time, location = null, note = null, photos = emptyList())
}
