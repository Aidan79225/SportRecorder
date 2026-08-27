package com.crazystudio.sportrecorder.ui.backup

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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.crazystudio.sportrecorder.backup.SnapshotInfo
import com.crazystudio.sportrecorder.shared.resources.Res
import com.crazystudio.sportrecorder.shared.resources.backup_cancel
import com.crazystudio.sportrecorder.shared.resources.backup_intro
import com.crazystudio.sportrecorder.shared.resources.backup_last_backed_up
import com.crazystudio.sportrecorder.shared.resources.backup_msg_backup_complete
import com.crazystudio.sportrecorder.shared.resources.backup_msg_failed
import com.crazystudio.sportrecorder.shared.resources.backup_msg_restore_complete
import com.crazystudio.sportrecorder.shared.resources.backup_msg_schema_too_new
import com.crazystudio.sportrecorder.shared.resources.backup_msg_sign_out_failed
import com.crazystudio.sportrecorder.shared.resources.backup_never
import com.crazystudio.sportrecorder.shared.resources.backup_now
import com.crazystudio.sportrecorder.shared.resources.backup_restore_confirm_button
import com.crazystudio.sportrecorder.shared.resources.backup_restore_confirm_message
import com.crazystudio.sportrecorder.shared.resources.backup_restore_confirm_title
import com.crazystudio.sportrecorder.shared.resources.backup_restore_empty
import com.crazystudio.sportrecorder.shared.resources.backup_restore_heading
import com.crazystudio.sportrecorder.shared.resources.backup_sign_in
import com.crazystudio.sportrecorder.shared.resources.backup_sign_out
import com.crazystudio.sportrecorder.shared.resources.backup_signed_in_as
import com.crazystudio.sportrecorder.shared.resources.backup_snapshot_subtitle
import com.crazystudio.sportrecorder.shared.resources.backup_title
import com.crazystudio.sportrecorder.shared.resources.ic_arrow_left_24dp
import com.crazystudio.sportrecorder.shared.resources.settings_back
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Instant

/**
 * Stateless backup & restore screen. Holds no platform/auth logic — sign-in, sign-out and the
 * Google consent flow are wired by the :app `BackupRoute`. Copy is intentionally gentle and
 * opt-in (陪伴, never nagging): backup is optional and the screen never pressures the user.
 */
@Composable
@Suppress("LongParameterList") // cohesive single-screen: one callback per user action
fun BackupScreen(
    state: BackupUiState,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
    onBackup: () -> Unit,
    onRestore: (SnapshotInfo) -> Unit,
    onConsumeMessage: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    val snackbarHostState = remember { SnackbarHostState() }
    val messageText = state.message?.let { stringResource(messageRes(it)) }
    LaunchedEffect(state.message) {
        if (messageText != null) {
            snackbarHostState.showSnackbar(messageText)
            onConsumeMessage()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colorScheme.surface),
    ) {
        Header(onBack = onBack)
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            if (state.isSignedIn) {
                SignedInContent(
                    state = state,
                    onSignOut = onSignOut,
                    onBackup = onBackup,
                    onRestore = onRestore,
                )
            } else {
                SignedOutContent(onSignIn = onSignIn, enabled = !state.isBusy)
            }
        }
        SnackbarHost(hostState = snackbarHostState)
    }
}

private fun messageRes(message: BackupMessage) = when (message) {
    BackupMessage.BackupComplete -> Res.string.backup_msg_backup_complete
    BackupMessage.RestoreComplete -> Res.string.backup_msg_restore_complete
    BackupMessage.RestoreSchemaTooNew -> Res.string.backup_msg_schema_too_new
    BackupMessage.SignOutFailed -> Res.string.backup_msg_sign_out_failed
    BackupMessage.Failed -> Res.string.backup_msg_failed
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
                painter = painterResource(Res.drawable.ic_arrow_left_24dp),
                contentDescription = stringResource(Res.string.settings_back),
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
        Text(
            text = stringResource(Res.string.backup_title),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}

@Composable
private fun SignedOutContent(onSignIn: () -> Unit, enabled: Boolean) {
    val colorScheme = MaterialTheme.colorScheme
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
        Text(
            text = stringResource(Res.string.backup_intro),
            style = MaterialTheme.typography.bodyMedium,
            color = colorScheme.onSurfaceVariant,
        )
        Button(
            onClick = onSignIn,
            enabled = enabled,
            modifier = Modifier.padding(top = 16.dp),
        ) {
            Text(stringResource(Res.string.backup_sign_in))
        }
    }
}

