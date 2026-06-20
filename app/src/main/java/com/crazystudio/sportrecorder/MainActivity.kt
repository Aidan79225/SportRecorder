package com.crazystudio.sportrecorder

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import com.crazystudio.sportrecorder.ui.AppRoot
import com.crazystudio.sportrecorder.ui.theme.SportRecorderTheme

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SportRecorderTheme {
                AppRoot()
            }
        }
    }
}
