package com.interlinedlist.android.feature.documents.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * A row of `GET /api/lists`, narrowed to the fields the Powered Document list
 * picker shows. The endpoint returns a lot more (folder, github, parent chain);
 * everything unmodelled is ignored.
 */
@Serializable
data class ListSourceDto(
    val id: String? = null,
    val title: String? = null,
    val description: String? = null,
)

/** `GET /api/lists` → `{ lists: [...], pagination: { … } }`. */
@Serializable
data class ListSourcesResponse(
    val lists: List<ListSourceDto>? = null,
) {
    val listsOrEmpty: List<ListSourceDto> get() = lists.orEmpty()
}
