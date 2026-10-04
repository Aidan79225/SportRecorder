package com.crazystudio.sportrecorder.backup

import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.crazystudio.sportrecorder.shared.resources.Res
import com.crazystudio.sportrecorder.shared.resources.backup_cancel
import com.crazystudio.sportrecorder.shared.resources.backup_msg_cancelled
import com.crazystudio.sportrecorder.shared.resources.backup_restore_confirm_button
import com.crazystudio.sportrecorder.shared.resources.backup_restore_confirm_message
import com.crazystudio.sportrecorder.shared.resources.backup_restore_confirm_message_counted
import com.crazystudio.sportrecorder.shared.resources.backup_restore_confirm_message_fresh
import com.crazystudio.sportrecorder.shared.resources.backup_snapshot_subtitle
import com.crazystudio.sportrecorder.shared.resources.backup_step_uploading_photos
import com.crazystudio.sportrecorder.ui.backup.BackupMessage
import com.crazystudio.sportrecorder.ui.backup.BackupScreen
import com.crazystudio.sportrecorder.ui.backup.BackupUiState
import com.crazystudio.sportrecorder.ui.theme.SportRecorderTheme
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@RunWith(AndroidJUnit4::class)
class BackupScreenTest {
    @get:Rule val compose = createComposeRule()

    private var cancels = 0
    private var restores = mutableListOf<SnapshotInfo>()
    private var consumed = 0

    private fun str(res: StringResource, vararg args: Any): String = runBlocking { getString(res, *args) }

    /** Same "yyyy-MM-dd HH:mm" in the device zone that the screen renders. */
    private fun dateText(epochMillis: Long): String =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()))

    private fun show(state: BackupUiState) {
        compose.setContent {
            SportRecorderTheme {
                BackupScreen(
                    state = state,
                    onSignIn = {}, onSignOut = {}, onBackup = {},
                    onRestore = { restores.add(it) },
                    onCancel = { cancels++ },
                    onToggleAutoBackup = {},
                    onConsumeMessage = { consumed++ },
                    onBack = {},
                )
            }
        }
    }

    private val signedIn = BackupUiState(account = BackupAccount("me@example.com"))
    private val snapshot = SnapshotInfo("s1", 1_700_000_000_000L, "0.7.1", 10L, mealCount = 7)

    @Test fun runningUpload_showsFractionCaptionAndEnabledCancel() {
        show(signedIn.copy(job = BackupJobState.Running(BackupJobKind.Backup, BackupStep.UploadingPhotos, 12, 48)))

        compose.onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo(0.25f, 0f..1f))).assertExists()
        compose.onNodeWithText(str(Res.string.backup_step_uploading_photos, 12, 48)).assertExists()
        compose.onNodeWithText(str(Res.string.backup_cancel)).assertIsEnabled().performClick()
        assertEquals(1, cancels)
    }

    @Test fun applying_disablesCancel() {
        show(signedIn.copy(job = BackupJobState.Running(BackupJobKind.Restore, BackupStep.Applying, 0, 0)))
        compose.onNodeWithText(str(Res.string.backup_cancel)).assertIsNotEnabled()
    }

    @Test fun zeroTotal_showsIndeterminateProgress() {
        show(signedIn.copy(job = BackupJobState.Running(BackupJobKind.Backup, BackupStep.Preparing, 0, 0)))
        compose.onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate)).assertExists()
    }

    private fun openDialog(state: BackupUiState) {
        show(state)
        compose.onNodeWithText(str(Res.string.backup_snapshot_subtitle, dateText(snapshot.createdAt), snapshot.appVersionName)).performClick()
    }

    @Test fun restoreDialog_freshDevice_explainsDirectRestore_andConfirms() {
        openDialog(signedIn.copy(snapshots = listOf(snapshot), localMealCount = 0))
        compose.onNodeWithText(str(Res.string.backup_restore_confirm_message_fresh, dateText(snapshot.createdAt))).assertExists()
        compose.onNodeWithText(str(Res.string.backup_restore_confirm_button)).performClick()
        assertEquals(listOf(snapshot), restores)
    }

    @Test fun restoreDialog_withCounts_explainsSafetySnapshot() {
        openDialog(signedIn.copy(snapshots = listOf(snapshot), localMealCount = 3))
        compose.onNodeWithText(str(Res.string.backup_restore_confirm_message_counted, 3, dateText(snapshot.createdAt), 7)).assertExists()
    }

    @Test fun restoreDialog_legacySnapshot_omitsSnapshotCount() {
        openDialog(signedIn.copy(snapshots = listOf(snapshot.copy(mealCount = null)), localMealCount = 3))
        compose.onNodeWithText(str(Res.string.backup_restore_confirm_message, 3, dateText(snapshot.createdAt))).assertExists()
    }

    @Test fun cancelledMessage_showsSnackbar_thenConsumes() {
        show(signedIn.copy(message = BackupMessage.Cancelled))
        compose.onNodeWithText(str(Res.string.backup_msg_cancelled)).assertExists()
        compose.waitUntil(timeoutMillis = 15_000) { consumed == 1 }
    }
}
