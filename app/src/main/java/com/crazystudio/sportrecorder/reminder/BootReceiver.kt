package com.crazystudio.sportrecorder.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.crazystudio.sportrecorder.domain.reminder.RemindersRescheduler
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Re-arms reminders from current DB state after a device reboot (alarms don't survive reboots). */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject
    lateinit var rescheduler: RemindersRescheduler

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
