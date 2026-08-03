package com.interlinedlist.android.feature.notifications.push

import com.google.common.truth.Truth.assertThat
import com.interlinedlist.android.feature.notifications.domain.Notification
import com.interlinedlist.android.feature.notifications.domain.NotificationTarget
import com.interlinedlist.android.feature.notifications.domain.NotificationTargetKind
import com.interlinedlist.android.feature.notifications.domain.NotificationType
import org.junit.Test

class NotificationDeepLinkTest {

    private fun notif(target: NotificationTarget?) = Notification(
        id = "1",
        type = NotificationType.OTHER,
        actor = null,
        subject = "s",
        body = null,
        createdAt = null,
        read = false,
        target = target,
    )

    @Test
    fun `message target routes to MESSAGE with its id`() {
        val n = notif(NotificationTarget(NotificationTargetKind.MESSAGE, "m1"))

        assertThat(NotificationDeepLink.destinationFor(n))
            .isEqualTo(NotificationDeepLink.Destination.MESSAGE)
        assertThat(NotificationDeepLink.targetIdFor(n)).isEqualTo("m1")
    }

    @Test
    fun `user target routes to USER`() {
        val n = notif(NotificationTarget(NotificationTargetKind.USER, "amy"))

        assertThat(NotificationDeepLink.destinationFor(n))
            .isEqualTo(NotificationDeepLink.Destination.USER)
        assertThat(NotificationDeepLink.targetIdFor(n)).isEqualTo("amy")
    }

    @Test
    fun `list target routes to LIST`() {
        val n = notif(NotificationTarget(NotificationTargetKind.LIST, "l9"))

        assertThat(NotificationDeepLink.destinationFor(n))
            .isEqualTo(NotificationDeepLink.Destination.LIST)
        assertThat(NotificationDeepLink.targetIdFor(n)).isEqualTo("l9")
    }

    @Test
    fun `null target falls back to the notifications feed`() {
        val n = notif(null)

        assertThat(NotificationDeepLink.destinationFor(n))
            .isEqualTo(NotificationDeepLink.Destination.NOTIFICATIONS)
        assertThat(NotificationDeepLink.targetIdFor(n)).isNull()
    }

    @Test
    fun `OTHER target kind falls back to the notifications feed`() {
        val n = notif(NotificationTarget(NotificationTargetKind.OTHER, "x"))

        assertThat(NotificationDeepLink.destinationFor(n))
            .isEqualTo(NotificationDeepLink.Destination.NOTIFICATIONS)
        assertThat(NotificationDeepLink.targetIdFor(n)).isNull()
    }

    @Test
    fun `blank target id falls back to the notifications feed`() {
        val n = notif(NotificationTarget(NotificationTargetKind.MESSAGE, "   "))

        assertThat(NotificationDeepLink.destinationFor(n))
            .isEqualTo(NotificationDeepLink.Destination.NOTIFICATIONS)
    }
}
