package com.interlinedlist.android.feature.notifications.push

import com.interlinedlist.android.feature.notifications.domain.Notification
import com.interlinedlist.android.feature.notifications.domain.NotificationChannel
import com.interlinedlist.android.feature.notifications.domain.NotificationPreference

/**
 * Pure decision logic that gates which notifications may be raised as a system
 * (push-tray) notification, based on the recipient's per-event `push` preference.
 * No Android or IO dependencies, so it is fully unit-testable.
 *
 * Mapping is best-effort: a notification's [NotificationType][com.interlinedlist.android.feature.notifications.domain.NotificationType]
 * is mapped to a [NotificationCategory], whose candidate [NotificationCategory.preferenceKeys]
 * are matched (case-insensitively) against the fetched preference event keys. The first
 * matching preference's [NotificationChannel.PUSH] state decides delivery.
 *
 * DEFAULT-NOTIFY: when no preference maps to the notification (unknown/unmodelled
 * event, or preferences could not be fetched), we err on the side of notifying — a
 * missed alert is worse than an unexpected one, and the recipient can still mute the
 * channel from system settings.
 */
class NotificationPushFilter(
    preferences: List<NotificationPreference>,
) {

    /** Preferences indexed by their lower-cased key for O(1), case-insensitive lookup. */
    private val byKey: Map<String, NotificationPreference> =
        preferences.associateBy { it.key.trim().lowercase() }

    /**
     * Whether [notification] should be raised in the system tray. True when the mapped
     * `push` preference is enabled, or when nothing maps (default-notify). False only
     * when a matching preference explicitly disables `push`.
     */
    fun shouldNotify(notification: Notification): Boolean {
        val preference = matchPreference(notification) ?: return true // default-notify
        // If the event doesn't model a push channel at all, treat it as allowed.
        if (NotificationChannel.PUSH !in preference.channels) return true
        return preference.isEnabled(NotificationChannel.PUSH)
    }

    /** Keeps only the notifications whose `push` channel is enabled (or unmapped). */
    fun filter(notifications: List<Notification>): List<Notification> =
        notifications.filter(::shouldNotify)

    private fun matchPreference(notification: Notification): NotificationPreference? {
        val category = NotificationCategory.forType(notification.type)
        for (candidate in category.preferenceKeys) {
            byKey[candidate.lowercase()]?.let { return it }
        }
        return null
    }
}
