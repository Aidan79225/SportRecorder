package com.crazystudio.sportrecorder.ui.backup

import com.crazystudio.sportrecorder.backup.BackupAccount
import com.crazystudio.sportrecorder.backup.BackupDietSettings
import com.crazystudio.sportrecorder.backup.BackupDocument
import com.crazystudio.sportrecorder.backup.BackupJson
import com.crazystudio.sportrecorder.backup.BackupReminderPrefs
import com.crazystudio.sportrecorder.backup.BackupService
import com.crazystudio.sportrecorder.backup.SnapshotInfo
import com.crazystudio.sportrecorder.backup.fakes.FakeAuthorizationRequiredException
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
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

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

    @Test fun authorizationRequired_fallsBackToSignedOutInsteadOfError() = runTest(dispatcher) {
        val store = FakeBackupStore()
        val vm = BackupViewModel(service(store), FakeBackupAuth(BackupAccount("me@x.com")))
        testScheduler.advanceUntilIdle()
        store.failWith = FakeAuthorizationRequiredException()

        vm.backup()
        testScheduler.advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state.needsAuthorization)
        assertFalse(state.isSignedIn) // the screen offers "Sign in with Google" again
        assertNull(state.message) // a lapsed grant is not "something went wrong"
        assertEquals(BackupPhase.Idle, state.phase)
    }

    @Test fun authorizationRequired_onListing_dropsStaleSnapshots() = runTest(dispatcher) {
        val store = FakeBackupStore()
        store.seedSnapshot(SnapshotInfo("s1", 1L, "9", 10L), "{}")
        val vm = BackupViewModel(service(store), FakeBackupAuth(BackupAccount("me@x.com")))
        vm.refreshSnapshots()
        testScheduler.advanceUntilIdle()
        assertEquals(1, vm.uiState.value.snapshots.size)

        store.failWith = FakeAuthorizationRequiredException()
        vm.refreshSnapshots()
        testScheduler.advanceUntilIdle()

        assertTrue(vm.uiState.value.needsAuthorization)
        assertTrue(vm.uiState.value.snapshots.isEmpty())
    }

    @Test fun onAuthorized_restoresSignedInState() = runTest(dispatcher) {
        val store = FakeBackupStore()
        val vm = BackupViewModel(service(store), FakeBackupAuth(BackupAccount("me@x.com")))
        testScheduler.advanceUntilIdle()
        store.failWith = FakeAuthorizationRequiredException()
        vm.backup()
        testScheduler.advanceUntilIdle()

        store.failWith = null
        vm.onAuthorized()

        assertFalse(vm.uiState.value.needsAuthorization)
        assertTrue(vm.uiState.value.isSignedIn)
    }

    @Test fun ordinaryFailure_stillReportsFailedMessage() = runTest(dispatcher) {
        val store = FakeBackupStore()
        val vm = BackupViewModel(service(store), FakeBackupAuth(BackupAccount("me@x.com")))
        testScheduler.advanceUntilIdle()
        store.failWith = IllegalStateException("network down")

        vm.backup()
        testScheduler.advanceUntilIdle()

        assertEquals(BackupMessage.Failed, vm.uiState.value.message)
        assertFalse(vm.uiState.value.needsAuthorization)
    }

    @Test fun signOut_clearsAccountAndSnapshots() = runTest(dispatcher) {
        val store = FakeBackupStore()
        store.seedSnapshot(SnapshotInfo("s1", 1L, "9", 10L), "{}")
        val auth = FakeBackupAuth(BackupAccount("me@x.com"))
        val vm = BackupViewModel(service(store), auth)
        vm.refreshSnapshots()
        testScheduler.advanceUntilIdle()
        assertEquals(1, vm.uiState.value.snapshots.size)

        auth.accountState.value = null
        testScheduler.advanceUntilIdle()

        assertFalse(vm.uiState.value.isSignedIn)
        assertTrue(vm.uiState.value.snapshots.isEmpty())
    }
}
