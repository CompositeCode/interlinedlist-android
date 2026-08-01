package com.interlinedlist.android.feature.notifications.data.remote.dto

import com.interlinedlist.android.feature.notifications.domain.Notification
import com.interlinedlist.android.feature.notifications.domain.NotificationActor
import com.interlinedlist.android.feature.notifications.domain.NotificationTarget
import com.interlinedlist.android.feature.notifications.domain.NotificationTargetKind
import com.interlinedlist.android.feature.notifications.domain.NotificationType
import kotlinx.serialization.Serializable

/**
 * Wire model for a notification returned by `GET /api/notifications`.
 *
 * The OpenAPI extract does not pin the response schema, so this is modelled
 * defensively: every field is defaulted and the shared [kotlinx.serialization.json.Json]
 * is configured with `ignoreUnknownKeys`, so extra/renamed fields are tolerated and
 * dropped rather than throwing. The mapper ([toDomain]) is the single point of change
 * if the real shape differs from what we assumed here.
 *
 * Common naming variants are accommodated where cheap:
 * - the actor may arrive as a nested [actor]/[sender]/[fromUser] object;
 * - the read flag may be `read` or `isRead`;
 * - the target may be a nested [target] object or flat `targetType`/`targetId` fields.
 */
@Serializable
data class NotificationDto(
    val id: String = "",
    val type: String? = null,
    val actor: NotificationActorDto? = null,
    /** Alternate key some payloads use for the acting user. */
    val sender: NotificationActorDto? = null,
    /** Alternate key some payloads use for the acting user. */
    val fromUser: NotificationActorDto? = null,
    /** Primary summary line; alternates handled by [subjectText]. */
    val subject: String? = null,
    val title: String? = null,
    val message: String? = null,
    val text: String? = null,
    /** Secondary detail line, when present. */
    val body: String? = null,
    val excerpt: String? = null,
    val createdAt: String? = null,
    val timestamp: String? = null,
    val read: Boolean? = null,
    val isRead: Boolean? = null,
    val readAt: String? = null,
    /** Nested target descriptor, when the server nests it. */
    val target: NotificationTargetDto? = null,
    /** Flat target fields, when the server flattens it. */
    val targetType: String? = null,
    val targetId: String? = null,
)

/** Acting user embedded in a notification. All fields defaulted for defensiveness. */
@Serializable
data class NotificationActorDto(
    val id: String = "",
    val username: String = "",
    val displayName: String? = null,
    val name: String? = null,
    val avatar: String? = null,
    val avatarUrl: String? = null,
)

/** Nested deep-link target descriptor. */
@Serializable
data class NotificationTargetDto(
    val type: String? = null,
    val id: String? = null,
)

/** The best available summary line across the payload's naming variants. */
private val NotificationDto.subjectText: String
    get() = (subject ?: title ?: message ?: text)?.takeIf { it.isNotBlank() }.orEmpty()

/** The best available secondary line across the payload's naming variants. */
private val NotificationDto.bodyText: String?
    get() = (body ?: excerpt)?.takeIf { it.isNotBlank() }

/** The best available creation timestamp across the payload's naming variants. */
private val NotificationDto.createdTimestamp: String?
    get() = (createdAt ?: timestamp)?.takeIf { it.isNotBlank() }

/**
 * Read state, tolerant of the several ways the API may express it: an explicit
 * `read`/`isRead` boolean, or the presence of a `readAt` timestamp. Defaults to
 * unread when nothing is provided.
 */
private val NotificationDto.readState: Boolean
    get() = read ?: isRead ?: (readAt?.isNotBlank() == true)

/** Maps the wire model into the domain [Notification]. Single point of change. */
fun NotificationDto.toDomain(): Notification = Notification(
    id = id,
    type = NotificationType.fromWire(type),
    actor = (actor ?: sender ?: fromUser)?.toDomain(),
    subject = subjectText,
    body = bodyText,
    createdAt = createdTimestamp,
    read = readState,
    target = resolveTarget(),
)

/** Maps an actor sub-object, dropping it entirely when it carries no identity. */
private fun NotificationActorDto.toDomain(): NotificationActor? {
    val actorId = id.takeIf { it.isNotBlank() }
    val handle = username.takeIf { it.isNotBlank() }
    // Without an id or a username there is nothing meaningful to show.
    if (actorId == null && handle == null) return null
    return NotificationActor(
        id = actorId.orEmpty(),
        username = handle.orEmpty(),
        displayName = (displayName ?: name)?.takeIf { it.isNotBlank() },
        avatarUrl = (avatar ?: avatarUrl)?.takeIf { it.isNotBlank() },
    )
}

/** Resolves the deep-link target from either the nested or the flat representation. */
private fun NotificationDto.resolveTarget(): NotificationTarget? {
    val kindRaw = target?.type ?: targetType
    val targetIdentifier = (target?.id ?: targetId)?.takeIf { it.isNotBlank() } ?: return null
    return NotificationTarget(
        kind = targetKindFromWire(kindRaw),
        id = targetIdentifier,
    )
}

/** Maps a free-form target type string to a [NotificationTargetKind]; unknowns -> OTHER. */
private fun targetKindFromWire(raw: String?): NotificationTargetKind {
    val value = raw?.trim()?.lowercase().orEmpty()
    return when {
        value.contains("message") || value.contains("post") -> NotificationTargetKind.MESSAGE
        value.contains("user") || value.contains("profile") -> NotificationTargetKind.USER
        value.contains("list") -> NotificationTargetKind.LIST
        else -> NotificationTargetKind.OTHER
    }
}
