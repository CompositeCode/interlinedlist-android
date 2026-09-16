package com.interlinedlist.android.core.appsettings

import com.interlinedlist.android.core.common.session.SessionTeardownTask
import javax.inject.Inject

/**
 * Hooks the device deregistration into `:feature:auth`'s existing sign-out path rather
 * than adding a parallel one. `DefaultAuthRepository.logout()` runs every contributed
 * [SessionTeardownTask] while the bearer token is still persisted, and BOTH user-facing
 * exits — the Account hub's "Sign out" and account deletion — go through that single
 * method, so there is no route out of a session that skips this. It is the same
 * mechanism the push-token unregister uses (#46).
 *
 * Why it matters: a device left registered keeps appearing under Settings →
 * Applications for an account that is no longer signed in here, and (once #78 lands)
 * keeps a settings document for a phone that will never write to it again.
 */
class AppDeviceSessionTeardown @Inject constructor(
    private val registrationManager: AppDeviceRegistrationManager,
) : SessionTeardownTask {

    override suspend fun onSessionEnding() = registrationManager.deregisterOnSessionEnding()
}
