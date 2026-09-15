package com.interlinedlist.android.feature.notifications.push

import com.interlinedlist.android.feature.notifications.domain.Notification
import com.interlinedlist.android.feature.notifications.domain.NotificationPreference

/**
 * Pure orchestration of one poll pass, with no Android/IO dependencies so it is fully
 * unit-testable. Given the freshly-fetched notifications, the current preferences, and
 * the persisted last-seen state, it decides:
 * - which notifications must be raised in the tray (NEW since last-seen AND push-enabled), and
 * - the marker to persist next.
 *
 * The worker feeds it inputs it gathered over the network / store and applies its
 * [Outcome] (post + persist). This keeps the "what to do" logic separate from the
 * "how to do it" side-effects (SRP).
 */
object NotificationPollProcessor {

    /**
     * @param fetched newest-first page from `GET /api/notifications`.
     * @param preferences current per-event preferences (empty if the fetch failed).
     * @param lastSeenId the previously-recorded newest id (null on first run).
     * @param hasSeenAny whether any marker has ever been recorded.
     */
    fun process(
        fetched: List<Notification>,
        preferences: List<NotificationPreference>,
        lastSeenId: String?,
        hasSeenAny: Boolean,
    ): Outcome {
        val selection = NewNotificationsSelector.select(
            notifications = fetched,
            lastSeenId = lastSeenId,
            hasSeenAny = hasSeenAny,
        )
        val toPost = NotificationPushFilter(preferences).filter(selection.newNotifications)
        return Outcome(
            toPost = toPost,
            newLastSeenId = selection.newLastSeenId,
        )
    }

    /** The actions the worker should apply after a poll pass. */
    data class Outcome(
        /** Notifications to raise in the system tray (newest-first). */
        val toPost: List<Notification>,
        /** The marker to persist, or null when there is nothing to record. */
        val newLastSeenId: String?,
    )
}
