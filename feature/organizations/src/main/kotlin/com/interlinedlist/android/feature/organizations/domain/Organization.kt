package com.interlinedlist.android.feature.organizations.domain

/**
 * An organization as shown in the index and detail screens. Enough to render a
 * card, open the detail, and (in detail) edit name/description/visibility. The
 * member list is loaded separately on demand, so this stays lightweight for the
 * index.
 */
data class Organization(
    val id: String,
    val name: String,
    val description: String?,
    val avatarUrl: String?,
    val isPublic: Boolean,
    val memberCount: Int,
    /** The current user's role in this org, when the API reports it. */
    val role: OrgRole?,
    val updatedAt: String?,
    /**
     * A built-in organization such as "The Public", which every account belongs to.
     * The help centre states it cannot be left; it is not deletable either.
     * Declared last with a default so existing call sites stay positional.
     */
    val isSystem: Boolean = false,
) {
    /** Best label for a card: the name, falling back to a placeholder. */
    val displayName: String get() = name.ifBlank { "Untitled organization" }

    /** What the signed-in user may do here, per the documented role model. */
    val permissions: OrgPermissions get() = OrgPermissions.of(this)

    /**
     * Whether the signed-in user belongs to this organization. The API reports a
     * role only for the caller's own memberships, so an absent role means "not a
     * member" — that drives the Join/Leave affordance.
     */
    val isMember: Boolean get() = permissions.isMember

    /** A public organization the user has not joined can be joined from the UI. */
    val canJoin: Boolean get() = permissions.canJoin
}

/**
 * A page of results plus whether more remain, so the UI can offer load-more
 * without knowing the wire pagination shape.
 */
data class Paged<T>(
    val items: List<T>,
    val hasMore: Boolean,
    val total: Int,
    val offset: Int,
)
