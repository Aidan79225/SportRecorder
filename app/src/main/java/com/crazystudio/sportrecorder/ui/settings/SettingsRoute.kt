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
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.crazystudio.sportrecorder.R

private const val MINUTES_PER_HOUR = 60

/** Wires [SettingsScreen] to the ViewModel plus the Android-only bits: notification permission,
 *  exact-alarm status/deep-link, and the quiet-hours time pickers. */
@Composable
fun SettingsRoute(onBack: () -> Unit) {
    val vm: SettingsViewModel = hiltViewModel()
    val state by vm.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Re-read exact-alarm permission whenever we resume (the user may grant it in system settings).
    var canScheduleExact by remember { mutableStateOf(canScheduleExactAlarms(context)) }
    LifecycleResumeEffect(Unit) {
        canScheduleExact = canScheduleExactAlarms(context)
        onPauseOrDispose { }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* Pref already reflects the user's intent; delivery just no-ops until granted. */ }

    fun enable(set: (Boolean) -> Unit, enabled: Boolean) {
        set(enabled)
        if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    SettingsScreen(
        state = state,
        canScheduleExact = canScheduleExact,
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
        onBack = onBack,
    )
}

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
