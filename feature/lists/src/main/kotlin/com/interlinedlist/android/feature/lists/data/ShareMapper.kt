package com.interlinedlist.android.feature.lists.data

import com.interlinedlist.android.feature.lists.data.remote.dto.ShareLinkDto
import com.interlinedlist.android.feature.lists.data.remote.dto.SharedListResponse
import com.interlinedlist.android.feature.lists.data.remote.dto.WatchingListDto
import com.interlinedlist.android.feature.lists.domain.ShareLink
import com.interlinedlist.android.feature.lists.domain.ShareRole
import com.interlinedlist.android.feature.lists.domain.SharedList
import com.interlinedlist.android.feature.lists.domain.SharedListResolution

/** DTO ↔ domain mapping for the list sharing surface. */
object ShareMapper {

    fun linkFromDto(dto: ShareLinkDto): ShareLink = ShareLink(
        id = dto.id,
        token = dto.token,
        role = ShareRole.fromApi(dto.role),
        expiresAt = dto.expiresAt,
        revokedAt = dto.revokedAt,
        createdAt = dto.createdAt,
    )

    fun sharedFromDto(dto: WatchingListDto): SharedList = SharedList(
        id = dto.id,
        title = dto.title,
        description = dto.description,
        ownerName = dto.user?.let { it.displayName?.takeIf(String::isNotBlank) ?: it.username }
            ?.takeIf(String::isNotBlank) ?: "Unknown owner",
        role = ShareRole.fromApi(dto.role),
        isPublic = dto.isPublic,
    )

    fun resolutionFromResponse(token: String, response: SharedListResponse): SharedListResolution {
        val list = response.listOrSelf
        val owner = (response.user ?: list?.user)?.let {
            it.displayName?.takeIf(String::isNotBlank) ?: it.username.takeIf(String::isNotBlank)
        }
        return SharedListResolution(
            token = token,
            listId = list?.id.orEmpty(),
            title = list?.title.orEmpty(),
            description = list?.description,
            ownerName = owner,
            role = ShareRole.fromApi(response.role ?: list?.role),
            rows = response.rowsOrEmpty.map(RowMapper::fromDto),
        )
    }
}
