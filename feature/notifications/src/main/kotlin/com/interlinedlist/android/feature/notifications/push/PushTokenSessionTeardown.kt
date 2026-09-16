package com.interlinedlist.android.feature.notifications.push

import com.interlinedlist.android.core.common.session.SessionTeardownTask
import javax.inject.Inject

/**
 * Hooks the device-token teardown into `:feature:auth`'s existing sign-out path rather
 * than adding a parallel one. `DefaultAuthRepository.logout()` runs every contributed
 * [SessionTeardownTask] while the bearer token is still persisted, and BOTH user-facing
 * exits — the Account hub's "Sign out" and account deletion — go through that single
 * method, so there is no route out of a session that skips this.
 *
 * Why it matters: a device token left registered keeps delivering the previous
 * account's notifications to a phone that someone else may now be signed in on.
 */
class PushTokenSessionTeardown @Inject constructor(
    private val registrationManager: PushRegistrationManager,
) : SessionTeardownTask {

    override suspend fun onSessionEnding() = registrationManager.unregisterCurrentToken()
}
