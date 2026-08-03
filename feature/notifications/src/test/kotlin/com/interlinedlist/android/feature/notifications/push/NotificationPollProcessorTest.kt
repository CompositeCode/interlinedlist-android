package com.interlinedlist.android.feature.notifications.push

import com.google.common.truth.Truth.assertThat
import com.interlinedlist.android.feature.notifications.domain.Notification
import com.interlinedlist.android.feature.notifications.domain.NotificationChannel
import com.interlinedlist.android.feature.notifications.domain.NotificationPreference
import com.interlinedlist.android.feature.notifications.domain.NotificationType
import org.junit.Test

class NotificationPollProcessorTest {

    private fun notif(id: String, type: NotificationType) = Notification(
        id = id,
        type = type,
        actor = null,
        subject = "s-$id",
        body = null,
        createdAt = null,
        read = false,
        target = null,
    )

    private fun pref(key: String, push: Boolean) = NotificationPreference(
        key = key,
        label = key,
        description = "",
        channels = mapOf(NotificationChannel.PUSH to push),
    )

    @Test
    fun `first poll posts nothing and seeds the marker`() {
        val outcome = NotificationPollProcessor.process(
            fetched = listOf(notif("3", NotificationType.FOLLOW), notif("2", NotificationType.FOLLOW)),
            preferences = emptyList(),
            lastSeenId = null,
            hasSeenAny = false,
        )

        assertThat(outcome.toPost).isEmpty()
        assertThat(outcome.newLastSeenId).isEqualTo("3")
    }

    @Test
    fun `only new items are posted and the marker advances`() {
        val outcome = NotificationPollProcessor.process(
            fetched = listOf(
                notif("5", NotificationType.FOLLOW),
                notif("4", NotificationType.MENTION),
                notif("3", NotificationType.FOLLOW),
            ),
            preferences = emptyList(),
            lastSeenId = "3",
            hasSeenAny = true,
        )

        assertThat(outcome.toPost.map { it.id }).containsExactly("5", "4").inOrder()
        assertThat(outcome.newLastSeenId).isEqualTo("5")
    }

    @Test
    fun `push-disabled events are filtered out of the post list`() {
        val outcome = NotificationPollProcessor.process(
            fetched = listOf(
                notif("5", NotificationType.FOLLOW), // follow push disabled -> dropped
                notif("4", NotificationType.MENTION), // no pref -> default notify
            ),
            preferences = listOf(pref("follow", push = false)),
            lastSeenId = "3",
            hasSeenAny = true,
        )

        assertThat(outcome.toPost.map { it.id }).containsExactly("4")
        // Marker still advances to the true newest, even though "5" wasn't posted.
        assertThat(outcome.newLastSeenId).isEqualTo("5")
    }

    @Test
    fun `no new items is a no-op post but keeps the marker current`() {
        val outcome = NotificationPollProcessor.process(
            fetched = listOf(notif("9", NotificationType.FOLLOW)),
            preferences = emptyList(),
            lastSeenId = "9",
            hasSeenAny = true,
        )

        assertThat(outcome.toPost).isEmpty()
        assertThat(outcome.newLastSeenId).isEqualTo("9")
    }

    @Test
    fun `empty fetch posts nothing`() {
        val outcome = NotificationPollProcessor.process(
            fetched = emptyList(),
            preferences = emptyList(),
            lastSeenId = "4",
            hasSeenAny = true,
        )

        assertThat(outcome.toPost).isEmpty()
        assertThat(outcome.newLastSeenId).isEqualTo("4")
    }
}
