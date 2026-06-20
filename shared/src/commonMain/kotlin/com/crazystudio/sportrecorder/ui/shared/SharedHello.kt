package com.crazystudio.sportrecorder.ui.shared

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.crazystudio.sportrecorder.shared.resources.Res
import com.crazystudio.sportrecorder.shared.resources.insights_streak
import com.crazystudio.sportrecorder.shared.resources.insights_weekday_initials
import com.crazystudio.sportrecorder.shared.resources.title_insights
import org.jetbrains.compose.resources.stringArrayResource
import org.jetbrains.compose.resources.stringResource

/**
 * Foundation smoke test for Compose Multiplatform + Compose Resources: exercises a plain string,
 * a format-arg string, and a string-array from the generated [Res], all resolved in commonMain so
 * the macOS CI proves resource generation works on Android, JVM and iOS.
 */
@Composable
fun SharedHello() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text = stringResource(Res.string.title_insights), style = MaterialTheme.typography.titleLarge)
        Text(text = stringResource(Res.string.insights_streak, 3))
        Text(text = stringArrayResource(Res.array.insights_weekday_initials).joinToString(" "))
    }
}
