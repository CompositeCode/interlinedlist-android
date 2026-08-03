package com.interlinedlist.android.feature.lists.domain

/**
 * A list owned by another user that the current user has been granted access to,
 * as returned by `GET /api/lists/watching`. Carries the owner and the [role] the
 * current user holds on it, so the "Shared with me" section can label each entry.
 */
data class SharedList(
    val id: String,
    val title: String,
    val description: String?,
    val ownerName: String,
    val role: ShareRole,
    val isPublic: Boolean,
) {
    /** Best label for the owner line: display name is already resolved by the mapper. */
    val ownerLabel: String get() = ownerName
}

/**
 * The outcome of resolving a `…/shared/{token}` link: the target list's preview
 * data plus the access the link grants. When [canClaim] is true the visitor can
 * POST to the link to claim edit/admin access under their own account.
 */
data class SharedListResolution(
    val token: String,
    val listId: String,
    val title: String,
    val description: String?,
    val ownerName: String?,
    val role: ShareRole,
    val rows: List<ListRow>,
) {
    /** True when following the link can upgrade the visitor to edit/admin access. */
    val canClaim: Boolean get() = role.grantsClaim
}
