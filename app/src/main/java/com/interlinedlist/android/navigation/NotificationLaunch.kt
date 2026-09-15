package com.interlinedlist.android.navigation

import android.content.Intent
import com.interlinedlist.android.feature.notifications.push.NotificationDeepLink

/**
 * A pending in-app destination derived from a tapped system notification. The
 * notifications module attaches [NotificationDeepLink] extras to the launch intent;
 * this parses them into a concrete nav route (falling back to the notifications feed),
 * so the tap lands the signed-in user on the right screen.
 */
data class NotificationLaunch(val route: String) {

    companion object {

        /**
         * Parses [intent] into a [NotificationLaunch], or null when it did not
         * originate from a notification tap. Unresolvable targets fall back to the
         * in-app notifications feed.
         */
        fun fromIntent(intent: Intent?): NotificationLaunch? {
            if (intent?.getBooleanExtra(NotificationDeepLink.EXTRA_FROM_NOTIFICATION, false) != true) {
                return null
            }
            val destination = runCatching {
                NotificationDeepLink.Destination.valueOf(
                    intent.getStringExtra(NotificationDeepLink.EXTRA_DESTINATION).orEmpty(),
                )
            }.getOrDefault(NotificationDeepLink.Destination.NOTIFICATIONS)

            val targetId = intent.getStringExtra(NotificationDeepLink.EXTRA_TARGET_ID)
            return NotificationLaunch(routeFor(destination, targetId))
        }

        private fun routeFor(
            destination: NotificationDeepLink.Destination,
            targetId: String?,
        ): String = when (destination) {
            NotificationDeepLink.Destination.MESSAGE ->
                targetId?.let { Routes.messageDetail(it) } ?: Routes.NOTIFICATIONS
            NotificationDeepLink.Destination.USER ->
                targetId?.let { Routes.userProfile(it) } ?: Routes.NOTIFICATIONS
            NotificationDeepLink.Destination.LIST ->
                targetId?.let { Routes.listDetail(it) } ?: Routes.NOTIFICATIONS
            NotificationDeepLink.Destination.NOTIFICATIONS -> Routes.NOTIFICATIONS
        }
    }
}
