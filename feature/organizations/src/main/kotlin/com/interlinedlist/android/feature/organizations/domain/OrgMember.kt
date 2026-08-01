package com.interlinedlist.android.feature.organizations.domain

/**
 * A member of an organization, together with the role granted to them. Roles gate
 * what a member can do; [OrgRole.MEMBER] is the default least-privileged grant.
 * A member row reaches the client either flattened (`userId`/`username` on the
 * row) or with a nested `user` object — the mapper tolerates both.
 */
data class OrgMember(
    val userId: String,
    val username: String,
    val displayName: String?,
    val avatarUrl: String?,
    val role: OrgRole,
    /** Whether the membership is active; inactive members are still listed. */
    val active: Boolean,
) {
    /** Best label for the row: display name when present, else the username. */
    val label: String get() = displayName?.takeIf { it.isNotBlank() } ?: username
}

/** A user candidate returned by the org user search (not yet a member). */
data class MemberCandidate(
    val userId: String,
    val username: String,
    val displayName: String?,
    val avatarUrl: String?,
) {
    val label: String get() = displayName?.takeIf { it.isNotBlank() } ?: username
}

/**
 * Access level a member holds in an organization. Unknown/absent roles map to
 * [MEMBER] so a member is never dropped and defaults to the least-privileged
 * grant. Ordered least- to most-privileged.
 */
enum class OrgRole(val apiValue: String) {
    MEMBER("member"),
    ADMIN("admin"),
    OWNER("owner");

    /** Human label for chips/menus, e.g. "Member". */
    val label: String get() = name.lowercase().replaceFirstChar { it.uppercase() }

    companion object {
        /** Maps an API role string (case-insensitive) to an [OrgRole], defaulting to [MEMBER]. */
        fun fromApi(raw: String?): OrgRole = when (raw?.trim()?.lowercase()) {
            "owner" -> OWNER
            "admin" -> ADMIN
            else -> MEMBER
        }
    }
}
