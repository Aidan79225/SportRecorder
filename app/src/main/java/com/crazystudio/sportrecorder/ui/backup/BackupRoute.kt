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
            auth.onAuthorizationResult(result.data)
            vm.refreshSnapshots()
        }
    }

    // On open, silently populate the signed-in account if consent was already granted.
    LaunchedEffect(Unit) { auth.refreshAccount() }
    // Once signed in, load the snapshot list.
    LaunchedEffect(state.isSignedIn) { if (state.isSignedIn) vm.refreshSnapshots() }

    BackupScreen(
        state = state,
        onSignIn = {
            scope.launch {
                try {
                    auth.accessToken() // populates account on success
                    vm.refreshSnapshots()
                } catch (e: BackupAuthorizationRequiredException) {
                    consentLauncher.launch(IntentSenderRequest.Builder(e.pendingIntent).build())
                }
            }
        },
        onSignOut = { auth.signOut() },
        onBackup = vm::backup,
        onRestore = { snapshot -> vm.restore(snapshot.id) },
        onConsumeMessage = vm::consumeMessage,
        onBack = onBack,
    )
}
