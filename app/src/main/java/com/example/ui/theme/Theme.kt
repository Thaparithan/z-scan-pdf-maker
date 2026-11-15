package com.example.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

enum class AppThemeMode {
    SYSTEM,
    LIGHT,
    DARK
}

private val ZScanDarkColorScheme = darkColorScheme(
    primary = DarkElectricViolet,
    onPrimary = DarkPrimaryBackground,
    primaryContainer = DarkPrimaryPurple,
    onPrimaryContainer = DarkSoftLavender,
    secondary = DarkElectricViolet,
    onSecondary = DarkPrimaryBackground,
    secondaryContainer = DarkElevatedSurface,
    onSecondaryContainer = DarkSoftLavender,
    tertiary = DarkSoftLavender,
    onTertiary = DarkPrimaryBackground,
    background = DarkPrimaryBackground,
    onBackground = DarkPrimaryText,
    surface = DarkCardBackground,
    onSurface = DarkPrimaryText,
    surfaceVariant = DarkSecondaryBackground,
    onSurfaceVariant = DarkSecondaryText,
    surfaceContainer = DarkElevatedSurface,
    outline = DarkBorder,
    outlineVariant = DarkBorder,
    error = ErrorRedDark,
    onError = DarkPrimaryBackground
)

private val ZScanLightColorScheme = lightColorScheme(
    primary = LightPrimaryPurple,
    onPrimary = Color.White,
    primaryContainer = LightSoftLavender,
    onPrimaryContainer = LightDeepPurple,
    secondary = LightAccentViolet,
    onSecondary = Color.White,
    secondaryContainer = LightElevatedSurface,
    onSecondaryContainer = LightDeepPurple,
    tertiary = LightDeepPurple,
    onTertiary = Color.White,
    background = LightPrimaryBackground,
    onBackground = LightPrimaryText,
    surface = LightCardBackground,
    onSurface = LightPrimaryText,
    surfaceVariant = LightSecondaryBackground,
    onSurfaceVariant = LightSecondaryText,
    surfaceContainer = LightElevatedSurface,
    outline = LightBorder,
    outlineVariant = LightBorder,
    error = ErrorRedLight,
    onError = Color.White
)

val ZScanShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun ZScanTheme(
    themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val isDark = when (themeMode) {
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    val colorScheme = if (isDark) ZScanDarkColorScheme else ZScanLightColorScheme
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            @Suppress("DEPRECATION")
            window.statusBarColor = colorScheme.background.toArgb()
            @Suppress("DEPRECATION")
            window.navigationBarColor = colorScheme.surface.toArgb()
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = !isDark
            insetsController.isAppearanceLightNavigationBars = !isDark
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = ZScanShapes,
        content = content
    )
}
