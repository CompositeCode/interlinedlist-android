package com.interlinedlist.android.feature.documents.domain

/**
 * A person granted direct access to a document (distinct from a public share
 * link). [userId] identifies the account; [role] is the access level. [displayName]
 * / [username] / [avatarUrl] are best-effort profile bits for rendering a row, and
 * may be blank when the list endpoint returns only the bare collaborator record.
 */
data class Collaborator(
    val userId: String,
    val role: CollaboratorRole,
    val displayName: String?,
    val username: String?,
    val email: String?,
    val avatarUrl: String?,
) {
    /** A short label for the row / avatar, preferring the friendliest identifier. */
    val label: String
        get() = displayName?.takeIf { it.isNotBlank() }
            ?: username?.takeIf { it.isNotBlank() }
            ?: email?.takeIf { it.isNotBlank() }
            ?: userId

    /** A single-letter avatar fallback. */
    val initial: String get() = label.trim().firstOrNull()?.uppercase() ?: "?"
}

/**
 * Access a collaborator holds on a document. Mirrors the web app's viewer / editor
 * / admin levels; unknown or absent server values map to [VIEWER] so access is
 * never inadvertently escalated.
 */
enum class CollaboratorRole(val apiValue: String, val label: String) {
    VIEWER("viewer", "Viewer"),
    EDITOR("editor", "Editor"),
    ADMIN("admin", "Admin");

    companion object {
        /** Maps an API role string (case-insensitive) to a [CollaboratorRole]. */
        fun fromApi(raw: String?): CollaboratorRole = when (raw?.trim()?.lowercase()) {
            "editor", "edit", "write" -> EDITOR
            "admin", "owner" -> ADMIN
            else -> VIEWER
        }
    }
}

/**
 * A user surfaced by the collaborator search (`/collaborators/users`) — a candidate
 * to invite. Carries just enough to render a pick row and issue the invite.
 */
data class CollaboratorCandidate(
    val userId: String,
    val username: String,
    val displayName: String?,
    val email: String?,
    val avatarUrl: String?,
) {
    val label: String
        get() = displayName?.takeIf { it.isNotBlank() } ?: username.takeIf { it.isNotBlank() } ?: email.orEmpty()

    val initial: String get() = label.trim().firstOrNull()?.uppercase() ?: "?"
}
