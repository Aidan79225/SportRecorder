package com.crazystudio.sportrecorder.reminder

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.crazystudio.sportrecorder.domain.reminder.ReminderType
import com.crazystudio.sportrecorder.domain.reminder.ScheduledReminder
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AlarmReminderSchedulerTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var scheduler: AlarmReminderScheduler

    @Before fun setUp() {
        scheduler = AlarmReminderScheduler(context, ReminderNotifier(context))
        scheduler.schedule(emptyList())
    }

    @After fun tearDown() { scheduler.schedule(emptyList()) }

    /** Same shape as production's slot: request code 1000 + ordinal, explicit ReminderReceiver intent + action. */
    private fun slot(type: ReminderType): PendingIntent? = PendingIntent.getBroadcast(
        context,
        1000 + type.ordinal,
        Intent(context, ReminderReceiver::class.java).setAction(ReminderReceiver.ACTION_FIRE),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE,
    )

    @Test fun schedule_armsOnlyTheGivenTypes() {
        val soon = System.currentTimeMillis() + 60_000
        scheduler.schedule(listOf(ScheduledReminder(ReminderType.WINDOW_CLOSING, soon)))
        assertNotNull(slot(ReminderType.WINDOW_CLOSING))
        assertNull(slot(ReminderType.FAST_COMPLETE))
    }

    @Test fun schedule_bothTypes_thenEmpty_cancelsEverything() {
        val soon = System.currentTimeMillis() + 60_000
        scheduler.schedule(
            listOf(
                ScheduledReminder(ReminderType.WINDOW_CLOSING, soon),
                ScheduledReminder(ReminderType.FAST_COMPLETE, soon + 1),
            ),
        )
        assertNotNull(slot(ReminderType.WINDOW_CLOSING)); assertNotNull(slot(ReminderType.FAST_COMPLETE))

        scheduler.schedule(emptyList())
        assertNull(slot(ReminderType.WINDOW_CLOSING)); assertNull(slot(ReminderType.FAST_COMPLETE))
    }

    @Test fun reschedule_replacesTheSlot() {
        val soon = System.currentTimeMillis() + 60_000
        scheduler.schedule(listOf(ScheduledReminder(ReminderType.FAST_COMPLETE, soon)))
        scheduler.schedule(listOf(ScheduledReminder(ReminderType.WINDOW_CLOSING, soon)))
        assertNull(slot(ReminderType.FAST_COMPLETE))
        assertNotNull(slot(ReminderType.WINDOW_CLOSING))
    }
}
