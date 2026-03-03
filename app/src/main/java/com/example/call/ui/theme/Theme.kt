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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = IOSBlue,
    secondary = IOSGray,
    tertiary = IOSGreen,
    background = Color.Black,
    surface = Color(0xFF1C1C1E),
    onBackground = Color.White,
    onSurface = Color.White,
    surfaceVariant = Color(0xFF2C2C2E),
    onSurfaceVariant = IOSGray
)

private val LightColorScheme = lightColorScheme(
    primary = IOSBlue,
    secondary = IOSGray,
    tertiary = IOSGreen,
    background = Color.White,
    surface = Color.White,
    onBackground = Color.Black,
    onSurface = Color.Black,
    surfaceVariant = Color(0xFFF2F2F7),
    onSurfaceVariant = IOSGray
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

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}