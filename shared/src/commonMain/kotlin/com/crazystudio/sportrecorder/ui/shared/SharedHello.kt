package com.crazystudio.sportrecorder.ui.shared

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/**
 * Foundation smoke test for Compose Multiplatform: a single shared Composable that compiles for
 * Android, JVM and iOS. Proves the CMP toolchain works before real screens move into commonMain.
 */
@Composable
fun SharedHello() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = "Hello from shared Compose", style = MaterialTheme.typography.titleLarge)
    }
}
