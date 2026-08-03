package com.interlinedlist.android.feature.notifications.push

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.interlinedlist.android.feature.notifications.R
import com.interlinedlist.android.feature.notifications.domain.Notification

/**
 * Raises system-tray notifications for a batch of new notifications. Abstracted behind
 * an interface (DIP) so the worker can be unit-tested with a fake, and the real Android
 * side-effects live only in [SystemNotificationPoster].
 */
interface SystemNotificationRaiser {
    /** Raises tray notifications for [items] (newest-first, already push-filtered). */
    fun post(items: List<Notification>)
}

/**
 * Posts system-tray notifications for a batch of NEW notifications. The batch is
 * capped so a large backlog can't spam the tray: at most [MAX_INDIVIDUAL] individual
 * items are shown, and when there are more, only a single summary is posted.
 *
 * Each notification's tap opens the app's launcher activity (resolved via the package
 * manager, so this module needs no compile-time reference to `MainActivity`) carrying
 * the [NotificationDeepLink] extras the app reads to route.
 *
 * Grouping: individual items share a [GROUP_KEY] and a summary notification anchors the
 * group so the shade collapses them tidily on Android N+.
 */
class SystemNotificationPoster(
    private val context: Context,
) : SystemNotificationRaiser {

    /**
     * Posts [items] (newest-first, already push-filtered). No-op when the list is empty
     * or the user has notifications disabled at the OS level.
     */
    override fun post(items: List<Notification>) {
        if (items.isEmpty()) return
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return

        if (items.size > MAX_INDIVIDUAL) {
            postSummaryOnly(manager, items)
            return
        }

        items.forEach { notification ->
            manager.safeNotify(notification.id.hashCode(), buildIndividual(notification))
        }
        if (items.size > 1) {
            manager.safeNotify(SUMMARY_ID, buildGroupSummary(items))
        }
    }

    private fun postSummaryOnly(manager: NotificationManagerCompat, items: List<Notification>) {
        val category = NotificationCategory.OTHER
        val summary = NotificationCompat.Builder(context, category.channelId)
            .setSmallIcon(R.drawable.il_notification_icon)
            .setContentTitle("$COUNT_TITLE_PREFIX ${items.size} new notifications")
            .setContentText(items.first().subject)
            .setAutoCancel(true)
            .setContentIntent(feedPendingIntent())
            .build()
        manager.safeNotify(SUMMARY_ID, summary)
    }

    private fun buildIndividual(notification: Notification): android.app.Notification {
        val category = NotificationCategory.forType(notification.type)
        val title = notification.actorLabel?.let { "$it" } ?: category.channelName
        return NotificationCompat.Builder(context, category.channelId)
            .setSmallIcon(R.drawable.il_notification_icon)
            .setContentTitle(notification.subject.ifBlank { title })
            .apply { notification.body?.let { setContentText(it) } }
            .setAutoCancel(true)
            .setGroup(GROUP_KEY)
            .setContentIntent(pendingIntentFor(notification))
            .build()
    }

    private fun buildGroupSummary(items: List<Notification>): android.app.Notification =
        NotificationCompat.Builder(context, NotificationCategory.OTHER.channelId)
            .setSmallIcon(R.drawable.il_notification_icon)
            .setContentTitle("$COUNT_TITLE_PREFIX ${items.size} new notifications")
            .setContentText(items.first().subject)
            .setGroup(GROUP_KEY)
            .setGroupSummary(true)
            .setAutoCancel(true)
            .setContentIntent(feedPendingIntent())
            .build()

    // --- intents -----------------------------------------------------------

    /** Launch intent → the app, tagged so the app routes to the specific target. */
    private fun pendingIntentFor(notification: Notification): PendingIntent {
        val destination = NotificationDeepLink.destinationFor(notification)
        val intent = launchIntent().apply {
            putExtra(NotificationDeepLink.EXTRA_FROM_NOTIFICATION, true)
            putExtra(NotificationDeepLink.EXTRA_DESTINATION, destination.name)
            NotificationDeepLink.targetIdFor(notification)?.let {
                putExtra(NotificationDeepLink.EXTRA_TARGET_ID, it)
            }
        }
        return activityPendingIntent(notification.id.hashCode(), intent)
    }

    /** Launch intent → the app, routed to the in-app notifications feed (fallback). */
    private fun feedPendingIntent(): PendingIntent {
        val intent = launchIntent().apply {
            putExtra(NotificationDeepLink.EXTRA_FROM_NOTIFICATION, true)
            putExtra(
                NotificationDeepLink.EXTRA_DESTINATION,
                NotificationDeepLink.Destination.NOTIFICATIONS.name,
            )
        }
        return activityPendingIntent(SUMMARY_ID, intent)
    }

    private fun launchIntent(): Intent =
        context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?.apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP }
            ?: Intent(Intent.ACTION_MAIN).apply { setPackage(context.packageName) }

    private fun activityPendingIntent(requestCode: Int, intent: Intent): PendingIntent =
        PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun NotificationManagerCompat.safeNotify(id: Int, notification: android.app.Notification) {
        // areNotificationsEnabled() is checked up front; guard the individual post too
        // so a revoked POST_NOTIFICATIONS permission never throws on the worker thread.
        runCatching { notify(id, notification) }
    }

    companion object {
        /** Max individual notifications before collapsing to a single summary. */
        const val MAX_INDIVIDUAL = 5

        /** Shared group key so the shade collapses our notifications together. */
        const val GROUP_KEY = "il.notifications.group"

        /** Stable id for the group-summary / overflow notification. */
        const val SUMMARY_ID = 424242

        private const val COUNT_TITLE_PREFIX = "InterlinedList:"
    }
}
