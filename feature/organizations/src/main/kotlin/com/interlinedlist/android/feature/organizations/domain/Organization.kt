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
) {
    /** Best label for a card: the name, falling back to a placeholder. */
    val displayName: String get() = name.ifBlank { "Untitled organization" }
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
