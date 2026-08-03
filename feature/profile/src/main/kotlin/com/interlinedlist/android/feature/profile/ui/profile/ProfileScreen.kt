package com.interlinedlist.android.feature.profile.ui.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.interlinedlist.android.core.designsystem.theme.InterlinedListTheme
import com.interlinedlist.android.core.model.CustomerStatus
import com.interlinedlist.android.feature.profile.domain.FollowCounts
import com.interlinedlist.android.feature.profile.domain.ProfileUser

/** Stable test tags for the Account hub's menu rows. */
object AccountMenuTestTags {
    const val FOLLOWERS = "accountMenuFollowers"
    const val FOLLOWING = "accountMenuFollowing"
    const val REQUESTS = "accountMenuRequests"
    const val NOTIFICATIONS = "accountMenuNotifications"
    const val ORGANIZATIONS = "accountMenuOrganizations"
    const val INTEGRATIONS = "accountMenuIntegrations"
    const val EDIT_PROFILE = "accountMenuEditProfile"
    const val SEARCH_USERS = "accountMenuSearchUsers"
    const val SESSIONS = "accountMenuSessions"
    const val CONNECTED_ACCOUNTS = "accountMenuConnectedAccounts"
    const val BLOCKED_MUTED = "accountMenuBlockedMuted"
    const val ACCOUNT_SETTINGS = "accountMenuAccountSettings"
}

/**
 * The app's "Account" tab: a hub built around the current user's profile header
 * (with tappable follower/following counts) and a menu that links out to the rest of
 * the app.
 *
 * The follows/edit/search callbacks navigate within this module; the notifications,
 * organizations, and integrations callbacks navigate to OTHER feature modules — this
 * module only exposes them, the app wires the destinations.
 *
 * @param onEditProfile navigate to the edit-profile route.
 * @param onSearchUsers navigate to the user-search route.
 * @param onOpenFollowers navigate to the current user's followers list.
 * @param onOpenFollowing navigate to the current user's following list.
 * @param onOpenRequests navigate to the pending follow-requests screen.
 * @param onOpenNotifications navigate to the notifications module.
 * @param onOpenOrganizations navigate to the organizations module.
 * @param onOpenIntegrations navigate to the integrations module.
 * @param onOpenSessions navigate to the Active Sessions screen (within this module).
 * @param onOpenConnectedAccounts navigate to the Connected Accounts screen (within this module).
 * @param onOpenBlockedMuted navigate to the "Blocked & muted" screen (within this module).
 * @param onOpenAccountSettings navigate to the Account settings screen (within this module).
 * @param onSignOut invoked after the caller performs sign-out; the profile module does
 *   not own session state, so the app wires this to the auth logout + navigation.
 */
@Composable
fun ProfileRoute(
    onEditProfile: () -> Unit,
    onSearchUsers: () -> Unit,
    onOpenFollowers: (String) -> Unit,
    onOpenFollowing: (String) -> Unit,
    onOpenRequests: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenOrganizations: () -> Unit,
    onOpenIntegrations: () -> Unit,
    onOpenSessions: () -> Unit,
    onOpenConnectedAccounts: () -> Unit,
    onOpenAccountSettings: () -> Unit,
    onSignOut: () -> Unit,
    // Defaulted so existing app nav wiring compiles unchanged; wire this to the
    // `account/blocked-muted` route to enable the Blocked & muted screen (Milestone D).
    onOpenBlockedMuted: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // The hub's own follower/following lists are keyed on the signed-in user's
    // username, which the loaded profile carries; ignore taps until it's loaded.
    ProfileScreen(
        state = state,
        onEditProfile = onEditProfile,
        onSearchUsers = onSearchUsers,
        onOpenFollowers = { state.user?.username?.let(onOpenFollowers) },
        onOpenFollowing = { state.user?.username?.let(onOpenFollowing) },
        onOpenRequests = onOpenRequests,
        onOpenNotifications = onOpenNotifications,
        onOpenOrganizations = onOpenOrganizations,
        onOpenIntegrations = onOpenIntegrations,
        onOpenSessions = onOpenSessions,
        onOpenConnectedAccounts = onOpenConnectedAccounts,
        onOpenBlockedMuted = onOpenBlockedMuted,
        onOpenAccountSettings = onOpenAccountSettings,
        onSignOut = onSignOut,
        onRetry = viewModel::refresh,
        modifier = modifier,
    )
}

