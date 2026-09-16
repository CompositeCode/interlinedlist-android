package com.interlinedlist.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.interlinedlist.android.core.datastore.SessionStore
import com.interlinedlist.android.core.datastore.ThemeMode
import com.interlinedlist.android.core.datastore.ThemeSettingsStore
import com.interlinedlist.android.core.designsystem.theme.InterlinedListTheme
import com.interlinedlist.android.feature.auth.nav.AuthRoutes
import com.interlinedlist.android.feature.messages.navigation.MessagesDestinations
import com.interlinedlist.android.navigation.InterlinedListNavHost
import com.interlinedlist.android.navigation.NotificationLaunch
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** Single activity hosting the Compose navigation graph. */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var sessionStore: SessionStore

    @Inject
    lateinit var themeSettingsStore: ThemeSettingsStore

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        // Resolve the entry screen before composing so there's no login flash.
        val startLoggedIn = sessionStore.isLoggedIn
        // A tapped system notification launches us with deep-link extras; resolve the
        // pending in-app route so the signed-in shell can navigate straight to it.
        val notificationRoute = NotificationLaunch.fromIntent(intent)?.route
        // A tapped email-change link (confirm or undo) launches us with the token in
        // the VIEW intent's data. Both endpoints behind it are unauthenticated, so the
        // route resolves regardless of whether a session exists.
        val emailChangeRoute = AuthRoutes.routeForEmailChangeLink(intent?.dataString)
        // A tapped tag link (`https://interlinedlist.com/?tag=…`, the URL the web's
        // own tag chips point at) resolves to the tag-filtered feed. Resolved here
        // rather than by implicit nav matching so the rule is unit-testable.
        val tagFeedRoute = MessagesDestinations.routeForTagLink(intent?.dataString)
        enableEdgeToEdge()
        setContent {
            val themeMode by themeSettingsStore.themeMode.collectAsStateWithLifecycle()
            val darkTheme = when (themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }
            InterlinedListTheme(darkTheme = darkTheme) {
                InterlinedListNavHost(
                    startLoggedIn = startLoggedIn,
                    notificationRoute = notificationRoute,
                    emailChangeRoute = emailChangeRoute,
                    tagFeedRoute = tagFeedRoute,
                )
            }
        }
    }
}
