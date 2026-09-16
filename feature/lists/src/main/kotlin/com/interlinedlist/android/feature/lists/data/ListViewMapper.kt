package com.interlinedlist.android.feature.lists.data

import com.interlinedlist.android.feature.lists.data.remote.dto.ListViewDto
import com.interlinedlist.android.feature.lists.domain.ListView
import com.interlinedlist.android.feature.lists.domain.ListViewConfig
import com.interlinedlist.android.feature.lists.domain.ListViewScope

/** DTO → domain mapping for saved views. */
object ListViewMapper {

    /**
     * Maps a server view, keeping its `config` verbatim. An unrecognised `scope`
     * is treated as [ListViewScope.SHARED]: the conservative reading, since it
     * stops the UI offering destructive actions on a view that may not be ours.
     */
    fun fromDto(dto: ListViewDto, listId: String): ListView = ListView(
        id = dto.id,
        listId = dto.listId ?: listId,
        userId = dto.userId,
        name = dto.name,
        scope = ListViewScope.fromApi(dto.scope) ?: ListViewScope.SHARED,
        config = ListViewConfig.fromJson(dto.config),
        isDefault = dto.isDefault,
        position = dto.position,
    )
}
