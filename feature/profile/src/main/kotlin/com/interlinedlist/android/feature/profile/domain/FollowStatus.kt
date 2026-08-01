package com.interlinedlist.android.feature.profile.domain

/**
 * The current user's follow relationship to a target user. Drives the follow button
 * on another user's profile. Private accounts return [REQUESTED] until the target
 * approves, so the button can read "Requested" instead of "Following".
 */
enum class FollowStatus {
    /** Not following and no pending request. */
    NOT_FOLLOWING,

    /** A follow request is pending approval (target account is private). */
    REQUESTED,

    /** Actively following the target. */
    FOLLOWING,

    /** Viewing your own profile — no follow affordance applies. */
    SELF,
}
