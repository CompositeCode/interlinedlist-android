package com.interlinedlist.android.feature.profile.domain

/**
 * Read-only domain models for another user's public content — their posts, lists, and
 * documents — surfaced on the other-user profile screen (Milestone L). These are
 * view-only projections; the module does not cache or mutate them (YAGNI), so they
 * carry only the fields the read-only surfaces render.
 */

/** A public post (message) authored by the viewed user. */
data class PublicPost(
    val id: String,
    val content: String,
    val createdAt: String?,
)

/** A summary of a public list in the viewed user's Lists tab. */
data class PublicListSummary(
    val id: String,
    val title: String,
    val description: String?,
) {
    /** A non-blank label to show, falling back to a placeholder for untitled lists. */
    val displayTitle: String get() = title.takeIf { it.isNotBlank() } ?: "Untitled list"
}

/**
 * A fully-loaded public list: its metadata plus its rows. Each row is a flat map of
 * schema-field label → display value, projected from the dynamic `rowData` object so
 * the read-only view can render arbitrary user-defined schemas without a fixed shape.
 */
data class PublicListDetail(
    val id: String,
    val title: String,
    val description: String?,
    val rows: List<PublicListRow>,
) {
    val displayTitle: String get() = title.takeIf { it.isNotBlank() } ?: "Untitled list"
}

/** A single read-only row: an ordered list of field label → value pairs. */
data class PublicListRow(
    val id: String,
    val cells: List<PublicListCell>,
)

/** One field within a [PublicListRow]. */
data class PublicListCell(
    val label: String,
    val value: String,
)

/** A summary of a public document in the viewed user's Documents tab. */
data class PublicDocumentSummary(
    val id: String,
    val title: String,
) {
    val displayTitle: String get() = title.takeIf { it.isNotBlank() } ?: "Untitled document"
}

/** A fully-loaded public document: its title and markdown/plain-text content. */
data class PublicDocumentDetail(
    val id: String,
    val title: String,
    val content: String,
) {
    val displayTitle: String get() = title.takeIf { it.isNotBlank() } ?: "Untitled document"
}

/**
 * Mutual-connection tallies with the viewed user, from `GET /api/follow/{userId}/mutual`.
 * The endpoint returns counts only (no user list), so the indicator shows a count.
 *
 * @property mutualFollowers people who follow you that also follow the viewed user.
 * @property mutualFollowing people you follow that the viewed user also follows.
 */
data class MutualConnections(
    val mutualFollowers: Int = 0,
    val mutualFollowing: Int = 0,
) {
    /** The headline mutual-connection count shown on the profile. */
    val total: Int get() = mutualFollowers
}
