package com.interlinedlist.android.feature.notifications.push

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Background poll that stands in for FCM push (the product ships no Firebase project).
 * A thin `@HiltWorker` adapter: it delegates the actual work to the injectable,
 * unit-tested [NotificationPollRunner] and maps its outcome onto WorkManager's [Result].
 *
 * The `HiltWorkerFactory` supplied by `InterlinedListApplication` (Configuration.Provider)
 * constructs this with its dependencies — no extra bootstrap needed.
 */
@HiltWorker
class NotificationsPollWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val runner: NotificationPollRunner,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = when (runner.run()) {
        NotificationPollRunner.Result.SUCCESS -> Result.success()
        NotificationPollRunner.Result.RETRY -> Result.retry()
    }

    companion object {
        /** Unique names for the scheduled work (see [NotificationsSyncScheduler]). */
        const val PERIODIC_WORK_NAME = "notifications-poll-periodic"
        const val ONE_SHOT_WORK_NAME = "notifications-poll-oneshot"
    }
}
