package com.example.proyectwin.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

import androidx.compose.material3.Shapes
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

// Radios alineados con la web: r-sm 6 / r-md 8 / r-lg 12 / r-xl 16.
val AppShapes = Shapes(
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(12.dp),
    extraLarge = RoundedCornerShape(16.dp),
)

private val DarkColorScheme = darkColorScheme(
    primary = SenaGreen,                     // #22B949 consola
    secondary = SenaAccentDark,
    tertiary = SenaAccentDark,
    background = Color(0xFF0A0A0A),
    surface = Color(0xFF1E1E1E),
    surfaceVariant = Color(0xFF2A2A2A),
    surfaceTint = SenaGreen,
    onPrimary = Color(0xFF000000),           // negro sobre verde brillante
    onSecondary = Color(0xFF000000),
    onTertiary = Color(0xFF000000),
    onBackground = Color(0xFFFFFFFF),
    onSurface = Color(0xFFFFFFFF),
    onSurfaceVariant = Color(0xFFD0D0D0),
    primaryContainer = Color(0xFF1F5E00),
    onPrimaryContainer = Color(0xFFE5F6D9),
    secondaryContainer = Color(0xFF09485A),
    onSecondaryContainer = Color(0xFFD9EEF3),
    tertiaryContainer = Color(0xFF09485A),
    onTertiaryContainer = Color(0xFFD9EEF3),
    error = Color(0xFFFFA3A3),
    onError = Color(0xFF000000),
    errorContainer = Color(0xFF421D1D),
    onErrorContainer = Color(0xFFFFD4D4),
    outline = Color(0xFFA0A0A0),
    outlineVariant = Color(0xFF404040),
    inverseSurface = Color(0xFFFFFFFF),
    inverseOnSurface = Color(0xFF171717),
    inversePrimary = Color(0xFF2A7C00),
    scrim = Color(0xFF000000)
)

private val LightColorScheme = lightColorScheme(
    primary = SenaGreenLight,                // g-700
    secondary = SenaAccent,
    tertiary = SenaAccent,
    background = Color(0xFFF7FAF3),
    surface = Color.White,
    surfaceVariant = Color(0xFFF5F5F5),
    surfaceTint = SenaGreenLight,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF171717),
    onSurface = Color(0xFF171717),
    onSurfaceVariant = Color(0xFF525252),
    primaryContainer = Color(0xFFE5F6D9),    // g-100
    onPrimaryContainer = Color(0xFF1F5E00),
    secondaryContainer = Color(0xFFD9EEF3),  // t-100
    onSecondaryContainer = Color(0xFF073643),
    tertiaryContainer = Color(0xFFD9EEF3),
    onTertiaryContainer = Color(0xFF073643),
    error = Color(0xFFDC2626),
    onError = Color.White,
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF991B1B),
    outline = Color(0xFFA3A3A3),
    outlineVariant = Color(0xFFD4D4D4),
    inverseSurface = Color(0xFF171717),
    inverseOnSurface = Color(0xFFF5F5F5),
    inversePrimary = Color(0xFF22B949),
    scrim = Color(0xFF000000)
)

// Local for manual dark mode control
val LocalThemeIsDark = staticCompositionLocalOf<MutableState<Boolean>> {
    error("No ThemeIsDark provided")
}

// SenaColorScheme mirrors the frontend's CSS custom properties approach:
// All UI code reads via senaColors() and automatically adapts to dark/light.
val LocalSenaColorScheme = staticCompositionLocalOf<SenaColorScheme> {
    error("No SenaColorScheme provided")
}

@Composable
fun senaColors(): SenaColorScheme = LocalSenaColorScheme.current

@Composable
fun ProyecTwinTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val isDarkState = remember { mutableStateOf(darkTheme) }

    CompositionLocalProvider(
        LocalThemeIsDark provides isDarkState,
        LocalSenaColorScheme provides if (isDarkState.value) darkSenaColors() else lightSenaColors()
    ) {
        val currentDark = LocalThemeIsDark.current.value
        val colorScheme = when {
            (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) -> {
                val context = LocalContext.current
                if (currentDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            }

            currentDark -> DarkColorScheme
            else -> LightColorScheme
        }

        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = AppShapes,
            content = content
        )
    }
}
