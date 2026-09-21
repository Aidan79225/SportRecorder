package com.crazystudio.sportrecorder.flow

import com.crazystudio.sportrecorder.backup.BackupDietSettings
import com.crazystudio.sportrecorder.backup.BackupDocument
import com.crazystudio.sportrecorder.backup.BackupJson
import com.crazystudio.sportrecorder.backup.BackupReminderPrefs
import com.crazystudio.sportrecorder.backup.BackupSchemaTooNewException
import com.crazystudio.sportrecorder.backup.BackupService
import com.crazystudio.sportrecorder.backup.SnapshotInfo
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 備份 / 還原 journey. The most mission-critical tests in the repo: *"陪你留住每一個美好的當下"*
 * means a user's records must survive a reinstall — and, just as importantly, must not be
 * destroyed by a backup that goes wrong halfway.
 */
class BackupRestoreFlowTest {

    private val f = 1_700_000_000_000L
    private fun h(n: Long) = n * 3_600_000L

    /** One "device": the four repositories plus a [BackupService] bound to [store]. */
    private class Device(
        store: FakeBackupStore,
        meals: List<EatRecord> = emptyList(),
        types: List<CustomFastingType> = emptyList(),
        settings: DietSettings = DietSettings(fastingHours = 16, eatingHours = 8),
        prefs: ReminderPrefs = ReminderPrefs(),
    ) {
        val eatRepo = FakeEatRecordRepository(meals)
        val typeRepo = FakeFastingTypeRepository(types)
        val settingsRepo = FakeDietSettingsRepository(settings)
        val prefsRepo = FakeReminderPreferencesRepository(prefs)
        val rescheduler = FakeRemindersRescheduler()
        val service = BackupService(
            eatRepo,
            typeRepo,
            settingsRepo,
            prefsRepo,
            store,
            rescheduler,
            appVersionName = APP_VERSION,
        ) { BACKUP_CREATED_AT }
    }

    private fun richMeals() = listOf(
        EatRecord(
            id = 1,
            time = f,
            location = GeoPoint(25.033, 121.565),
            note = "在公園吃的早餐",
            photos = listOf(EatPhoto(7, "morning.webp", f + 10), EatPhoto(8, "park.webp", f + 20)),
        ),
        EatRecord(id = 2, time = f + h(5), location = null, note = null, photos = emptyList()),
    )

    private val richPrefs = ReminderPrefs(
        windowClosingEnabled = true,
        fastCompleteEnabled = true,
        leadMinutes = 45,
        quietHoursEnabled = true,
        quietStartMinutes = 1_290,
        quietEndMinutes = 450,
    )

    @Test
    fun roundTrip_restoresEveryFieldOntoAWipedDevice() = runTest {
        val store = FakeBackupStore()
        val source = Device(
            store,
            meals = richMeals(),
            types = listOf(CustomFastingType(18, 6, "我的節奏"), CustomFastingType(12, 12, null)),
            settings = DietSettings(fastingHours = 20, eatingHours = 4),
            prefs = richPrefs,
        )
        val info = source.service.backup()

        val restored = Device(store) // a fresh install: empty everything, defaults everywhere
        restored.service.restore(info.id)

        val meals = restored.eatRepo.state.value.sortedBy { it.time }
        assertEquals(listOf(f, f + h(5)), meals.map { it.time })
        assertEquals("在公園吃的早餐", meals[0].note)
        assertEquals(GeoPoint(25.033, 121.565), meals[0].location)
        assertEquals(listOf("morning.webp", "park.webp"), meals[0].photos.map { it.fileName })
        assertNull(meals[1].note)
        assertEquals(
            listOf(CustomFastingType(18, 6, "我的節奏"), CustomFastingType(12, 12, null)),
            restored.typeRepo.state.value,
        )
        assertEquals(DietSettings(fastingHours = 20, eatingHours = 4), restored.settingsRepo.state.value)
        assertEquals(richPrefs, restored.prefsRepo.state.value)
        assertEquals(1, restored.rescheduler.rescheduleCount)
    }

    @Test
    fun restore_replacesRatherThanMergesExistingLocalData() = runTest {
        val store = FakeBackupStore()
        val info = Device(store, meals = richMeals()).service.backup()

        val target = Device(
            store,
            meals = listOf(EatRecord(99, f + h(50), null, "只存在於這台裝置", emptyList())),
            types = listOf(CustomFastingType(23, 1, "stale")),
        )
        target.service.restore(info.id)

        assertEquals(listOf(f, f + h(5)), target.eatRepo.state.value.map { it.time }.sorted())
        assertTrue(target.eatRepo.state.value.none { it.note == "只存在於這台裝置" })
        assertTrue(target.typeRepo.state.value.none { it.name == "stale" })
    }

    @Test
    fun backupOfAnEmptyDevice_restoresToAnEmptyButValidState() = runTest {
        val store = FakeBackupStore()
        val info = Device(store).service.backup()

        val target = Device(store, meals = richMeals())
        target.service.restore(info.id)

        assertTrue(target.eatRepo.state.value.isEmpty())
        assertTrue(target.typeRepo.state.value.isEmpty())
        assertEquals(DietSettings(fastingHours = 16, eatingHours = 8), target.settingsRepo.state.value)
        assertEquals(ReminderPrefs(), target.prefsRepo.state.value)
    }

    @Test
    fun manifestCarriesTheSchemaVersionAndAppVersion() = runTest {
        val store = FakeBackupStore()
        Device(store, meals = richMeals()).service.backup()

        val doc = BackupJson.decodeFromString(
            BackupDocument.serializer(),
            store.uploads.last().manifestJson,
        )
        assertEquals(BackupDocument.SCHEMA_VERSION, doc.schemaVersion)
        assertEquals(APP_VERSION, doc.appVersionName)
        assertEquals(BACKUP_CREATED_AT, doc.createdAt)
    }

