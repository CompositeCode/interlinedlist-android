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
import com.interlinedlist.android.core.designsystem.theme.ILGreen
import com.interlinedlist.android.core.designsystem.theme.ILGreenDark
import com.interlinedlist.android.core.designsystem.theme.ILTeal
import com.interlinedlist.android.core.designsystem.theme.ILTealAccent
import com.interlinedlist.android.core.designsystem.theme.PlayFontFamily

/**
 * The InterlinedList "Strata" icon mark (the interlinked list ladder). The tri-colour
 * mark reads on either surface, but on dark the deep teal is lifted to the accent so
 * the structure stays legible. The vector is the same art shipped on interlinedlist.com;
 * never recoloured beyond this light/dark pairing or distorted.
 */
@Composable
fun InterlinedListLogoMark(
    modifier: Modifier = Modifier,
    darkTheme: Boolean = isSystemInDarkTheme(),
) {
    val markRes = if (darkTheme) R.drawable.il_logo_mark_dark else R.drawable.il_logo_mark
    Image(
        painter = painterResource(markRes),
        contentDescription = "InterlinedList",
        modifier = modifier,
    )
}

/**
 * The "InterlinedList" wordmark in the brand typeface: teal "Interlined" (lifted to the
 * teal accent on dark surfaces for legibility) + green "List", with an optional tagline.
 */
@Composable
fun InterlinedListWordmark(
    modifier: Modifier = Modifier,
    showTagline: Boolean = true,
    darkTheme: Boolean = isSystemInDarkTheme(),
) {
    val interlinedColor = if (darkTheme) ILTealAccent else ILTeal
    val listColor = if (darkTheme) ILGreenDark else ILGreen
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
                color = listColor,
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
