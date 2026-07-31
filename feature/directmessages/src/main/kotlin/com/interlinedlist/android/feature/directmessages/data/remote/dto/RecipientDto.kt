package com.interlinedlist.android.feature.directmessages.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * A person the current user can DM.
 *
 * Confirmed live shape from `GET /api/dm/recipients` and the `otherUser`/`user`
 * sub-objects on threads: `{ id, username, displayName, avatar }`.
 */
@Serializable
data class RecipientDto(
    val id: String = "",
    val username: String = "",
    val displayName: String? = null,
    val avatar: String? = null,
)

/** Response for `GET /api/dm/recipients`: `{ "recipients": [...] }`. */
@Serializable
data class RecipientsResponse(
    val recipients: List<RecipientDto> = emptyList(),
)
