package com.interlinedlist.android.feature.billing.data.remote

import com.interlinedlist.android.feature.billing.data.remote.dto.CreateCheckoutSessionRequest
import com.interlinedlist.android.feature.billing.data.remote.dto.CreatePortalSessionRequest
import com.interlinedlist.android.feature.billing.data.remote.dto.StripeSessionResponse
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * Retrofit description of the Stripe billing endpoints. Provided from the shared,
 * already-authenticated [retrofit2.Retrofit] (base URL + Bearer interceptor), so
 * both calls are authed.
 *
 * Each endpoint mints a short-lived Stripe hosted session and returns its URL; the
 * client opens that URL in a browser. No live sessions are created in tests — the
 * repository is exercised against MockWebServer only.
 */
interface BillingApi {

    /** Creates a Stripe Checkout session and returns its hosted URL. */
    @POST("api/stripe/create-checkout-session")
    suspend fun createCheckoutSession(
        @Body body: CreateCheckoutSessionRequest,
    ): StripeSessionResponse

    /** Creates a Stripe customer-portal session and returns its hosted URL. */
    @POST("api/stripe/create-portal-session")
    suspend fun createPortalSession(
        @Body body: CreatePortalSessionRequest,
    ): StripeSessionResponse
}
