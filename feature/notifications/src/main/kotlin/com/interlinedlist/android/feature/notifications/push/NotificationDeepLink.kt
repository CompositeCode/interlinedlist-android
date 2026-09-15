package com.interlinedlist.android.feature.notifications.push

import com.interlinedlist.android.feature.notifications.domain.Notification
import com.interlinedlist.android.feature.notifications.domain.NotificationTargetKind

/**
 * The intent extras carried by a tapped system notification, and the pure logic that
 * turns a [Notification]'s target into a destination the app can route to.
 *
 * The worker attaches these extras to the [android.app.PendingIntent] that opens
 * [MainActivity]; the app reads them and navigates. Keeping the keys and the
 * type→destination mapping here means the module owns the contract and it stays
 * unit-testable without any Android UI.
 */
object NotificationDeepLink {

    /** Extra flagging that the launch originated from a notification tap. */
    const val EXTRA_FROM_NOTIFICATION = "il.extra.from_notification"

    /** Extra naming the destination the app should route to (see [Destination]). */
    const val EXTRA_DESTINATION = "il.extra.notification_destination"

    /** Extra carrying the target entity id, when the destination needs one. */
    const val EXTRA_TARGET_ID = "il.extra.notification_target_id"

    /**
     * Where a tapped notification should land. The app maps these to concrete nav
     * routes; anything without a resolvable target falls back to [NOTIFICATIONS].
     */
    enum class Destination {
        /** Open a specific message thread ([EXTRA_TARGET_ID] = message id). */
        MESSAGE,

        /** Open a user's profile ([EXTRA_TARGET_ID] = username/id). */
        USER,

        /** Open a specific list ([EXTRA_TARGET_ID] = list id). */
        LIST,

        /** Open the in-app notifications feed (the safe fallback). */
        NOTIFICATIONS,
    }

    /**
     * Resolves the destination for [notification] from its target. Falls back to
     * [Destination.NOTIFICATIONS] when there is no usable, specific target.
     */
    fun destinationFor(notification: Notification): Destination {
        val target = notification.target ?: return Destination.NOTIFICATIONS
        if (target.id.isBlank()) return Destination.NOTIFICATIONS
        return when (target.kind) {
            NotificationTargetKind.MESSAGE -> Destination.MESSAGE
            NotificationTargetKind.USER -> Destination.USER
            NotificationTargetKind.LIST -> Destination.LIST
            NotificationTargetKind.OTHER -> Destination.NOTIFICATIONS
        }
    }

    /** The target id to carry for [notification], or null when routing to the feed. */
    fun targetIdFor(notification: Notification): String? =
        when (destinationFor(notification)) {
            Destination.NOTIFICATIONS -> null
            else -> notification.target?.id?.takeIf { it.isNotBlank() }
        }
}
