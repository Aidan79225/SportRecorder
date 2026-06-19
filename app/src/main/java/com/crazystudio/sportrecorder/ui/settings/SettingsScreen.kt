package com.crazystudio.sportrecorder.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.crazystudio.sportrecorder.R
import com.crazystudio.sportrecorder.domain.reminder.ReminderPrefs
import com.crazystudio.sportrecorder.ui.theme.SportRecorderTheme

@Composable
@Suppress("LongParameterList") // cohesive single-screen: one callback per reminder control
fun SettingsScreen(
    state: ReminderPrefs,
    canScheduleExact: Boolean,
    notificationsBlocked: Boolean,
    onWindowClosingToggle: (Boolean) -> Unit,
    onFastCompleteToggle: (Boolean) -> Unit,
    onLeadDelta: (Long) -> Unit,
    onQuietHoursToggle: (Boolean) -> Unit,
    onPickQuietStart: () -> Unit,
    onPickQuietEnd: () -> Unit,
    onOpenExactAlarmSettings: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colorScheme.surface)
            .verticalScroll(rememberScrollState()),
    ) {
        Header(onBack = onBack)

        // Notifications off entirely (e.g. POST_NOTIFICATIONS denied) → nothing can be delivered.
        if (notificationsBlocked) {
            BannerRow(
                titleRes = R.string.settings_notifications_blocked_title,
                descRes = R.string.settings_notifications_blocked_desc,
                onClick = onOpenNotificationSettings,
            )
        }

        if (!canScheduleExact) {
            BannerRow(
                titleRes = R.string.settings_exact_alarm_title,
                descRes = R.string.settings_exact_alarm_desc,
                onClick = onOpenExactAlarmSettings,
            )
        }

        ToggleRow(
            titleRes = R.string.settings_reminder_window_closing_title,
            descRes = R.string.settings_reminder_window_closing_desc,
            checked = state.windowClosingEnabled,
            onCheckedChange = onWindowClosingToggle,
        )
        if (state.windowClosingEnabled) {
            LeadTimeRow(leadMinutes = state.leadMinutes, onLeadDelta = onLeadDelta)
        }

        ToggleRow(
            titleRes = R.string.settings_reminder_fast_complete_title,
            descRes = R.string.settings_reminder_fast_complete_desc,
            checked = state.fastCompleteEnabled,
            onCheckedChange = onFastCompleteToggle,
        )

        ToggleRow(
            titleRes = R.string.settings_quiet_hours_title,
            descRes = R.string.settings_quiet_hours_desc,
            checked = state.quietHoursEnabled,
            onCheckedChange = onQuietHoursToggle,
        )
        if (state.quietHoursEnabled) {
            TimeRow(
                labelRes = R.string.settings_quiet_start,
                minutesSinceMidnight = state.quietStartMinutes,
                onClick = onPickQuietStart,
            )
            TimeRow(
                labelRes = R.string.settings_quiet_end,
                minutesSinceMidnight = state.quietEndMinutes,
                onClick = onPickQuietEnd,
            )
        }
    }
}

@Composable
private fun Header(onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                painter = painterResource(id = R.drawable.ic_arrow_left_24dp),
                contentDescription = stringResource(id = R.string.settings_back),
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
        Text(
            text = stringResource(id = R.string.settings_title),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}

@Composable
private fun ToggleRow(
    titleRes: Int,
    descRes: Int,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(id = titleRes),
                style = MaterialTheme.typography.bodyLarge,
                color = colorScheme.onSurface,
            )
            Text(
                text = stringResource(id = descRes),
                style = MaterialTheme.typography.bodyMedium,
                color = colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun LeadTimeRow(leadMinutes: Long, onLeadDelta: (Long) -> Unit) {
    val colorScheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(id = R.string.settings_lead_time_title),
            style = MaterialTheme.typography.bodyLarge,
            color = colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            StepperButton(symbol = "−", onClick = { onLeadDelta(-LEAD_STEP_MINUTES) })
            Text(
                text = stringResource(id = R.string.settings_lead_time_value, leadMinutes),
                style = MaterialTheme.typography.bodyLarge,
                color = colorScheme.primary,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
            StepperButton(symbol = "+", onClick = { onLeadDelta(LEAD_STEP_MINUTES) })
        }
    }
}

@Composable
private fun StepperButton(symbol: String, onClick: () -> Unit) {
    val colorScheme = MaterialTheme.colorScheme
    Text(
        text = symbol,
        style = MaterialTheme.typography.headlineSmall,
        color = colorScheme.onSurface,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(colorScheme.surfaceContainer)
            .clickable { onClick() }
            .size(36.dp)
            .padding(top = 2.dp),
    )
}

@Composable
private fun TimeRow(labelRes: Int, minutesSinceMidnight: Int, onClick: () -> Unit) {
    val colorScheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(start = 20.dp, end = 20.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = stringResource(id = labelRes),
            style = MaterialTheme.typography.bodyLarge,
            color = colorScheme.onSurface,
        )
        Text(
            text = formatMinutes(minutesSinceMidnight),
            style = MaterialTheme.typography.bodyLarge,
            color = colorScheme.primary,
        )
    }
}

/** Tappable banner used for the actionable permission prompts (notifications, exact alarms). */
@Composable
private fun BannerRow(titleRes: Int, descRes: Int, onClick: () -> Unit) {
    val colorScheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(colorScheme.secondaryContainer)
            .clickable { onClick() }
            .padding(16.dp),
    ) {
        Text(
            text = stringResource(id = titleRes),
            style = MaterialTheme.typography.bodyLarge,
            color = colorScheme.onSecondaryContainer,
        )
        Text(
            text = stringResource(id = descRes),
            style = MaterialTheme.typography.bodyMedium,
            color = colorScheme.onSecondaryContainer,
        )
    }
}

/** Format minutes-since-midnight as a zero-padded HH:mm string (locale-independent digits). */
private fun formatMinutes(minutesSinceMidnight: Int): String {
    val hours = minutesSinceMidnight / MINUTES_PER_HOUR
    val minutes = minutesSinceMidnight % MINUTES_PER_HOUR
    return "%02d:%02d".format(hours, minutes)
}

private const val MINUTES_PER_HOUR = 60
private const val LEAD_STEP_MINUTES = 5L

@Preview(showBackground = true, backgroundColor = 0xFF2B2B2B)
@Composable
@Suppress("UnusedPrivateMember") // @Preview entry point used by the IDE preview tooling
private fun SettingsScreenPreview() {
    SportRecorderTheme {
        SettingsScreen(
            state = ReminderPrefs(
                windowClosingEnabled = true,
                fastCompleteEnabled = true,
                quietHoursEnabled = true,
            ),
            canScheduleExact = false,
            notificationsBlocked = true,
            onWindowClosingToggle = {},
            onFastCompleteToggle = {},
            onLeadDelta = {},
            onQuietHoursToggle = {},
            onPickQuietStart = {},
            onPickQuietEnd = {},
            onOpenExactAlarmSettings = {},
            onOpenNotificationSettings = {},
            onBack = {},
        )
    }
}
