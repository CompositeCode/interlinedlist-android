package com.interlinedlist.android.feature.profile.ui.profile

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.interlinedlist.android.core.designsystem.theme.InterlinedListTheme
import com.interlinedlist.android.core.model.CustomerStatus
import com.interlinedlist.android.feature.profile.domain.ProfileUser

/**
 * Another user's public profile, reached by drilling down from search (route
 * `profile/{username}`). Includes a back affordance to ascend, mirroring the app's
 * drill-down navigation pattern.
 *
 * @param onBack pop back to the previous screen (search).
 */
@Composable
fun UserProfileRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: UserProfileViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    UserProfileScreen(
        state = state,
        onBack = onBack,
        onRetry = viewModel::refresh,
        modifier = modifier,
    )
}

/** Stateless other-user profile UI. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun UserProfileScreen(
    state: ProfileUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(state.user?.displayLabel ?: "Profile") },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag(ProfileTestTags.BACK)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        when {
            state.user != null -> ProfileContent(
                user = state.user,
                modifier = Modifier.fillMaxSize().padding(padding),
            )

            state.isLoading -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(Modifier.testTag(ProfileTestTags.PROGRESS))
            }

            else -> Box(
                Modifier.fillMaxSize().padding(padding).padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = state.errorMessage ?: "Couldn't load this profile.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag(ProfileTestTags.ERROR),
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = onRetry) { Text("Retry") }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun UserProfileScreenPreview() {
    InterlinedListTheme {
        UserProfileScreen(
            state = ProfileUiState(
                user = ProfileUser(
                    id = "2",
                    username = "ada",
                    displayName = "Ada Lovelace",
                    avatarUrl = null,
                    bio = "First programmer.",
                    customerStatus = CustomerStatus.FREE,
                    isCurrentUser = false,
                ),
                isLoading = false,
            ),
            onBack = {},
            onRetry = {},
        )
    }
}
