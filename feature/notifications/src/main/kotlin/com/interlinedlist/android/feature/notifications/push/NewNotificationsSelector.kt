package com.interlinedlist.android.feature.notifications.push

import com.interlinedlist.android.feature.notifications.domain.Notification

/**
 * Pure logic that decides which notifications are NEW relative to the last-seen
 * marker, and what the new marker should be. No Android or IO dependencies, so it
 * is fully unit-testable.
 *
 * Input is the API's newest-first page (index 0 is the most recent). Behaviour:
 * - The newest id in the page becomes the [SelectionResult.newLastSeenId] to persist.
 * - When a [lastSeenId] is present, everything strictly newer than it (i.e. every
 *   item preceding the matching id in the newest-first list) is NEW.
 * - When [lastSeenId] is not found in the page (it fell off the end, or the list was
 *   cleared server-side), we conservatively treat the whole page as NEW rather than
 *   guessing — the cap in the poster keeps this from spamming.
 * - On the very FIRST poll ([hasSeenAny] == false) we surface nothing and simply
 *   record the newest id, so logging in / installing never dumps the entire backlog
 *   into the tray.
 */
object NewNotificationsSelector {

    /**
     * @param notifications the newest-first page from `GET /api/notifications`.
     * @param lastSeenId the previously-recorded newest id (null on first run).
     * @param hasSeenAny whether any marker has ever been recorded.
     */
    fun select(
        notifications: List<Notification>,
        lastSeenId: String?,
        hasSeenAny: Boolean,
    ): SelectionResult {
        if (notifications.isEmpty()) {
            // Nothing to surface; keep the existing marker untouched.
            return SelectionResult(newNotifications = emptyList(), newLastSeenId = lastSeenId)
        }

        val newestId = notifications.first().id

        // First ever poll: adopt the newest id as a baseline, surface nothing.
        if (!hasSeenAny || lastSeenId == null) {
            return SelectionResult(newNotifications = emptyList(), newLastSeenId = newestId)
        }

        // Already up to date.
        if (newestId == lastSeenId) {
            return SelectionResult(newNotifications = emptyList(), newLastSeenId = newestId)
        }

        val markerIndex = notifications.indexOfFirst { it.id == lastSeenId }
        val newItems = if (markerIndex >= 0) {
            notifications.subList(0, markerIndex).toList()
        } else {
            // Marker not in this page — treat the whole page as new (bounded by the cap).
            notifications.toList()
        }

        return SelectionResult(newNotifications = newItems, newLastSeenId = newestId)
    }
}

/** Outcome of [NewNotificationsSelector.select]. */
data class SelectionResult(
    /** Notifications that arrived since the last poll, newest-first. */
    val newNotifications: List<Notification>,
    /** The marker to persist as the new last-seen id (null only when there is nothing to record). */
    val newLastSeenId: String?,
)
