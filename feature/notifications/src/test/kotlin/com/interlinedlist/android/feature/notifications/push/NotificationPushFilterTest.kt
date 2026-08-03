package com.interlinedlist.android.feature.notifications.push

import com.google.common.truth.Truth.assertThat
import com.interlinedlist.android.feature.notifications.domain.Notification
import com.interlinedlist.android.feature.notifications.domain.NotificationChannel
import com.interlinedlist.android.feature.notifications.domain.NotificationPreference
import com.interlinedlist.android.feature.notifications.domain.NotificationType
import org.junit.Test

class NotificationPushFilterTest {

    private fun notif(id: String, type: NotificationType) = Notification(
        id = id,
        type = type,
        actor = null,
        subject = "s",
        body = null,
        createdAt = null,
        read = false,
        target = null,
    )

    private fun pref(key: String, push: Boolean) = NotificationPreference(
        key = key,
        label = key,
        description = "",
        channels = mapOf(
            NotificationChannel.PUSH to push,
            NotificationChannel.IN_APP to true,
        ),
    )

    @Test
    fun `push-disabled event is filtered out`() {
        val filter = NotificationPushFilter(listOf(pref("follow", push = false)))

        assertThat(filter.shouldNotify(notif("1", NotificationType.FOLLOW))).isFalse()
    }

    @Test
    fun `push-enabled event is kept`() {
        val filter = NotificationPushFilter(listOf(pref("follow", push = true)))

        assertThat(filter.shouldNotify(notif("1", NotificationType.FOLLOW))).isTrue()
    }

    @Test
    fun `key matching is case-insensitive`() {
        val filter = NotificationPushFilter(listOf(pref("FoLLoW", push = false)))

        assertThat(filter.shouldNotify(notif("1", NotificationType.FOLLOW))).isFalse()
    }

    @Test
    fun `unmapped event defaults to notify`() {
        // No preference for the DIG/like category at all.
        val filter = NotificationPushFilter(listOf(pref("follow", push = false)))

        assertThat(filter.shouldNotify(notif("1", NotificationType.LIKE))).isTrue()
    }

    @Test
    fun `empty preferences default to notify`() {
        val filter = NotificationPushFilter(emptyList())

        assertThat(filter.shouldNotify(notif("1", NotificationType.MENTION))).isTrue()
    }

    @Test
    fun `event without a push channel is treated as allowed`() {
        val emailOnly = NotificationPreference(
            key = "follow",
            label = "follow",
            description = "",
            channels = mapOf(NotificationChannel.EMAIL to true), // no PUSH modelled
        )
        val filter = NotificationPushFilter(listOf(emailOnly))

        assertThat(filter.shouldNotify(notif("1", NotificationType.FOLLOW))).isTrue()
    }

    @Test
    fun `filter keeps only push-enabled notifications`() {
        val filter = NotificationPushFilter(
            listOf(
                pref("follow", push = true),
                pref("dig", push = false),
            ),
        )
        val input = listOf(
            notif("1", NotificationType.FOLLOW), // kept
            notif("2", NotificationType.LIKE), // dropped (dig disabled)
            notif("3", NotificationType.MENTION), // kept (unmapped -> default notify)
        )

        assertThat(filter.filter(input).map { it.id }).containsExactly("1", "3").inOrder()
    }

    @Test
    fun `dig preference maps to the like notification type`() {
        val filter = NotificationPushFilter(listOf(pref("dig", push = false)))

        assertThat(filter.shouldNotify(notif("1", NotificationType.LIKE))).isFalse()
    }
}
