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

/**
 * Response from creating a message or posting a reply. Unlike [MessageResponse],
 * here `message` is a human-readable status string ("Message created
 * successfully") and the created message is under [data]:
 * `{ message: "…", data: { …message… }, crossPosts: [...] }`.
 */
@Serializable
data class CreateMessageResponse(
    val data: MessageDto,
)

/**
 * Request body for creating a message or posting a reply.
 *
 * [imageUrls] / [videoUrls] carry media previously uploaded via the upload
 * endpoints, and [scheduledAt] (ISO-8601) defers publishing to a future time.
 * Only non-null fields are serialised (the shared Json uses `explicitNulls =
 * false`), so a plain post still sends just `{ content }`.
 */
@Serializable
data class CreateMessageRequest(
    val content: String,
    val parentId: String? = null,
    val imageUrls: List<String>? = null,
    val videoUrls: List<String>? = null,
    val scheduledAt: String? = null,
)

/**
 * Response from the image/video upload endpoints. The API returns the hosted URL
 * of the stored media under one of a few common keys; all are optional so the
 * repository can pick whichever the server populated.
 */
@Serializable
data class MediaUploadResponse(
    val url: String? = null,
    val imageUrl: String? = null,
    val videoUrl: String? = null,
) {
    /** The hosted media URL, whichever field the server used. */
    val hostedUrl: String? get() = url ?: imageUrl ?: videoUrl
}

/**
 * Response from the scheduled-messages endpoint. May be a bare list or wrapped in
 * a `data` envelope depending on the server; the repository reads [messages].
 */
@Serializable
data class ScheduledMessagesResponse(
    val data: List<MessageDto> = emptyList(),
)

/** Request body for reporting a message: `{ reason, detail? }`. */
@Serializable
data class ReportRequest(
    val reason: String,
    val detail: String? = null,
)

/**
 * Request body for editing one of the caller's own messages via
 * `PATCH /api/messages/{id}`. Only `content` is sent; the shared Json uses
 * `explicitNulls = false`, so the body is a plain `{ "content": "…" }`.
 */
@Serializable
data class EditMessageRequest(
    val content: String,
)

/**
 * Request body for reporting a *user* via `POST /api/users/{username}/report`.
 * Mirrors the message [ReportRequest] shape: `{ reason, detail? }`.
 */
@Serializable
data class UserReportRequest(
    val reason: String,
    val detail: String? = null,
)

/**
 * Response from the metadata endpoint. The updated message (with its populated
 * `linkMetadata`) is returned either at the top level or under `message`.
 */
@Serializable
data class MetadataResponse(
    val message: MessageDto? = null,
    val id: String? = null,
    val linkMetadata: LinkMetadataDto? = null,
)
