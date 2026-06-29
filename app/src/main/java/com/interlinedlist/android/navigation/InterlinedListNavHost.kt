package com.interlinedlist.android.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.interlinedlist.android.feature.auth.ui.LoginRoute
import com.interlinedlist.android.ui.home.HomeScreen

/** Navigation route keys. Expanded as feature phases add destinations. */
object Routes {
    const val LOGIN = "login"
    const val HOME = "home"
}

/**
 * Top-level navigation host. Starts on the home screen when a session already
 * exists, otherwise on login. Successful login replaces login in the back stack.
 */
@Composable
fun InterlinedListNavHost(startLoggedIn: Boolean) {
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = if (startLoggedIn) Routes.HOME else Routes.LOGIN,
    ) {
        composable(Routes.LOGIN) {
            LoginRoute(
                onLoggedIn = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                },
            )
        }
        composable(Routes.HOME) {
            HomeScreen(
                onLoggedOut = {
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(Routes.HOME) { inclusive = true }
                    }
                },
            )
        }
    }
}
