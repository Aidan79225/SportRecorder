package com.crazystudio.sportrecorder.ui.backup

import com.crazystudio.sportrecorder.backup.BackupAccount
import com.crazystudio.sportrecorder.backup.BackupDietSettings
import com.crazystudio.sportrecorder.backup.BackupDocument
import com.crazystudio.sportrecorder.backup.BackupJobHost
import com.crazystudio.sportrecorder.backup.BackupJobRunner
import com.crazystudio.sportrecorder.backup.BackupJobState
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
import com.crazystudio.sportrecorder.domain.usecase.ObserveEatRecordsUseCase
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class BackupViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeTest fun setUp() { Dispatchers.setMain(dispatcher) }
    @AfterTest fun tearDown() { Dispatchers.resetMain() }

    private fun service(store: FakeBackupStore, eat: FakeEatRecordRepository) =
        BackupService(
            eat, FakeFastingTypeRepository(), FakeDietSettingsRepository(),
            FakeReminderPreferencesRepository(), store, FakeRemindersRescheduler(), "0.7.1",
        ) { 1L }

    private fun vm(
        store: FakeBackupStore,
        eat: FakeEatRecordRepository = FakeEatRecordRepository(),
        auth: FakeBackupAuth = FakeBackupAuth(BackupAccount("me@x.com")),
    ): BackupViewModel {
        val svc = service(store, eat)
        val runner = BackupJobRunner(svc, BackupJobHost.None, CoroutineScope(SupervisorJob() + dispatcher))
        return BackupViewModel(runner, svc, auth, ObserveEatRecordsUseCase(eat))
    }

    private fun emptyDocJson(): String = BackupJson.encodeToString(
        BackupDocument.serializer(),
        BackupDocument(
            schemaVersion = BackupDocument.SCHEMA_VERSION, createdAt = 1L, appVersionName = "0.7.1",
            meals = emptyList(), fastingTypes = emptyList(),
            dietSettings = BackupDietSettings(16, 8),
            reminderPrefs = BackupReminderPrefs(false, false, 30, false, 1320, 480),
        ),
    )

    @Test fun backup_setsCompleteMessageAndRefreshesSnapshots() = runTest(dispatcher) {
        val store = FakeBackupStore()
        val eat = FakeEatRecordRepository(listOf(EatRecord(1, 1L, null, "n", listOf(EatPhoto(7, "a.webp", 2L)))))
        val vm = vm(store, eat)

        vm.backup()
        testScheduler.advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(BackupMessage.BackupComplete, state.message)
        assertEquals(1, state.snapshots.size)
        assertIs<BackupJobState.Finished>(state.job)
        assertEquals(false, state.isBusy)
    }

    @Test fun runningJob_isBusy_andMirrorsProgress() = runTest(dispatcher) {
        val store = FakeBackupStore()
        store.seedSnapshot(SnapshotInfo("s", 1L, "0.7.1", 1L), emptyDocJson())
        store.downloadGate = CompletableDeferred()
        val vm = vm(store)

        vm.restore("s")
        testScheduler.advanceUntilIdle()

        val job = vm.uiState.value.job
        assertIs<BackupJobState.Running>(job)
        assertEquals(true, vm.uiState.value.isBusy)

        store.downloadGate!!.complete(Unit)
        testScheduler.advanceUntilIdle()
        assertEquals(BackupMessage.RestoreComplete, vm.uiState.value.message)
    }

    @Test fun cancel_setsCancelledMessage() = runTest(dispatcher) {
        val store = FakeBackupStore()
        store.seedSnapshot(SnapshotInfo("s", 1L, "0.7.1", 1L), emptyDocJson())
        store.downloadGate = CompletableDeferred()
        val vm = vm(store)

        vm.restore("s")
        testScheduler.advanceUntilIdle()
        vm.cancel()
        testScheduler.advanceUntilIdle()

        assertEquals(BackupMessage.Cancelled, vm.uiState.value.message)
    }

    @Test fun consumeMessage_clearsMessage_andReturnsRunnerToIdle() = runTest(dispatcher) {
        val vm = vm(FakeBackupStore())
        vm.backup()
        testScheduler.advanceUntilIdle()

        vm.consumeMessage()
        testScheduler.advanceUntilIdle()

        assertNull(vm.uiState.value.message)
        assertEquals(BackupJobState.Idle, vm.uiState.value.job)
    }

    @Test fun localMealCount_followsRepository() = runTest(dispatcher) {
        val eat = FakeEatRecordRepository(listOf(EatRecord(1, 1L, null, null, emptyList())))
        val vm = vm(FakeBackupStore(), eat)
        testScheduler.advanceUntilIdle()
        assertEquals(1, vm.uiState.value.localMealCount)

        eat.state.value = emptyList()
        testScheduler.advanceUntilIdle()
        assertEquals(0, vm.uiState.value.localMealCount)
    }

    @Test fun account_isReflectedInState() = runTest(dispatcher) {
        val vm = vm(FakeBackupStore())
        testScheduler.advanceUntilIdle()
        assertEquals("me@x.com", vm.uiState.value.account?.email)
    }

    @Test fun accountChange_clearsPreviousAccountsSnapshots() = runTest(dispatcher) {
        val store = FakeBackupStore()
        val auth = FakeBackupAuth(BackupAccount("me@x.com"))
        val vm = vm(store, auth = auth)

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
        val vm = vm(store)

        vm.restore("s1")
        testScheduler.advanceUntilIdle()

        assertEquals(BackupMessage.RestoreSchemaTooNew, vm.uiState.value.message)
    }
}
