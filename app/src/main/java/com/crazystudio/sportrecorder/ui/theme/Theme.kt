package com.crazystudio.sportrecorder.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = light_green,
    onPrimary = Color(0xFF003828),
    primaryContainer = dark_green,
    onPrimaryContainer = Color(0xFF5BF0B8),
    inversePrimary = Color(0xFF006C4E),
    secondary = Color(0xFFB2CCBF),
    onSecondary = Color(0xFF1D352B),
    secondaryContainer = Color(0xFF334B40),
    onSecondaryContainer = Color(0xFFCEE9DB),
    tertiary = Color(0xFFA6CCDC),
    onTertiary = Color(0xFF093543),
    tertiaryContainer = Color(0xFF264B5A),
    onTertiaryContainer = Color(0xFFC2E8F9),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = bg_black,
    onBackground = white,
    surface = bg_black,
    onSurface = white,
    surfaceVariant = bg_black2,
    onSurfaceVariant = grey_1,
    surfaceTint = light_green,
    inverseSurface = Color(0xFFE2E3DF),
    inverseOnSurface = Color(0xFF2E312F),
    outline = Color(0xFF8B9389),
    outlineVariant = Color(0xFF414941),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF3A3A3A),
    surfaceDim = Color(0xFF1F1F1F),
    surfaceContainerLowest = Color(0xFF1F1F1F),
    surfaceContainerLow = bg_black,
    surfaceContainer = Color(0xFF303030),
    surfaceContainerHigh = Color(0xFF3A3A3A),
    surfaceContainerHighest = bg_black2,
)

@Composable
fun SportRecorderTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        typography = createTypography(DarkColors),
        content = content,
    )
}
