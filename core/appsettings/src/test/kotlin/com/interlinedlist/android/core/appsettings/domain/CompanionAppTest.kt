package com.interlinedlist.android.core.appsettings.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The `appKey` is this app's permanent identity in the account-level registry.
 * Changing it would orphan every device registration and settings document already
 * stored under the old key, so it is pinned here as well as in [CompanionApp]'s KDoc.
 */
class CompanionAppTest {

    @Test
    fun `the appKey is interlinedlist-android and must never change`() {
        assertThat(CompanionApp.APP_KEY).isEqualTo("interlinedlist-android")
    }

    @Test
    fun `the appKey matches the format the server validates`() {
        // ^[a-z0-9][a-z0-9-]{0,63}$ — see /help/api/app-settings.
        assertThat(CompanionApp.APP_KEY).matches("[a-z0-9][a-z0-9-]{0,63}")
    }

    @Test
    fun `the platform is one the registry accepts`() {
        // macos | ios | android | windows | linux | web | other
        assertThat(CompanionApp.PLATFORM).isEqualTo("android")
    }
}
