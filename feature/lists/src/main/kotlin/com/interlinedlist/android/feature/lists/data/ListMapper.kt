package com.interlinedlist.android.feature.lists.data

import com.interlinedlist.android.feature.lists.data.local.CachedListEntity
import com.interlinedlist.android.feature.lists.data.remote.dto.FolderDto
import com.interlinedlist.android.feature.lists.data.remote.dto.ListDto
import com.interlinedlist.android.feature.lists.domain.ListFolder
import com.interlinedlist.android.feature.lists.domain.ListSource
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
        parentId = dto.parentId ?: dto.parent?.id,
        source = ListSource.fromWire(dto.source),
        githubRepo = dto.githubRepo?.takeIf { it.isNotBlank() },
        // Left null when the server has not recorded it: an unknown repository
        // visibility must never be rendered as "public".
        githubRepoPrivate = dto.githubRepoPrivate,
    )

    fun summaryToEntity(summary: ListSummary): CachedListEntity = CachedListEntity(
        id = summary.id,
        title = summary.title,
        description = summary.description,
        itemCount = summary.itemCount,
        folderId = summary.folderId,
        isPublic = summary.isPublic,
        updatedAt = summary.updatedAt,
        parentId = summary.parentId,
        source = summary.source.wire,
        githubRepo = summary.githubRepo,
        githubRepoPrivate = summary.githubRepoPrivate,
    )

    fun summaryFromEntity(entity: CachedListEntity): ListSummary = ListSummary(
        id = entity.id,
        title = entity.title,
        description = entity.description,
        itemCount = entity.itemCount,
        folderId = entity.folderId,
        isPublic = entity.isPublic,
        updatedAt = entity.updatedAt,
        parentId = entity.parentId,
        source = ListSource.fromWire(entity.source),
        githubRepo = entity.githubRepo,
        githubRepoPrivate = entity.githubRepoPrivate,
    )

    fun folderFromDto(dto: FolderDto): ListFolder = ListFolder(
        id = dto.id,
        name = dto.name,
        parentId = dto.parentId,
    )
}
