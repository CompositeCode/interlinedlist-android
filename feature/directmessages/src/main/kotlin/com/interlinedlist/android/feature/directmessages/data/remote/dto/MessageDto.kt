package com.interlinedlist.android.feature.directmessages.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * Wire model for a single direct message.
 *
 * Mirrors the `DirectMessage` component schema in the OpenAPI spec:
 * `{ id, pairKey, senderId, recipientId, body, imageUrls, createdAt, readAt,
 * senderDeletedAt, recipientDeletedAt }`. Timestamps are ISO-8601 strings.
 *
 * Envelope quirks honoured: the author sub-object appears as `sender`, `author`,
 * or `user` depending on the endpoint, and optional fields may arrive as
 * explicit `null` — every field therefore has a default. `imageUrls` is a raw
 * list because the schema leaves its item type unspecified.
 */
@Serializable
data class MessageDto(
    val id: String = "",
    val pairKey: String? = null,
    val senderId: String = "",
    val recipientId: String = "",
    val body: String = "",
    val imageUrls: List<String> = emptyList(),
    val createdAt: String = "",
    val readAt: String? = null,
    val senderDeletedAt: String? = null,
    val recipientDeletedAt: String? = null,
    // The author sometimes rides along embedded; accept any of the observed keys.
    val sender: RecipientDto? = null,
    val author: RecipientDto? = null,
    val user: RecipientDto? = null,
) {
    /** The embedded author under whichever key the endpoint used, if present. */
    val embeddedAuthor: RecipientDto? get() = sender ?: author ?: user
}
