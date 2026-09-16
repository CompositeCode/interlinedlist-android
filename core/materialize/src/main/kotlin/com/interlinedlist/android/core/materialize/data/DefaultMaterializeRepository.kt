package com.interlinedlist.android.core.materialize.data

import com.interlinedlist.android.core.common.dispatcher.DispatcherProvider
import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.core.common.result.AppError
import com.interlinedlist.android.core.materialize.data.mapper.toDto
import com.interlinedlist.android.core.materialize.data.mapper.toOutcomeOrNull
import com.interlinedlist.android.core.materialize.data.remote.MaterializeApi
import com.interlinedlist.android.core.materialize.domain.MaterializeGate
import com.interlinedlist.android.core.materialize.domain.MaterializeOutcome
import com.interlinedlist.android.core.materialize.domain.MaterializeRequest
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import javax.inject.Inject

class DefaultMaterializeRepository @Inject constructor(
    private val api: MaterializeApi,
    private val gate: MaterializeGate,
    private val json: Json,
    private val dispatchers: DispatcherProvider,
) : MaterializeRepository {

    override suspend fun materialize(
        request: MaterializeRequest,
    ): ApiResult<MaterializeOutcome> = withContext(dispatchers.io) {
        // The gate is the client-side half of the subscriber check and runs
        // before anything is sent. It only blocks a target that actually
        // creates, and only when the account is positively known to be free:
        // an unread status falls through to the server, which is the real gate.
        if (request.target.createsContent && gate.ensureResolved().isKnownFree) {
            return@withContext ApiResult.Failure(
                AppError.SubscriptionRequired(SUBSCRIPTION_MESSAGE),
            )
        }

        when (val result = materializeApiCall(json) { api.materialize(request.toDto()) }) {
            is ApiResult.Failure -> {
                // The server just told us this account cannot create; remember it
                // so the next confirm short-circuits to the upsell.
                if (result.error is AppError.SubscriptionRequired) gate.recordSubscriptionRequired()
                result
            }

            is ApiResult.Success -> result.data.toOutcomeOrNull(request)
                ?.let { ApiResult.Success(it) }
                // A 201 that did not carry what the target promised.
                ?: ApiResult.Failure(AppError.Server(EMPTY_RESULT_MESSAGE))
        }
    }

    private companion object {
        const val SUBSCRIPTION_MESSAGE =
            "Creating lists and documents requires an active subscription."
        const val EMPTY_RESULT_MESSAGE =
            "InterlinedList did not return what it created. Check your lists and documents."
    }
}
