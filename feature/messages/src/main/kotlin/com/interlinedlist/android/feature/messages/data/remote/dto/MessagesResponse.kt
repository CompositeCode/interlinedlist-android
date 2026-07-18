package com.interlinedlist.android.feature.messages.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * Paginated list envelope shared by the feed, replies, and search endpoints:
 * `{ data: [...], pagination: { total, limit, offset, hasMore } }`.
 */
@Serializable
data class MessagesResponse(
    val data: List<MessageDto> = emptyList(),
    val pagination: PaginationDto = PaginationDto(),
)

/** Pagination cursor returned alongside a list of messages. */
@Serializable
data class PaginationDto(
    val total: Int = 0,
    val limit: Int = DEFAULT_LIMIT,
    val offset: Int = 0,
    val hasMore: Boolean = false,
) {
    companion object {
        const val DEFAULT_LIMIT = 20
    }
}

/** Single-message envelope: `{ message: { ... } }`. */
@Serializable
data class MessageResponse(
    val message: MessageDto,
)

/** Request body for creating a message or posting a reply. */
@Serializable
data class CreateMessageRequest(
    val content: String,
    val parentId: String? = null,
)
