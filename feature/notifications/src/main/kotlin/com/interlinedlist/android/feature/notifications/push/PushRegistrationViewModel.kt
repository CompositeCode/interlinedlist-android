package com.interlinedlist.android.feature.notifications.push

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Thin Compose-facing adapter over the app-scoped [PushRegistrationManager], so the
 * signed-in shell and the notification-preferences screen can drive the device-token
 * lifecycle without either of them holding Android/DI plumbing. The manager is a
 * singleton, so every instance of this view model talks to the same state.
 */
@HiltViewModel
class PushRegistrationViewModel @Inject constructor(
    private val registrationManager: PushRegistrationManager,
) : ViewModel() {

    /**
     * Runs the device-token lifecycle for as long as the caller's coroutine lives:
     * registers the current token now (app launch / just-signed-in) and on every
     * rotation. Suspends until cancelled.
     */
    suspend fun runForSession() = registrationManager.runForSession()

    /** Registers the device now that the user has just granted POST_NOTIFICATIONS. */
    fun onNotificationPermissionGranted() {
        viewModelScope.launch { registrationManager.onNotificationPermissionGranted() }
    }
}
