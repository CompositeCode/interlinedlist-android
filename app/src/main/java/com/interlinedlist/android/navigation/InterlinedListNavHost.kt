package com.interlinedlist.android.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import androidx.navigation.navigation
import com.interlinedlist.android.feature.auth.nav.AuthRoutes
import com.interlinedlist.android.feature.auth.nav.authGraph
import com.interlinedlist.android.feature.directmessages.navigation.DirectMessagesDestinations
import com.interlinedlist.android.feature.directmessages.navigation.directMessagesGraph
import com.interlinedlist.android.feature.directmessages.navigation.navigateToDmThread
import com.interlinedlist.android.feature.directmessages.navigation.navigateToNewDm
import com.interlinedlist.android.feature.documents.ui.browser.DocumentsFolderRoute
import com.interlinedlist.android.feature.documents.ui.browser.DocumentsRoute
import com.interlinedlist.android.feature.documents.ui.editor.DocumentEditorRoute
import com.interlinedlist.android.feature.documents.ui.share.DocumentShareRoute
import com.interlinedlist.android.feature.documents.ui.share.SharedDocumentRoute
import com.interlinedlist.android.feature.integrations.ui.accounts.ConnectedAccountsRoute
import com.interlinedlist.android.feature.integrations.ui.export.ExportRoute
import com.interlinedlist.android.feature.integrations.ui.hub.IntegrationsRoute
import com.interlinedlist.android.feature.lists.ui.connections.ConnectionsRoute
import com.interlinedlist.android.feature.lists.ui.detail.ListDetailRoute
import com.interlinedlist.android.feature.lists.ui.folders.FolderBrowserRoute
import com.interlinedlist.android.feature.lists.ui.list.ListsRoute
import com.interlinedlist.android.feature.lists.ui.schema.SchemaEditorRoute
import com.interlinedlist.android.feature.lists.ui.share.ShareRoute
import com.interlinedlist.android.feature.lists.ui.share.SharedListRoute
import com.interlinedlist.android.feature.lists.ui.share.SharedWithMeRoute
import com.interlinedlist.android.feature.lists.ui.watchers.WatchersRoute
import com.interlinedlist.android.feature.messages.ui.detail.MessageDetailRoute
import com.interlinedlist.android.feature.messages.ui.feed.MessagesRoute
import com.interlinedlist.android.feature.messages.ui.scheduled.ScheduledMessagesRoute
import com.interlinedlist.android.feature.notifications.ui.NotificationPreferencesRoute
import com.interlinedlist.android.feature.notifications.ui.NotificationsRoute
import com.interlinedlist.android.feature.organizations.ui.detail.OrganizationDetailRoute
import com.interlinedlist.android.feature.organizations.ui.list.OrganizationsRoute
import com.interlinedlist.android.feature.profile.ui.account.AccountSettingsRoute
import com.interlinedlist.android.feature.profile.ui.account.ConnectedAccountsRoute as ProfileConnectedAccountsRoute
import com.interlinedlist.android.feature.profile.ui.account.SessionsRoute
import com.interlinedlist.android.feature.profile.ui.edit.EditProfileRoute
import com.interlinedlist.android.feature.profile.ui.follow.FollowRequestsRoute
import com.interlinedlist.android.feature.profile.ui.follow.FollowersRoute
import com.interlinedlist.android.feature.profile.ui.follow.FollowingRoute
import com.interlinedlist.android.feature.profile.ui.profile.ProfileRoute
import com.interlinedlist.android.feature.profile.ui.profile.PublicDocumentRoute
import com.interlinedlist.android.feature.profile.ui.profile.PublicListRoute
import com.interlinedlist.android.feature.profile.ui.profile.UserProfileRoute
import com.interlinedlist.android.feature.profile.ui.search.UserSearchRoute
import com.interlinedlist.android.ui.home.HomeViewModel

/** Navigation route keys. */
object Routes {
    const val MAIN = "main"

    // Top-level tabs (bottom navigation).
    const val MESSAGES = "messages"
    const val LISTS = "lists"
    const val DOCUMENTS = "documents"
    const val ACCOUNT = "account"

    // Lists destinations.
    const val LIST_DETAIL = "lists/{listId}"
    const val LIST_SCHEMA = "lists/{listId}/schema"
    const val LIST_WATCHERS = "lists/{listId}/watchers"
    const val LIST_CONNECTIONS = "lists/connections"

