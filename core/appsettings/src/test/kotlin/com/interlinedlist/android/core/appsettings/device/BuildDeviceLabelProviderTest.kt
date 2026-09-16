package com.interlinedlist.android.core.appsettings.device

import com.google.common.truth.Truth.assertThat
import com.interlinedlist.android.core.common.device.DeviceLabelProvider
import org.junit.Test

/**
 * The single device label. Its format is deliberately unchanged from the string
 * `DefaultAuthRepository` used to build inline, so a phone that was already listed
 * under Settings → Sessions keeps the same name there and now matches the name it
 * reports to Settings → Applications.
 */
class BuildDeviceLabelProviderTest {

    @Test
    fun `the label names the app and the model`() {
        assertThat(BuildDeviceLabelProvider.labelFor("Pixel 8"))
            .isEqualTo("InterlinedList Android · Pixel 8")
    }

    @Test
    fun `a missing model degrades to the app name`() {
        assertThat(BuildDeviceLabelProvider.labelFor(null)).isEqualTo("InterlinedList Android")
        assertThat(BuildDeviceLabelProvider.labelFor("   ")).isEqualTo("InterlinedList Android")
    }

    @Test
    fun `the label stays inside the registry's 120-character limit`() {
        // deviceName is 1..120 characters; a longer one is a 400 that would leave the
        // device unregistered.
        val label = BuildDeviceLabelProvider.labelFor("M".repeat(300))

        assertThat(label.length).isEqualTo(DeviceLabelProvider.MAX_LENGTH)
    }
}
