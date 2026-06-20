package com.crazystudio.sportrecorder.reminder

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.StringRes
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.crazystudio.sportrecorder.MainActivity
import com.crazystudio.sportrecorder.R
import com.crazystudio.sportrecorder.domain.reminder.ReminderType

/** Per-type notification metadata so each reminder owns a distinct channel the user can tune. */
private enum class ReminderChannel(
    val type: ReminderType,
    val channelId: String,
    val notificationId: Int,
    @StringRes val channelNameRes: Int,
    @StringRes val titleRes: Int,
    @StringRes val textRes: Int,
) {
    WINDOW_CLOSING(
        type = ReminderType.WINDOW_CLOSING,
        channelId = "reminder_window_closing",
        notificationId = 2001,
        channelNameRes = R.string.reminder_channel_window_closing_name,
        titleRes = R.string.reminder_window_closing_title,
        textRes = R.string.reminder_window_closing_text,
    ),
    FAST_COMPLETE(
        type = ReminderType.FAST_COMPLETE,
        channelId = "reminder_fast_complete",
        notificationId = 2002,
        channelNameRes = R.string.reminder_channel_fast_complete_name,
        titleRes = R.string.reminder_fast_complete_title,
        textRes = R.string.reminder_fast_complete_text,
    ),
    ;

    companion object {
        fun of(type: ReminderType): ReminderChannel = entries.first { it.type == type }
    }
}

/** Creates the per-reminder notification channels and posts reminder notifications. */
class ReminderNotifier constructor(
    private val context: Context,
) {

    /** Idempotent: safe to call repeatedly (createNotificationChannel just updates the channel). */
    fun ensureChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        ReminderChannel.entries.forEach { spec ->
            manager.createNotificationChannel(
                NotificationChannel(
                    spec.channelId,
                    context.getString(spec.channelNameRes),
                    NotificationManager.IMPORTANCE_HIGH,
                ),
            )
        }
    }

    fun notify(type: ReminderType) {
        ensureChannels()
        // POST_NOTIFICATIONS is a 33+ runtime permission; bail if the user hasn't granted it.
        // Inlined (not a helper) so lint can see the guard dominating the notify() call below.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val spec = ReminderChannel.of(type)
        val notification = NotificationCompat.Builder(context, spec.channelId)
            .setSmallIcon(R.drawable.ic_baseline_notifications_24)
            .setContentTitle(context.getString(spec.titleRes))
            .setContentText(context.getString(spec.textRes))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent())
            .build()
        NotificationManagerCompat.from(context).notify(spec.notificationId, notification)
    }

    private fun openAppIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
