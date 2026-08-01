package com.interlinedlist.android.feature.profile.data.mapper

import com.interlinedlist.android.feature.profile.data.remote.dto.ModeratedUserDto
import com.interlinedlist.android.feature.profile.domain.ModeratedUser

/**
 * Maps a blocks/mutes list entry to a domain [ModeratedUser], dropping entries with no
 * resolvable user (missing id or username), since a row can neither render nor be
 * un-blocked / un-muted without a username.
 */
fun ModeratedUserDto.toModeratedUserOrNull(): ModeratedUser? {
    val dto = userOrSelf ?: return null
    if (dto.id.isBlank() || dto.username.isBlank()) return null
    return ModeratedUser(
        id = dto.id,
        username = dto.username,
        displayName = dto.displayName,
        avatarUrl = dto.avatarOrNull,
    )
}
