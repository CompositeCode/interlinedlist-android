package com.interlinedlist.android.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.interlinedlist.android.core.designsystem.R

/** The InterlinedList brand typeface, "Play" (OFL, via Google Fonts). */
val PlayFontFamily = FontFamily(
    Font(R.font.play_regular, FontWeight.Normal),
    Font(R.font.play_bold, FontWeight.Bold),
)

private val Default = Typography()

/** Material 3 type scale rendered in the brand typeface. */
val InterlinedListTypography = Typography(
    displayLarge = Default.displayLarge.copy(fontFamily = PlayFontFamily),
    displayMedium = Default.displayMedium.copy(fontFamily = PlayFontFamily),
    displaySmall = Default.displaySmall.copy(fontFamily = PlayFontFamily),
    headlineLarge = Default.headlineLarge.copy(fontFamily = PlayFontFamily),
    headlineMedium = Default.headlineMedium.copy(fontFamily = PlayFontFamily),
    headlineSmall = Default.headlineSmall.copy(fontFamily = PlayFontFamily),
    titleLarge = Default.titleLarge.copy(fontFamily = PlayFontFamily),
    titleMedium = Default.titleMedium.copy(fontFamily = PlayFontFamily),
    titleSmall = Default.titleSmall.copy(fontFamily = PlayFontFamily),
    bodyLarge = Default.bodyLarge.copy(fontFamily = PlayFontFamily),
    bodyMedium = Default.bodyMedium.copy(fontFamily = PlayFontFamily),
    bodySmall = Default.bodySmall.copy(fontFamily = PlayFontFamily),
    labelLarge = Default.labelLarge.copy(fontFamily = PlayFontFamily),
    labelMedium = Default.labelMedium.copy(fontFamily = PlayFontFamily),
    labelSmall = Default.labelSmall.copy(fontFamily = PlayFontFamily),
)
