package com.crazystudio.sportrecorder.ui.shared

import androidx.compose.ui.window.ComposeUIViewController
import com.crazystudio.sportrecorder.ui.insights.InsightsScreen
import com.crazystudio.sportrecorder.ui.insights.InsightsUiState
import com.crazystudio.sportrecorder.ui.theme.SportRecorderTheme
import platform.UIKit.UIViewController

/**
 * iOS entry point that the future Xcode/SwiftUI app hosts. For now it renders the real shared
 * [InsightsScreen] (empty state) inside the shared theme — so CI proves the actual screen
 * compiles and links on iOS-native, not just a placeholder.
 */
@Suppress("FunctionName", "unused")
fun MainViewController(): UIViewController = ComposeUIViewController {
    SportRecorderTheme {
        InsightsScreen(
            state = InsightsUiState(),
            onSelectPeriod = {},
            onShiftMonth = {},
            photoModel = { null },
            onPhotoClick = { _, _ -> },
        )
    }
}
