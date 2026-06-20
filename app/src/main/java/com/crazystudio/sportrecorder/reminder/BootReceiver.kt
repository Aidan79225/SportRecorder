package com.crazystudio.sportrecorder.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.crazystudio.sportrecorder.domain.reminder.RemindersRescheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/** Re-arms reminders from current DB state after a device reboot (alarms don't survive reboots). */
class BootReceiver : BroadcastReceiver(), KoinComponent {

    private val rescheduler: RemindersRescheduler by inject()

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
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
}
