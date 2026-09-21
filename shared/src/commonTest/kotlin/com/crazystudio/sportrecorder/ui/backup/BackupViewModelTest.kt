package com.crazystudio.sportrecorder.ui.backup

import com.crazystudio.sportrecorder.backup.BackupAccount
import com.crazystudio.sportrecorder.backup.BackupDietSettings
import com.crazystudio.sportrecorder.backup.BackupDocument
import com.crazystudio.sportrecorder.backup.BackupJson
import com.crazystudio.sportrecorder.backup.BackupReminderPrefs
import com.crazystudio.sportrecorder.backup.BackupService
import com.crazystudio.sportrecorder.backup.SnapshotInfo
import com.crazystudio.sportrecorder.backup.fakes.FakeBackupAuth
import com.crazystudio.sportrecorder.backup.fakes.FakeBackupStore
import com.crazystudio.sportrecorder.backup.fakes.FakeDietSettingsRepository
import com.crazystudio.sportrecorder.backup.fakes.FakeEatRecordRepository
import com.crazystudio.sportrecorder.backup.fakes.FakeFastingTypeRepository
import com.crazystudio.sportrecorder.backup.fakes.FakeReminderPreferencesRepository
import com.crazystudio.sportrecorder.backup.fakes.FakeRemindersRescheduler
import com.crazystudio.sportrecorder.domain.model.EatPhoto
import com.crazystudio.sportrecorder.domain.model.EatRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class BackupViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeTest fun setUp() { Dispatchers.setMain(dispatcher) }
    @AfterTest fun tearDown() { Dispatchers.resetMain() }

    private fun service(store: FakeBackupStore, eat: FakeEatRecordRepository = FakeEatRecordRepository()) =
        BackupService(
            eat, FakeFastingTypeRepository(), FakeDietSettingsRepository(),
            FakeReminderPreferencesRepository(), store, FakeRemindersRescheduler(), "0.6.2",
        ) { 1L }

    @Test fun backup_setsCompleteMessageAndRefreshesSnapshots() = runTest(dispatcher) {
        val store = FakeBackupStore()
        val eat = FakeEatRecordRepository(listOf(EatRecord(1, 1L, null, "n", listOf(EatPhoto(7, "a.webp", 2L)))))
        val vm = BackupViewModel(service(store, eat), FakeBackupAuth(BackupAccount("me@x.com")))

        vm.backup()
        testScheduler.advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(BackupMessage.BackupComplete, state.message)
        assertEquals(1, state.snapshots.size)
        assertEquals(BackupPhase.Idle, state.phase)
    }

    @Test fun account_isReflectedInState() = runTest(dispatcher) {
        val vm = BackupViewModel(service(FakeBackupStore()), FakeBackupAuth(BackupAccount("me@x.com")))
        testScheduler.advanceUntilIdle()
        assertEquals("me@x.com", vm.uiState.value.account?.email)
    }

    @Test fun accountChange_clearsPreviousAccountsSnapshots() = runTest(dispatcher) {
        val store = FakeBackupStore()
        val auth = FakeBackupAuth(BackupAccount("me@x.com"))
        val vm = BackupViewModel(service(store), auth)

        vm.backup()
        testScheduler.advanceUntilIdle()
        assertEquals(1, vm.uiState.value.snapshots.size)

        auth.accountState.value = null // signed out
        testScheduler.advanceUntilIdle()
        assertEquals(emptyList<SnapshotInfo>(), vm.uiState.value.snapshots)

        auth.accountState.value = BackupAccount("someone-else@x.com") // a different account
        testScheduler.advanceUntilIdle()
        assertEquals(emptyList<SnapshotInfo>(), vm.uiState.value.snapshots)
    }

    @Test fun restore_tooNewSchema_setsSchemaMessage() = runTest(dispatcher) {
        val store = FakeBackupStore()
        val tooNew = BackupDocument(
            schemaVersion = BackupDocument.SCHEMA_VERSION + 1,
            createdAt = 1L, appVersionName = "9",
            meals = emptyList(), fastingTypes = emptyList(),
            dietSettings = BackupDietSettings(16, 8),
            reminderPrefs = BackupReminderPrefs(false, false, 30, false, 1320, 480),
        )
        val json = BackupJson.encodeToString(BackupDocument.serializer(), tooNew)
        store.seedSnapshot(SnapshotInfo("s1", 1L, "9", json.length.toLong()), json)
        val vm = BackupViewModel(service(store), FakeBackupAuth(BackupAccount("me@x.com")))

        vm.restore("s1")
        testScheduler.advanceUntilIdle()

        assertEquals(BackupMessage.RestoreSchemaTooNew, vm.uiState.value.message)
    }
}
