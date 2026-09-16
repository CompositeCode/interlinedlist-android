package com.interlinedlist.android.feature.auth.nav

import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import androidx.navigation.NavDeepLink
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import com.interlinedlist.android.feature.auth.ui.EMAIL_CHANGE_ACTION_ARG
import com.interlinedlist.android.feature.auth.ui.EmailChangeRoute
import com.interlinedlist.android.feature.auth.ui.ForgotPasswordRoute
import com.interlinedlist.android.feature.auth.ui.LoginRoute
import com.interlinedlist.android.feature.auth.ui.RegisterRoute
import com.interlinedlist.android.feature.auth.ui.ResetPasswordRoute
import com.interlinedlist.android.feature.auth.ui.VerifyEmailRoute

/**
 * Route keys for the module's own unauthenticated sub-graph. Kept here so the
 * app can nest this graph without knowing the individual screen wiring, and so
 * the reset/verify deep-link paths live next to their consuming destinations.
 */
object AuthRoutes {
    /** Nested-graph route the app points at as the "signed-out" destination. */
    const val GRAPH = "auth"

    const val LOGIN = "auth/login"
    const val REGISTER = "auth/register"
    const val FORGOT = "auth/forgot"

    /** Query-arg name carrying the emailed token on the reset/verify links. */
    const val TOKEN_ARG = "token"

    const val RESET = "auth/reset?$TOKEN_ARG={$TOKEN_ARG}"
    const val VERIFY = "auth/verify?$TOKEN_ARG={$TOKEN_ARG}"

    /**
     * Web deep links the app forwards into this graph. The app-level manifest
     * `<intent-filter>` maps `https://interlinedlist.com/reset-password` and
     * `/verify-email` onto these so `NavController.handleDeepLink` lands directly
     * on the matching screen with its `token` populated.
     *
     * Built lazily: `NavDeepLink` parses its pattern with `android.net.Uri`, and the
     * plain route helpers on this object are covered by JVM unit tests that must not
     * drag the Android framework in just by touching the object.
     */
    val RESET_DEEP_LINKS: List<NavDeepLink> by lazy {
        listOf(
            navDeepLink { uriPattern = "https://interlinedlist.com/reset-password?$TOKEN_ARG={$TOKEN_ARG}" },
            navDeepLink { uriPattern = "interlinedlist://reset-password?$TOKEN_ARG={$TOKEN_ARG}" },
        )
    }
    val VERIFY_DEEP_LINKS: List<NavDeepLink> by lazy {
        listOf(
            navDeepLink { uriPattern = "https://interlinedlist.com/verify-email?$TOKEN_ARG={$TOKEN_ARG}" },
            navDeepLink { uriPattern = "interlinedlist://verify-email?$TOKEN_ARG={$TOKEN_ARG}" },
        )
    }

    fun reset(token: String) = "auth/reset?$TOKEN_ARG=$token"
    fun verify(token: String) = "auth/verify?$TOKEN_ARG=$token"

    /**
     * Confirm-or-undo destination for the two email-change links. Both halves share
     * one screen and differ only by the `action` argument, so the emailed
     * `/verify-email-change` and `/undo-email-change` links map onto the same route.
     */
    const val EMAIL_CHANGE =
        "auth/email-change?$EMAIL_CHANGE_ACTION_ARG={$EMAIL_CHANGE_ACTION_ARG}&$TOKEN_ARG={$TOKEN_ARG}"

    fun emailChange(action: EmailChangeAction, token: String) =
        "auth/email-change?$EMAIL_CHANGE_ACTION_ARG=${action.name}&$TOKEN_ARG=$token"

    /**
     * Maps a tapped email-change link onto an in-app route, or returns null when the
     * URI is not one of those links (or carries no token).
     *
     * The app resolves the launch intent through here — the same way a tapped
     * notification goes through `NotificationLaunch` — rather than relying on implicit
     * `navDeepLink` matching, so this security-sensitive entry point is exercised by
     * plain unit tests. Both endpoints behind it are unauthenticated, so the route
     * resolves whether or not the app has a session.
     */
    fun routeForEmailChangeLink(uri: String?): String? =
        EmailChangeLink.parse(uri)?.let { emailChange(it.action, it.token) }
}

/**
 * Registers the unauthenticated sub-graph: Login ⇄ Register ⇄ Forgot → Reset,
 * plus a verify-email handler. All navigation stays inside the module; the only
 * hook the host provides is [onAuthenticated], invoked once a session exists
 * (identical to the existing login callback), so the app can swap to its
 * signed-in shell.
 */
fun NavGraphBuilder.authGraph(
    navController: NavController,
    onAuthenticated: () -> Unit,
) {
    composable(AuthRoutes.LOGIN) {
        LoginRoute(
            onLoggedIn = onAuthenticated,
            onRegister = { navController.navigate(AuthRoutes.REGISTER) },
            onForgotPassword = { navController.navigate(AuthRoutes.FORGOT) },
        )
    }

    composable(AuthRoutes.REGISTER) {
        RegisterRoute(
            onRegistered = onAuthenticated,
            onBackToLogin = { navController.popBackStack() },
        )
    }

    composable(AuthRoutes.FORGOT) {
        ForgotPasswordRoute(
            onBackToLogin = { navController.popBackStack() },
        )
    }

    composable(
        route = AuthRoutes.RESET,
        arguments = listOf(
            navArgument(AuthRoutes.TOKEN_ARG) {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            },
        ),
        deepLinks = AuthRoutes.RESET_DEEP_LINKS,
    ) {
        ResetPasswordRoute(
            onReset = { navController.popToLogin() },
            onBackToLogin = { navController.popToLogin() },
        )
    }

    composable(
        route = AuthRoutes.EMAIL_CHANGE,
        arguments = listOf(
            navArgument(EMAIL_CHANGE_ACTION_ARG) {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            },
            navArgument(AuthRoutes.TOKEN_ARG) {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            },
        ),
    ) {
        EmailChangeRoute(
            // Reached from an email while signed in *or* signed out: go back to
            // whatever was underneath, falling back to Login when nothing is.
            onDone = { if (!navController.popBackStack()) navController.popToLogin() },
        )
    }

    composable(
        route = AuthRoutes.VERIFY,
        arguments = listOf(
            navArgument(AuthRoutes.TOKEN_ARG) {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            },
        ),
        deepLinks = AuthRoutes.VERIFY_DEEP_LINKS,
    ) {
        VerifyEmailRoute(
            onDone = { navController.popToLogin() },
        )
    }
}

/** Returns to Login, clearing any Register/Forgot/Reset/Verify screens above it. */
private fun NavController.popToLogin() {
    navigate(AuthRoutes.LOGIN) {
        popUpTo(AuthRoutes.LOGIN) { inclusive = true }
        launchSingleTop = true
    }
}

/**
 * Standalone host for the whole unauthenticated flow. Handy for the auth
 * module's own instrumented tests, and usable by the app as the pre-login
 * screen. Deep links resolve through the nested [authGraph].
 */
@Composable
fun AuthNavHost(
    onAuthenticated: () -> Unit,
    navController: NavHostController = rememberNavController(),
) {
    NavHost(
        navController = navController,
        startDestination = AuthRoutes.LOGIN,
    ) {
        authGraph(navController = navController, onAuthenticated = onAuthenticated)
    }
}
