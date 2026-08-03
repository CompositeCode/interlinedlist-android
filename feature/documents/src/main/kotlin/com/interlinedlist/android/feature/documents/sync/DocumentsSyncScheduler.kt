package com.interlinedlist.android.feature.documents.sync

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
 * Schedules the documents delta-sync work. Keep this the single place that enqueues
 * [DocumentsSyncWorker] so the unique-work names and constraints stay consistent.
 *
 * Wiring (call from the app, e.g. after sign-in / on app start):
 * ```
 * DocumentsSyncScheduler.schedulePeriodic(context)   // hourly background pull+push
 * DocumentsSyncScheduler.syncNow(context)            // e.g. on foreground / after a save
 * ```
 */
object DocumentsSyncScheduler {

    private val NETWORK_CONSTRAINTS = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    /** Periodic background sync (min WorkManager interval is 15 minutes; we use 1 hour). */
    fun schedulePeriodic(context: Context, interval: Duration = Duration.ofHours(1)) {
        val request = PeriodicWorkRequestBuilder<DocumentsSyncWorker>(interval)
            .setConstraints(NETWORK_CONSTRAINTS)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, Duration.ofSeconds(30))
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            DocumentsSyncWorker.PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    /** One-shot sync now — e.g. after a save, or when the documents area is opened. */
    fun syncNow(context: Context) {
        val request = OneTimeWorkRequestBuilder<DocumentsSyncWorker>()
            .setConstraints(NETWORK_CONSTRAINTS)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, Duration.ofSeconds(15))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            DocumentsSyncWorker.ONE_SHOT_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    /** Cancels all scheduled documents sync (e.g. on sign-out). */
    fun cancelAll(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(DocumentsSyncWorker.PERIODIC_WORK_NAME)
        WorkManager.getInstance(context).cancelUniqueWork(DocumentsSyncWorker.ONE_SHOT_WORK_NAME)
    }
}
