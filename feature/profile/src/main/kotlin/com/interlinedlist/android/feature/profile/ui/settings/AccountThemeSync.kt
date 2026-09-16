package com.interlinedlist.android.feature.profile.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.interlinedlist.android.feature.profile.data.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Runs the account-preference sync for the signed-in shell.
 *
 * This is what makes "adopt the account value on first sign-in" actually happen:
 * without it the theme would only reconcile when the user happened to open Settings,
 * which is precisely the screen they would not open if the app already looked right
 * on the web.
 */
@HiltViewModel
class AccountThemeSyncViewModel @Inject constructor(
    private val repository: SettingsRepository,
) : ViewModel() {

    /**
     * Reads the account's preferences and reconciles the device's theme with them.
     *
     * The result is deliberately ignored: this runs behind whatever screen the user
     * landed on, so a failure must stay silent — the device keeps rendering its own
     * stored theme, and anything it still owes the account is pushed by the next sync.
     */
    fun sync() {
        viewModelScope.launch { repository.refresh() }
    }
}

/**
 * Syncs the account's theme into the app once, when the signed-in shell is entered.
 *
 * Placed at the shell rather than in the activity because the shell is entered in both
 * cases that matter — a cold start that already had a session, and the navigation that
 * follows a fresh sign-in — whereas the activity is only created in the first.
 */
@Composable
fun AccountThemeSyncEffect(viewModel: AccountThemeSyncViewModel = hiltViewModel()) {
    LaunchedEffect(Unit) { viewModel.sync() }
}
