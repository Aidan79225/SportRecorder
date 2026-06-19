package com.crazystudio.sportrecorder.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.crazystudio.sportrecorder.domain.reminder.ReminderScheduler
import com.crazystudio.sportrecorder.domain.reminder.ReminderType
import com.crazystudio.sportrecorder.domain.reminder.ScheduledReminder
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * AlarmManager-backed [ReminderScheduler]. Each [ReminderType] owns one alarm slot (a distinct
 * PendingIntent request code), so [schedule] cancels every type first and re-arms only the ones
 * present. Uses exact alarms when allowed, degrading to inexact (no USE_EXACT_ALARM — see the
 * manifest's TODO(release) note).
 */
class AlarmReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val notifier: ReminderNotifier,
) : ReminderScheduler {

    private val alarmManager: AlarmManager? = context.getSystemService(AlarmManager::class.java)

    override fun schedule(reminders: List<ScheduledReminder>) {
        val manager = alarmManager ?: return
        notifier.ensureChannels()
        ReminderType.entries.forEach { cancel(manager, it) }
        reminders.forEach { setAlarm(manager, it) }
    }

    private fun setAlarm(manager: AlarmManager, reminder: ScheduledReminder) {
        val pendingIntent = pendingIntent(reminder.type, PendingIntent.FLAG_UPDATE_CURRENT)
        if (canScheduleExact(manager)) {
            try {
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminder.triggerAtMillis, pendingIntent)
                return
            } catch (_: SecurityException) {
                // Exact-alarm access was revoked between the check and here; fall back to inexact.
            }
        }
        manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminder.triggerAtMillis, pendingIntent)
    }

    private fun cancel(manager: AlarmManager, type: ReminderType) {
        existingPendingIntent(type)?.let {
            manager.cancel(it)
            it.cancel()
        }
    }

    private fun canScheduleExact(manager: AlarmManager): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) manager.canScheduleExactAlarms() else true

    private fun pendingIntent(type: ReminderType, extraFlags: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            requestCode(type),
            fireIntent(type),
            PendingIntent.FLAG_IMMUTABLE or extraFlags,
        )

    /** Returns null when no alarm of [type] is currently scheduled (nothing to cancel). */
    private fun existingPendingIntent(type: ReminderType): PendingIntent? =
        PendingIntent.getBroadcast(
            context,
            requestCode(type),
            fireIntent(type),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE,
        )

    private fun fireIntent(type: ReminderType): Intent =
        Intent(context, ReminderReceiver::class.java).apply {
            action = ReminderReceiver.ACTION_FIRE
            putExtra(ReminderReceiver.EXTRA_TYPE, type.name)
        }

    private fun requestCode(type: ReminderType): Int = REQUEST_CODE_BASE + type.ordinal

    private companion object {
        const val REQUEST_CODE_BASE = 1000
    }
}
