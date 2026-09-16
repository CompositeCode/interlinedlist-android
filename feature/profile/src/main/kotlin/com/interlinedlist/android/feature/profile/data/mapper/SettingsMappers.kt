package com.interlinedlist.android.feature.profile.data.mapper

import com.interlinedlist.android.feature.profile.data.remote.dto.ProfileUserDto
import com.interlinedlist.android.feature.profile.data.remote.dto.UpdateProfileRequest
import com.interlinedlist.android.feature.profile.domain.LocationUpdate
import com.interlinedlist.android.feature.profile.domain.UserSettings
import com.interlinedlist.android.feature.profile.domain.UserSettingsUpdate
import com.interlinedlist.android.feature.profile.domain.ViewingPreference
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive

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
 *
 * The location is the one field with three states rather than two: absent (left
 * alone), set, or cleared. See [latitudeJson] for how a clear is put on the wire.
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
    latitude = location?.latitudeJson,
    longitude = location?.longitudeJson,
    isPrivateAccount = isPrivateAccount,
    githubDefaultRepo = githubDefaultRepo,
    notificationTrayLimit = notificationTrayLimit,
)

/**
 * The JSON to send for `latitude`: the number when setting a location, an explicit
 * `null` when clearing one. A Kotlin null is never used here — the serializer drops
 * those, which would silently turn a clear into a no-op request.
 *
 * The clear shape is correct but currently unreachable: the live endpoint rejects a
 * null coordinate outright (400 `bad_request`), so no caller builds a
 * [LocationUpdate.Clear]. See that type for the evidence.
 */
private val LocationUpdate.latitudeJson: JsonElement
    get() = when (this) {
        is LocationUpdate.Set -> JsonPrimitive(coordinates.latitude)
        LocationUpdate.Clear -> JsonNull
    }

/** The JSON to send for `longitude`; see [latitudeJson]. */
private val LocationUpdate.longitudeJson: JsonElement
    get() = when (this) {
        is LocationUpdate.Set -> JsonPrimitive(coordinates.longitude)
        LocationUpdate.Clear -> JsonNull
    }
