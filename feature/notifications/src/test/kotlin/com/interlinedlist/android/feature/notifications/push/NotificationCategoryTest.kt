package com.interlinedlist.android.feature.notifications.push

import com.google.common.truth.Truth.assertThat
import com.interlinedlist.android.feature.notifications.domain.NotificationType
import org.junit.Test

class NotificationCategoryTest {

    @Test
    fun `every notification type maps to a category`() {
        // Exhaustive: no type should be unmapped.
        NotificationType.entries.forEach { type ->
            val category = NotificationCategory.forType(type)
            assertThat(category).isNotNull()
        }
    }

    @Test
    fun `core types map to their expected categories`() {
        assertThat(NotificationCategory.forType(NotificationType.MESSAGE))
            .isEqualTo(NotificationCategory.MESSAGES)
        assertThat(NotificationCategory.forType(NotificationType.FOLLOW))
            .isEqualTo(NotificationCategory.FOLLOWS)
        assertThat(NotificationCategory.forType(NotificationType.LIKE))
            .isEqualTo(NotificationCategory.DIGS)
        assertThat(NotificationCategory.forType(NotificationType.MENTION))
            .isEqualTo(NotificationCategory.MENTIONS)
        assertThat(NotificationCategory.forType(NotificationType.REPLY))
            .isEqualTo(NotificationCategory.REPLIES)
    }

    @Test
    fun `list share, system and unknown fall back to OTHER`() {
        assertThat(NotificationCategory.forType(NotificationType.LIST_SHARE))
            .isEqualTo(NotificationCategory.OTHER)
        assertThat(NotificationCategory.forType(NotificationType.SYSTEM))
            .isEqualTo(NotificationCategory.OTHER)
        assertThat(NotificationCategory.forType(NotificationType.OTHER))
            .isEqualTo(NotificationCategory.OTHER)
    }

    @Test
    fun `channel ids are unique and non-blank`() {
        val ids = NotificationCategory.entries.map { it.channelId }
        assertThat(ids).containsNoDuplicates()
        assertThat(ids.none { it.isBlank() }).isTrue()
    }
}
