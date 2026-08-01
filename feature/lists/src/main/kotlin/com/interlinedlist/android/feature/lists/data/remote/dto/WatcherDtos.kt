package com.interlinedlist.android.feature.lists.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * Wire models for the list watchers endpoints. Field names follow the
 * InterlinedList REST contract; the shared Json ignores unknown keys, so only the
 * fields the UI renders need declaring. A watcher may arrive either flattened
 * (`userId`/`username` on the row) or with a nested `user` object — the mapper
 * ([com.interlinedlist.android.feature.lists.data.WatcherMapper]) tolerates both.
 */
@Serializable
data class WatcherDto(
    val id: String? = null,
    val userId: String? = null,
    val role: String? = null,
    val username: String? = null,
    val displayName: String? = null,
    val avatarUrl: String? = null,
    val user: WatcherUserDto? = null,
)

/** A user reference nested on a watcher row or returned by the user search. */
@Serializable
data class WatcherUserDto(
    val id: String,
    val username: String = "",
    val displayName: String? = null,
    val avatarUrl: String? = null,
)

/** Envelope for `GET /api/lists/{id}/watchers`; watchers may be wrapped or bare. */
@Serializable
data class WatchersResponse(
    val data: List<WatcherDto>? = null,
    val watchers: List<WatcherDto>? = null,
    val pagination: PaginationDto? = null,
) {
    val items: List<WatcherDto> get() = data ?: watchers ?: emptyList()
}

/** Envelope for `GET /api/lists/{id}/watchers/users` (candidate users to add). */
@Serializable
data class WatcherUsersResponse(
    val data: List<WatcherUserDto>? = null,
    val users: List<WatcherUserDto>? = null,
    val pagination: PaginationDto? = null,
) {
    val items: List<WatcherUserDto> get() = data ?: users ?: emptyList()
}

/** Envelope for `GET /api/lists/{id}/watchers/me`. */
@Serializable
data class WatchingStatusDto(
    val watching: Boolean = false,
    val isWatching: Boolean? = null,
    val role: String? = null,
) {
    /** True when either boolean flavour the API may use reports watching. */
    val isWatchingResolved: Boolean get() = isWatching ?: watching
}

/** Body for `POST /api/lists/{id}/watchers` — add one user with an optional role. */
@Serializable
data class AddWatcherRequest(
    val userId: String,
    val role: String? = null,
)

/** Body for `PUT /api/lists/{id}/watchers/{userId}` — change a user's role. */
@Serializable
data class UpdateWatcherRoleRequest(
    val role: String,
)
