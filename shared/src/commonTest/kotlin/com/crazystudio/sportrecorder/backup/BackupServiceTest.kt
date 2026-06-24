package com.crazystudio.sportrecorder.backup

import com.crazystudio.sportrecorder.backup.fakes.FakeBackupStore
import com.crazystudio.sportrecorder.backup.fakes.FakeDietSettingsRepository
import com.crazystudio.sportrecorder.backup.fakes.FakeEatRecordRepository
import com.crazystudio.sportrecorder.backup.fakes.FakeFastingTypeRepository
import com.crazystudio.sportrecorder.backup.fakes.FakeReminderPreferencesRepository
import com.crazystudio.sportrecorder.backup.fakes.FakeRemindersRescheduler
import com.crazystudio.sportrecorder.domain.model.CustomFastingType
import com.crazystudio.sportrecorder.domain.model.DietSettings
import com.crazystudio.sportrecorder.domain.model.EatPhoto
import com.crazystudio.sportrecorder.domain.model.EatRecord
import com.crazystudio.sportrecorder.domain.model.GeoPoint
import com.crazystudio.sportrecorder.domain.reminder.ReminderPrefs
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class BackupServiceTest {
    private fun service(eat: FakeEatRecordRepository, store: FakeBackupStore) =
        BackupService(
            eat,
            FakeFastingTypeRepository(),
            FakeDietSettingsRepository(),
            FakeReminderPreferencesRepository(),
            store,
            FakeRemindersRescheduler(),
            appVersionName = "0.6.2",
        ) { 9_999L }

    @Test fun backup_uploadsManifestWithAllDataAndPhotoNames() = runTest {
        val eat = FakeEatRecordRepository(
            listOf(EatRecord(1, 1_700L, null, "n", listOf(EatPhoto(7, "a.webp", 1_701L)))),
        )
        val store = FakeBackupStore()
        service(eat, store).backup()

        val upload = store.uploads.last()
        val doc = BackupJson.decodeFromString(BackupDocument.serializer(), upload.manifestJson)
        assertEquals(1, doc.meals.size)
        assertEquals("0.6.2", doc.appVersionName)
        assertEquals(9_999L, doc.createdAt)
        assertEquals(BackupDocument.SCHEMA_VERSION, doc.schemaVersion)
        assertEquals(listOf("a.webp"), upload.uploadedPhotos)
    }

    @Test fun backup_prunesToKeepLast() = runTest {
        val store = FakeBackupStore()
        service(FakeEatRecordRepository(), store).backup()
        assertEquals(BackupService.KEEP_LAST, store.pruneKeepLast)
    }

    @Test fun restore_replacesLocalDataFromSnapshot() = runTest {
        val store = FakeBackupStore()
        val backupSvc = BackupService(
            FakeEatRecordRepository(
                listOf(EatRecord(1, 1_700L, GeoPoint(25.0, 121.5), "n", listOf(EatPhoto(7, "a.webp", 1_701L)))),
            ),
            FakeFastingTypeRepository(listOf(CustomFastingType(18, 6, "my"))),
            FakeDietSettingsRepository(DietSettings(20, 4)),
            FakeReminderPreferencesRepository(ReminderPrefs(windowClosingEnabled = true, leadMinutes = 45)),
            store,
            FakeRemindersRescheduler(),
            appVersionName = "0.6.2",
        ) { 1L }
        val info = backupSvc.backup()

        val tgtEat = FakeEatRecordRepository()
        val tgtFasting = FakeFastingTypeRepository()
        val tgtSettings = FakeDietSettingsRepository()
        val tgtPrefs = FakeReminderPreferencesRepository()
        val resched = FakeRemindersRescheduler()
        BackupService(tgtEat, tgtFasting, tgtSettings, tgtPrefs, store, resched, appVersionName = "0.6.2") { 1L }
            .restore(info.id)

        assertEquals(1, tgtEat.state.value.size)
        assertEquals("a.webp", tgtEat.state.value.first().photos.first().fileName)
        assertEquals(listOf(CustomFastingType(18, 6, "my")), tgtFasting.state.value)
        assertEquals(DietSettings(20, 4), tgtSettings.state.value)
        assertTrue(tgtPrefs.state.value.windowClosingEnabled)
        assertEquals(45L, tgtPrefs.state.value.leadMinutes)
        assertEquals(1, resched.rescheduleCount)
    }

    @Test fun restore_refusesNewerSchema() = runTest {
        val store = FakeBackupStore()
        val tooNew = BackupDocument(
            schemaVersion = BackupDocument.SCHEMA_VERSION + 1,
            createdAt = 1L,
            appVersionName = "9.9.9",
            meals = emptyList(),
            fastingTypes = emptyList(),
            dietSettings = BackupDietSettings(16, 8),
            reminderPrefs = BackupReminderPrefs(false, false, 30, false, 1320, 480),
        )
        val json = BackupJson.encodeToString(BackupDocument.serializer(), tooNew)
        store.seedSnapshot(SnapshotInfo("s1", 1L, "9.9.9", json.length.toLong()), json)

        val eat = FakeEatRecordRepository(listOf(EatRecord(1, 1_700L, null, "keep", emptyList())))
        val resched = FakeRemindersRescheduler()
        val svc = BackupService(
            eat, FakeFastingTypeRepository(), FakeDietSettingsRepository(),
            FakeReminderPreferencesRepository(), store, resched, appVersionName = "0.6.2",
        ) { 1L }

        assertFailsWith<BackupSchemaTooNewException> { svc.restore("s1") }
        assertEquals(1, eat.state.value.size) // untouched
        assertEquals(0, resched.rescheduleCount)
    }

    @Test fun restore_doesNotWipeLocal_whenDownloadFails() = runTest {
        val store = FakeBackupStore()
        BackupService(
            FakeEatRecordRepository(listOf(EatRecord(2, 2_000L, null, "src", emptyList()))),
            FakeFastingTypeRepository(), FakeDietSettingsRepository(),
            FakeReminderPreferencesRepository(), store, FakeRemindersRescheduler(), appVersionName = "0.6.2",
        ) { 1L }.backup()
        val info = store.uploads.last().info
        store.failDownloadPhotos = true

        val tgtEat = FakeEatRecordRepository(listOf(EatRecord(99, 9_000L, null, "keep", emptyList())))
        val resched = FakeRemindersRescheduler()
        val svc = BackupService(
            tgtEat, FakeFastingTypeRepository(), FakeDietSettingsRepository(),
            FakeReminderPreferencesRepository(), store, resched, appVersionName = "0.6.2",
        ) { 1L }

        assertFailsWith<IllegalStateException> { svc.restore(info.id) }
        assertEquals(listOf(99), tgtEat.state.value.map { it.id }) // untouched
        assertEquals(0, resched.rescheduleCount)
    }
}
