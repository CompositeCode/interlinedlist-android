package com.interlinedlist.android.feature.lists.data

import com.interlinedlist.android.feature.lists.data.remote.dto.PresentUserDto
import com.interlinedlist.android.feature.lists.data.remote.dto.RowVersionsResponse
import com.interlinedlist.android.feature.lists.domain.ListFreshness
import com.interlinedlist.android.feature.lists.domain.ListPresence

/**
 * Projects the freshness poll's response into the domain.
 *
 * Changed rows arrive in exactly the shape `GET /api/lists/{id}/data` returns, so
 * they go through [RowMapper] untouched and a repainted row is indistinguishable
 * from a fetched one. Presence entries with no resolvable user id are dropped
 * rather than rendered as a blank avatar.
 */
object FreshnessMapper {

    fun fromDto(dto: RowVersionsResponse): ListFreshness = ListFreshness(
        changed = dto.changed.filter { it.id.isNotBlank() }.map(RowMapper::fromDto),
        deletedRowIds = dto.deletedIds,
        presence = dto.users.mapNotNull(::presenceFromDto),
        collaborative = dto.collaborative,
    )

    private fun presenceFromDto(dto: PresentUserDto): ListPresence? {
        val userId = dto.resolvedUserId ?: return null
        return ListPresence(
            userId = userId,
            displayName = dto.resolvedName,
            username = dto.username,
            focusedRowId = dto.focusedRowId,
            color = dto.color,
        )
    }
}
