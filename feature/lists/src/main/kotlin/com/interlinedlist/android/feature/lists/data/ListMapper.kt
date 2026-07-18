package com.interlinedlist.android.feature.lists.data

import com.interlinedlist.android.feature.lists.data.local.CachedListEntity
import com.interlinedlist.android.feature.lists.data.remote.dto.FolderDto
import com.interlinedlist.android.feature.lists.data.remote.dto.ListDto
import com.interlinedlist.android.feature.lists.domain.ListFolder
import com.interlinedlist.android.feature.lists.domain.ListSummary

/** DTO/entity ↔ domain mapping for list summaries and folders. */
object ListMapper {

    /** Resolves the item count across the API's several count field names. */
    private fun ListDto.resolveItemCount(): Int =
        itemCount ?: rowCount ?: count ?: 0

    fun summaryFromDto(dto: ListDto): ListSummary = ListSummary(
        id = dto.id,
        title = dto.title,
        description = dto.description,
        itemCount = dto.resolveItemCount(),
        folderId = dto.folderId,
        isPublic = dto.isPublic,
        updatedAt = dto.updatedAt,
    )

    fun summaryToEntity(summary: ListSummary): CachedListEntity = CachedListEntity(
        id = summary.id,
        title = summary.title,
        description = summary.description,
        itemCount = summary.itemCount,
        folderId = summary.folderId,
        isPublic = summary.isPublic,
        updatedAt = summary.updatedAt,
    )

    fun summaryFromEntity(entity: CachedListEntity): ListSummary = ListSummary(
        id = entity.id,
        title = entity.title,
        description = entity.description,
        itemCount = entity.itemCount,
        folderId = entity.folderId,
        isPublic = entity.isPublic,
        updatedAt = entity.updatedAt,
    )

    fun folderFromDto(dto: FolderDto): ListFolder = ListFolder(
        id = dto.id,
        name = dto.name,
        parentId = dto.parentId,
    )
}
