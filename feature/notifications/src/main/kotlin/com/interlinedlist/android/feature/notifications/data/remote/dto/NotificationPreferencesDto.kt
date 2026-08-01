package com.interlinedlist.android.feature.notifications.data.remote.dto

import com.interlinedlist.android.feature.notifications.domain.NotificationChannel
import com.interlinedlist.android.feature.notifications.domain.NotificationPreference
import kotlinx.serialization.Serializable

/**
 * Wire envelope for `GET /api/user/notification-preferences`:
 * `{ "events": [ { key, label, description, channels: { push, inApp, email } }, ... ] }`
 * (verified live 2026-07-31).
 *
 * Everything is defaulted and the shared [kotlinx.serialization.json.Json] is
 * configured with `ignoreUnknownKeys`, so extra/renamed fields decode cleanly rather
 * than throwing. [toDomain] is the single point of change if the shape shifts.
 */
@Serializable
data class NotificationPreferencesResponse(
    val events: List<NotificationPreferenceDto> = emptyList(),
)

/**
 * One preference event. CRITICAL: the [channels] map carries only the channel keys
 * this event actually supports — the SET varies per event — so it is modelled as an
 * open map rather than a fixed push/inApp/email triple. Unknown channel keys are
 * dropped by [toDomain].
 */
@Serializable
data class NotificationPreferenceDto(
    val key: String = "",
    val label: String? = null,
    val description: String? = null,
    /** Present channels only, each mapped to its current on/off state. */
    val channels: Map<String, Boolean> = emptyMap(),
)

/**
 * Body for `PATCH /api/user/notification-preferences` (OpenAPI: `{ key, channels }`).
 * Sends the single event being changed plus its full channel map, so the server
 * applies exactly the toggled state.
 */
@Serializable
data class NotificationPreferenceUpdateDto(
    val key: String,
    val channels: Map<String, Boolean>,
)

/** Maps the wire event into the domain [NotificationPreference], dropping unknown channels. */
fun NotificationPreferenceDto.toDomain(): NotificationPreference = NotificationPreference(
    key = key,
    label = label?.takeIf { it.isNotBlank() } ?: key,
    description = description.orEmpty(),
    channels = channels.mapNotNull { (rawKey, enabled) ->
        NotificationChannel.fromWire(rawKey)?.let { it to enabled }
    }.toMap(),
)

/** Maps the whole response into an ordered list of domain preferences. */
fun NotificationPreferencesResponse.toDomain(): List<NotificationPreference> =
    events.map { it.toDomain() }

/** Builds the PATCH body for a single toggled [NotificationPreference]. */
fun NotificationPreference.toUpdateDto(): NotificationPreferenceUpdateDto =
    NotificationPreferenceUpdateDto(
        key = key,
        channels = channels.entries.associate { (channel, enabled) -> channel.wireKey to enabled },
    )
