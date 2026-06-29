package com.interlinedlist.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.interlinedlist.android.core.datastore.SessionStore
import com.interlinedlist.android.core.designsystem.theme.InterlinedListTheme
import com.interlinedlist.android.navigation.InterlinedListNavHost
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** Single activity hosting the Compose navigation graph. */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var sessionStore: SessionStore

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        // Resolve the entry screen before composing so there's no login flash.
        val startLoggedIn = sessionStore.isLoggedIn
        enableEdgeToEdge()
        setContent {
            InterlinedListTheme {
                InterlinedListNavHost(startLoggedIn = startLoggedIn)
            }
        }
    }
}
