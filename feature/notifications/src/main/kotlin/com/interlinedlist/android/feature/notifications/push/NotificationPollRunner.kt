package com.interlinedlist.android.feature.notifications.push

import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.core.network.preferences.NotificationTrayLimitStore
import com.interlinedlist.android.feature.notifications.data.NotificationPreferencesRepository
import com.interlinedlist.android.feature.notifications.data.NotificationsRepository
import javax.inject.Inject

/**
 * Runs one background-poll pass: fetch the latest notifications, resolve the recipient's
 * preferences, decide what is NEW and push-enabled (via the pure [NotificationPollProcessor]),
 * raise the tray notifications, and advance the persisted last-seen marker.
 *
 * The account's `notificationTrayLimit` is what the system tray is sized by: the
 * repository already fetches a page of that size, and this runner passes the same
 * number to the raiser so the shade never shows more separate notifications than the
 * user asked the tray to hold. The value is cached by [NotificationTrayLimitStore],
 * so reading it here costs no second request.
 *
 * Holding this outside the `@HiltWorker` keeps it free of the Android `CoroutineWorker`
 * superclass, so it is fully unit-testable; [NotificationsPollWorker] is a thin adapter
 * that maps the [Result] onto WorkManager's outcome.
 */
class NotificationPollRunner @Inject constructor(
    private val notificationsRepository: NotificationsRepository,
    private val preferencesRepository: NotificationPreferencesRepository,
    private val lastSeenStore: LastSeenNotificationStore,
    private val trayLimitStore: NotificationTrayLimitStore,
    private val raiser: SystemNotificationRaiser,
) {

    /** The outcome of a poll, mapped by the worker onto WorkManager's Result. */
    enum class Result {
        /** Completed (whether or not anything was posted). */
        SUCCESS,

        /** Transient failure (fetch failed) — the worker should retry with backoff. */
        RETRY,
    }

    suspend fun run(): Result {
        val fetched = when (val result = notificationsRepository.fetchLatest()) {
            is ApiResult.Success -> result.data
            is ApiResult.Failure -> return Result.RETRY
        }

        // Preferences are best-effort: on failure, fall back to no preferences, which
        // the push filter interprets as default-notify.
        val preferences = when (val prefs = preferencesRepository.getPreferences()) {
            is ApiResult.Success -> prefs.data
            is ApiResult.Failure -> emptyList()
        }

        val outcome = NotificationPollProcessor.process(
            fetched = fetched,
            preferences = preferences,
            lastSeenId = lastSeenStore.lastSeenId(),
            hasSeenAny = lastSeenStore.hasSeenAny(),
        )

        if (outcome.toPost.isNotEmpty()) {
            raiser.post(outcome.toPost, maxIndividual = trayLimitStore.current())
        }
        // Advance the marker after posting, so a crash before posting doesn't skip items.
        outcome.newLastSeenId?.let(lastSeenStore::setLastSeenId)

        return Result.SUCCESS
    }
}
