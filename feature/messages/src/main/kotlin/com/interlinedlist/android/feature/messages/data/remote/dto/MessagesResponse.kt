package com.interlinedlist.android.feature.messages.data.remote.dto

import com.interlinedlist.android.feature.messages.domain.CrossPostStatus
import kotlinx.serialization.Serializable

/**
 * Paginated list envelope shared by the feed, replies, and search endpoints.
 *
 * The feed is documented as `{ messages: [...], pagination: {...} }` while the
 * sibling list endpoints wrap their rows in `data`; both keys are accepted and
 * read through [rows].
 */
@Serializable
data class MessagesResponse(
    val data: List<MessageDto> = emptyList(),
    val messages: List<MessageDto> = emptyList(),
    val pagination: PaginationDto = PaginationDto(),
) {
    /** The message rows under whichever key the endpoint used. */
    val rows: List<MessageDto> get() = data.ifEmpty { messages }

    /**
     * The opaque cursor to request the next page with, or null at the end of the
     * list. Hand it straight back as `cursor`: never parse or modify it.
     */
    val nextCursor: String? get() = pagination.nextCursor?.takeIf { it.isNotBlank() }
}

/**
 * Pagination envelope returned alongside a list of messages.
 *
 * The feed uses keyset pagination: [nextCursor] is an **opaque** token that is
 * passed back verbatim as the next request's `cursor`, and `null` means the end
 * of the list. [total] and [offset] only appear on the legacy offset path and are
 * omitted once a cursor is in play.
 */
@Serializable
data class PaginationDto(
    val total: Int = 0,
    val limit: Int = DEFAULT_LIMIT,
    val offset: Int = 0,
    val hasMore: Boolean = false,
    val nextCursor: String? = null,
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
 *
 * [crossPosts] carries per-network delivery status when the post targeted linked
 * networks. Its shape is not modelled in the OpenAPI spec, so it is best-effort
 * and defaults to empty (the shared Json `coerceInputValues`, so an explicit
 * `null` also becomes the empty default).
 */
@Serializable
data class CreateMessageResponse(
    val data: MessageDto,
    val crossPosts: List<CrossPostStatusDto> = emptyList(),
)

/**
 * Per-network delivery status entry in a create response's `crossPosts` array.
 * Not schema-modelled, so every field is optional; the repository maps only
 * entries that name a [provider].
 */
@Serializable
data class CrossPostStatusDto(
    val provider: String? = null,
    val status: String? = null,
    val url: String? = null,
    val error: String? = null,
) {
    /** Maps into the domain, or null when the entry names no provider. */
    fun toDomainOrNull(): CrossPostStatus? {
        val networkProvider = provider?.takeIf { it.isNotBlank() } ?: return null
        return CrossPostStatus(
            provider = networkProvider,
            status = status?.takeIf { it.isNotBlank() } ?: if (error != null) "failed" else "pending",
            url = url,
            error = error,
        )
    }
}

/**
 * Request body for creating a message or posting a reply.
 *
 * [imageUrls] / [videoUrls] carry media previously uploaded via the upload
 * endpoints, and [scheduledAt] (ISO-8601) defers publishing to a future time.
 *
 * [publiclyVisible] decides whether the message lands on the public feed or stays
 * visible only to its author. The composer always sends an explicit value (seeded
 * from the account's `defaultPubliclyVisible` preference) so the server default
 * never silently decides; it stays nullable for the call sites that do not offer
 * the choice (e.g. replies).
 *
 * Cross-posting targets are encoded per the create schema: [mastodonProviderIds]
 * lists the selected mastodon identity ids (a user may link several instances),
 * while [crossPostToBluesky] / [crossPostToLinkedIn] / [crossPostToTwitter] are
 * single boolean flags for the one-account networks.
 *
 * [pushedMessageId] re-shares another message: with no [content] it is a plain
 * push (repost), and with content it is a quote. It is mutually exclusive with
 * [parentId] and [scheduledAt], and a push/quote is always public.
 *
 * Only non-null fields are serialised (the shared Json uses `explicitNulls =
 * false`), so a plain InterlinedList-only post sends just
 * `{ content, publiclyVisible }` and a bare push sends no `content` at all.
 */
@Serializable
data class CreateMessageRequest(
    /** Null only for a push with no comment — the one case the API allows it. */
    val content: String? = null,
    val parentId: String? = null,
    val pushedMessageId: String? = null,
    val publiclyVisible: Boolean? = null,
    val imageUrls: List<String>? = null,
    val videoUrls: List<String>? = null,
    val scheduledAt: String? = null,
    val mastodonProviderIds: List<String>? = null,
    val crossPostToBluesky: Boolean? = null,
    val crossPostToLinkedIn: Boolean? = null,
    val crossPostToTwitter: Boolean? = null,
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
