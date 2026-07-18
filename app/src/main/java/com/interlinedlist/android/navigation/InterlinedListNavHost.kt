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
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.interlinedlist.android.feature.auth.ui.LoginRoute
import com.interlinedlist.android.feature.documents.ui.editor.DocumentEditorRoute
import com.interlinedlist.android.feature.documents.ui.index.DocumentsRoute
import com.interlinedlist.android.feature.lists.ui.detail.ListDetailRoute
import com.interlinedlist.android.feature.lists.ui.list.ListsRoute
import com.interlinedlist.android.feature.messages.ui.detail.MessageDetailRoute
import com.interlinedlist.android.feature.messages.ui.feed.MessagesRoute
import com.interlinedlist.android.ui.home.HomeScreen

/** Navigation route keys. */
object Routes {
    const val LOGIN = "login"
    const val MAIN = "main"

    // Top-level tabs (bottom navigation).
    const val LISTS = "lists"
    const val MESSAGES = "messages"
    const val DOCUMENTS = "documents"
    const val ACCOUNT = "account"

    // Detail destinations.
    const val LIST_DETAIL = "lists/{listId}"
    const val MESSAGE_DETAIL = "messageDetail/{messageId}"
    const val DOCUMENT_EDITOR = "documents/editor/{documentId}"

    fun listDetail(id: String) = "lists/$id"
    fun messageDetail(id: String) = "messageDetail/$id"
    fun documentEditor(id: String) = "documents/editor/$id"
}

/** The four post-login home tabs shown in the bottom navigation bar. */
private enum class HomeTab(val route: String, val label: String, val icon: ImageVector) {
    Lists(Routes.LISTS, "Lists", Icons.AutoMirrored.Filled.List),
    Messages(Routes.MESSAGES, "Messages", Icons.Filled.Forum),
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
                                    // Reselecting a tab returns to its root and keeps
                                    // per-tab state, mirroring standard bottom-nav UX.
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
            startDestination = Routes.LISTS,
            modifier = Modifier.padding(padding),
        ) {
            composable(Routes.LISTS) {
                ListsRoute(onOpenList = { id -> tabNav.navigate(Routes.listDetail(id)) })
            }
            composable(
                Routes.LIST_DETAIL,
                arguments = listOf(navArgument("listId") { type = NavType.StringType }),
            ) {
                ListDetailRoute(
                    onBack = { tabNav.popBackStack() },
                    onListDeleted = { tabNav.popBackStack() },
                )
            }

            composable(Routes.MESSAGES) {
                MessagesRoute(onOpenMessage = { id -> tabNav.navigate(Routes.messageDetail(id)) })
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

            composable(Routes.DOCUMENTS) {
                DocumentsRoute(
                    onOpenDocument = { id -> tabNav.navigate(Routes.documentEditor(id)) },
                    onSearch = { /* Dedicated search screen deferred; see roadmap. */ },
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

            composable(Routes.ACCOUNT) {
                HomeScreen(onLoggedOut = onLoggedOut)
            }
        }
    }
}
