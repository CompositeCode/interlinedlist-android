package com.interlinedlist.android.feature.notifications.push

import com.interlinedlist.android.feature.notifications.domain.NotificationType

/**
 * A system-notification channel category. Each maps a coarse [NotificationType] to a
 * user-facing Android notification channel (registered on O+) and to the notification
 * -preference event keys that gate its `push` delivery.
 *
 * The channel ids are stable strings persisted by the OS, so they must not change once
 * shipped. [preferenceKeys] lists the candidate event keys the backend uses for this
 * category (`GET /api/user/notification-preferences`); the first one present in the
 * fetched preferences wins when deciding whether push is enabled.
 */
enum class NotificationCategory(
    val channelId: String,
    val channelName: String,
    val channelDescription: String,
    val preferenceKeys: List<String>,
) {
    MESSAGES(
        channelId = "il_messages",
        channelName = "Messages",
        channelDescription = "New messages and direct messages",
        preferenceKeys = listOf("message", "messages", "dm", "direct_message"),
    ),
    FOLLOWS(
        channelId = "il_follows",
        channelName = "Follows",
        channelDescription = "New followers and follow requests",
        preferenceKeys = listOf("follow", "follower", "new_follower", "follow_request"),
    ),
    DIGS(
        channelId = "il_digs",
        channelName = "Digs",
        channelDescription = "When someone digs your content",
        preferenceKeys = listOf("dig", "like", "favorite"),
    ),
    MENTIONS(
        channelId = "il_mentions",
        channelName = "Mentions",
        channelDescription = "When someone mentions you",
        preferenceKeys = listOf("mention", "tag"),
    ),
    REPLIES(
        channelId = "il_replies",
        channelName = "Replies",
        channelDescription = "Replies and comments on your content",
        preferenceKeys = listOf("reply", "comment"),
    ),
    OTHER(
        channelId = "il_other",
        channelName = "Other",
        channelDescription = "Shared lists, system and account notifications",
        preferenceKeys = listOf("list", "list_share", "system", "account"),
    );

    companion object {

        /** Maps a coarse [NotificationType] to the category whose channel it belongs to. */
        fun forType(type: NotificationType): NotificationCategory = when (type) {
            NotificationType.MESSAGE -> MESSAGES
            NotificationType.FOLLOW -> FOLLOWS
            NotificationType.LIKE -> DIGS
            NotificationType.MENTION -> MENTIONS
            NotificationType.REPLY -> REPLIES
            NotificationType.LIST_SHARE,
            NotificationType.SYSTEM,
            NotificationType.OTHER,
            -> OTHER
        }
    }
}