@Composable
private fun SignedInContent(
    state: BackupUiState,
    onSignOut: () -> Unit,
    onBackup: () -> Unit,
    onRestore: (SnapshotInfo) -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val enabled = !state.isBusy

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = stringResource(Res.string.backup_signed_in_as, state.account?.email.orEmpty()),
            style = MaterialTheme.typography.bodyLarge,
            color = colorScheme.onSurface,
            modifier = Modifier.weight(1f).padding(end = 8.dp),
        )
        TextButton(onClick = onSignOut, enabled = enabled) {
            Text(stringResource(Res.string.backup_sign_out))
        }
    }

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        Button(onClick = onBackup, enabled = enabled) {
            Text(stringResource(Res.string.backup_now))
        }
        val lastBackedUp = state.lastBackedUpAt
        Text(
            text = if (lastBackedUp != null) {
                stringResource(Res.string.backup_last_backed_up, formatTimestamp(lastBackedUp))
            } else {
                stringResource(Res.string.backup_never)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
        if (state.isBusy) {
            CircularProgressIndicator(modifier = Modifier.padding(top = 16.dp).size(28.dp))
        }
    }

    Text(
        text = stringResource(Res.string.backup_restore_heading),
        style = MaterialTheme.typography.titleMedium,
        color = colorScheme.onSurface,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
    )
    RestoreList(snapshots = state.snapshots, enabled = enabled, onRestore = onRestore)
}

@Composable
private fun RestoreList(
    snapshots: List<SnapshotInfo>,
    enabled: Boolean,
    onRestore: (SnapshotInfo) -> Unit,
) {
    if (snapshots.isEmpty()) {
        Text(
            text = stringResource(Res.string.backup_restore_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
        )
        return
    }

    var pending by remember { mutableStateOf<SnapshotInfo?>(null) }
    snapshots.forEach { snapshot ->
        SnapshotRow(snapshot = snapshot, enabled = enabled, onClick = { pending = snapshot })
    }

    pending?.let { snapshot ->
        RestoreConfirmDialog(
            snapshot = snapshot,
            onConfirm = {
                onRestore(snapshot)
                pending = null
            },
            onDismiss = { pending = null },
        )
    }
}

@Composable
private fun SnapshotRow(snapshot: SnapshotInfo, enabled: Boolean, onClick: () -> Unit) {
    val colorScheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Text(
            text = stringResource(
                Res.string.backup_snapshot_subtitle,
                formatTimestamp(snapshot.createdAt),
                snapshot.appVersionName,
            ),
            style = MaterialTheme.typography.bodyLarge,
            color = colorScheme.onSurface,
        )
    }
}

@Composable
private fun RestoreConfirmDialog(
    snapshot: SnapshotInfo,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.backup_restore_confirm_title)) },
        text = {
            Text(
                stringResource(
                    Res.string.backup_restore_confirm_message,
                    formatTimestamp(snapshot.createdAt),
                ),
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(Res.string.backup_restore_confirm_button))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.backup_cancel))
            }
        },
    )
}

/** Format epoch millis as a locale-independent "yyyy-MM-dd HH:mm" in the device time zone. */
private fun formatTimestamp(epochMillis: Long): String {
    val dt = Instant.fromEpochMilliseconds(epochMillis)
        .toLocalDateTime(TimeZone.currentSystemDefault())
    fun pad2(n: Int) = n.toString().padStart(2, '0')
    return "${dt.year}-${pad2(dt.month.ordinal + 1)}-${pad2(dt.day)} ${pad2(dt.hour)}:${pad2(dt.minute)}"
}
