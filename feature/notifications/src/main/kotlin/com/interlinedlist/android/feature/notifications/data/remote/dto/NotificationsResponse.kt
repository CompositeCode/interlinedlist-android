package com.interlinedlist.android.feature.notifications.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Paginated list envelope for `GET /api/notifications`. Mirrors the shape shared by
 * the other list endpoints — `{ data: [...], pagination: { total, limit, offset,
 * hasMore } }` — while tolerating a couple of alternate key names the notifications
 * endpoint might use (`notifications` instead of `data`, `unreadCount` alongside).
 *
 * Everything is defaulted so an empty or partially-populated body decodes cleanly.
 */
@Serializable
data class NotificationsResponse(
    /** Primary key the live API uses: `{ unreadCount, items: [...] }`. */
    @SerialName("items") private val itemsKey: List<NotificationDto> = emptyList(),
    val data: List<NotificationDto> = emptyList(),
    /** Alternate key some payloads use for the list. */
    val notifications: List<NotificationDto> = emptyList(),
    val pagination: PaginationDto = PaginationDto(),
    /** Server-provided unread count, when present; otherwise derived from [items]. */
    val unreadCount: Int? = null,
) {
    /** The notification list, whichever key the server populated (`items`, `data`, or `notifications`). */
    val items: List<NotificationDto> get() = itemsKey.ifEmpty { data.ifEmpty { notifications } }
}

/** Pagination cursor returned alongside a list of notifications. */
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
