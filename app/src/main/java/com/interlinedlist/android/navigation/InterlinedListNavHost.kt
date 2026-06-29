package com.interlinedlist.android.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.interlinedlist.android.ui.home.PlaceholderScreen

/** Navigation route keys. Expanded as feature phases add destinations. */
object Routes {
    const val HOME = "home"
}

/** Top-level navigation host for the app. */
@Composable
fun InterlinedListNavHost() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) { PlaceholderScreen() }
    }
}
