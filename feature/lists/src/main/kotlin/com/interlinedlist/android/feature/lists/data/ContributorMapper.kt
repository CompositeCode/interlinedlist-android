package com.interlinedlist.android.feature.lists.data

import com.interlinedlist.android.feature.lists.data.remote.dto.ContributorDto
import com.interlinedlist.android.feature.lists.domain.Contributor

/**
 * DTO → domain mapping for list contributors. The avatar arrives as `avatar` on the
 * wire (not `avatarUrl`); the username falls back to the id so a row is never blank.
 */
object ContributorMapper {

    fun fromDto(dto: ContributorDto): Contributor = Contributor(
        userId = dto.id,
        username = dto.username.ifBlank { dto.id },
        displayName = dto.displayName,
        avatarUrl = dto.avatar,
        addedCount = dto.addedCount,
        editedCount = dto.editedCount,
        score = dto.score,
    )
}