    // Lists sharing & folders (Milestones F / M).
    const val LIST_SHARE = "lists/{listId}/share"
    const val LISTS_SHARED_WITH_ME = "lists/shared-with-me"
    const val LIST_SHARED = "lists/shared/{token}"
    const val LIST_FOLDERS = "lists/folders"

    // Messages destinations.
    const val MESSAGE_DETAIL = "messageDetail/{messageId}"
    const val MESSAGES_SCHEDULED = "messages/scheduled"

    // Documents destinations.
    const val DOCUMENT_FOLDER = "documents/folder/{folderId}"
    const val DOCUMENT_EDITOR = "documents/editor/{documentId}"

    // Documents sharing (Milestone F).
    const val DOCUMENT_SHARE = "documents/{documentId}/share"
    const val DOCUMENT_SHARED = "documents/shared/{token}"

    // Public read-only content (Milestone L).
    const val PUBLIC_LIST = "publicList/{username}/{listId}"
    const val PUBLIC_DOCUMENT = "publicDocument/{documentId}"

    // Profile / following destinations. Distinct prefixes so a username can
    // never collide with the edit/search/list routes.
    const val PROFILE_EDIT = "editProfile"
    const val USER_SEARCH = "userSearch"
    const val USER_PROFILE = "user/{username}"
    const val FOLLOWERS = "followers/{username}"
    const val FOLLOWING = "following/{username}"
    const val FOLLOW_REQUESTS = "followRequests"

    // Account & security (Milestone K), reached from the Account hub.
    const val ACCOUNT_SESSIONS = "account/sessions"
    const val ACCOUNT_CONNECTED = "account/connected-accounts"
    const val ACCOUNT_SETTINGS = "account/settings"

    // Notifications / organizations / integrations (reached from the Account hub).
    const val NOTIFICATIONS = "notifications"
    const val NOTIFICATION_PREFERENCES = "notifications/preferences"
    const val ORGANIZATIONS = "organizations"
    const val ORGANIZATION_DETAIL = "organizations/{orgId}"
    const val INTEGRATIONS = "integrations"
    const val INTEGRATIONS_EXPORT = "integrations/export"
    const val INTEGRATIONS_ACCOUNTS = "integrations/accounts"

    fun listDetail(id: String) = "lists/$id"
    fun listSchema(id: String) = "lists/$id/schema"
    fun listWatchers(id: String) = "lists/$id/watchers"
    fun listShare(id: String) = "lists/$id/share"
    fun listShared(token: String) = "lists/shared/$token"
    fun messageDetail(id: String) = "messageDetail/$id"
    fun documentFolder(id: String) = "documents/folder/$id"
    fun documentEditor(id: String) = "documents/editor/$id"
    fun documentShare(id: String) = "documents/$id/share"
    fun documentShared(token: String) = "documents/shared/$token"
    fun publicList(username: String, listId: String) = "publicList/$username/$listId"
    fun publicDocument(documentId: String) = "publicDocument/$documentId"
    fun userProfile(username: String) = "user/$username"
    fun followers(username: String) = "followers/$username"
    fun following(username: String) = "following/$username"
    fun organization(orgId: String) = "organizations/$orgId"
}

/**
 * The post-login home tabs shown in the bottom navigation bar. Order mirrors the
 * web app: Messages, DMs, Lists, Documents (then Account).
 */
private enum class HomeTab(val route: String, val label: String, val icon: ImageVector) {
    Messages(Routes.MESSAGES, "Messages", Icons.Filled.Forum),
    DirectMessages(DirectMessagesDestinations.INBOX, "DMs", Icons.Filled.MailOutline),
    Lists(Routes.LISTS, "Lists", Icons.AutoMirrored.Filled.List),
    Documents(Routes.DOCUMENTS, "Documents", Icons.Filled.Description),
    Account(Routes.ACCOUNT, "Account", Icons.Filled.AccountCircle),
}

/**
 * Top-level navigation host. Starts on the signed-in shell when a session
 * already exists, otherwise on login. Successful login replaces login in the
 * back stack; sign-out returns to login.
 */
