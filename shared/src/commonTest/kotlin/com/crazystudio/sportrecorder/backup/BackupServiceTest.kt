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
import kotlin.test.assertIs
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

    private class RecordingProgress : BackupProgress {
        val reports = mutableListOf<Triple<BackupStep, Int, Int>>()
        override fun report(step: BackupStep, done: Int, total: Int) { reports.add(Triple(step, done, total)) }
        val steps get() = reports.map { it.first }.distinct()
    }

    @Test fun backup_reportsStepsInOrderWithPhotoCounts() = runTest {
        val eat = FakeEatRecordRepository(
            listOf(
                EatRecord(1, 1_700L, null, "n", listOf(EatPhoto(7, "a.webp", 1_701L))),
                EatRecord(2, 1_800L, null, "m", listOf(EatPhoto(8, "b.webp", 1_801L))),
            ),
        )
        val progress = RecordingProgress()
        service(eat, FakeBackupStore()).backup(progress)

        assertEquals(
            listOf(BackupStep.Preparing, BackupStep.UploadingPhotos, BackupStep.UploadingManifest, BackupStep.Pruning),
            progress.steps,
        )
        assertEquals(Triple(BackupStep.UploadingPhotos, 2, 2), progress.reports.last { it.first == BackupStep.UploadingPhotos })
        assertEquals(Triple(BackupStep.UploadingManifest, 1, 1), progress.reports.last { it.first == BackupStep.UploadingManifest })
    }

    private fun emptyDocJson(): String = BackupJson.encodeToString(
        BackupDocument.serializer(),
        BackupDocument(
            schemaVersion = BackupDocument.SCHEMA_VERSION,
            createdAt = 1L,
            appVersionName = "0.6.2",
            meals = emptyList(),
            fastingTypes = emptyList(),
            dietSettings = BackupDietSettings(16, 8),
            reminderPrefs = BackupReminderPrefs(false, false, 30, false, 1320, 480),
        ),
    )

    @Test fun restore_backsUpCurrentDataFirst_withoutPruning() = runTest {
        val store = FakeBackupStore()
        store.seedSnapshot(SnapshotInfo("target", 1L, "0.6.2", 1L), emptyDocJson())
        val eat = FakeEatRecordRepository(
            listOf(
                EatRecord(1, 1_700L, null, "keep-me", listOf(EatPhoto(7, "a.webp", 1_701L))),
                EatRecord(2, 1_800L, null, "me-too", emptyList()),
            ),
        )
        val progress = RecordingProgress()

        service(eat, store).restore("target", progress)

        val safety = store.uploads.single()
        val safetyDoc = BackupJson.decodeFromString(BackupDocument.serializer(), safety.manifestJson)
        assertEquals(listOf("keep-me", "me-too"), safetyDoc.meals.mapNotNull { it.note }.sorted())
        assertEquals(listOf("a.webp"), safety.uploadedPhotos)
        assertEquals(null, store.pruneKeepLast) // the safety snapshot must never prune the target away
        assertEquals(emptyList(), eat.state.value) // and the restore still applied
        assertEquals(
            listOf(BackupStep.DownloadingManifest, BackupStep.SafetyBackup, BackupStep.DownloadingPhotos, BackupStep.Applying),
            progress.steps,
        )
    }

    @Test fun restore_safetyBackupFails_throwsDistinctError_andLeavesLocalUntouched() = runTest {
        val store = FakeBackupStore()
        store.seedSnapshot(SnapshotInfo("target", 1L, "0.6.2", 1L), emptyDocJson())
        store.failUploadSnapshot = true
        val eat = FakeEatRecordRepository(listOf(EatRecord(5, 5_000L, null, "keep", emptyList())))
        val resched = FakeRemindersRescheduler()
        val svc = BackupService(
            eat, FakeFastingTypeRepository(), FakeDietSettingsRepository(),
            FakeReminderPreferencesRepository(), store, resched, appVersionName = "0.6.2",
        ) { 1L }

        val error = assertFailsWith<SafetyBackupFailedException> { svc.restore("target") }
        assertIs<IllegalStateException>(error.cause)
        assertEquals(listOf(5), eat.state.value.map { it.id }) // untouched
        assertTrue(store.downloadedPhotosFor.isEmpty()) // never started downloading
        assertEquals(0, resched.rescheduleCount)
    }

    @Test fun restore_skipsSafetyBackup_whenDeviceHasNoRecords() = runTest {
        val store = FakeBackupStore()
        store.seedSnapshot(SnapshotInfo("target", 1L, "0.6.2", 1L), emptyDocJson())
        val progress = RecordingProgress()

        service(FakeEatRecordRepository(), store).restore("target", progress)

        assertTrue(store.uploads.isEmpty())
        assertTrue(BackupStep.SafetyBackup !in progress.steps)
    }

    @Test fun restore_passesReferencedPhotoNamesToStore() = runTest {
        val store = FakeBackupStore()
        val info = service(
            FakeEatRecordRepository(listOf(EatRecord(1, 1L, null, null, listOf(EatPhoto(1, "x.webp", 1L))))),
            store,
        ).backup()

        service(FakeEatRecordRepository(), store).restore(info.id)

        assertEquals(listOf("x.webp"), store.lastDownloadedPhotoNames)
    }
}
