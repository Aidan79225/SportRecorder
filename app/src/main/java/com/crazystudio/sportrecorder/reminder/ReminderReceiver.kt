package com.crazystudio.sportrecorder.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.crazystudio.sportrecorder.domain.reminder.ReminderType
import com.crazystudio.sportrecorder.domain.reminder.RemindersRescheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/** Fires when an alarm goes off: posts the notification, then schedules the next occurrence. */
class ReminderReceiver : BroadcastReceiver(), KoinComponent {

    private val notifier: ReminderNotifier by inject()

    private val rescheduler: RemindersRescheduler by inject()

    override fun onReceive(context: Context, intent: Intent) {
        val type = intent.getStringExtra(EXTRA_TYPE)
            ?.let { name -> runCatching { ReminderType.valueOf(name) }.getOrNull() }
            ?: return
        notifier.notify(type)

        // Re-arm the next occurrence (e.g. the fast-complete alarm after the window-closing one).
        val pendingResult = goAsync()
        @Suppress("InjectDispatcher") // receiver has no injected scope; Default is fine for a one-shot read
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                rescheduler.reschedule()
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_FIRE = "com.crazystudio.sportrecorder.action.REMINDER_FIRE"
        const val EXTRA_TYPE = "extra_reminder_type"
    }
}
