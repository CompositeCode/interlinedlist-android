package com.interlinedlist.android.core.appsettings.ui

import androidx.lifecycle.ViewModel
import com.interlinedlist.android.core.appsettings.AppDeviceRegistrationManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * Thin Compose-facing adapter over the app-scoped [AppDeviceRegistrationManager], so
 * the signed-in shell can drive the registration lifecycle without holding DI
 * plumbing. Mirrors `PushRegistrationViewModel`; the manager is a singleton, so every
 * instance talks to the same state.
 */
@HiltViewModel
class AppDeviceRegistrationViewModel @Inject constructor(
    private val registrationManager: AppDeviceRegistrationManager,
) : ViewModel() {

    /**
     * Registers this device with the account (and seeds a brand-new install). Never
     * throws and never blocks anything the user is waiting on.
     */
    suspend fun registerForSession() = registrationManager.registerForSession()
}
