package com.interlinedlist.android.feature.notifications.push

import com.google.common.truth.Truth.assertThat
import com.interlinedlist.android.feature.notifications.domain.NotificationChannel
import org.junit.Test

/** The rule behind "ask at a moment that earns the grant, not on cold start". */
class NotificationPermissionPromptTest {

    private val tiramisu = 33
    private val preTiramisu = 32

    @Test
    fun `asks when the user switches push on`() {
        assertThat(
            shouldRequestPostNotifications(
                channel = NotificationChannel.PUSH,
                enabled = true,
                alreadyGranted = false,
                sdkInt = tiramisu,
            ),
        ).isTrue()
    }

    @Test
    fun `does not ask when the user switches push off`() {
        assertThat(
            shouldRequestPostNotifications(
                channel = NotificationChannel.PUSH,
                enabled = false,
                alreadyGranted = false,
                sdkInt = tiramisu,
            ),
        ).isFalse()
    }

    @Test
    fun `does not ask for the in-app or email channels`() {
        listOf(NotificationChannel.IN_APP, NotificationChannel.EMAIL).forEach { channel ->
            assertThat(
                shouldRequestPostNotifications(
                    channel = channel,
                    enabled = true,
                    alreadyGranted = false,
                    sdkInt = tiramisu,
                ),
            ).isFalse()
        }
    }

    @Test
    fun `does not ask again once the permission is held`() {
        assertThat(
            shouldRequestPostNotifications(
                channel = NotificationChannel.PUSH,
                enabled = true,
                alreadyGranted = true,
                sdkInt = tiramisu,
            ),
        ).isFalse()
    }

    @Test
    fun `does not ask below Android 13 where the permission does not exist`() {
        assertThat(
            shouldRequestPostNotifications(
                channel = NotificationChannel.PUSH,
                enabled = true,
                alreadyGranted = false,
                sdkInt = preTiramisu,
            ),
        ).isFalse()
    }
}
