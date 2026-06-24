package com.crazystudio.sportrecorder.backup

import com.crazystudio.sportrecorder.backup.fakes.FakeBackupStore
import com.crazystudio.sportrecorder.backup.fakes.FakeDietSettingsRepository
import com.crazystudio.sportrecorder.backup.fakes.FakeEatRecordRepository
import com.crazystudio.sportrecorder.backup.fakes.FakeFastingTypeRepository
import com.crazystudio.sportrecorder.backup.fakes.FakeReminderPreferencesRepository
import com.crazystudio.sportrecorder.domain.model.EatPhoto
import com.crazystudio.sportrecorder.domain.model.EatRecord
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class BackupServiceTest {
    private fun service(eat: FakeEatRecordRepository, store: FakeBackupStore) =
        BackupService(
            eat,
            FakeFastingTypeRepository(),
            FakeDietSettingsRepository(),
            FakeReminderPreferencesRepository(),
            store,
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
}
