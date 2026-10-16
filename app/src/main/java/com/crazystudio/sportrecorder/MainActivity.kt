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
        // The launch intent is kept across recreation, so this stays true for the whole share.
        val launchedByShare = intent?.action == Intent.ACTION_SEND
        // Only on a fresh start: after a rotation the editor is already restored on the back
        // stack, and reading the extra again would open a second one.
        val sharedText = if (launchedByShare && savedInstanceState == null) {
            intent.getStringExtra(Intent.EXTRA_TEXT)
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
