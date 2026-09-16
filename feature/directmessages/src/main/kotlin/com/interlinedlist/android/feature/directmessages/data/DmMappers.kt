package com.interlinedlist.android.feature.directmessages.data

import com.interlinedlist.android.feature.directmessages.data.local.ConversationEntity
import com.interlinedlist.android.feature.directmessages.data.local.DirectMessageEntity
import com.interlinedlist.android.feature.directmessages.data.remote.dto.ConversationDto
import com.interlinedlist.android.feature.directmessages.data.remote.dto.MessageDto
import com.interlinedlist.android.feature.directmessages.data.remote.dto.RecipientDto
import java.time.Instant
import java.time.format.DateTimeParseException

/** Parses an ISO-8601 timestamp into epoch millis, tolerating blanks/garbage. */
internal fun parseIsoMillis(value: String?): Long {
    if (value.isNullOrBlank()) return 0L
    return try {
        Instant.parse(value).toEpochMilli()
    } catch (_: DateTimeParseException) {
        0L
    }
}

/**
 * Maps a message DTO into an entity for a known conversation partner.
 * [conversationUsername] is the other participant's username.
 */
internal fun MessageDto.toEntity(conversationUsername: String): DirectMessageEntity =
    DirectMessageEntity(
        id = id,
        conversationUsername = conversationUsername,
        senderId = senderId,
        recipientId = recipientId,
        body = body,
        imageUrls = imageUrls,
        createdAt = createdAt,
        createdAtMillis = parseIsoMillis(createdAt),
        readAt = readAt,
        trashed = false,
        pending = false,
    )

internal fun DirectMessageEntity.toDomain(): DirectMessage = DirectMessage(
    id = id,
    conversationUsername = conversationUsername,
    senderId = senderId,
    recipientId = recipientId,
    body = body,
    imageUrls = imageUrls,
    createdAt = createdAt,
    createdAtMillis = createdAtMillis,
    readAt = readAt,
    pending = pending,
)

/**
 * Maps a conversation row into its cache entity, or null when the row carries no
 * participant username — without one the conversation cannot be keyed or opened.
 *
 * [currentUserId] is only consulted when the row omits unread bookkeeping and a
 * nested last message has to stand in for it.
 */
internal fun ConversationDto.toEntity(currentUserId: String?): ConversationEntity? {
    val other = participant ?: return null
    val otherUsername = other.username.takeIf { it.isNotBlank() } ?: return null
    return ConversationEntity(
        username = otherUsername,
        pairKey = pairKey,
        displayName = other.displayName,
        avatarUrl = other.avatar,
        lastMessageId = newestMessageId,
        lastMessageBody = previewText,
        lastMessageAtMillis = parseIsoMillis(lastActivityAt),
        unreadCount = resolveUnreadCount(currentUserId),
    )
}

/**
 * The row's unread count, preferring what the server reports and otherwise
 * inferring it from the newest message: unread only when the current user is the
 * one who received it.
 */
private fun ConversationDto.resolveUnreadCount(currentUserId: String?): Int = when {
    unreadCount != null -> unreadCount.coerceAtLeast(0)
    hasUnread != null -> if (hasUnread) 1 else 0
    else -> {
        val newest = newestMessage
        val received = currentUserId != null && newest?.recipientId == currentUserId
        if (received && (newest?.readAt ?: readAt) == null) 1 else 0
    }
}

internal fun ConversationEntity.toDomain(): Conversation = Conversation(
    username = username,
    displayName = displayName,
    avatarUrl = avatarUrl,
    lastMessageBody = lastMessageBody,
    lastMessageAtMillis = lastMessageAtMillis,
    unreadCount = unreadCount,
    pairKey = pairKey,
)

internal fun RecipientDto.toDomain(): Recipient = Recipient(
    id = id,
    username = username,
    displayName = displayName,
    avatarUrl = avatar,
)
