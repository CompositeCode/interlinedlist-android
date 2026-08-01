package com.interlinedlist.android.feature.profile.domain

import com.interlinedlist.android.core.model.CustomerStatus

/**
 * A profile as shown in this feature: the current signed-in user or another user
 * viewed by username. Wraps the shared [com.interlinedlist.android.core.model.User]
 * fields plus a `isCurrentUser` flag so the UI can decide whether to show the edit
 * and sign-out affordances.
 *
 * Kept distinct from the shared `User` so the module can carry profile-only fields
 * (e.g. [isCurrentUser]) without touching `:core:model`.
 */
data class ProfileUser(
    val id: String,
    val username: String,
    val displayName: String?,
    val avatarUrl: String?,
    val bio: String?,
    val customerStatus: CustomerStatus,
    val isCurrentUser: Boolean,
) {
    /** The best label to show for the user: display name if set, else the @username. */
    val displayLabel: String
        get() = displayName?.takeIf { it.isNotBlank() } ?: "@$username"

    /** True when the account has any active paid subscription. */
    val isSubscriber: Boolean get() = customerStatus.isSubscriber
}
