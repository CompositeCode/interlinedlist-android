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
                )
            }
        }
    }
}
