package com.crazystudio.sportrecorder.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    background = bg_black,
    onBackground = white,
    primary = light_green,
    onPrimary = bg_black,
    outline = light_green,
    surface = bg_black,
    onSurface = white,
    surfaceVariant = bg_black2,
    onSurfaceVariant = white,
    surfaceContainerLowest = bg_black,
    surfaceContainerLow = bg_black,
    surfaceContainer = bg_black,
    surfaceContainerHigh = bg_black2,
    surfaceContainerHighest = bg_black2,
)

private val LightColorScheme = lightColorScheme(
    background = bg_black,
    onBackground = white,
    primary = light_green,
    onPrimary = bg_black,
    outline = light_green,
    surface = bg_black,
    onSurface = white,
    surfaceVariant = bg_black2,
    onSurfaceVariant = white,
    surfaceContainerLowest = bg_black,
    surfaceContainerLow = bg_black,
    surfaceContainer = bg_black,
    surfaceContainerHigh = bg_black2,
    surfaceContainerHighest = bg_black2,
    /* Other default colors to override
    background = Color(0xFFFFFBFE),
    surface = Color(0xFFFFFBFE),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F),
     */
)

@Composable
@Suppress("UnusedParameter") // public theme API slot; dynamic-color branch below is intentionally disabled
fun SportRecorderTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
//        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
//            val context = LocalContext.current
//            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
//        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
//    val view = LocalView.current
//    if (!view.isInEditMode) {
//        SideEffect {
//            val window = (view.context as Activity).window
//            window.statusBarColor = colorScheme.primary.toArgb()
//            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = darkTheme
//        }
//    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = createTypography(colorScheme),
        content = content
    )
}
