package com.crazystudio.sportrecorder.ui.backup

import android.app.Activity
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.crazystudio.sportrecorder.backup.BackupAuthorizationRequiredException
import com.crazystudio.sportrecorder.backup.GoogleBackupAuth
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

private const val TAG = "BackupRoute"

/**
 * :app wrapper around the shared [BackupScreen]. Owns the Android-only Google consent flow: the
 * StartIntentSenderForResult launcher for the authorization PendingIntent, and the silent
 * account refresh on open. The screen itself stays platform-free in commonMain.
 */
@Composable
fun BackupRoute(onBack: () -> Unit) {
    val vm: BackupViewModel = koinViewModel()
    val auth: GoogleBackupAuth = koinInject()
    val state by vm.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

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

    // On open, silently populate the signed-in account if consent was already granted.
    LaunchedEffect(Unit) { runCatching { auth.refreshAccount() } }
    // Once signed in, load the snapshot list — keyed on the account itself, so switching
    // accounts (not just signing out and back in) reloads it.
    LaunchedEffect(state.account?.email) { if (state.isSignedIn) vm.refreshSnapshots() }

    // Every Drive call needs a live token, and Google can ask for consent again at any point
    // (the token is short-lived and nothing is persisted). Only sign-in used to handle that, so a
    // backup hitting it failed with a generic error and tapping again failed the same way —
    // there was no path back to the consent sheet. Run every action through here instead.
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
        onBackup = { authorizedThen { vm.backup() } },
        onRestore = { snapshot -> authorizedThen { vm.restore(snapshot.id) } },
        onConsumeMessage = vm::consumeMessage,
        onBack = onBack,
    )
}
