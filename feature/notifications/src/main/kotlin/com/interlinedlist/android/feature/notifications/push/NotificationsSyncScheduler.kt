package com.interlinedlist.android.feature.notifications.push

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.time.Duration

/**
 * Schedules the background notification poll. Keep this the single place that enqueues
 * [NotificationsPollWorker] so the unique-work names and constraints stay consistent
 * (mirrors `DocumentsSyncScheduler`).
 *
 * Wiring (call from the app after sign-in):
 * ```
 * NotificationsSyncScheduler.schedulePeriodic(context)   // near-real-time tray poll
 * NotificationsSyncScheduler.syncNow(context)            // e.g. right after login
 * NotificationsSyncScheduler.cancelAll(context)          // on sign-out
 * ```
 */
object NotificationsSyncScheduler {

    /** WorkManager's minimum periodic interval; also our chosen cadence. */
    val MIN_INTERVAL: Duration = Duration.ofMinutes(15)

    private val NETWORK_CONSTRAINTS = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    /**
     * Periodic background poll. WorkManager's floor is 15 minutes, so any shorter
     * [interval] is clamped up to [MIN_INTERVAL].
     */
    fun schedulePeriodic(context: Context, interval: Duration = MIN_INTERVAL) {
        val effective = if (interval < MIN_INTERVAL) MIN_INTERVAL else interval
        val request = PeriodicWorkRequestBuilder<NotificationsPollWorker>(effective)
            .setConstraints(NETWORK_CONSTRAINTS)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, Duration.ofSeconds(30))
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            NotificationsPollWorker.PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    /** One-shot poll now — e.g. immediately after login so the marker seeds fast. */
    fun syncNow(context: Context) {
        val request = OneTimeWorkRequestBuilder<NotificationsPollWorker>()
            .setConstraints(NETWORK_CONSTRAINTS)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, Duration.ofSeconds(15))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            NotificationsPollWorker.ONE_SHOT_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    /** Cancels all scheduled notification polling (e.g. on sign-out). */
    fun cancelAll(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(NotificationsPollWorker.PERIODIC_WORK_NAME)
        WorkManager.getInstance(context).cancelUniqueWork(NotificationsPollWorker.ONE_SHOT_WORK_NAME)
    }
}
