package com.interlinedlist.android.core.designsystem.theme

import androidx.compose.ui.graphics.Color

// InterlinedList — "Strata" design tokens. Canonical source: brand-kit/theme/tokens.json
// (identical to the palette on interlinedlist.com). Light = soft sand, dark = near-black.

// ---- Brand (constant across themes) ----
val ILGreen = Color(0xFF2FA877) // primary action
val ILGreenHover = Color(0xFF28936A)
val ILGreenDark = Color(0xFF3FBF8C) // primary in dark mode
val ILTeal = Color(0xFF184860) // structure / masthead
val ILTealDeep = Color(0xFF0C2C3A) // dark masthead
val ILTealAccent = Color(0xFF7FB8C4) // links / borders in dark
val ILTealBright = Color(0xFF4FD09C) // wordmark accent
val ILAmber = Color(0xFFF0A830) // live / highlight
val ILAmberHover = Color(0xFFDB9720)
val ILError = Color(0xFFED321F)
val ILWhite = Color(0xFFFFFFFF)

// ---- Light theme surfaces & text ----
val ILBgLight = Color(0xFFF4EEE2) // soft sand background
val ILSurfaceLight = Color(0xFFFBF7EF)
val ILSurface2Light = Color(0xFFF6F1E7)
val ILSurface3Light = Color(0xFFF1EADD)
val ILTextLight = Color(0xFF16323C)
val ILTextBodyLight = Color(0xFF22383E)
val ILTextMutedLight = Color(0xFF6B7C83) // ≈ rgba(24,72,96,0.55) over sand
val ILBorderLight = Color(0xFFDDD6C8)
// Tonal containers (FAB, tonal buttons, nav indicator) in brand hues.
val ILGreenContainerLight = Color(0xFFBDE8D4)
val ILOnGreenContainerLight = Color(0xFF0B4A34)
val ILTealContainerLight = Color(0xFFCFE0E4)
val ILAmberContainerLight = Color(0xFFFBE3BC)
val ILOnAmberContainerLight = Color(0xFF4A3305)

// ---- Dark theme surfaces & text ----
val ILBgDark = Color(0xFF121317) // near-black background
val ILSurfaceDark = Color(0xFF17191F)
val ILSurface2Dark = Color(0xFF1B1D23)
val ILSurface3Dark = Color(0xFF22252D)
val ILTextDark = Color(0xFFF3F1EA)
val ILTextBodyDark = Color(0xFFE4E1D9)
val ILTextMutedDark = Color(0xFF9AA1A6) // ≈ rgba(243,241,234,0.45) over near-black
val ILBorderDark = Color(0xFF2C2F37)
// Tonal containers for dark surfaces.
val ILGreenContainerDark = Color(0xFF1E5641)
val ILOnGreenContainerDark = Color(0xFFB6ECD6)
val ILTealContainerDark = Color(0xFF20343C)
val ILAmberContainerDark = Color(0xFF4E3A12)
val ILOnAmberContainerDark = Color(0xFFF7DDB0)
