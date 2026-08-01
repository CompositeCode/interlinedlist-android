package com.interlinedlist.android.feature.lists.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * Wire models for `GET /api/lists/{id}/contributors`. Field names follow the
 * InterlinedList REST contract (`ListContributor`); the shared Json ignores
 * unknown keys, so only the fields the UI renders are declared. All optionals are
 * defaulted so the shared `coerceInputValues` Json never fails on explicit nulls.
 *
 * Note the avatar arrives as `avatar` (not `avatarUrl`), verified against the web
 * app's contributors handler.
 */
@Serializable
data class ContributorDto(
    val id: String = "",
    val username: String = "",
    val displayName: String? = null,
    val avatar: String? = null,
    val addedCount: Int = 0,
    val editedCount: Int = 0,
    val score: Int = 0,
)

/**
 * Envelope for `GET /api/lists/{id}/contributors`. Contributors arrive under
 * `contributors` (verified against the web handler) with a `totalContributors`
 * count; `data` is tolerated for forward-compatibility.
 */
@Serializable
data class ContributorsResponse(
    val contributors: List<ContributorDto>? = null,
    val data: List<ContributorDto>? = null,
    val totalContributors: Int? = null,
) {
    val items: List<ContributorDto> get() = contributors ?: data ?: emptyList()
}
