package com.interlinedlist.android.feature.lists.data

import com.interlinedlist.android.feature.lists.data.remote.dto.WatcherDto
import com.interlinedlist.android.feature.lists.data.remote.dto.WatcherUserDto
import com.interlinedlist.android.feature.lists.domain.Watcher
import com.interlinedlist.android.feature.lists.domain.WatcherCandidate
import com.interlinedlist.android.feature.lists.domain.WatcherRole

/**
 * DTO → domain mapping for watchers and candidate users.
 *
 * A watcher row reaches the client in two shapes: flattened (`userId`/`username`
 * on the row) or with a nested `user` object. This mapper reads the user id,
 * username, display name, and avatar from whichever is present, and normalises the
 * role via [WatcherRole.fromApi], so a watcher is never dropped for a missing field.
 */
object WatcherMapper {

    fun watcherFromDto(dto: WatcherDto): Watcher? {
        val userId = dto.userId ?: dto.user?.id ?: dto.id ?: return null
        return Watcher(
            userId = userId,
            username = dto.username ?: dto.user?.username ?: userId,
            displayName = dto.displayName ?: dto.user?.displayName,
            avatarUrl = dto.avatarUrl ?: dto.user?.avatarUrl,
            role = WatcherRole.fromApi(dto.role),
        )
    }

    fun candidateFromDto(dto: WatcherUserDto): WatcherCandidate = WatcherCandidate(
        userId = dto.id,
        username = dto.username.ifBlank { dto.id },
        displayName = dto.displayName,
        avatarUrl = dto.avatarUrl,
    )
}