/** Stateless "Account" hub UI — easy to preview and to drive from Compose tests. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    state: ProfileUiState,
    onEditProfile: () -> Unit,
    onSearchUsers: () -> Unit,
    onOpenFollowers: () -> Unit,
    onOpenFollowing: () -> Unit,
    onOpenRequests: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenOrganizations: () -> Unit,
    onOpenIntegrations: () -> Unit,
    onOpenSessions: () -> Unit,
    onOpenConnectedAccounts: () -> Unit,
    onOpenAccountSettings: () -> Unit,
    onSignOut: () -> Unit,
    onRetry: () -> Unit,
    onOpenBlockedMuted: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { TopAppBar(title = { Text("Account") }) },
    ) { padding ->
        when {
            state.user != null -> Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState()),
            ) {
                // Profile header with tappable follower/following counts.
                ProfileContent(
                    user = state.user,
                    counts = state.followCounts,
                    onOpenFollowers = onOpenFollowers,
                    onOpenFollowing = onOpenFollowing,
                    // followStatus defaults to SELF here, so no follow button renders.
                )

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

                HorizontalDivider()

                AccountMenuRow(
                    icon = Icons.Default.Group,
                    label = "Followers",
                    onClick = onOpenFollowers,
                    tag = AccountMenuTestTags.FOLLOWERS,
                )
                AccountMenuRow(
                    icon = Icons.Default.Group,
                    label = "Following",
                    onClick = onOpenFollowing,
                    tag = AccountMenuTestTags.FOLLOWING,
                )
                AccountMenuRow(
                    icon = Icons.Default.PersonAdd,
                    label = "Follow requests",
                    onClick = onOpenRequests,
                    tag = AccountMenuTestTags.REQUESTS,
                )
                AccountMenuRow(
                    icon = Icons.Default.Notifications,
                    label = "Notifications",
                    onClick = onOpenNotifications,
                    tag = AccountMenuTestTags.NOTIFICATIONS,
                )
                AccountMenuRow(
                    icon = Icons.Default.Business,
                    label = "Organizations",
                    onClick = onOpenOrganizations,
                    tag = AccountMenuTestTags.ORGANIZATIONS,
                )
                AccountMenuRow(
                    icon = Icons.Default.Extension,
                    label = "Integrations",
                    onClick = onOpenIntegrations,
                    tag = AccountMenuTestTags.INTEGRATIONS,
                )
                AccountMenuRow(
                    icon = Icons.Default.Devices,
                    label = "Active sessions",
                    onClick = onOpenSessions,
                    tag = AccountMenuTestTags.SESSIONS,
                )
                AccountMenuRow(
                    icon = Icons.Default.Link,
                    label = "Connected accounts",
                    onClick = onOpenConnectedAccounts,
                    tag = AccountMenuTestTags.CONNECTED_ACCOUNTS,
                )
                AccountMenuRow(
                    icon = Icons.Default.Block,
                    label = "Blocked & muted",
                    onClick = onOpenBlockedMuted,
                    tag = AccountMenuTestTags.BLOCKED_MUTED,
                )
                AccountMenuRow(
                    icon = Icons.Default.ManageAccounts,
                    label = "Account settings",
                    onClick = onOpenAccountSettings,
                    tag = AccountMenuTestTags.ACCOUNT_SETTINGS,
                )
                AccountMenuRow(
                    icon = Icons.Default.Edit,
                    label = "Edit profile",
                    onClick = onEditProfile,
                    tag = AccountMenuTestTags.EDIT_PROFILE,
                )
                AccountMenuRow(
                    icon = Icons.Default.Search,
                    label = "Search users",
                    onClick = onSearchUsers,
                    tag = AccountMenuTestTags.SEARCH_USERS,
                )
                AccountMenuRow(
                    icon = Icons.AutoMirrored.Filled.Logout,
                    label = "Sign out",
                    onClick = onSignOut,
                    tag = ProfileTestTags.SIGN_OUT,
                )

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
                    androidx.compose.material3.Button(onClick = onRetry) { Text("Retry") }
                }
            }
        }
    }
}

/** A single tappable row in the Account hub's menu. */
@Composable
private fun AccountMenuRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    tag: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag(tag)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(16.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Icon(
            Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
                followCounts = FollowCounts(followers = 128, following = 87),
            ),
            onEditProfile = {},
            onSearchUsers = {},
            onOpenFollowers = {},
            onOpenFollowing = {},
            onOpenRequests = {},
            onOpenNotifications = {},
            onOpenOrganizations = {},
            onOpenIntegrations = {},
            onOpenSessions = {},
            onOpenConnectedAccounts = {},
            onOpenAccountSettings = {},
            onSignOut = {},
            onRetry = {},
        )
    }
}
