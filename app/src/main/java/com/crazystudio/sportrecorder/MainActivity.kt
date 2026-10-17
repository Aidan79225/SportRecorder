package com.crazystudio.sportrecorder

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import com.crazystudio.sportrecorder.ui.AppRoot
import com.crazystudio.sportrecorder.ui.theme.SportRecorderTheme

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The launch intent is kept across recreation, so this stays true for the whole share. A
        // share reopened from Recents re-delivers the same SEND: that is an ordinary launch.
        val launchedByShare = intent?.action == Intent.ACTION_SEND &&
            (intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) == 0
        // Only on a fresh start: after a rotation the editor is already restored on the back
        // stack, and reading the extra again would open a second one. Read as a CharSequence (a
        // styled share is not a String) and never null, so a share always opens the editor; capped
        // because it travels in the route and the saved state, which have a size limit.
        val sharedText = if (launchedByShare && savedInstanceState == null) {
            intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString().orEmpty().take(MAX_SHARED_TEXT)
        } else {
            null
        }
        setContent {
            SportRecorderTheme {
                AppRoot(
                    sharedText = sharedText,
                    launchedByShare = launchedByShare,
                    onShareDone = ::finish,
                )
            }
        }
    }
}

// Far longer than any Maps share, far below the Binder transaction limit.
private const val MAX_SHARED_TEXT = 4_096
