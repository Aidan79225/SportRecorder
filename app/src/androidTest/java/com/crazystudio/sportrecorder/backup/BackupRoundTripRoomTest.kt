package com.crazystudio.sportrecorder.backup

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.crazystudio.sportrecorder.data.PhotoFileStore
import com.crazystudio.sportrecorder.data.repository.DietSettingsRepositoryImpl
import com.crazystudio.sportrecorder.data.repository.EatRecordRepositoryImpl
import com.crazystudio.sportrecorder.data.repository.FastingTypeRepositoryImpl
import com.crazystudio.sportrecorder.data.repository.ReminderPreferencesRepositoryImpl
import com.crazystudio.sportrecorder.data.repository.VenueRepositoryImpl
import com.crazystudio.sportrecorder.database.AppDatabase
import com.crazystudio.sportrecorder.domain.model.CustomFastingType
import com.crazystudio.sportrecorder.domain.model.DietSettings
import com.crazystudio.sportrecorder.domain.model.EatRecord
import com.crazystudio.sportrecorder.domain.model.FastingWindow
import com.crazystudio.sportrecorder.domain.model.GeoPoint
import com.crazystudio.sportrecorder.domain.reminder.RemindersRescheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/**
 * BackupService over the REAL Room repositories and a REAL DataStore, with only the cloud faked.
 * This is the one place `EatRecordRepositoryImpl.replaceAll` is exercised against SQLite rather
 * than a fake that restates its contract.
 */
