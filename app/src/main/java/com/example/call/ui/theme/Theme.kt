package com.example.call.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = VisionPrimary,
    secondary = VisionTextDarkSecondary,
    tertiary = VisionPrimary,
    background = VisionDarkBg,
    surface = VisionSurfaceDark,
    onBackground = VisionTextDarkPrimary,
    onSurface = VisionTextDarkPrimary,
    surfaceVariant = Color(0x33FFFFFF),
    onSurfaceVariant = VisionTextDarkSecondary
)

private val LightColorScheme = lightColorScheme(
    primary = VisionPrimary,
    secondary = VisionTextLightSecondary,
    tertiary = VisionPrimary,
    background = VisionLightBg,
    surface = VisionSurfaceLight,
    onBackground = VisionTextLightPrimary,
    onSurface = VisionTextLightPrimary,
    surfaceVariant = Color(0x1A000000),
    onSurfaceVariant = VisionTextLightSecondary
)

@Composable
fun CallTheme(
    themePreference: String = "System",
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val isDark = when (themePreference) {
        "Dark" -> true
        "Light" -> false
        else -> darkTheme
    }

    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        isDark -> DarkColorScheme
        else -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.setDecorFitsSystemWindows(window, false)
            // Setting status/navigation bar transparent to allow edge-to-edge drawing
            window.statusBarColor = Color.Transparent.value.toInt()
            window.navigationBarColor = Color.Transparent.value.toInt()
            
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = !isDark
            insetsController.isAppearanceLightNavigationBars = !isDark
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}