    @Test
    fun photosUploadIncrementally() = runTest {
        val store = FakeBackupStore()
        val device = Device(store, meals = richMeals())
        device.service.backup()

        // Capture one more photo on an existing meal, then back up again.
        device.eatRepo.save(
            device.eatRepo.state.value.first { it.id == 2 },
            listOf("lunch.webp"),
            emptyList(),
        )
        device.service.backup()

        assertEquals(listOf("morning.webp", "park.webp"), store.uploads[0].uploadedPhotos)
        assertEquals(listOf("lunch.webp"), store.uploads[1].uploadedPhotos)
    }

    @Test
    fun retention_keepsOnlyTheNewestSnapshots() = runTest {
        val store = FakeBackupStore()
        val device = Device(store, meals = richMeals())

        repeat(BackupService.KEEP_LAST + 2) { device.service.backup() }

        assertEquals(BackupService.KEEP_LAST, store.pruneKeepLast)
        assertEquals(BackupService.KEEP_LAST, device.service.listSnapshots().size)
        assertEquals(store.uploads.last().info.id, device.service.listSnapshots().first().id)
    }

    @Test
    fun restore_refusesASnapshotFromANewerAppAndChangesNothing() = runTest {
        val store = FakeBackupStore()
        val tooNew = BackupDocument(
            schemaVersion = BackupDocument.SCHEMA_VERSION + 1,
            createdAt = BACKUP_CREATED_AT,
            appVersionName = "9.9.9",
            meals = emptyList(),
            fastingTypes = emptyList(),
            dietSettings = BackupDietSettings(16, 8),
            reminderPrefs = BackupReminderPrefs(false, false, 30, false, 1_320, 480),
        )
        val json = BackupJson.encodeToString(BackupDocument.serializer(), tooNew)
        store.seedSnapshot(SnapshotInfo("from-the-future", 1L, "9.9.9", json.length.toLong()), json)

        val device = Device(
            store,
            meals = richMeals(),
            types = listOf(CustomFastingType(18, 6, "我的節奏")),
            settings = DietSettings(fastingHours = 20, eatingHours = 4),
            prefs = richPrefs,
        )
        val failure = assertFailsWith<BackupSchemaTooNewException> {
            device.service.restore("from-the-future")
        }

        assertEquals(BackupDocument.SCHEMA_VERSION + 1, failure.found)
        assertEquals(2, device.eatRepo.state.value.size)
        assertEquals(listOf(CustomFastingType(18, 6, "我的節奏")), device.typeRepo.state.value)
        assertEquals(DietSettings(fastingHours = 20, eatingHours = 4), device.settingsRepo.state.value)
        assertEquals(richPrefs, device.prefsRepo.state.value)
        assertEquals(0, device.rescheduler.rescheduleCount)
    }

    @Test
    fun restore_leavesLocalDataIntactWhenTheDownloadDiesHalfway() = runTest {
        val store = FakeBackupStore()
        val info = Device(store, meals = richMeals()).service.backup()
        store.failDownloadPhotos = true

        val device = Device(
            store,
            meals = listOf(EatRecord(99, f + h(50), null, "請不要弄丟我", emptyList())),
            types = listOf(CustomFastingType(23, 1, "keep")),
            settings = DietSettings(fastingHours = 14, eatingHours = 10),
            prefs = ReminderPrefs(leadMinutes = 15),
        )
        assertFailsWith<IllegalStateException> { device.service.restore(info.id) }

        assertEquals(listOf(99), device.eatRepo.state.value.map { it.id })
        assertEquals(listOf(CustomFastingType(23, 1, "keep")), device.typeRepo.state.value)
        assertEquals(DietSettings(fastingHours = 14, eatingHours = 10), device.settingsRepo.state.value)
        assertEquals(15L, device.prefsRepo.state.value.leadMinutes)
        assertEquals(0, device.rescheduler.rescheduleCount)
    }

    @Test
    fun switchingAccounts_swapsTheVisibleSnapshotsEntirely() = runTest {
        val store = FakeBackupStore()
        val deviceA = Device(store, meals = richMeals(), types = listOf(CustomFastingType(18, 6, "A 的節奏")))
        val infoA = deviceA.service.backup()

        store.account = "second@example.com"
        val deviceB = Device(
            store,
            meals = listOf(EatRecord(5, f + h(30), null, "B 的紀錄", emptyList())),
            types = listOf(CustomFastingType(12, 12, "B 的節奏")),
        )
        val infoB = deviceB.service.backup()

        // Account B sees only its own snapshot, and cannot read account A's.
        assertEquals(listOf(infoB.id), deviceB.service.listSnapshots().map { it.id })
        assertFailsWith<IllegalStateException> { deviceB.service.restore(infoA.id) }

        // Restoring B's snapshot onto a device that holds A's data replaces it completely.
        val shared = Device(store, meals = richMeals(), types = listOf(CustomFastingType(18, 6, "A 的節奏")))
        shared.service.restore(infoB.id)
        assertEquals(listOf("B 的紀錄"), shared.eatRepo.state.value.map { it.note })
        assertEquals(listOf(CustomFastingType(12, 12, "B 的節奏")), shared.typeRepo.state.value)

        // Switching back reveals account A's snapshot again, untouched.
        store.account = "primary@example.com"
        assertEquals(listOf(infoA.id), shared.service.listSnapshots().map { it.id })
    }

    private companion object {
        const val APP_VERSION = "0.6.2"
        const val BACKUP_CREATED_AT = 9_999L
    }
}
