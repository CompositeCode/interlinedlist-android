package com.interlinedlist.android.feature.lists.data.remote.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Body for `POST /api/lists/{id}/data/versions`: the row versions we are holding
 * plus the row the user is on. Both are optional — the server accepts an empty
 * body — but without [rowVersions] it has nothing to compare against, so a poll
 * that sends none can only answer the presence half.
 */
@Serializable
data class RowVersionsRequest(
    val rowVersions: Map<String, Int> = emptyMap(),
    val focusedRowId: String? = null,
)

/**
 * Someone present in the list. Field names follow the documented
 * `{ userId, name, username, color, focusedRowId }`; `id`/`displayName` are
 * tolerated too, because `users` could not be observed live (it needs a second
 * person on the list) and the user objects the same endpoint *does* return —
 * `createdByUser` / `lastEditedByUser` — are keyed `{ id, username, displayName,
 * avatar }`.
 */
@Serializable
data class PresentUserDto(
    val userId: String? = null,
    val id: String? = null,
    val name: String? = null,
    val displayName: String? = null,
    val username: String? = null,
    val avatar: String? = null,
    val color: String? = null,
    val focusedRowId: String? = null,
) {
    val resolvedUserId: String? get() = (userId ?: id)?.takeIf { it.isNotBlank() }
    val resolvedName: String? get() = displayName?.takeIf { it.isNotBlank() } ?: name?.takeIf { it.isNotBlank() }
}

/**
 * Response for the freshness poll, verified live:
 * `changed` holds whole rows in the same shape `GET .../data` returns (keyed
 * `rowData`, with `version`), `deleted` is a bare array of row id strings, and
 * `collaborative` is the server's own flag. Only `users` could not be observed —
 * it needs a second person on the list.
 *
 * `deleted` is still decoded as raw JSON so an array of `{ "id": … }` objects
 * would be understood rather than failing the whole poll.
 */
@Serializable
data class RowVersionsResponse(
    val changed: List<RowDto> = emptyList(),
    val deleted: List<JsonElement> = emptyList(),
    val users: List<PresentUserDto> = emptyList(),
    val collaborative: Boolean = false,
) {
    val deletedIds: List<String>
        get() = deleted.mapNotNull { element ->
            when (element) {
                is JsonPrimitive -> element.content.takeIf { it.isNotBlank() }
                is JsonObject -> element.jsonObject["id"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
                else -> null
            }
        }
}
