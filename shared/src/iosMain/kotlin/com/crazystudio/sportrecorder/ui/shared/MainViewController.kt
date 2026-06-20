package com.crazystudio.sportrecorder.ui.shared

import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIViewController

/**
 * iOS entry point: wraps the shared Compose UI in a [UIViewController] that the SwiftUI/Xcode app
 * will host. For now it renders [SharedHello] as the CMP foundation smoke test.
 */
@Suppress("FunctionName", "unused")
fun MainViewController(): UIViewController = ComposeUIViewController { SharedHello() }
