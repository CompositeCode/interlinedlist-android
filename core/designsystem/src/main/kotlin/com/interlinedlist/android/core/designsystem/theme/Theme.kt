package com.interlinedlist.android.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = OceanBlue,
    onPrimary = BrandWhite,
    secondary = EmeraldGreen,
    onSecondary = BrandWhite,
    tertiary = AmberGold,
    onTertiary = NearBlack,
    background = BrandWhite,
    onBackground = NearBlack,
    surface = BrandWhite,
    onSurface = NearBlack,
    error = AlertRed,
    onError = BrandWhite,
)

private val DarkColors = darkColorScheme(
    // Emerald reads better than Ocean Blue as the accent on dark surfaces.
    primary = EmeraldGreen,
    onPrimary = NearBlack,
    secondary = AmberGold,
    onSecondary = NearBlack,
    tertiary = TealCyan,
    onTertiary = NearBlack,
    background = NearBlack,
    onBackground = BrandWhite,
    surface = DeepOcean,
    onSurface = BrandWhite,
    error = AlertRed,
    onError = NearBlack,
)

/** App-wide Material 3 theme carrying the InterlinedList brand colours and type. */
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
