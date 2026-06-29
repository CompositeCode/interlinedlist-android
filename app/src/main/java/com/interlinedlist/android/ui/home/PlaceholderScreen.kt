package com.interlinedlist.android.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.interlinedlist.android.core.designsystem.component.InterlinedListLogoMark
import com.interlinedlist.android.core.designsystem.component.InterlinedListWordmark
import com.interlinedlist.android.core.designsystem.theme.InterlinedListTheme

/**
 * Phase 0 landing screen. Confirms the brand theme, typeface, and logo assets
 * render correctly. Replaced by the auth/home flow in Phase 1.
 */
@Composable
fun PlaceholderScreen(modifier: Modifier = Modifier) {
    Scaffold(modifier = modifier.fillMaxSize()) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            InterlinedListLogoMark(modifier = Modifier.size(96.dp))
            Spacer(Modifier.height(16.dp))
            InterlinedListWordmark()
            Spacer(Modifier.height(24.dp))
            Text(
                text = "Android client — foundation ready.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PlaceholderScreenPreview() {
    InterlinedListTheme {
        PlaceholderScreen()
    }
}
