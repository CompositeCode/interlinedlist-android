package com.interlinedlist.android.feature.profile.ui.profile

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
 * The app's "Account" tab: the current signed-in user's profile with entries to
 * edit the profile, search users, and sign out.
 *
 * @param onEditProfile navigate to the edit-profile route.
 * @param onSearchUsers navigate to the user-search route.
 * @param onSignOut invoked after the caller performs sign-out (mirrors HomeScreen's
 *   `onLoggedOut`); the profile module does not own session state, so the app wires
 *   this to the auth logout + navigation.
 */
@Composable
fun ProfileRoute(
    onEditProfile: () -> Unit,
    onSearchUsers: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ProfileScreen(
        state = state,
        onEditProfile = onEditProfile,
        onSearchUsers = onSearchUsers,
        onSignOut = onSignOut,
        onRetry = viewModel::refresh,
        modifier = modifier,
    )
}

/** Stateless "Account" UI — easy to preview and to drive from Compose tests. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    state: ProfileUiState,
    onEditProfile: () -> Unit,
    onSearchUsers: () -> Unit,
    onSignOut: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Account") },
                actions = {
                    IconButton(onClick = onSearchUsers, modifier = Modifier.testTag(ProfileTestTags.SEARCH)) {
                        Icon(Icons.Default.Search, contentDescription = "Search users")
                    }
                    IconButton(onClick = onEditProfile, modifier = Modifier.testTag(ProfileTestTags.EDIT)) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit profile")
                    }
                },
            )
        },
    ) { padding ->
        when {
            state.user != null -> Column(
                Modifier.fillMaxSize().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                ProfileContent(user = state.user, modifier = Modifier.weight(1f, fill = false))

                if (state.errorMessage != null) {
                    Text(
                        text = state.errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp)
                            .testTag(ProfileTestTags.ERROR),
                    )
                }

                Spacer(Modifier.height(24.dp))
                OutlinedButton(
                    onClick = onSignOut,
                    modifier = Modifier
                        .padding(horizontal = 24.dp)
                        .fillMaxWidth()
                        .testTag(ProfileTestTags.SIGN_OUT),
                ) {
                    Text("Sign out")
                }
                Spacer(Modifier.height(24.dp))
            }

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
                        text = state.errorMessage ?: "Couldn't load your profile.",
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
private fun ProfileScreenPreview() {
    InterlinedListTheme {
        ProfileScreen(
            state = ProfileUiState(
                user = ProfileUser(
                    id = "1",
                    username = "adron",
                    displayName = "Adron Hall",
                    avatarUrl = null,
                    bio = "Building things at InterlinedList.",
                    customerStatus = CustomerStatus.SUBSCRIBER,
                    isCurrentUser = true,
                ),
                isLoading = false,
            ),
            onEditProfile = {},
            onSearchUsers = {},
            onSignOut = {},
            onRetry = {},
        )
    }
}