@Composable
fun InterlinedListNavHost(startLoggedIn: Boolean) {
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = if (startLoggedIn) Routes.MAIN else AuthRoutes.GRAPH,
    ) {
        // Unauthenticated flow owned by the auth module: Login ⇄ Register ⇄ Forgot
        // → Reset + verify (incl. its own deep links). Successful auth replaces the
        // whole graph with the signed-in shell.
        navigation(route = AuthRoutes.GRAPH, startDestination = AuthRoutes.LOGIN) {
            authGraph(
                navController = navController,
                onAuthenticated = {
                    navController.navigate(Routes.MAIN) {
                        popUpTo(AuthRoutes.GRAPH) { inclusive = true }
                    }
                },
            )
        }
        composable(Routes.MAIN) {
            MainShell(
                onLoggedOut = {
                    navController.navigate(AuthRoutes.GRAPH) {
                        popUpTo(Routes.MAIN) { inclusive = true }
                    }
                },
            )
        }
    }
}

/**
 * Signed-in shell: a bottom navigation bar over the feature surfaces. The bar
 * is shown on the four tab roots and hidden on detail screens, which carry
 * their own back navigation.
 */
@Composable
private fun MainShell(onLoggedOut: () -> Unit) {
    val tabNav = rememberNavController()
    val backStackEntry by tabNav.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val onTabRoot = HomeTab.entries.any { it.route == currentRoute }

    Scaffold(
        bottomBar = {
            if (onTabRoot) {
                NavigationBar {
                    val hierarchy = backStackEntry?.destination?.hierarchy
                    HomeTab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = hierarchy?.any { it.route == tab.route } == true,
                            onClick = {
                                tabNav.navigate(tab.route) {
                                    popUpTo(tabNav.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = tabNav,
            startDestination = Routes.MESSAGES,
            modifier = Modifier.padding(padding),
        ) {
            // ---- Messages ----
            composable(Routes.MESSAGES) {
                MessagesRoute(
                    onOpenMessage = { id -> tabNav.navigate(Routes.messageDetail(id)) },
                    onOpenScheduled = { tabNav.navigate(Routes.MESSAGES_SCHEDULED) },
                )
            }
            composable(
                Routes.MESSAGE_DETAIL,
                arguments = listOf(navArgument("messageId") { type = NavType.StringType }),
            ) {
                MessageDetailRoute(
                    onBack = { tabNav.popBackStack() },
                    onOpenMessage = { id -> tabNav.navigate(Routes.messageDetail(id)) },
                )
            }
            composable(Routes.MESSAGES_SCHEDULED) {
                ScheduledMessagesRoute(onBack = { tabNav.popBackStack() })
            }

            // ---- Direct Messages ----
            directMessagesGraph(
                onBack = { tabNav.popBackStack() },
                onOpenThread = { username -> tabNav.navigateToDmThread(username) },
                onComposeNew = { tabNav.navigateToNewDm() },
            )

            // ---- Lists ----
            composable(Routes.LISTS) {
                ListsRoute(
                    onOpenList = { id -> tabNav.navigate(Routes.listDetail(id)) },
                    onOpenConnections = { tabNav.navigate(Routes.LIST_CONNECTIONS) },
                    onOpenSharedWithMe = { tabNav.navigate(Routes.LISTS_SHARED_WITH_ME) },
                )
            }
            composable(
                Routes.LIST_DETAIL,
                arguments = listOf(navArgument("listId") { type = NavType.StringType }),
            ) { entry ->
                val listId = entry.arguments?.getString("listId").orEmpty()
                ListDetailRoute(
                    onBack = { tabNav.popBackStack() },
                    onListDeleted = { tabNav.popBackStack() },
                    onEditSchema = { tabNav.navigate(Routes.listSchema(listId)) },
                    onOpenWatchers = { tabNav.navigate(Routes.listWatchers(listId)) },
                    onOpenShare = { tabNav.navigate(Routes.listShare(listId)) },
                )
            }
            composable(
                Routes.LIST_SCHEMA,
                arguments = listOf(navArgument("listId") { type = NavType.StringType }),
            ) {
                SchemaEditorRoute(
                    onBack = { tabNav.popBackStack() },
                    onSaved = { tabNav.popBackStack() },
                )
            }
            composable(
                Routes.LIST_WATCHERS,
                arguments = listOf(navArgument("listId") { type = NavType.StringType }),
            ) {
                WatchersRoute(onBack = { tabNav.popBackStack() })
            }
            composable(Routes.LIST_CONNECTIONS) {
                ConnectionsRoute(onBack = { tabNav.popBackStack() })
            }
            composable(
                Routes.LIST_SHARE,
                arguments = listOf(navArgument("listId") { type = NavType.StringType }),
            ) {
                ShareRoute(onDismiss = { tabNav.popBackStack() })
            }
            composable(Routes.LISTS_SHARED_WITH_ME) {
                SharedWithMeRoute(
                    onBack = { tabNav.popBackStack() },
                    onOpenList = { id -> tabNav.navigate(Routes.listDetail(id)) },
                )
            }
            composable(
                Routes.LIST_SHARED,
                arguments = listOf(navArgument("token") { type = NavType.StringType }),
                deepLinks = listOf(
                    navDeepLink { uriPattern = "https://interlinedlist.com/lists/shared/{token}" },
                    navDeepLink { uriPattern = "interlinedlist://lists/shared/{token}" },
                ),
            ) {
                SharedListRoute(onBack = { tabNav.popBackStack() })
            }
            composable(Routes.LIST_FOLDERS) {
                FolderBrowserRoute(onBack = { tabNav.popBackStack() })
            }

            // ---- Documents ----
            composable(Routes.DOCUMENTS) {
                DocumentsRoute(
                    onOpenFolder = { id -> tabNav.navigate(Routes.documentFolder(id)) },
                    onOpenDocument = { id -> tabNav.navigate(Routes.documentEditor(id)) },
                )
            }
            composable(
                Routes.DOCUMENT_FOLDER,
                arguments = listOf(navArgument("folderId") { type = NavType.StringType }),
            ) {
                DocumentsFolderRoute(
                    onOpenFolder = { id -> tabNav.navigate(Routes.documentFolder(id)) },
                    onOpenDocument = { id -> tabNav.navigate(Routes.documentEditor(id)) },
                    onBack = { tabNav.popBackStack() },
                )
            }
            composable(
                Routes.DOCUMENT_EDITOR,
                arguments = listOf(navArgument("documentId") { type = NavType.StringType }),
            ) { entry ->
                val documentId = entry.arguments?.getString("documentId").orEmpty()
                DocumentEditorRoute(
                    onBack = { tabNav.popBackStack() },
                    onDeleted = { tabNav.popBackStack() },
                    onOpenShare = { tabNav.navigate(Routes.documentShare(documentId)) },
                )
            }
            composable(
                Routes.DOCUMENT_SHARE,
                arguments = listOf(navArgument("documentId") { type = NavType.StringType }),
            ) {
                DocumentShareRoute(onDismiss = { tabNav.popBackStack() })
            }
            composable(
                Routes.DOCUMENT_SHARED,
                arguments = listOf(navArgument("token") { type = NavType.StringType }),
                deepLinks = listOf(
                    navDeepLink { uriPattern = "https://interlinedlist.com/documents/shared/{token}" },
                    navDeepLink { uriPattern = "interlinedlist://documents/shared/{token}" },
                ),
            ) {
                SharedDocumentRoute(onBack = { tabNav.popBackStack() })
            }

            // ---- Account / Profile hub ----
            composable(Routes.ACCOUNT) {
                // Sign-out reuses the existing auth-backed logout; the profile
                // module intentionally owns no session state.
                val logoutViewModel: HomeViewModel = hiltViewModel()
                ProfileRoute(
                    onEditProfile = { tabNav.navigate(Routes.PROFILE_EDIT) },
                    onSearchUsers = { tabNav.navigate(Routes.USER_SEARCH) },
                    onOpenFollowers = { username -> tabNav.navigate(Routes.followers(username)) },
                    onOpenFollowing = { username -> tabNav.navigate(Routes.following(username)) },
                    onOpenRequests = { tabNav.navigate(Routes.FOLLOW_REQUESTS) },
                    onOpenNotifications = { tabNav.navigate(Routes.NOTIFICATIONS) },
                    onOpenOrganizations = { tabNav.navigate(Routes.ORGANIZATIONS) },
                    onOpenIntegrations = { tabNav.navigate(Routes.INTEGRATIONS) },
                    onOpenSessions = { tabNav.navigate(Routes.ACCOUNT_SESSIONS) },
                    onOpenConnectedAccounts = { tabNav.navigate(Routes.ACCOUNT_CONNECTED) },
                    onOpenAccountSettings = { tabNav.navigate(Routes.ACCOUNT_SETTINGS) },
                    onSignOut = { logoutViewModel.logout(onLoggedOut) },
                )
            }
            composable(Routes.ACCOUNT_SESSIONS) {
                SessionsRoute(onBack = { tabNav.popBackStack() })
            }
            composable(Routes.ACCOUNT_CONNECTED) {
                ProfileConnectedAccountsRoute(onBack = { tabNav.popBackStack() })
            }
            composable(Routes.ACCOUNT_SETTINGS) {
                // Account deletion clears the session; reuse the same logout path as sign-out.
                val logoutViewModel: HomeViewModel = hiltViewModel()
                AccountSettingsRoute(
                    onBack = { tabNav.popBackStack() },
                    onSignedOut = { logoutViewModel.logout(onLoggedOut) },
                )
            }
            composable(Routes.PROFILE_EDIT) {
                EditProfileRoute(
                    onBack = { tabNav.popBackStack() },
                    onSaved = { tabNav.popBackStack() },
                )
            }
            composable(Routes.USER_SEARCH) {
                UserSearchRoute(
                    onOpenUser = { username -> tabNav.navigate(Routes.userProfile(username)) },
                    onBack = { tabNav.popBackStack() },
                )
            }
            composable(
                Routes.USER_PROFILE,
                arguments = listOf(navArgument("username") { type = NavType.StringType }),
            ) {
                UserProfileRoute(
                    onBack = { tabNav.popBackStack() },
                    onOpenFollowers = { username -> tabNav.navigate(Routes.followers(username)) },
                    onOpenFollowing = { username -> tabNav.navigate(Routes.following(username)) },
                    onOpenList = { username, listId -> tabNav.navigate(Routes.publicList(username, listId)) },
                    onOpenDocument = { documentId -> tabNav.navigate(Routes.publicDocument(documentId)) },
                )
            }
            composable(
                Routes.PUBLIC_LIST,
                arguments = listOf(
                    navArgument("username") { type = NavType.StringType },
                    navArgument("listId") { type = NavType.StringType },
                ),
            ) {
                PublicListRoute(onBack = { tabNav.popBackStack() })
            }
            composable(
                Routes.PUBLIC_DOCUMENT,
                arguments = listOf(navArgument("documentId") { type = NavType.StringType }),
            ) {
                PublicDocumentRoute(onBack = { tabNav.popBackStack() })
            }
            composable(
                Routes.FOLLOWERS,
                arguments = listOf(navArgument("username") { type = NavType.StringType }),
            ) {
                FollowersRoute(
                    onOpenUser = { username -> tabNav.navigate(Routes.userProfile(username)) },
                    onBack = { tabNav.popBackStack() },
                )
            }
            composable(
                Routes.FOLLOWING,
                arguments = listOf(navArgument("username") { type = NavType.StringType }),
            ) {
                FollowingRoute(
                    onOpenUser = { username -> tabNav.navigate(Routes.userProfile(username)) },
                    onBack = { tabNav.popBackStack() },
                )
            }
            composable(Routes.FOLLOW_REQUESTS) {
                FollowRequestsRoute(
                    onOpenUser = { username -> tabNav.navigate(Routes.userProfile(username)) },
                    onBack = { tabNav.popBackStack() },
                )
            }

            // ---- Notifications ----
            composable(Routes.NOTIFICATIONS) {
                NotificationsRoute(
                    onBack = { tabNav.popBackStack() },
                    onOpenPreferences = { tabNav.navigate(Routes.NOTIFICATION_PREFERENCES) },
                )
            }
            composable(Routes.NOTIFICATION_PREFERENCES) {
                NotificationPreferencesRoute(onBack = { tabNav.popBackStack() })
            }

            // ---- Organizations ----
            composable(Routes.ORGANIZATIONS) {
                OrganizationsRoute(
                    onOpenOrg = { id -> tabNav.navigate(Routes.organization(id)) },
                    onBack = { tabNav.popBackStack() },
                )
            }
            composable(
                Routes.ORGANIZATION_DETAIL,
                arguments = listOf(navArgument("orgId") { type = NavType.StringType }),
            ) {
                OrganizationDetailRoute(
                    onBack = { tabNav.popBackStack() },
                    onDeleted = { tabNav.popBackStack() },
                )
            }

            // ---- Integrations & exports ----
            composable(Routes.INTEGRATIONS) {
                IntegrationsRoute(
                    onBack = { tabNav.popBackStack() },
                    onOpenExport = { tabNav.navigate(Routes.INTEGRATIONS_EXPORT) },
                    onOpenConnectedAccounts = { tabNav.navigate(Routes.INTEGRATIONS_ACCOUNTS) },
                )
            }
            composable(Routes.INTEGRATIONS_EXPORT) {
                ExportRoute(onBack = { tabNav.popBackStack() })
            }
            composable(Routes.INTEGRATIONS_ACCOUNTS) {
                ConnectedAccountsRoute(onBack = { tabNav.popBackStack() })
            }
        }
    }
}
