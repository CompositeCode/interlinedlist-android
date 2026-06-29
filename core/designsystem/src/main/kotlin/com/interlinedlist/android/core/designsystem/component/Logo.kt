package com.interlinedlist.android.core.designsystem.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.interlinedlist.android.core.designsystem.R
import com.interlinedlist.android.core.designsystem.theme.EmeraldGreen
import com.interlinedlist.android.core.designsystem.theme.OceanBlue
import com.interlinedlist.android.core.designsystem.theme.PlayFontFamily

/**
 * The InterlinedList icon mark. Picks the light or dark asset variant to suit
 * the current theme, per the brand rule (light mark on dark surfaces and vice
 * versa). The provided files are used as-is — never recoloured or distorted.
 */
@Composable
fun InterlinedListLogoMark(
    modifier: Modifier = Modifier,
    darkTheme: Boolean = isSystemInDarkTheme(),
) {
    val markRes = if (darkTheme) R.drawable.il_mark_light else R.drawable.il_mark_dark
    Image(
        painter = painterResource(markRes),
        contentDescription = "InterlinedList",
        modifier = modifier,
    )
}

/**
 * The "InterlinedList" wordmark rendered in the brand typeface: Ocean Blue
 * "Interlined" (switching to the theme's on-background colour in dark mode for
 * legibility) + Emerald "List", with the optional tagline.
 */
@Composable
fun InterlinedListWordmark(
    modifier: Modifier = Modifier,
    showTagline: Boolean = true,
    darkTheme: Boolean = isSystemInDarkTheme(),
) {
    val interlinedColor = if (darkTheme) MaterialTheme.colorScheme.onBackground else OceanBlue
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Row {
            Text(
                text = "Interlined",
                fontFamily = PlayFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 28.sp,
                color = interlinedColor,
            )
            Text(
                text = "List",
                fontFamily = PlayFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 28.sp,
                color = EmeraldGreen,
            )
        }
        if (showTagline) {
            Text(
                text = "Productivity, Connected.",
                fontFamily = PlayFontFamily,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
