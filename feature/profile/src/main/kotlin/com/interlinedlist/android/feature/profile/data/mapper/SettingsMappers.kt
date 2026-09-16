package com.interlinedlist.android.feature.profile.data.mapper

import com.interlinedlist.android.feature.profile.data.remote.dto.ProfileUserDto
import com.interlinedlist.android.feature.profile.data.remote.dto.UpdateProfileRequest
import com.interlinedlist.android.feature.profile.domain.UserSettings
import com.interlinedlist.android.feature.profile.domain.UserSettingsUpdate
import com.interlinedlist.android.feature.profile.domain.ViewingPreference

/**
 * Maps the wire user onto the settings the Settings screen and the feed read.
 * An absent or unrecognised `viewingPreference`/`showPreviews` falls back to the
 * server default rather than failing — these two drive the feed, so they must
 * always resolve to something usable.
 */
fun ProfileUserDto.toUserSettings(): UserSettings = UserSettings(
    theme = theme,
    maxMessageLength = maxMessageLength,
    defaultPubliclyVisible = defaultPubliclyVisible,
    messagesPerPage = messagesPerPage,
    viewingPreference = ViewingPreference.fromWireOrDefault(viewingPreference),
    showPreviews = showPreviews ?: true,
    showAdvancedPostSettings = showAdvancedPostSettings,
    latitude = latitude,
    longitude = longitude,
    isPrivateAccount = isPrivateAccount,
    githubDefaultRepo = githubDefaultRepo,
    notificationTrayLimit = notificationTrayLimit,
)

/**
 * Maps a partial settings update onto the PATCH body. Untouched (null) fields stay
 * null and are dropped from the JSON, so the request only carries what changed.
 */
fun UserSettingsUpdate.toRequest(): UpdateProfileRequest = UpdateProfileRequest(
    displayName = displayName,
    bio = bio,
    avatar = avatar,
    theme = theme,
    maxMessageLength = maxMessageLength,
    defaultPubliclyVisible = defaultPubliclyVisible,
    messagesPerPage = messagesPerPage,
    viewingPreference = viewingPreference?.wire,
    showPreviews = showPreviews,
    showAdvancedPostSettings = showAdvancedPostSettings,
    latitude = latitude,
    longitude = longitude,
    isPrivateAccount = isPrivateAccount,
    githubDefaultRepo = githubDefaultRepo,
    notificationTrayLimit = notificationTrayLimit,
)
