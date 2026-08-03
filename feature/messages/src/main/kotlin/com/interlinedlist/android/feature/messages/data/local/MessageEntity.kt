package com.interlinedlist.android.feature.messages.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.interlinedlist.android.feature.messages.domain.LinkPreview
import com.interlinedlist.android.feature.messages.domain.Message

/**
 * Locally cached message row — this module's own offline-first source of truth
 * for the feed and detail screens. Kept flat (author fields inlined) so a single
 * table serves both list and detail reads without joins.
 */
@Entity(tableName = "message")
data class MessageEntity(
    @PrimaryKey val id: String,
    val content: String,
    val authorId: String,
    val authorUsername: String,
    val authorDisplayName: String?,
    val authorAvatarUrl: String?,
    val createdAt: String?,
    val digCount: Int,
    val replyCount: Int,
    val dugByMe: Boolean,
    val parentId: String?,
    val mine: Boolean,
    /** Server-relative ordering position captured at fetch time (feed order). */
    val feedOrder: Long,
    /** Attached image URLs, stored via [MessageConverters]. */
    val imageUrls: List<String> = emptyList(),
    /** Attached video URLs, stored via [MessageConverters]. */
    val videoUrls: List<String> = emptyList(),
    /** Link-preview card, stored via [MessageConverters]; null when none. */
    val linkPreview: LinkPreview? = null,
    /** Future send time for a scheduled message; null for a normal message. */
    val scheduledAt: String? = null,
    /** Last-edited instant; null when the message has not been edited. */
    val editedAt: String? = null,
)

fun MessageEntity.toDomain(): Message = Message(
    id = id,
    content = content,
    authorId = authorId,
    authorUsername = authorUsername,
    authorDisplayName = authorDisplayName,
    authorAvatarUrl = authorAvatarUrl,
    createdAt = createdAt,
    digCount = digCount,
    replyCount = replyCount,
    dugByMe = dugByMe,
    parentId = parentId,
    mine = mine,
    imageUrls = imageUrls,
    videoUrls = videoUrls,
    linkPreview = linkPreview,
    scheduledAt = scheduledAt,
    editedAt = editedAt,
)

fun Message.toEntity(feedOrder: Long): MessageEntity = MessageEntity(
    id = id,
    content = content,
    authorId = authorId,
    authorUsername = authorUsername,
    authorDisplayName = authorDisplayName,
    authorAvatarUrl = authorAvatarUrl,
    createdAt = createdAt,
    digCount = digCount,
    replyCount = replyCount,
    dugByMe = dugByMe,
    parentId = parentId,
    mine = mine,
    feedOrder = feedOrder,
    imageUrls = imageUrls,
    videoUrls = videoUrls,
    linkPreview = linkPreview,
    scheduledAt = scheduledAt,
    editedAt = editedAt,
)
