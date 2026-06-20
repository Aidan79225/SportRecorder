package com.crazystudio.sportrecorder.ui.settings

import android.app.AlarmManager
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.crazystudio.sportrecorder.R
import org.koin.androidx.compose.koinViewModel

private const val MINUTES_PER_HOUR = 60

/** Wires [SettingsScreen] to the ViewModel plus the Android-only bits: notification permission,
 *  exact-alarm status/deep-link, and the quiet-hours time pickers. */
@Composable
fun SettingsRoute(onBack: () -> Unit) {
    val vm: SettingsViewModel = koinViewModel()
    val state by vm.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Re-read permission/notification state whenever we resume (the user may change it in system
    // settings, or deny POST_NOTIFICATIONS, while we're backgrounded).
    var canScheduleExact by remember { mutableStateOf(canScheduleExactAlarms(context)) }
    var notificationsEnabled by remember { mutableStateOf(areNotificationsEnabled(context)) }
    LifecycleResumeEffect(Unit) {
        canScheduleExact = canScheduleExactAlarms(context)
        notificationsEnabled = areNotificationsEnabled(context)
        onPauseOrDispose { }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        // Reflect the outcome immediately: a denial surfaces the "notifications off" banner.
        notificationsEnabled = areNotificationsEnabled(context)
    }

    fun enable(set: (Boolean) -> Unit, enabled: Boolean) {
        set(enabled)
        if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // Only nag about notifications once the user actually wants a reminder.
    val notificationsBlocked = !notificationsEnabled &&
        (state.windowClosingEnabled || state.fastCompleteEnabled)

    SettingsScreen(
        state = state,
        canScheduleExact = canScheduleExact,
        notificationsBlocked = notificationsBlocked,
        onWindowClosingToggle = { enable(vm::setWindowClosingEnabled, it) },
        onFastCompleteToggle = { enable(vm::setFastCompleteEnabled, it) },
        onLeadDelta = vm::changeLeadMinutes,
        onQuietHoursToggle = vm::setQuietHoursEnabled,
        onPickQuietStart = {
            showTimePicker(context, state.quietStartMinutes, vm::setQuietStart)
        },
        onPickQuietEnd = {
            showTimePicker(context, state.quietEndMinutes, vm::setQuietEnd)
        },
        onOpenExactAlarmSettings = { openExactAlarmSettings(context) },
        onOpenNotificationSettings = { openNotificationSettings(context) },
        onBack = onBack,
    )
}

private fun areNotificationsEnabled(context: Context): Boolean =
    NotificationManagerCompat.from(context).areNotificationsEnabled()

private fun canScheduleExactAlarms(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
    val manager = context.getSystemService(AlarmManager::class.java)
    return manager?.canScheduleExactAlarms() ?: true
}

private fun showTimePicker(context: Context, minutesSinceMidnight: Int, onPicked: (Int) -> Unit) {
    TimePickerDialog(
        context,
        R.style.TimeDialogStyle,
        { _, hourOfDay, minute -> onPicked(hourOfDay * MINUTES_PER_HOUR + minute) },
        minutesSinceMidnight / MINUTES_PER_HOUR,
        minutesSinceMidnight % MINUTES_PER_HOUR,
        true,
    ).show()
}

private fun openExactAlarmSettings(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
        data = "package:${context.packageName}".toUri()
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}

private fun openNotificationSettings(context: Context) {
    val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    } else {
        // ACTION_APP_NOTIFICATION_SETTINGS is API 26+; fall back to the app details page.
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData("package:${context.packageName}".toUri())
    }
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
}