@RunWith(AndroidJUnit4::class)
class BackupRoundTripRoomTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var db: AppDatabase
    private lateinit var dataStoreScope: CoroutineScope
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var eatRepo: EatRecordRepositoryImpl
    private lateinit var venueRepo: VenueRepositoryImpl
    private lateinit var fastingRepo: FastingTypeRepositoryImpl
    private lateinit var settingsRepo: DietSettingsRepositoryImpl
    private lateinit var prefsRepo: ReminderPreferencesRepositoryImpl
    private val store = GatedBackupStore()
    private var rescheduleCount = 0
    private val deletedPhotos = mutableListOf<String>()

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        dataStoreScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        dataStore = PreferenceDataStoreFactory.create(scope = dataStoreScope) {
            File(context.cacheDir, "backup-test-${UUID.randomUUID()}.preferences_pb")
        }
        val photoFiles = object : PhotoFileStore {
            override fun delete(fileName: String) {
                deletedPhotos.add(fileName)
            }
        }
        eatRepo = EatRecordRepositoryImpl(db, db.getEatTimeDao(), db.getPhotoDao(), photoFiles)
        venueRepo = VenueRepositoryImpl(db, db.getVenueDao())
        fastingRepo = FastingTypeRepositoryImpl(db, db.getFastingTypeDao())
        settingsRepo = DietSettingsRepositoryImpl(dataStore)
        prefsRepo = ReminderPreferencesRepositoryImpl(dataStore)
    }

    @After fun tearDown() {
        db.close()
        dataStoreScope.cancel()
    }

    private fun service() = BackupService(
        eatRepo, venueRepo, fastingRepo, settingsRepo, prefsRepo, store,
        object : RemindersRescheduler {
            override suspend fun reschedule() {
                rescheduleCount++
            }
        },
        appVersionName = "test",
    ) { 4_242L }

    private suspend fun seedTwoMeals() {
        eatRepo.save(EatRecord(0, 1_000L, GeoPoint(25.03, 121.56), "lunch", emptyList()), listOf("a.webp"), emptyList())
        eatRepo.save(EatRecord(0, 2_000L, null, "dinner", emptyList()), emptyList(), emptyList())
    }

    private fun List<EatRecord>.shape() = map { Triple(it.time, it.note, it.location) to it.photos.map { p -> p.fileName to p.createdAt } }

    @Test fun backup_thenRestore_bringsEverythingBack() = runBlocking {
        seedTwoMeals()
        fastingRepo.add(FastingWindow(18, 6), "my-18-6")
        settingsRepo.setSelection(FastingWindow(20, 4))
        prefsRepo.setLeadMinutes(45)
        val before = eatRepo.observeAll().first()
        val svc = service()

        val info = svc.backup()
        eatRepo.replaceAll(emptyList())
        fastingRepo.replaceAllCustom(emptyList())
        settingsRepo.setSelection(FastingWindow(16, 8))
        prefsRepo.setLeadMinutes(30)
        assertTrue(eatRepo.observeAll().first().isEmpty())

        svc.restore(info.id)

        val after = eatRepo.observeAll().first()
        assertEquals(before.shape(), after.shape()) // newest-first, same content, same photo names
        assertEquals(listOf(CustomFastingType(18, 6, "my-18-6")), fastingRepo.observeRecentCustomTypes().first())
        assertEquals(DietSettings(20, 4), settingsRepo.settings.first())
        assertEquals(45L, prefsRepo.prefs.first().leadMinutes)
        assertEquals(1, rescheduleCount)
        assertEquals(2, info.mealCount)
        assertTrue(deletedPhotos.isEmpty())
    }

    // The design rule behind venue backup: a snapshot names a venue, it never carries an id, because
    // replaceAll deletes everything and Room hands out fresh ones. BackupServiceTest proves the rule
    // against fakes; this is the same path over SQLite.
    @Test fun backup_thenRestore_remapsAMealsVenueToTheFreshId() = runBlocking {
        // A throwaway venue goes first so the venue under test does not hold id 1: a restore that
        // simply re-inserted from scratch could otherwise "pass" by landing on the same id.
        val throwaway = venueRepo.findOrCreate("throwaway", null, null, now = 1L)
        val venue = venueRepo.findOrCreate("大戶屋", 25.03, 121.56, now = 2L)
        eatRepo.save(EatRecord(0, 1_000L, null, "lunch", emptyList(), venue = venue), emptyList(), emptyList())
        assertTrue(venue.id != throwaway.id)
        val info = service().backup()

        eatRepo.replaceAll(emptyList())
        venueRepo.replaceAll(emptyList())
        assertTrue(venueRepo.observeAll().first().isEmpty())

        service().restore(info.id)

        val restored = venueRepo.observeAll().first()
        val restoredVenue = restored.single { it.name == "大戶屋" }
        assertEquals(25.03, restoredVenue.lat!!, 0.0)
        assertEquals(121.56, restoredVenue.lng!!, 0.0)
        assertEquals(setOf("throwaway", "大戶屋"), restored.map { it.name }.toSet())
        val meal = eatRepo.observeAll().first().single()
        assertEquals(restoredVenue.id, meal.venue?.id) // points at the venue as it exists NOW
        assertEquals("大戶屋", meal.venue?.name)
        // Fresh ids, not the pre-backup ones: AUTOINCREMENT never reuses a deleted id.
        assertTrue(restoredVenue.id != venue.id)
        assertTrue(restored.none { it.id == venue.id || it.id == throwaway.id })
    }

    @Test fun restore_ontoDeviceWithData_uploadsSafetySnapshotFirst_withoutPrune() = runBlocking {
        seedTwoMeals()
        val seedJson = BackupJson.encodeToString(
            BackupDocument.serializer(),
            BackupDocument(
                schemaVersion = BackupDocument.SCHEMA_VERSION, createdAt = 1L, appVersionName = "seed",
                meals = listOf(BackupMeal(1, 9_000L, "from-cloud", null, emptyList())),
                fastingTypes = emptyList(),
                dietSettings = BackupDietSettings(16, 8),
                reminderPrefs = BackupReminderPrefs(false, false, 30, false, 1320, 480),
            ),
        )
        store.seedSnapshot(SnapshotInfo("seed", 1L, "seed", 1L, 1), seedJson)

        service().restore("seed")

        val safety = store.uploads.single()
        val safetyDoc = BackupJson.decodeFromString(BackupDocument.serializer(), safety.manifestJson)
        assertEquals(listOf("dinner", "lunch"), safetyDoc.meals.map { it.note }) // newest-first, the pre-restore data
        assertEquals(listOf("a.webp"), safety.uploadedPhotos)
        assertEquals(0, store.pruneCalls)
        assertEquals(listOf("from-cloud"), eatRepo.observeAll().first().map { it.note })
        assertTrue(deletedPhotos.isEmpty())
    }

    @Test fun restore_whenDownloadFails_leavesRoomUntouched() = runBlocking {
        seedTwoMeals()
        val info = service().backup()
        store.failDownloadPhotos = true
        val before = eatRepo.observeAll().first().shape()
        rescheduleCount = 0

        val error = assertThrows(IllegalStateException::class.java) { runBlocking { service().restore(info.id) } }

        assertTrue(error.message!!.contains("simulated"))
        assertEquals(before, eatRepo.observeAll().first().shape())
        assertEquals(0, rescheduleCount)
    }
}
