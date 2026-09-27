package com.crazystudio.sportrecorder.reminder

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.crazystudio.sportrecorder.backup.activeNotification
import com.crazystudio.sportrecorder.backup.awaitUntil
import com.crazystudio.sportrecorder.domain.reminder.RemindersRescheduler
import kotlinx.coroutines.CompletableDeferred
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.loadKoinModules
import org.koin.dsl.module

/** A [RemindersRescheduler] that records calls and lets a test await the first one. */
class RecordingRescheduler : RemindersRescheduler {
    val called = CompletableDeferred<Unit>()
    var count = 0
    override suspend fun reschedule() { count++; called.complete(Unit) }
}

@RunWith(AndroidJUnit4::class)
class ReminderReceiverTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var rescheduler: RecordingRescheduler

    @Before fun setUp() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            InstrumentationRegistry.getInstrumentation().uiAutomation
                .grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        }
        NotificationManagerCompat.from(context).cancelAll()
        rescheduler = RecordingRescheduler()
        // Overrides the app's RemindersRescheduler for the rest of the instrumentation process.
        loadKoinModules(module { single<RemindersRescheduler> { rescheduler } })
    }

    @After fun tearDown() { NotificationManagerCompat.from(context).cancelAll() }

    private fun fire(typeName: String) = context.sendBroadcast(
        Intent(context, ReminderReceiver::class.java)
            .setAction(ReminderReceiver.ACTION_FIRE)
            .putExtra(ReminderReceiver.EXTRA_TYPE, typeName),
    )

    @Test fun fastComplete_postsNotification_andReschedules() {
        fire("FAST_COMPLETE")
        awaitUntil(what = "fast-complete notification") { context.activeNotification(2002) != null }
        awaitUntil(what = "reschedule called") { rescheduler.called.isCompleted }
    }

    @Test fun unknownType_doesNothing() {
        fire("NOT_A_TYPE")
        Thread.sleep(1_500) // give a wrongly-behaving receiver time to act
        assertNull(context.activeNotification(2001)); assertNull(context.activeNotification(2002))
        assertFalse(rescheduler.called.isCompleted)
    }
}
