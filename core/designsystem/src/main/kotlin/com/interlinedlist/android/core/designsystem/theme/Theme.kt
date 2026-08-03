package com.interlinedlist.android.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

// Light = soft sand surfaces, teal structure, green primary actions, amber highlights.
private val LightColors = lightColorScheme(
    primary = ILGreen,
    onPrimary = ILWhite,
    primaryContainer = ILGreenContainerLight,
    onPrimaryContainer = ILOnGreenContainerLight,
    secondary = ILTeal,
    onSecondary = ILWhite,
    secondaryContainer = ILTealContainerLight,
    onSecondaryContainer = ILTeal,
    tertiary = ILAmber,
    onTertiary = ILTextLight,
    tertiaryContainer = ILAmberContainerLight,
    onTertiaryContainer = ILOnAmberContainerLight,
    background = ILBgLight,
    onBackground = ILTextLight,
    surface = ILSurfaceLight,
    onSurface = ILTextLight,
    surfaceVariant = ILSurface2Light,
    onSurfaceVariant = ILTextMutedLight,
    surfaceContainer = ILSurface2Light,
    outline = ILBorderLight,
    outlineVariant = ILBorderLight,
    error = ILError,
    onError = ILWhite,
)

// Dark = near-black surfaces; the deep teal lifts to accent and green brightens so
// both stay legible per the brand's "light mark on dark" rule.
private val DarkColors = darkColorScheme(
    primary = ILGreenDark,
    onPrimary = ILBgDark,
    primaryContainer = ILGreenContainerDark,
    onPrimaryContainer = ILOnGreenContainerDark,
    secondary = ILTealAccent,
    onSecondary = ILBgDark,
    secondaryContainer = ILTealContainerDark,
    onSecondaryContainer = ILTextDark,
    tertiary = ILAmber,
    onTertiary = ILBgDark,
    tertiaryContainer = ILAmberContainerDark,
    onTertiaryContainer = ILOnAmberContainerDark,
    background = ILBgDark,
    onBackground = ILTextDark,
    surface = ILSurfaceDark,
    onSurface = ILTextDark,
    surfaceVariant = ILSurface2Dark,
    onSurfaceVariant = ILTextMutedDark,
    surfaceContainer = ILSurface2Dark,
    outline = ILBorderDark,
    outlineVariant = ILBorderDark,
    error = ILError,
    onError = ILBgDark,
)

/**
 * App-wide Material 3 theme carrying the InterlinedList "Strata" brand colours and
 * type. [darkTheme] is resolved by the caller from the user's appearance setting
 * (System / Light / Dark); it defaults to following the OS.
 */
@Composable
fun InterlinedListTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = InterlinedListTypography,
        content = content,
    )
}
