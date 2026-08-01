package com.interlinedlist.android.feature.billing.ui

import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.feature.billing.data.BillingRepository

/**
 * In-memory [BillingRepository] for ViewModel tests. Each operation returns its
 * configured result and records the arguments it was called with, so tests can
 * assert both the emitted effect and that the right request was made.
 */
class FakeBillingRepository : BillingRepository {

    var checkoutResult: ApiResult<String> = ApiResult.Success("https://checkout.example/session")
    var portalResult: ApiResult<String> = ApiResult.Success("https://portal.example/session")

    val requestedPriceIds = mutableListOf<String?>()
    var portalCalls = 0

    override suspend fun createCheckoutSession(priceId: String?): ApiResult<String> {
        requestedPriceIds += priceId
        return checkoutResult
    }

    override suspend fun createPortalSession(): ApiResult<String> {
        portalCalls++
        return portalResult
    }
}
