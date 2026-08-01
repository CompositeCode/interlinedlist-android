package com.interlinedlist.android.feature.notifications.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.interlinedlist.android.feature.notifications.domain.Notification
import com.interlinedlist.android.feature.notifications.domain.NotificationActor
import com.interlinedlist.android.feature.notifications.domain.NotificationTarget
import com.interlinedlist.android.feature.notifications.domain.NotificationTargetKind
import com.interlinedlist.android.feature.notifications.domain.NotificationType

/**
 * Locally cached notification row — this module's own offline-first source of truth
 * for the list screen. Kept flat (actor + target fields inlined) so a single table
 * serves the list without joins. Enum categories are stored by name so the schema
 * survives new enum values (unknown names decode back to the OTHER fallbacks).
 */
@Entity(tableName = "notification")
data class NotificationEntity(
    @PrimaryKey val id: String,
    val type: String,
    val actorId: String?,
    val actorUsername: String?,
    val actorDisplayName: String?,
    val actorAvatarUrl: String?,
    val subject: String,
    val body: String?,
    val createdAt: String?,
    val read: Boolean,
    val targetKind: String?,
    val targetId: String?,
    /** Server-relative ordering position captured at fetch time (list order). */
    val listOrder: Long,
)

fun NotificationEntity.toDomain(): Notification = Notification(
    id = id,
    type = runCatching { NotificationType.valueOf(type) }.getOrDefault(NotificationType.OTHER),
    actor = if (actorId != null || actorUsername != null) {
        NotificationActor(
            id = actorId.orEmpty(),
            username = actorUsername.orEmpty(),
            displayName = actorDisplayName,
            avatarUrl = actorAvatarUrl,
        )
    } else {
        null
    },
    subject = subject,
    body = body,
    createdAt = createdAt,
    read = read,
    target = if (targetId != null) {
        NotificationTarget(
            kind = targetKind
                ?.let { runCatching { NotificationTargetKind.valueOf(it) }.getOrNull() }
                ?: NotificationTargetKind.OTHER,
            id = targetId,
        )
    } else {
        null
    },
)

fun Notification.toEntity(listOrder: Long): NotificationEntity = NotificationEntity(
    id = id,
    type = type.name,
    actorId = actor?.id,
    actorUsername = actor?.username,
    actorDisplayName = actor?.displayName,
    actorAvatarUrl = actor?.avatarUrl,
    subject = subject,
    body = body,
    createdAt = createdAt,
    read = read,
    targetKind = target?.kind?.name,
    targetId = target?.id,
    listOrder = listOrder,
)
