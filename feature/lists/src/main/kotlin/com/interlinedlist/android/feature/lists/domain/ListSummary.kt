package com.interlinedlist.android.feature.lists.domain

/**
 * A user's list as shown in the index/feed: enough to render a card and open the
 * detail screen. The full schema and data rows are loaded lazily on the detail
 * screen, so this stays lightweight for the (potentially long) index.
 */
data class ListSummary(
    val id: String,
    val title: String,
    val description: String?,
    val itemCount: Int,
    val folderId: String?,
    val isPublic: Boolean,
    val updatedAt: String?,
    /**
     * The list this one hangs under, if any. Lists form a tree, so the detail
     * screen walks this up to render a breadcrumb
     * ([com.interlinedlist.android.feature.lists.data.ListsRepository.getParentChain]).
     */
    val parentId: String? = null,
    /** Where the rows come from; defaults to [ListSource.LOCAL] as the API does. */
    val source: ListSource = ListSource.LOCAL,
    /** `"owner/repo"` for a GitHub-backed list, else null. */
    val githubRepo: String? = null,
    /**
     * Whether the backing **repository** is private on GitHub — not whether this
     * list is private, which is [isPublic]. Null until the list's first sync
     * records it; an unknown visibility is never presented as public.
     */
    val githubRepoPrivate: Boolean? = null,
) {
    /**
     * True when this list mirrors a GitHub repository's issues. Such a list has a
     * fixed schema and its rows are issues, so the UI locks the schema editor and
     * offers "Refresh from GitHub" instead of ordinary column editing.
     */
    val isGithubBacked: Boolean get() = source == ListSource.GITHUB && !githubRepo.isNullOrBlank()
}
