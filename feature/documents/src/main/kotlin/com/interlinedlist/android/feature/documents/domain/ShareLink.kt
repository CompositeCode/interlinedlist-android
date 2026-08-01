package com.interlinedlist.android.feature.documents.domain

/**
 * A public share link for a document. [token] is the opaque secret embedded in the
 * shareable URL; [role] is the access it grants. A link with a non-null [revokedAt]
 * is dead and should not be surfaced as active.
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
        "${baseUrl.trimEnd('/')}/documents/shared/$token"

    companion object {
        const val INTERLINEDLIST_BASE_URL = "https://interlinedlist.com"
    }
}

/**
 * Access level a document share link grants. The web app offers view / edit / admin;
 * unknown or absent server values map to [VIEW] so a link is never over-privileged.
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

/**
 * The outcome of resolving a `documents/shared/{token}` link: the target document's
 * read-only preview plus the access the link grants. When [canClaim] is true the
 * visitor can POST to the link to claim edit/admin access under their own account.
 */
data class SharedDocument(
    val token: String,
    val documentId: String,
    val title: String,
    val content: String?,
    val ownerName: String?,
    val role: ShareRole,
) {
    /** True when following the link can upgrade the visitor to edit/admin access. */
    val canClaim: Boolean get() = role.grantsClaim
}
