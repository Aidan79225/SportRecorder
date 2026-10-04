package com.crazystudio.sportrecorder.ui.backup

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.crazystudio.sportrecorder.backup.BackupAuthorizationRequiredException
import com.crazystudio.sportrecorder.backup.BackupNotifications
import com.crazystudio.sportrecorder.backup.GoogleBackupAuth
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

private const val TAG = "BackupRoute"

/**
 * :app wrapper around the shared [BackupScreen]. Owns the Android-only bits: the Google consent
 * flow (StartIntentSenderForResult for the authorization PendingIntent), the silent account
 * refresh on open, the one-time notification-permission prompt before a job, and dropping the
 * result notification once the screen has shown the same outcome as a snackbar.
 */
@Composable
fun BackupRoute(onBack: () -> Unit) {
    val vm: BackupViewModel = koinViewModel()
    val auth: GoogleBackupAuth = koinInject()
    val state by vm.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val notifications = remember(context) { BackupNotifications(context) }

    val consentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        scope.launch {
            // Surface any failure after consent — otherwise the dialog just closes and nothing
            // happens. A plain user cancel (no result data) stays quiet.
            runCatching { auth.onAuthorizationResult(result.data) }
                .onFailure { if (result.resultCode == Activity.RESULT_OK || result.data != null) vm.reportFailure() }
        }
    }

    // The job runs with or without notification permission; the prompt just lets the user see
    // progress with the screen off. Whatever they answer, the pending action proceeds.
    var pendingJob by remember { mutableStateOf<(() -> Unit)?>(null) }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        pendingJob?.invoke()
        pendingJob = null
    }

    fun withNotificationPermission(action: () -> Unit) {
        val needsPrompt = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        if (needsPrompt) {
            pendingJob = action
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            action()
        }
    }

    // On open, silently populate the signed-in account if consent was already granted.
    LaunchedEffect(Unit) { runCatching { auth.refreshAccount() } }
    // Once signed in, load the snapshot list — keyed on the account itself, so switching
    // accounts (not just signing out and back in) reloads it.
    LaunchedEffect(state.account?.email) { if (state.isSignedIn) vm.refreshSnapshots() }

    // Every Drive call needs a live token, and Google can ask for consent again at any point
    // (the token is short-lived and nothing is persisted). Run every action through here so the
    // consent sheet appears before a job, never inside one.
    fun authorizedThen(action: () -> Unit) {
        scope.launch {
            runCatching { auth.accessToken() } // populates account on success
                .onSuccess { action() }
                .onFailure { e ->
                    if (e is BackupAuthorizationRequiredException) {
                        consentLauncher.launch(IntentSenderRequest.Builder(e.pendingIntent).build())
                    } else {
                        Log.w(TAG, "authorization failed before a backup action", e)
                        vm.reportFailure()
                    }
                }
        }
    }

    BackupScreen(
        state = state,
        onSignIn = { authorizedThen { } },
        onSignOut = { auth.signOut() },
        onBackup = { authorizedThen { withNotificationPermission { vm.backup() } } },
        onRestore = { snapshot -> authorizedThen { withNotificationPermission { vm.restore(snapshot.id) } } },
        onToggleAutoBackup = vm::setAutoBackupEnabled,
        onCancel = vm::cancel,
        onConsumeMessage = {
            // The snackbar has been shown for this outcome; the notification would only repeat it.
            notifications.cancelResult()
            vm.consumeMessage()
        },
        onBack = onBack,
    )
}
