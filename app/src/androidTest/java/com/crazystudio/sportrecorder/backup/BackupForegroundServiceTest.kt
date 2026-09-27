package com.crazystudio.sportrecorder.backup

import android.Manifest
import android.app.Notification
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.crazystudio.sportrecorder.MainActivity
import com.crazystudio.sportrecorder.shared.resources.Res
import com.crazystudio.sportrecorder.shared.resources.backup_msg_cancelled
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.getString
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The real BackupJobRunner + AndroidBackupJobHost + BackupForegroundService + BackupNotifications,
 * with only the cloud faked. MainActivity is kept in the foreground so `startForegroundService`
 * is allowed (Android 12+ forbids it from the background).
 */
@RunWith(AndroidJUnit4::class)
class BackupForegroundServiceTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var scenario: ActivityScenario<MainActivity>
    private lateinit var store: GatedBackupStore
    private lateinit var runner: BackupJobRunner

    @Before fun setUp() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            InstrumentationRegistry.getInstrumentation().uiAutomation
                .grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        }
        NotificationManagerCompat.from(context).cancelAll()
        scenario = ActivityScenario.launch(MainActivity::class.java)
        store = GatedBackupStore()
        runner = loadBackupTestModule(store)
    }

    @After fun tearDown() {
        try {
            if (::store.isInitialized) store.uploadGate?.complete(Unit)
            if (::runner.isInitialized) {
                runner.cancel()
                awaitUntil(what = "runner to settle") { runner.state.value !is BackupJobState.Running }
                awaitUntil(what = "progress card removed") { progressCard() == null }
            }
        } finally {
            NotificationManagerCompat.from(context).cancelAll()
            if (::scenario.isInitialized) scenario.close()
        }
    }

    private fun progressCard() = context.activeNotification(BackupNotifications.PROGRESS_ID)
    private fun resultCard() = context.activeNotification(BackupNotifications.RESULT_ID)

    @Test fun backup_postsOngoingProgress_thenAutoCancelResult_andStopsForeground() {
        store.uploadGate = CompletableDeferred()

        assertTrue(runner.startBackup())
        awaitUntil(what = "progress notification") { progressCard() != null }
        val progress = progressCard()!!.notification
        assertTrue(progress.flags and Notification.FLAG_ONGOING_EVENT != 0)

        store.uploadGate!!.complete(Unit)
        awaitUntil(what = "result notification") { resultCard() != null }
        awaitUntil(what = "progress card removed") { progressCard() == null }
        val result = resultCard()!!.notification
        assertFalse(result.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertTrue(result.flags and Notification.FLAG_AUTO_CANCEL != 0)
        assertEquals(BackupJobState.Finished(BackupJobKind.Backup, BackupOutcome.Completed), runner.state.value)
    }

    @Test fun cancelAction_cancelsJob_andShowsCancelledResult() {
        store.uploadGate = CompletableDeferred()
        assertTrue(runner.startBackup())
        awaitUntil(what = "progress notification") { progressCard() != null }

        val cancelIntent = Intent(context, BackupForegroundService::class.java)
            .setAction(BackupForegroundService.ACTION_CANCEL)
        context.startService(cancelIntent)

        awaitUntil(what = "cancelled outcome") {
            runner.state.value == BackupJobState.Finished(BackupJobKind.Backup, BackupOutcome.Cancelled)
        }
        awaitUntil(what = "result notification") { resultCard() != null }
        val title = resultCard()!!.notification.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        assertEquals(runBlocking { getString(Res.string.backup_msg_cancelled) }, title)
        awaitUntil(what = "progress card removed") { progressCard() == null }
    }

    @Test fun secondStart_isRefusedWhileRunning() {
        store.uploadGate = CompletableDeferred()
        assertTrue(runner.startBackup())
        awaitUntil(what = "progress notification") { progressCard() != null }

        assertFalse(runner.startBackup())
        assertNotNull(progressCard())
        assertNull(resultCard())
        assertTrue(runner.state.value is BackupJobState.Running)
    }
}
