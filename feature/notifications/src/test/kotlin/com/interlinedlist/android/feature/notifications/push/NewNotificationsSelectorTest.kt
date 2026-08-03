package com.interlinedlist.android.feature.notifications.push

import com.google.common.truth.Truth.assertThat
import com.interlinedlist.android.feature.notifications.domain.Notification
import com.interlinedlist.android.feature.notifications.domain.NotificationType
import org.junit.Test

class NewNotificationsSelectorTest {

    private fun notif(id: String) = Notification(
        id = id,
        type = NotificationType.FOLLOW,
        actor = null,
        subject = "s-$id",
        body = null,
        createdAt = null,
        read = false,
        target = null,
    )

    @Test
    fun `first ever poll surfaces nothing and adopts the newest id as baseline`() {
        val page = listOf(notif("3"), notif("2"), notif("1"))

        val result = NewNotificationsSelector.select(
            notifications = page,
            lastSeenId = null,
            hasSeenAny = false,
        )

        assertThat(result.newNotifications).isEmpty()
        assertThat(result.newLastSeenId).isEqualTo("3")
    }

    @Test
    fun `only items newer than the marker are new and marker advances`() {
        val page = listOf(notif("5"), notif("4"), notif("3"), notif("2"))

        val result = NewNotificationsSelector.select(
            notifications = page,
            lastSeenId = "3",
            hasSeenAny = true,
        )

        assertThat(result.newNotifications.map { it.id }).containsExactly("5", "4").inOrder()
        assertThat(result.newLastSeenId).isEqualTo("5")
    }

    @Test
    fun `no new items when the newest already equals the marker`() {
        val page = listOf(notif("9"), notif("8"))

        val result = NewNotificationsSelector.select(
            notifications = page,
            lastSeenId = "9",
            hasSeenAny = true,
        )

        assertThat(result.newNotifications).isEmpty()
        assertThat(result.newLastSeenId).isEqualTo("9")
    }

    @Test
    fun `marker missing from the page treats the whole page as new`() {
        val page = listOf(notif("7"), notif("6"))

        val result = NewNotificationsSelector.select(
            notifications = page,
            lastSeenId = "1", // fell off the page
            hasSeenAny = true,
        )

        assertThat(result.newNotifications.map { it.id }).containsExactly("7", "6").inOrder()
        assertThat(result.newLastSeenId).isEqualTo("7")
    }

    @Test
    fun `empty page is a no-op and keeps the existing marker`() {
        val result = NewNotificationsSelector.select(
            notifications = emptyList(),
            lastSeenId = "4",
            hasSeenAny = true,
        )

        assertThat(result.newNotifications).isEmpty()
        assertThat(result.newLastSeenId).isEqualTo("4")
    }
}
