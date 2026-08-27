package com.crazystudio.sportrecorder.ui.backup

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
            runCatching { auth.onAuthorizationResult(result.data) }
                .onSuccess { granted -> if (granted) vm.onAuthorized() }
        }
    }

    // On open, silently populate the signed-in account if consent was already granted.
    LaunchedEffect(Unit) { runCatching { auth.refreshAccount() } }
    // Once signed in, load the snapshot list.
    LaunchedEffect(state.isSignedIn) { if (state.isSignedIn) vm.refreshSnapshots() }

    BackupScreen(
        state = state,
        onSignIn = {
            scope.launch {
                runCatching { auth.accessToken() } // populates account on success
                    .onSuccess { vm.onAuthorized() }
                    .onFailure { e ->
                        if (e is BackupAuthorizationRequiredException) {
                            consentLauncher.launch(IntentSenderRequest.Builder(e.pendingIntent).build())
                        } else {
                            vm.reportFailure()
                        }
                    }
            }
        },
        onSignOut = {
            // Sign-out revokes the grant over the network; if that fails the account is still
            // connected, so say so instead of showing a sign-out that wouldn't survive a restart.
            scope.launch {
                runCatching { auth.signOut() }
                    .onFailure { vm.reportFailure(BackupMessage.SignOutFailed) }
            }
        },
        onBackup = vm::backup,
        onRestore = { snapshot -> vm.restore(snapshot.id) },
        onConsumeMessage = vm::consumeMessage,
        onBack = onBack,
    )
}
