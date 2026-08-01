package com.interlinedlist.android.feature.profile.data.mapper

import com.interlinedlist.android.feature.profile.data.remote.dto.MutualConnectionsResponse
import com.interlinedlist.android.feature.profile.data.remote.dto.PublicDocumentDetailDto
import com.interlinedlist.android.feature.profile.data.remote.dto.PublicDocumentDto
import com.interlinedlist.android.feature.profile.data.remote.dto.PublicListDto
import com.interlinedlist.android.feature.profile.data.remote.dto.PublicListRowDto
import com.interlinedlist.android.feature.profile.data.remote.dto.PublicPostDto
import com.interlinedlist.android.feature.profile.domain.MutualConnections
import com.interlinedlist.android.feature.profile.domain.PublicDocumentDetail
import com.interlinedlist.android.feature.profile.domain.PublicDocumentSummary
import com.interlinedlist.android.feature.profile.domain.PublicListCell
import com.interlinedlist.android.feature.profile.domain.PublicListRow
import com.interlinedlist.android.feature.profile.domain.PublicListSummary
import com.interlinedlist.android.feature.profile.domain.PublicPost
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Maps a public post DTO into the read-only [PublicPost]. */
fun PublicPostDto.toPublicPost(): PublicPost = PublicPost(
    id = id,
    content = content,
    createdAt = createdAt,
)

/** Maps a public list summary DTO. */
fun PublicListDto.toPublicListSummary(): PublicListSummary = PublicListSummary(
    id = id,
    title = title,
    description = description,
)

/**
 * Maps a public list row's dynamic `rowData` map into an ordered list of display
 * cells. Value types are never assumed — strings, numbers, booleans, nulls, and
 * nested arrays/objects are all coerced to a readable string so any user-defined
 * schema renders. Blank/null cells are dropped so the read-only view stays tidy.
 */
fun PublicListRowDto.toPublicListRow(): PublicListRow = PublicListRow(
    id = id,
    cells = fields.entries
        .map { (key, value) -> PublicListCell(label = key, value = displayString(value)) }
        .filter { it.value.isNotBlank() },
)

/** Maps a public document summary DTO. */
fun PublicDocumentDto.toPublicDocumentSummary(): PublicDocumentSummary = PublicDocumentSummary(
    id = id,
    title = title,
)

/** Maps a public document detail DTO (title + content). */
fun PublicDocumentDetailDto.toPublicDocumentDetail(): PublicDocumentDetail = PublicDocumentDetail(
    id = id,
    title = title,
    content = content,
)

/** Maps the mutual-connections counts response. */
fun MutualConnectionsResponse.toMutualConnections(): MutualConnections = MutualConnections(
    mutualFollowers = mutualFollowersOrZero,
    mutualFollowing = mutualFollowingOrZero,
)

/** Coerces any JSON value to a human-readable string (nulls become empty). */
internal fun displayString(value: JsonElement): String = when (value) {
    is JsonNull -> ""
    is JsonPrimitive -> value.content
    is JsonArray -> value.joinToString(", ") { displayString(it) }
    is JsonObject -> value.entries.joinToString(", ") { (k, v) -> "$k: ${displayString(v)}" }
}
