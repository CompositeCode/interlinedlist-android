package com.interlinedlist.android.feature.billing.data

import com.interlinedlist.android.core.common.result.ApiResult

/**
 * Data operations for subscription billing. Both calls mint a short-lived Stripe
 * hosted session on the server and return its URL for the UI to open in a browser.
 * Everything is a live, stateless request — there is no cache — so results come
 * back as an [ApiResult] carrying the URL string.
 */
interface BillingRepository {

    /**
     * Creates a Stripe Checkout session for the given [priceId] (null lets the
     * server pick the default subscription price) and returns its hosted URL.
     */
    suspend fun createCheckoutSession(priceId: String? = null): ApiResult<String>

    /**
     * Creates a Stripe customer-portal session and returns its hosted URL, where
     * the user can manage or cancel an existing subscription.
     */
    suspend fun createPortalSession(): ApiResult<String>
}
