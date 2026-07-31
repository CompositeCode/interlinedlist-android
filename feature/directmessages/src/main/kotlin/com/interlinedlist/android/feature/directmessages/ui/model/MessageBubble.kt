package com.interlinedlist.android.feature.directmessages.ui.model

import com.interlinedlist.android.feature.directmessages.data.DirectMessage

/**
 * A single message prepared for rendering in a thread: whether it is the current
 * user's own message, whether it has been read, and whether it is still sending.
 */
data class MessageBubble(
    val id: String,
    val body: String,
    val imageUrls: List<String>,
    val isMine: Boolean,
    val isRead: Boolean,
    val isPending: Boolean,
    val createdAtMillis: Long,
)

/** Projects a domain [DirectMessage] into a [MessageBubble] for [currentUserId]. */
fun DirectMessage.toBubble(currentUserId: String?): MessageBubble = MessageBubble(
    id = id,
    body = body,
    imageUrls = imageUrls,
    isMine = currentUserId != null && senderId == currentUserId,
    isRead = readAt != null,
    isPending = pending,
    createdAtMillis = createdAtMillis,
)
