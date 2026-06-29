package com.interlinedlist.android.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.interlinedlist.android.core.designsystem.component.InterlinedListLogoMark
import com.interlinedlist.android.core.designsystem.component.InterlinedListWordmark

/** Post-login landing screen. Real feature surfaces (lists, feed) land in later phases. */
@Composable
fun HomeScreen(
    onLoggedOut: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val name by viewModel.displayName.collectAsStateWithLifecycle()
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
                text = name?.let { "Signed in as $it" } ?: "You're signed in.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.testTag("homeGreeting"),
            )
            Spacer(Modifier.height(24.dp))
            OutlinedButton(
                onClick = { viewModel.logout(onLoggedOut) },
                modifier = Modifier.testTag("homeSignOut"),
            ) {
                Text("Sign out")
            }
        }
    }
}
