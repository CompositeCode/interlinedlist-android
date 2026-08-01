package com.interlinedlist.android.feature.lists.domain

/**
 * A user who watches a list, together with the access role granted to them. Roles
 * gate what a watcher can do; [WatcherRole.VIEWER] is the default read-only grant.
 */
data class Watcher(
    val userId: String,
    val username: String,
    val displayName: String?,
    val avatarUrl: String?,
    val role: WatcherRole,
) {
    /** Best label for the row: display name when present, else the username. */
    val label: String get() = displayName?.takeIf { it.isNotBlank() } ?: username
}

/** A user candidate returned by the watcher search (not yet a watcher). */
data class WatcherCandidate(
    val userId: String,
    val username: String,
    val displayName: String?,
    val avatarUrl: String?,
) {
    val label: String get() = displayName?.takeIf { it.isNotBlank() } ?: username
}

/**
 * Access level a watcher holds on a list. Unknown/absent roles map to [VIEWER] so
 * a watcher is never dropped and defaults to the least-privileged grant.
 */
enum class WatcherRole(val apiValue: String) {
    VIEWER("viewer"),
    EDITOR("editor"),
    ADMIN("admin"),
    OWNER("owner");

    companion object {
        /** Maps an API role string (case-insensitive) to a [WatcherRole], defaulting to [VIEWER]. */
        fun fromApi(raw: String?): WatcherRole = when (raw?.trim()?.lowercase()) {
            "editor" -> EDITOR
            "admin" -> ADMIN
            "owner" -> OWNER
            else -> VIEWER
        }
    }
}
