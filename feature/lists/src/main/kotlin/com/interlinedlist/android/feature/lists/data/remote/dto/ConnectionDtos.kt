package com.interlinedlist.android.feature.lists.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * A connection (edge) between two lists. The endpoint may return the linked list
 * titles inline (`fromListTitle`/`toListTitle`) or omit them; the mapper falls
 * back to the ids so the row still renders.
 */
@Serializable
data class ConnectionDto(
    val id: String,
    val fromListId: String = "",
    val toListId: String = "",
    val label: String? = null,
    val fromListTitle: String? = null,
    val toListTitle: String? = null,
)

/** Envelope for `GET /api/lists/connections`; connections may be wrapped or bare. */
@Serializable
data class ConnectionsResponse(
    val data: List<ConnectionDto>? = null,
    val connections: List<ConnectionDto>? = null,
) {
    val items: List<ConnectionDto> get() = data ?: connections ?: emptyList()
}

/** Envelope for a single connection create; the connection may be wrapped or bare. */
@Serializable
data class ConnectionEnvelope(
    val connection: ConnectionDto? = null,
    val data: ConnectionDto? = null,
)

/** Body for `POST /api/lists/connections`. */
@Serializable
data class CreateConnectionRequest(
    val fromListId: String,
    val toListId: String,
    val label: String? = null,
)
