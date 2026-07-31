package com.interlinedlist.android.feature.documents.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * A presence heartbeat row (`DocumentPresence`: id, documentId, userId, updatedAt).
 * Cursor offsets exist server-side but are ignored here — we only render "who is
 * here". Profile bits may be inlined or nested under `user`; accessors tolerate both.
 */
@Serializable
data class PresenceDto(
    val id: String = "",
    val documentId: String? = null,
    val userId: String? = null,
    val updatedAt: String? = null,
    val user: UserSummaryDto? = null,
    val username: String? = null,
    val displayName: String? = null,
    val avatar: String? = null,
) {
    val resolvedUserId: String get() = userId ?: user?.id.orEmpty()
    val resolvedUsername: String? get() = username ?: user?.username
    val resolvedDisplayName: String? get() = displayName ?: user?.displayName
    val resolvedAvatar: String? get() = avatar ?: user?.avatar
}

/**
 * Response for `POST /api/documents/{id}/presence` — the current heartbeat plus the
 * set of everyone present. Shapes vary; both `presence`/`participants` and a bare
 * list under `data` are tolerated.
 */
@Serializable
data class PresenceResponse(
    val presences: List<PresenceDto>? = null,
    val participants: List<PresenceDto>? = null,
    val data: List<PresenceDto>? = null,
    val presence: PresenceDto? = null,
) {
    val items: List<PresenceDto>
        get() = presences ?: participants ?: data ?: presence?.let { listOf(it) } ?: emptyList()
}
