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

/**
 * Re-arms reminders from current DB state on two system broadcasts:
 * - [Intent.ACTION_BOOT_COMPLETED]: alarms don't survive a reboot, so nothing is armed until we re-arm.
 * - [Intent.ACTION_MY_PACKAGE_REPLACED]: alarms survive an app update, but they may hold trigger
 *   times computed by the previous version's rules, so we recompute them with the current ones.
 */
class BootReceiver : BroadcastReceiver(), KoinComponent {

    private val rescheduler: RemindersRescheduler by inject()

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            return
        }
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
