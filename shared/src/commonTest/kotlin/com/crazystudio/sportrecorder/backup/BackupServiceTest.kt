package com.crazystudio.sportrecorder.backup

import com.crazystudio.sportrecorder.backup.fakes.FakeBackupStore
import com.crazystudio.sportrecorder.backup.fakes.FakeDietSettingsRepository
import com.crazystudio.sportrecorder.backup.fakes.FakeEatRecordRepository
import com.crazystudio.sportrecorder.backup.fakes.FakeFastingTypeRepository
import com.crazystudio.sportrecorder.backup.fakes.FakeReminderPreferencesRepository
import com.crazystudio.sportrecorder.backup.fakes.FakeRemindersRescheduler
import com.crazystudio.sportrecorder.backup.fakes.FakeVenueRepository
import com.crazystudio.sportrecorder.domain.model.CustomFastingType
import com.crazystudio.sportrecorder.domain.model.DietSettings
import com.crazystudio.sportrecorder.domain.model.EatPhoto
import com.crazystudio.sportrecorder.domain.model.EatRecord
import com.crazystudio.sportrecorder.domain.model.GeoPoint
import com.crazystudio.sportrecorder.domain.model.Venue
import com.crazystudio.sportrecorder.domain.reminder.ReminderPrefs
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BackupServiceTest {
    private fun service(
        eat: FakeEatRecordRepository,
        store: FakeBackupStore,
        venues: FakeVenueRepository = FakeVenueRepository(),
    ) =
        BackupService(
            eat,
            venues,
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
            FakeVenueRepository(),
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
        BackupService(tgtEat, FakeVenueRepository(), tgtFasting, tgtSettings, tgtPrefs, store, resched, appVersionName = "0.6.2") { 1L }
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
            eat, FakeVenueRepository(), FakeFastingTypeRepository(), FakeDietSettingsRepository(),
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
            FakeVenueRepository(), FakeFastingTypeRepository(), FakeDietSettingsRepository(),
            FakeReminderPreferencesRepository(), store, FakeRemindersRescheduler(), appVersionName = "0.6.2",
        ) { 1L }.backup()
        val info = store.uploads.last().info
        store.failDownloadPhotos = true

        val tgtEat = FakeEatRecordRepository(listOf(EatRecord(99, 9_000L, null, "keep", emptyList())))
        val resched = FakeRemindersRescheduler()
        val svc = BackupService(
            tgtEat, FakeVenueRepository(), FakeFastingTypeRepository(), FakeDietSettingsRepository(),
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
            eat, FakeVenueRepository(), FakeFastingTypeRepository(), FakeDietSettingsRepository(),
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

    @Test fun backupThenRestore_bringsVenuesBackAndReattachesMeals() = runTest {
        val venueRepo = FakeVenueRepository()
        val venue = venueRepo.findOrCreate("大戶屋", lat = 25.0, lng = 121.0, now = 1L)
        val eat = FakeEatRecordRepository(
            listOf(EatRecord(1, 100L, null, null, emptyList(), venue)),
        )
        val store = FakeBackupStore()
        val service = service(eat, store, venueRepo)

        val info = service.backup()
        venueRepo.replaceAll(emptyList())
        eat.state.value = emptyList()
        service.restore(info.id)

        assertEquals(listOf("大戶屋"), venueRepo.observeAll().first().map { it.name })
        // Re-attached by key, with the id the venue has on THIS device after the restore ...
        val restoredVenue = venueRepo.observeAll().first().single()
        assertEquals(restoredVenue.id, eat.state.value.single().venue?.id)
        // ... which is not the id it had before (ids do not survive a restore).
        assertNotEquals(venue.id, restoredVenue.id)
        assertEquals(25.0, restoredVenue.lat!!, 0.0001)
        assertEquals(121.0, restoredVenue.lng!!, 0.0001)
    }

    @Test fun backup_writesVenuesAndReferencesMealsByName_notById() = runTest {
        val venueRepo = FakeVenueRepository()
        val venue = venueRepo.findOrCreate("Cafe", lat = null, lng = null, now = 5L)
        val store = FakeBackupStore()
        val eat = FakeEatRecordRepository(listOf(EatRecord(1, 100L, null, null, emptyList(), venue)))
        service(eat, store, venueRepo).backup()

        val doc = BackupJson.decodeFromString(BackupDocument.serializer(), store.uploads.last().manifestJson)
        assertEquals(listOf(BackupVenue("Cafe", null, null, 5L, null, venue.id.toString())), doc.venues)
        assertEquals("Cafe", doc.meals.single().venueName) // still written, for humans reading the JSON
        assertEquals(venue.id.toString(), doc.meals.single().venueKey)
    }

    @Test fun backup_writesSchemaVersion2() = runTest {
        val store = FakeBackupStore()
        service(FakeEatRecordRepository(), store).backup()

        val doc = BackupJson.decodeFromString(BackupDocument.serializer(), store.uploads.last().manifestJson)
        assertEquals(2, BackupDocument.SCHEMA_VERSION)
        assertEquals(2, doc.schemaVersion)
    }

    @Test fun twoBranches_roundTripOnTheirOwnVenues() = runTest {
        val venueRepo = FakeVenueRepository()
        val taipei = venueRepo.create("麥當勞", lat = 25.0, lng = 121.0, now = 1L)
            .also { venueRepo.setAddressIfUnchanged(it.id, 25.0, 121.0, "Taipei St") }
        val taichung = venueRepo.create("麥當勞", lat = 24.1, lng = 120.7, now = 2L)
            .also { venueRepo.setAddressIfUnchanged(it.id, 24.1, 120.7, "Taichung Rd") }
        val eat = FakeEatRecordRepository(
            listOf(
                EatRecord(1, 100L, null, null, emptyList(), venueRepo.stored.first { it.id == taipei.id }),
                EatRecord(2, 200L, null, null, emptyList(), venueRepo.stored.first { it.id == taichung.id }),
            ),
        )
        val store = FakeBackupStore()
        val service = service(eat, store, venueRepo)

        val info = service.backup()
        venueRepo.replaceAll(emptyList())
        eat.state.value = emptyList()
        service.restore(info.id)

        assertEquals(2, venueRepo.stored.size)
        val byTime = eat.state.value.sortedBy { it.time }
        val first = byTime[0].venue!!
        val second = byTime[1].venue!!
        assertNotEquals(first.id, second.id)
        assertEquals(Triple(25.0, 121.0, "Taipei St"), Triple(first.lat, first.lng, first.address))
        assertEquals(Triple(24.1, 120.7, "Taichung Rd"), Triple(second.lat, second.lng, second.address))
        assertEquals(setOf(first.id, second.id), venueRepo.stored.map { it.id }.toSet())
    }

    @Test fun addressIsBackedUp() = runTest {
        val venueRepo = FakeVenueRepository()
        val venue = venueRepo.create("Cafe", lat = 1.0, lng = 2.0, now = 5L)
        venueRepo.setAddressIfUnchanged(venue.id, 1.0, 2.0, "1 Main St")
        val store = FakeBackupStore()
        val eat = FakeEatRecordRepository()
        val service = service(eat, store, venueRepo)

        val info = service.backup()
        venueRepo.replaceAll(emptyList())
        service.restore(info.id)

        val doc = BackupJson.decodeFromString(BackupDocument.serializer(), store.uploads.first().manifestJson)
        assertEquals("1 Main St", doc.venues.single().address)
        assertEquals("1 Main St", venueRepo.stored.single().address)
    }

    @Test fun aVersion1Snapshot_stillRestoresVenuesByName() = runTest {
        val json = """
            {"schemaVersion":1,"createdAt":1,"appVersionName":"0.12.0",
             "meals":[{"id":1,"time":100,"note":null,"location":null,"photos":[],"venueName":"Cafe"},
                      {"id":2,"time":200,"note":null,"location":null,"photos":[],"venueName":" cafe "}],
             "fastingTypes":[],"dietSettings":{"fastingHours":16,"eatingHours":8},
             "reminderPrefs":{"windowClosingEnabled":false,"fastCompleteEnabled":false,
             "leadMinutes":30,"quietHoursEnabled":false,"quietStartMinutes":1320,"quietEndMinutes":480},
             "venues":[{"name":"Cafe","lat":1.0,"lng":2.0,"lastUsedAt":9}]}
        """.trimIndent()
        val store = FakeBackupStore()
        store.seedSnapshot(SnapshotInfo("v1", 1L, "0.12.0", json.length.toLong()), json)
        val venueRepo = FakeVenueRepository()
        val eat = FakeEatRecordRepository()

        service(eat, store, venueRepo).restore("v1")

        val restored = venueRepo.stored.single()
        assertNull(restored.address)
        assertEquals(listOf(restored.id, restored.id), eat.state.value.sortedBy { it.time }.map { it.venue?.id })
    }

    @Test fun restore_aVenueKeyTakesPrecedenceOverTheName() = runTest {
        val snapshot = BackupDocument(
            schemaVersion = BackupDocument.SCHEMA_VERSION, createdAt = 1L, appVersionName = "0.6.2",
            meals = listOf(BackupMeal(1, 100L, null, null, emptyList(), venueName = "Cafe", venueKey = "b")),
            fastingTypes = emptyList(),
            dietSettings = BackupDietSettings(16, 8),
            reminderPrefs = BackupReminderPrefs(false, false, 30, false, 1320, 480),
            venues = listOf(
                BackupVenue("Cafe", 1.0, 1.0, 1L, key = "a"),
                BackupVenue("Cafe", 2.0, 2.0, 2L, key = "b"),
            ),
        )
        val json = BackupJson.encodeToString(BackupDocument.serializer(), snapshot)
        val store = FakeBackupStore()
        store.seedSnapshot(SnapshotInfo("s", 1L, "0.6.2", json.length.toLong()), json)
        val eat = FakeEatRecordRepository()

        service(eat, store).restore("s")

        assertEquals(2.0, eat.state.value.single().venue!!.lat!!, 0.0001)
    }

    @Test fun restore_aVenueKeyNamingNoBackedUpVenue_comesBackWithoutOne() = runTest {
        val snapshot = BackupDocument(
            schemaVersion = BackupDocument.SCHEMA_VERSION, createdAt = 1L, appVersionName = "0.6.2",
            meals = listOf(BackupMeal(1, 100L, null, null, emptyList(), venueName = "Cafe", venueKey = "ghost")),
            fastingTypes = emptyList(),
            dietSettings = BackupDietSettings(16, 8),
            reminderPrefs = BackupReminderPrefs(false, false, 30, false, 1320, 480),
            venues = listOf(BackupVenue("Cafe", 1.0, 1.0, 1L, key = "a")),
        )
        val json = BackupJson.encodeToString(BackupDocument.serializer(), snapshot)
        val store = FakeBackupStore()
        store.seedSnapshot(SnapshotInfo("s", 1L, "0.6.2", json.length.toLong()), json)
        val eat = FakeEatRecordRepository()

        service(eat, store).restore("s")

        assertNull(eat.state.value.single().venue)
    }

    @Test fun restore_attachesOneVenueToEveryMealThatNamesIt_matchingCaseInsensitively() = runTest {
        val store = FakeBackupStore()
        val snapshot = BackupDocument(
            schemaVersion = BackupDocument.SCHEMA_VERSION, createdAt = 1L, appVersionName = "0.6.2",
            meals = listOf(
                BackupMeal(1, 100L, null, null, emptyList(), venueName = "Cafe"),
                BackupMeal(2, 200L, null, null, emptyList(), venueName = " cafe "),
                BackupMeal(3, 300L, null, null, emptyList(), venueName = null),
            ),
            fastingTypes = emptyList(),
            dietSettings = BackupDietSettings(16, 8),
            reminderPrefs = BackupReminderPrefs(false, false, 30, false, 1320, 480),
            venues = listOf(BackupVenue("Cafe", 1.0, 2.0, 9L)),
        )
        val json = BackupJson.encodeToString(BackupDocument.serializer(), snapshot)
        store.seedSnapshot(SnapshotInfo("s", 1L, "0.6.2", json.length.toLong()), json)
        val venueRepo = FakeVenueRepository(listOf(Venue(77, "stale", null, null, 0L)))
        val eat = FakeEatRecordRepository()

        service(eat, store, venueRepo).restore("s")

        val restored = venueRepo.stored.single() // the stale venue is gone
        assertEquals("Cafe", restored.name)
        val byTime = eat.state.value.sortedBy { it.time }
        assertEquals(listOf(restored.id, restored.id, null), byTime.map { it.venue?.id })
    }

    @Test fun restore_mealNamingAVenueTheSnapshotLacks_comesBackWithoutOne() = runTest {
        val store = FakeBackupStore()
        val snapshot = BackupDocument(
            schemaVersion = BackupDocument.SCHEMA_VERSION, createdAt = 1L, appVersionName = "0.6.2",
            meals = listOf(BackupMeal(1, 100L, "kept", null, emptyList(), venueName = "Ghost")),
            fastingTypes = emptyList(),
            dietSettings = BackupDietSettings(16, 8),
            reminderPrefs = BackupReminderPrefs(false, false, 30, false, 1320, 480),
            venues = emptyList(),
        )
        val json = BackupJson.encodeToString(BackupDocument.serializer(), snapshot)
        store.seedSnapshot(SnapshotInfo("s", 1L, "0.6.2", json.length.toLong()), json)
        val eat = FakeEatRecordRepository()

        service(eat, store).restore("s")

        val meal = eat.state.value.single()
        assertEquals("kept", meal.note)
        assertNull(meal.venue)
    }

    /** A snapshot written before venues existed must still restore, with no venue attached. */
    @Test fun restoringAPreVenueSnapshot_stillWorks() = runTest {
        val json = """
            {"schemaVersion":1,"createdAt":1,"appVersionName":"0.10.0",
             "meals":[{"id":1,"time":100,"note":"old","location":null,"photos":[]}],
             "fastingTypes":[],"dietSettings":{"fastingHours":16,"eatingHours":8},
             "reminderPrefs":{"windowClosingEnabled":false,"fastCompleteEnabled":false,
             "leadMinutes":30,"quietHoursEnabled":false,"quietStartMinutes":1320,"quietEndMinutes":480}}
        """.trimIndent()
        val doc = BackupJson.decodeFromString(BackupDocument.serializer(), json)

        assertEquals(emptyList(), doc.venues)
        assertEquals(null, doc.meals.single().venueName)

        // And it restores end to end, not merely decodes.
        val store = FakeBackupStore()
        store.seedSnapshot(SnapshotInfo("old", 1L, "0.10.0", json.length.toLong()), json)
        val eat = FakeEatRecordRepository()
        service(eat, store).restore("old")
        assertEquals("old", eat.state.value.single().note)
        assertNull(eat.state.value.single().venue)
    }

    /** Venues are backup data now: a device with places but no meals must still get a safety snapshot. */
    @Test fun restore_backsUpFirst_whenDeviceHasVenuesButNoMeals() = runTest {
        val store = FakeBackupStore()
        store.seedSnapshot(SnapshotInfo("target", 1L, "0.6.2", 1L), emptyDocJson())
        val venueRepo = FakeVenueRepository()
        venueRepo.findOrCreate("My place", lat = 1.0, lng = 2.0, now = 3L)

        service(FakeEatRecordRepository(), store, venueRepo).restore("target")

        val safety = store.uploads.single()
        val safetyDoc = BackupJson.decodeFromString(BackupDocument.serializer(), safety.manifestJson)
        assertEquals(listOf("My place"), safetyDoc.venues.map { it.name })
        assertEquals(emptyList(), venueRepo.stored) // and the restore still applied
    }
}
