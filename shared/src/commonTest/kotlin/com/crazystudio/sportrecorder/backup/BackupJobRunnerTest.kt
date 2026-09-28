package com.crazystudio.sportrecorder.backup

import com.crazystudio.sportrecorder.backup.fakes.FakeBackupJobHost
import com.crazystudio.sportrecorder.backup.fakes.FakeBackupStore
import com.crazystudio.sportrecorder.backup.fakes.FakeDietSettingsRepository
import com.crazystudio.sportrecorder.backup.fakes.FakeEatRecordRepository
import com.crazystudio.sportrecorder.backup.fakes.FakeFastingTypeRepository
import com.crazystudio.sportrecorder.backup.fakes.FakeReminderPreferencesRepository
import com.crazystudio.sportrecorder.backup.fakes.FakeRemindersRescheduler
import com.crazystudio.sportrecorder.domain.model.EatPhoto
import com.crazystudio.sportrecorder.domain.model.EatRecord
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class BackupJobRunnerTest {
    private val dispatcher = StandardTestDispatcher()

    private fun service(store: FakeBackupStore, eat: FakeEatRecordRepository = FakeEatRecordRepository()) =
        BackupService(
            eat, FakeFastingTypeRepository(), FakeDietSettingsRepository(),
            FakeReminderPreferencesRepository(), store, FakeRemindersRescheduler(), "0.7.1",
        ) { 1L }

    private fun runner(service: BackupService, host: FakeBackupJobHost = FakeBackupJobHost()) =
        BackupJobRunner(service, host, CoroutineScope(SupervisorJob() + dispatcher))

    private fun emptyDocJson(): String = BackupJson.encodeToString(
        BackupDocument.serializer(),
        BackupDocument(
            schemaVersion = BackupDocument.SCHEMA_VERSION, createdAt = 1L, appVersionName = "0.7.1",
            meals = emptyList(), fastingTypes = emptyList(),
            dietSettings = BackupDietSettings(16, 8),
            reminderPrefs = BackupReminderPrefs(false, false, 30, false, 1320, 480),
        ),
    )

    @Test fun backup_goesIdleRunningFinished_andPairsHostHooks() = runTest(dispatcher) {
        val host = FakeBackupJobHost()
        val eat = FakeEatRecordRepository(listOf(EatRecord(1, 1L, null, "n", listOf(EatPhoto(1, "a.webp", 1L)))))
        val runner = runner(service(FakeBackupStore(), eat), host)
        assertEquals(BackupJobState.Idle, runner.state.value)

        assertTrue(runner.startBackup())
        assertIs<BackupJobState.Running>(runner.state.value)
        assertEquals(1, host.started)

        testScheduler.advanceUntilIdle()
        assertEquals(BackupJobState.Finished(BackupJobKind.Backup, BackupOutcome.Completed), runner.state.value)
        assertEquals(1, host.finished)

        runner.acknowledgeFinished()
        assertEquals(BackupJobState.Idle, runner.state.value)
    }

    @Test fun start_isRefused_whileAnotherJobRuns() = runTest(dispatcher) {
        val store = FakeBackupStore()
        store.seedSnapshot(SnapshotInfo("s", 1L, "0.7.1", 1L), emptyDocJson())
        store.downloadGate = CompletableDeferred()
        val runner = runner(service(store))

        assertTrue(runner.startRestore("s"))
        testScheduler.advanceUntilIdle() // parked on the gate
        assertFalse(runner.startBackup())
        assertFalse(runner.startRestore("s"))

        store.downloadGate!!.complete(Unit)
        testScheduler.advanceUntilIdle()
        assertEquals(BackupJobState.Finished(BackupJobKind.Restore, BackupOutcome.Completed), runner.state.value)
    }

    @Test fun cancel_duringDownload_leavesLocalDataAndReportsCancelled() = runTest(dispatcher) {
        val store = FakeBackupStore()
        store.seedSnapshot(SnapshotInfo("s", 1L, "0.7.1", 1L), emptyDocJson())
        store.downloadGate = CompletableDeferred()
        val eat = FakeEatRecordRepository(listOf(EatRecord(9, 9L, null, "keep", emptyList())))
        val host = FakeBackupJobHost()
        val runner = runner(service(store, eat), host)

        runner.startRestore("s")
        testScheduler.advanceUntilIdle()
        runner.cancel()
        testScheduler.advanceUntilIdle()

        assertEquals(BackupJobState.Finished(BackupJobKind.Restore, BackupOutcome.Cancelled), runner.state.value)
        assertEquals(listOf(9), eat.state.value.map { it.id })
        assertEquals(1, host.finished)
    }

    @Test fun cancel_duringApply_stillCompletesTheRestore() = runTest(dispatcher) {
        val store = FakeBackupStore()
        store.seedSnapshot(SnapshotInfo("s", 1L, "0.7.1", 1L), emptyDocJson())
        val eat = FakeEatRecordRepository(listOf(EatRecord(9, 9L, null, "old", emptyList())))
        eat.replaceAllGate = CompletableDeferred()
        val runner = runner(service(store, eat))

        runner.startRestore("s")
        testScheduler.advanceUntilIdle() // parked inside the NonCancellable apply
        runner.cancel()
        testScheduler.advanceUntilIdle()
        // apply is not cancellable
        assertEquals(BackupJobState.Running(BackupJobKind.Restore, BackupStep.Applying, 0, 0), runner.state.value)

        eat.replaceAllGate!!.complete(Unit)
        testScheduler.advanceUntilIdle()
        assertEquals(BackupJobState.Finished(BackupJobKind.Restore, BackupOutcome.Completed), runner.state.value)
        assertEquals(emptyList(), eat.state.value) // the (empty) snapshot was applied
    }

    @Test fun failure_reportsFailed() = runTest(dispatcher) {
        val store = FakeBackupStore()
        store.seedSnapshot(SnapshotInfo("s", 1L, "0.7.1", 1L), emptyDocJson())
        store.failDownloadPhotos = true
        val runner = runner(service(store))

        runner.startRestore("s")
        testScheduler.advanceUntilIdle()

        assertEquals(BackupJobState.Finished(BackupJobKind.Restore, BackupOutcome.Failed), runner.state.value)
    }

    @Test fun safetyBackupFailure_reportsSafetyBackupFailed() = runTest(dispatcher) {
        val store = FakeBackupStore()
        store.seedSnapshot(SnapshotInfo("s", 1L, "0.7.1", 1L), emptyDocJson())
        store.failUploadSnapshot = true
        val eat = FakeEatRecordRepository(listOf(EatRecord(9, 9L, null, "keep", emptyList())))
        val runner = runner(service(store, eat))

        runner.startRestore("s")
        testScheduler.advanceUntilIdle()

        assertEquals(
            BackupJobState.Finished(BackupJobKind.Restore, BackupOutcome.SafetyBackupFailed),
            runner.state.value,
        )
        assertEquals(listOf(9), eat.state.value.map { it.id })
    }

    @Test fun newerSchema_reportsSchemaTooNew() = runTest(dispatcher) {
        val store = FakeBackupStore()
        val tooNew = BackupJson.encodeToString(
            BackupDocument.serializer(),
            BackupDocument(
                schemaVersion = BackupDocument.SCHEMA_VERSION + 1, createdAt = 1L, appVersionName = "9",
                meals = emptyList(), fastingTypes = emptyList(),
                dietSettings = BackupDietSettings(16, 8),
                reminderPrefs = BackupReminderPrefs(false, false, 30, false, 1320, 480),
            ),
        )
        store.seedSnapshot(SnapshotInfo("s", 1L, "9", 1L), tooNew)
        val runner = runner(service(store))

        runner.startRestore("s")
        testScheduler.advanceUntilIdle()

        assertEquals(BackupJobState.Finished(BackupJobKind.Restore, BackupOutcome.SchemaTooNew), runner.state.value)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test fun progress_isMirroredIntoRunningState() = runTest(dispatcher) {
        val eat = FakeEatRecordRepository(listOf(EatRecord(1, 1L, null, "n", listOf(EatPhoto(1, "a.webp", 1L)))))
        val runner = runner(service(FakeBackupStore(), eat))
        val seen = mutableListOf<BackupJobState>()
        val collector = launch(UnconfinedTestDispatcher(testScheduler)) { runner.state.collect { seen.add(it) } }

        runner.startBackup()
        testScheduler.advanceUntilIdle()
        collector.cancel()

        assertTrue(seen.contains(BackupJobState.Running(BackupJobKind.Backup, BackupStep.UploadingPhotos, 1, 1)))
    }
}
