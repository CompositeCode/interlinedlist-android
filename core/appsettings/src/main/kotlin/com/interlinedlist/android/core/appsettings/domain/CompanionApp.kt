package com.interlinedlist.android.core.appsettings.domain

/**
 * This app's identity in the account-level companion-app registry
 * (`/api/user/app-settings/{appKey}/…`, documented at `/help/api/app-settings`).
 *
 * ## The `appKey` is `interlinedlist-android`, and it must never change
 *
 * `appKey` is **free-form**: there is no registration step and no allow-list. The
 * server creates the namespace the first time it sees a key, seeding the shared app
 * catalog entry from the `appDisplayName` sent on that first device registration
 * ("Used only to seed the shared app catalog entry the first time this `appKey` is
 * seen; ignored afterward"). The web's own first consumer uses `visual-introspection`
 * for the Visual Introspection macOS app, so the convention is a human-readable slug
 * naming the application — not a vendor prefix or a UUID.
 *
 * Because the key *is* the namespace, changing it later would orphan every device
 * registration and every settings document already stored under the old one: users
 * would see a second, empty "InterlinedList Android" entry under Settings →
 * Applications and a fresh phone would seed from nothing. **Treat [APP_KEY] as
 * permanent.** It is also verified by a unit test against the server's documented
 * format (`^[a-z0-9][a-z0-9-]{0,63}$`).
 */
object CompanionApp {

    /**
     * The permanent account-level identity of the InterlinedList Android app.
     * Never change this value — see the class KDoc.
     */
    const val APP_KEY = "interlinedlist-android"

    /**
     * Seeds the shared app catalog entry the very first time [APP_KEY] is seen, which
     * is the name the web shows under Settings → Applications. Ignored on every later
     * registration.
     */
    const val APP_DISPLAY_NAME = "InterlinedList Android"

    /** The registry's platform vocabulary allows exactly one value for this client. */
    const val PLATFORM = "android"
}
