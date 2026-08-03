package com.interlinedlist.android.feature.documents.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * A user profile as embedded in collaborator/search responses. All fields default
 * so the DTO tolerates the shape variation across endpoints (with the shared,
 * lenient Json: `ignoreUnknownKeys` + `coerceInputValues`).
 */
@Serializable
data class UserSummaryDto(
    val id: String = "",
    val username: String = "",
    val displayName: String? = null,
    val email: String? = null,
    val avatar: String? = null,
)

/**
 * A collaborator row (`DocumentCollaborator`: id, userId, documentId, role,
 * createdAt). Profile details may be flattened onto the row or nested under `user`
 * depending on the endpoint; [resolvedUserId] and the accessors below tolerate both.
 */
@Serializable
data class CollaboratorDto(
    val id: String = "",
    val userId: String? = null,
    val documentId: String? = null,
    val role: String? = null,
    val createdAt: String? = null,
    val user: UserSummaryDto? = null,
    // Flattened profile fields, when the server inlines them onto the row.
    val username: String? = null,
    val displayName: String? = null,
    val email: String? = null,
    val avatar: String? = null,
) {
    val resolvedUserId: String get() = userId ?: user?.id.orEmpty()
    val resolvedUsername: String? get() = username ?: user?.username
    val resolvedDisplayName: String? get() = displayName ?: user?.displayName
    val resolvedEmail: String? get() = email ?: user?.email
    val resolvedAvatar: String? get() = avatar ?: user?.avatar
}

/** `GET /api/documents/{id}/collaborators` — `{ collaborators, pagination }`. */
@Serializable
data class CollaboratorsResponse(
    val collaborators: List<CollaboratorDto> = emptyList(),
    val data: List<CollaboratorDto>? = null,
    val pagination: PaginationDto? = null,
) {
    val items: List<CollaboratorDto> get() = data ?: collaborators
}

/**
 * A single collaborator, returned bare or wrapped in `{ "collaborator": ... }` from
 * the invite (`POST`) / role-change (`PUT`) endpoints.
 */
@Serializable
data class CollaboratorEnvelope(
    val collaborator: CollaboratorDto? = null,
    val data: CollaboratorDto? = null,
) {
    val collaboratorOrNull: CollaboratorDto? get() = collaborator ?: data
}

/** `GET /api/documents/{id}/collaborators/users?search=` — `{ users, total, pagination }`. */
@Serializable
data class CollaboratorUsersResponse(
    val users: List<UserSummaryDto> = emptyList(),
    val total: Int = 0,
    val pagination: PaginationDto? = null,
)

/** Body for `POST /api/documents/{id}/collaborators` — invite a user at a role. */
@Serializable
data class InviteCollaboratorRequest(
    val userId: String,
    val role: String,
)

/** Body for `PUT /api/documents/{id}/collaborators/{userId}` — change a role. */
@Serializable
data class UpdateCollaboratorRoleRequest(
    val role: String,
)
