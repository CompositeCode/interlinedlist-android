package com.interlinedlist.android.feature.notifications.push

import android.content.Context
import androidx.core.app.NotificationManagerCompat

/**
 * Whether this app may actually show a notification right now. Abstracted (DIP) so the
 * registration lifecycle is unit-testable without an Android runtime.
 */
interface NotificationPermissionChecker {

    /** True when notifications can be posted (POST_NOTIFICATIONS held and not muted). */
    fun canPostNotifications(): Boolean
}

/**
 * Real check, delegating to [NotificationManagerCompat.areNotificationsEnabled] — the
 * same gate [SystemNotificationPoster] already applies before raising a tray
 * notification from the background poll. Using one signal for both keeps "we told the
 * server to push to this device" and "this device can display a push" in step: it
 * covers the Android 13+ runtime permission AND the user switching notifications off
 * in system settings afterwards.
 */
class SystemNotificationPermissionChecker(
    private val context: Context,
) : NotificationPermissionChecker {

    override fun canPostNotifications(): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled()
}
