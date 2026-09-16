package com.interlinedlist.android.feature.notifications.push

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The seam between "this device's push token" and everything that manages its
 * lifecycle. Nothing outside an implementation of this interface knows which push
 * provider issued the token — [PushRegistrationManager] just sees an opaque string.
 *
 * The token is exposed as a [StateFlow] so a single collector covers all three cases
 * the server contract cares about: the current value replays on collection (re-register
 * on every app launch), the first non-null value arrives when the token first becomes
 * available, and later values arrive on rotation.
 */
interface PushTokenProvider {

    /** The current device token, or `null` while none is available. */
    val token: StateFlow<String?>
}

/**
 * The only implementation that can exist today: there is no Firebase project and no
 * `google-services.json` in this repo, so no FCM token can be obtained and the token
 * is permanently `null`. Everything downstream degrades cleanly — nothing registers,
 * and the WorkManager notification poll keeps delivering the tray notifications.
 *
 * TODO(#45/#47): once the repo owner lands the Firebase project and
 * `google-services.json` (#45), replace this binding in `PushModule` with an
 * FCM-backed provider (#47) that seeds `token` from `FirebaseMessaging.getToken()`
 * and pushes rotations in from `FirebaseMessagingService.onNewToken`. That is the
 * ONLY change the lifecycle needs — registration, launch re-registration, sign-out
 * unregistration and the permission prompt are all already wired against this seam.
 */
@Singleton
class UnavailablePushTokenProvider @Inject constructor() : PushTokenProvider {
    override val token: StateFlow<String?> = MutableStateFlow<String?>(null).asStateFlow()
}
