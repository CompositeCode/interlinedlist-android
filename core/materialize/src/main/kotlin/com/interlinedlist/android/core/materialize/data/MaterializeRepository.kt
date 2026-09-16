package com.interlinedlist.android.core.materialize.data

import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.core.materialize.domain.MaterializeOutcome
import com.interlinedlist.android.core.materialize.domain.MaterializeRequest

/**
 * `POST /api/materialize` — the whole "Create from…" surface, in one call.
 *
 * There is nothing to cache: the endpoint is a one-shot write whose result is
 * authoritative, so this repository has no Room database and no offline read.
 */
interface MaterializeRepository {

    /**
     * Runs a confirmed conversion.
     *
     * Enforces the subscriber gate first: when the account is positively known
     * to be free and [request] would create something, this fails with
     * `AppError.SubscriptionRequired` **without issuing the request**, so a free
     * account cannot trigger a write. The menu that leads here is open to
     * everyone; only this confirmation is gated.
     */
    suspend fun materialize(request: MaterializeRequest): ApiResult<MaterializeOutcome>
}
