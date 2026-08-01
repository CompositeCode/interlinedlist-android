package com.interlinedlist.android.feature.lists.data

import com.interlinedlist.android.feature.lists.data.remote.dto.ConnectionDto
import com.interlinedlist.android.feature.lists.data.remote.dto.RefreshResultDto
import com.interlinedlist.android.feature.lists.domain.ListConnection
import com.interlinedlist.android.feature.lists.domain.RefreshResult

/** DTO → domain mapping for list connections and the GitHub refresh result. */
object ConnectionMapper {

    fun connectionFromDto(dto: ConnectionDto): ListConnection = ListConnection(
        id = dto.id,
        fromListId = dto.fromListId,
        toListId = dto.toListId,
        label = dto.label?.takeIf { it.isNotBlank() },
        // Prefer titles when the API supplies them, else fall back to the ids.
        fromListTitle = dto.fromListTitle?.takeIf { it.isNotBlank() } ?: dto.fromListId,
        toListTitle = dto.toListTitle?.takeIf { it.isNotBlank() } ?: dto.toListId,
    )

    fun refreshFromDto(dto: RefreshResultDto): RefreshResult = RefreshResult(
        message = dto.message?.takeIf { it.isNotBlank() },
        added = dto.added,
        updated = dto.updated,
        removed = dto.removed,
    )
}
