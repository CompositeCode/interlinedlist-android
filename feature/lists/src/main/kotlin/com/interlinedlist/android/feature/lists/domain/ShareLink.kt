package com.interlinedlist.android.feature.lists.domain

/**
 * A public share link for a list. The [token] is the opaque secret embedded in the
 * shareable URL; [role] is the access it grants to whoever follows it. A link with
 * a non-null [revokedAt] is dead and should not be surfaced as active.
 */
data class ShareLink(
    val id: String,
    val token: String,
    val role: ShareRole,
    val expiresAt: String?,
    val revokedAt: String?,
    val createdAt: String?,
) {
    /** True when the link is still usable (not revoked). */
    val isActive: Boolean get() = revokedAt == null

    /** The public URL a user copies/shares to grant access via this link. */
    fun url(baseUrl: String = INTERLINEDLIST_BASE_URL): String =
        "${baseUrl.trimEnd('/')}/lists/shared/$token"

    companion object {
        const val INTERLINEDLIST_BASE_URL = "https://interlinedlist.com"
    }
}

/**
 * Access level a share link grants. The web app offers view / edit / admin; unknown
 * or absent server values map to [VIEW] so a link is never over-privileged by accident.
 */
enum class ShareRole(val apiValue: String, val label: String) {
    VIEW("view", "View"),
    EDIT("edit", "Edit"),
    ADMIN("admin", "Admin");

    /** True when following this link lets the visitor claim edit/admin access. */
    val grantsClaim: Boolean get() = this != VIEW

    companion object {
        /** Maps an API role string (case-insensitive) to a [ShareRole], defaulting to [VIEW]. */
        fun fromApi(raw: String?): ShareRole = when (raw?.trim()?.lowercase()) {
            "edit", "editor", "collaborator" -> EDIT
            "admin", "owner" -> ADMIN
            else -> VIEW
        }
    }
}
