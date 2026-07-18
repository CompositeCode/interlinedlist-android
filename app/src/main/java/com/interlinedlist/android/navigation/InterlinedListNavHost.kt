package com.interlinedlist.android.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Forum
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
import com.interlinedlist.android.feature.auth.ui.LoginRoute
import com.interlinedlist.android.feature.documents.ui.browser.DocumentsFolderRoute
import com.interlinedlist.android.feature.documents.ui.browser.DocumentsRoute
import com.interlinedlist.android.feature.documents.ui.editor.DocumentEditorRoute
import com.interlinedlist.android.feature.lists.ui.connections.ConnectionsRoute
import com.interlinedlist.android.feature.lists.ui.detail.ListDetailRoute
import com.interlinedlist.android.feature.lists.ui.list.ListsRoute
import com.interlinedlist.android.feature.lists.ui.schema.SchemaEditorRoute
import com.interlinedlist.android.feature.lists.ui.watchers.WatchersRoute
import com.interlinedlist.android.feature.messages.ui.detail.MessageDetailRoute
import com.interlinedlist.android.feature.messages.ui.feed.MessagesRoute
import com.interlinedlist.android.feature.messages.ui.scheduled.ScheduledMessagesRoute
import com.interlinedlist.android.feature.profile.ui.edit.EditProfileRoute
import com.interlinedlist.android.feature.profile.ui.profile.ProfileRoute
import com.interlinedlist.android.feature.profile.ui.profile.UserProfileRoute
import com.interlinedlist.android.feature.profile.ui.search.UserSearchRoute
import com.interlinedlist.android.ui.home.HomeViewModel

/** Navigation route keys. */
object Routes {
    const val LOGIN = "login"
    const val MAIN = "main"

    // Top-level tabs (bottom navigation).
    const val LISTS = "lists"
    const val MESSAGES = "messages"
    const val DOCUMENTS = "documents"
    const val ACCOUNT = "account"

    // Lists destinations.
    const val LIST_DETAIL = "lists/{listId}"
    const val LIST_SCHEMA = "lists/{listId}/schema"
    const val LIST_WATCHERS = "lists/{listId}/watchers"
    const val LIST_CONNECTIONS = "lists/connections"

    // Messages destinations.
    const val MESSAGE_DETAIL = "messageDetail/{messageId}"
    const val MESSAGES_SCHEDULED = "messages/scheduled"

    // Documents destinations.
    const val DOCUMENT_FOLDER = "documents/folder/{folderId}"
    const val DOCUMENT_EDITOR = "documents/editor/{documentId}"

    // Profile destinations. Distinct prefixes so a username can never collide
    // with the edit/search routes.
    const val PROFILE_EDIT = "editProfile"
    const val USER_SEARCH = "userSearch"
    const val USER_PROFILE = "user/{username}"

    fun listDetail(id: String) = "lists/$id"
    fun listSchema(id: String) = "lists/$id/schema"
    fun listWatchers(id: String) = "lists/$id/watchers"
    fun messageDetail(id: String) = "messageDetail/$id"
    fun documentFolder(id: String) = "documents/folder/$id"
    fun documentEditor(id: String) = "documents/editor/$id"
    fun userProfile(username: String) = "user/$username"
}

/**
 * The four post-login home tabs shown in the bottom navigation bar. Order
 * mirrors the web app: Messages, Lists, Documents (then Account).
 */
private enum class HomeTab(val route: String, val label: String, val icon: ImageVector) {
    Messages(Routes.MESSAGES, "Messages", Icons.Filled.Forum),
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
        startDestination = if (startLoggedIn) Routes.MAIN else Routes.LOGIN,
    ) {
        composable(Routes.LOGIN) {
            LoginRoute(
                onLoggedIn = {
                    navController.navigate(Routes.MAIN) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                },
            )
        }
        composable(Routes.MAIN) {
            MainShell(
                onLoggedOut = {
                    navController.navigate(Routes.LOGIN) {
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
            // ---- Lists ----
            composable(Routes.LISTS) {
                ListsRoute(
                    onOpenList = { id -> tabNav.navigate(Routes.listDetail(id)) },
                    onOpenConnections = { tabNav.navigate(Routes.LIST_CONNECTIONS) },
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
            ) {
                DocumentEditorRoute(
                    onBack = { tabNav.popBackStack() },
                    onDeleted = { tabNav.popBackStack() },
                )
            }

            // ---- Account / Profile ----
            composable(Routes.ACCOUNT) {
                // Sign-out reuses the existing auth-backed logout; the profile
                // module intentionally owns no session state.
                val logoutViewModel: HomeViewModel = hiltViewModel()
                ProfileRoute(
                    onEditProfile = { tabNav.navigate(Routes.PROFILE_EDIT) },
                    onSearchUsers = { tabNav.navigate(Routes.USER_SEARCH) },
                    onSignOut = { logoutViewModel.logout(onLoggedOut) },
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
                UserProfileRoute(onBack = { tabNav.popBackStack() })
            }
        }
    }
}
