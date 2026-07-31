package com.interlinedlist.android.feature.profile.ui.profile

import com.interlinedlist.android.feature.profile.domain.PublicDocumentSummary
import com.interlinedlist.android.feature.profile.domain.PublicListSummary
import com.interlinedlist.android.feature.profile.domain.PublicPost

/** The content tabs shown on another user's public profile. */
enum class ProfileContentTab { POSTS, LISTS, DOCUMENTS }

/**
 * The content shown under the other-user profile's tabs. Each tab is loaded lazily
 * the first time it is selected ([loadedTabs]); [isLoading]/[errorMessage] track the
 * currently-selected tab's fetch.
 */
data class PublicContentState(
    val posts: List<PublicPost> = emptyList(),
    val lists: List<PublicListSummary> = emptyList(),
    val documents: List<PublicDocumentSummary> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val loadedTabs: Set<ProfileContentTab> = emptySet(),
)
