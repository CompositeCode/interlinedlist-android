package com.interlinedlist.android.feature.directmessages.data

import com.interlinedlist.android.feature.directmessages.data.local.ConversationEntity
import com.interlinedlist.android.feature.directmessages.data.local.DirectMessageEntity
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

internal fun ConversationEntity.toDomain(): Conversation = Conversation(
    username = username,
    displayName = displayName,
    avatarUrl = avatarUrl,
    lastMessageBody = lastMessageBody,
    lastMessageAtMillis = lastMessageAtMillis,
    hasUnread = hasUnread,
)

internal fun RecipientDto.toDomain(): Recipient = Recipient(
    id = id,
    username = username,
    displayName = displayName,
    avatarUrl = avatar,
)
