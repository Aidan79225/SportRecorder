package com.crazystudio.sportrecorder.backup

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield

enum class BackupJobKind { Backup, Restore }

enum class BackupOutcome { Completed, Cancelled, SchemaTooNew, Failed }

/** What the one-and-only backup/restore job is doing right now. */
sealed interface BackupJobState {
    data object Idle : BackupJobState

    data class Running(val kind: BackupJobKind, val step: BackupStep, val done: Int, val total: Int) : BackupJobState

    /** Stays until [BackupJobRunner.acknowledgeFinished], so a screen opened later still sees it once. */
    data class Finished(val kind: BackupJobKind, val outcome: BackupOutcome) : BackupJobState
}

/**
 * Platform hook around a job's lifetime. Android starts a foreground service in [onJobStarted] so
 * the process stays alive with the screen off; [onJobFinished] is always called, even on failure.
 */
interface BackupJobHost {
    fun onJobStarted()
    fun onJobFinished()

    /** No platform hosting (tests, iOS until it has one). */
    object None : BackupJobHost {
        override fun onJobStarted() = Unit
        override fun onJobFinished() = Unit
    }
}

/**
 * Runs backup/restore jobs in an application-scoped coroutine so they outlive the screen that
 * started them (a `viewModelScope` job dies with the ViewModel the moment the user navigates
 * back). One job at a time; progress is mirrored into [state] for any observer.
 */
class BackupJobRunner(
    private val service: BackupService,
    private val host: BackupJobHost,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    private val _state = MutableStateFlow<BackupJobState>(BackupJobState.Idle)
    val state: StateFlow<BackupJobState> = _state.asStateFlow()

    private var job: Job? = null

    /** Start a backup. Returns false (and does nothing) if a job is already running. */
    fun startBackup(): Boolean = start(BackupJobKind.Backup) { progress -> service.backup(progress) }

    /** Start restoring [snapshotId]. Returns false (and does nothing) if a job is already running. */
    fun startRestore(snapshotId: String): Boolean =
        start(BackupJobKind.Restore) { progress -> service.restore(snapshotId, progress) }

    /** Cancel the running job, if any. Safe at every point: the service guards its own apply step. */
    fun cancel() {
        job?.cancel()
    }

    /** The UI has shown the outcome; go back to [BackupJobState.Idle]. */
    fun acknowledgeFinished() {
        if (_state.value is BackupJobState.Finished) _state.value = BackupJobState.Idle
    }

    private fun start(kind: BackupJobKind, work: suspend (BackupProgress) -> Unit): Boolean {
        if (_state.value is BackupJobState.Running) return false
        _state.value = BackupJobState.Running(kind, BackupStep.Preparing, 0, 0)
        host.onJobStarted()
        job = scope.launch {
            // BackupProgress.report is a plain (non-suspend) callback that may fire many times
            // back-to-back with no real suspension in between (e.g. the in-memory store in tests).
            // Applying every report straight onto a MutableStateFlow would let most of them be
            // conflated away before an observer ever gets scheduled. Routing them through a
            // channel drained by a dedicated pump — with a `yield()` after each apply — guarantees
            // every distinct step is actually published and observable in order.
            val updates = Channel<BackupJobState.Running>(Channel.UNLIMITED)
            val pump = launch {
                for (running in updates) {
                    _state.value = running
                    yield()
                }
            }
            val result = runCatching {
                work { step, done, total -> updates.trySend(BackupJobState.Running(kind, step, done, total)) }
            }
            updates.close()
            withContext(NonCancellable) { pump.join() }
            _state.value = BackupJobState.Finished(kind, result.outcome())
            host.onJobFinished()
        }
        return true
    }
}

private fun Result<Unit>.outcome(): BackupOutcome = when (exceptionOrNull()) {
    null -> BackupOutcome.Completed
    is CancellationException -> BackupOutcome.Cancelled
    is BackupSchemaTooNewException -> BackupOutcome.SchemaTooNew
    else -> BackupOutcome.Failed
}
