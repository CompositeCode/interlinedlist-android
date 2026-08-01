package com.interlinedlist.android.feature.billing.data

import com.interlinedlist.android.core.common.dispatcher.DispatcherProvider
import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.core.common.result.AppError
import com.interlinedlist.android.core.network.error.safeApiCall
import com.interlinedlist.android.feature.billing.data.remote.BillingApi
import com.interlinedlist.android.feature.billing.data.remote.dto.CreateCheckoutSessionRequest
import com.interlinedlist.android.feature.billing.data.remote.dto.CreatePortalSessionRequest
import com.interlinedlist.android.feature.billing.data.remote.dto.StripeSessionResponse
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import javax.inject.Inject

class DefaultBillingRepository @Inject constructor(
    private val api: BillingApi,
    private val json: Json,
    private val dispatchers: DispatcherProvider,
) : BillingRepository {

    override suspend fun createCheckoutSession(priceId: String?): ApiResult<String> =
        withContext(dispatchers.io) {
            safeApiCall(json) { api.createCheckoutSession(CreateCheckoutSessionRequest(priceId)) }
                .requireUrl()
        }

    override suspend fun createPortalSession(): ApiResult<String> =
        withContext(dispatchers.io) {
            safeApiCall(json) { api.createPortalSession(CreatePortalSessionRequest()) }
                .requireUrl()
        }

    /**
     * A 2xx with no URL is a contract violation, not a success — the UI has nothing
     * to open — so it is folded into a failure the error mapper can render, rather
     * than surfaced as an empty string.
     */
    private fun ApiResult<StripeSessionResponse>.requireUrl(): ApiResult<String> = when (this) {
        is ApiResult.Success -> data.resolvedUrl
            ?.let { ApiResult.Success(it) }
            ?: ApiResult.Failure(AppError.Server("The billing session did not return a URL."))
        is ApiResult.Failure -> this
    }
}
