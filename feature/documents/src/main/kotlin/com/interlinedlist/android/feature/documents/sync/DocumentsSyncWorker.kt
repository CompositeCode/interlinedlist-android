package com.interlinedlist.android.feature.documents.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.feature.documents.data.DocumentsRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Background delta-sync for documents. On each run it first pushes any queued local
 * edits (`POST /sync`) so offline changes reach the server, then pulls remote deltas
 * (`GET /sync`) and reconciles them into the module's Room cache. Transient failures
 * ask WorkManager to [retry] with its backoff policy; the reconciliation itself is
 * idempotent (upsert by id/version, tombstones remove), so a re-run is safe.
 */
@HiltWorker
class DocumentsSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val repository: DocumentsRepository,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        // Push first so a stale pull never overwrites a not-yet-sent local edit.
        val push = repository.pushPendingOps()
        if (push is ApiResult.Failure) return Result.retry()

        return when (repository.pullDelta()) {
            is ApiResult.Success -> Result.success()
            is ApiResult.Failure -> Result.retry()
        }
    }

    companion object {
        /** Unique names for the scheduled work (see [DocumentsSyncScheduler]). */
        const val PERIODIC_WORK_NAME = "documents-delta-sync-periodic"
        const val ONE_SHOT_WORK_NAME = "documents-delta-sync-oneshot"
    }
}
