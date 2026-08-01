package com.interlinedlist.android.feature.billing.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * Body for `POST /api/stripe/create-checkout-session` (OpenAPI: `{ priceId }`).
 *
 * [priceId] selects which Stripe Price the checkout is for. It is optional in the
 * spec — the server falls back to the account's default subscription price when it
 * is omitted — so it is nullable and, with the shared Json's `explicitNulls = false`,
 * simply left out of the request body when null.
 */
@Serializable
data class CreateCheckoutSessionRequest(
    val priceId: String? = null,
)

/**
 * Body for `POST /api/stripe/create-portal-session` (OpenAPI: `{ flow }`).
 *
 * [flow] optionally deep-links the customer portal to a specific flow (e.g.
 * `subscription_cancel`). Omitted when null, landing the user on the portal home.
 */
@Serializable
data class CreatePortalSessionRequest(
    val flow: String? = null,
)

/**
 * Response for both Stripe session endpoints.
 *
 * The OpenAPI spec does not model these response bodies, but Stripe's
 * `checkout.sessions.create` / `billingPortal.sessions.create` return an object
 * carrying a hosted `url`, and the web app redirects the browser to it. This DTO
 * therefore reads [url] first and, to stay resilient to a minor key rename on the
 * backend, falls back to a handful of common aliases via [resolvedUrl]. The shared
 * Json is configured with `ignoreUnknownKeys`, so any extra Stripe fields (id,
 * sessionId, etc.) decode without throwing.
 */
@Serializable
data class StripeSessionResponse(
    val url: String? = null,
    val checkoutUrl: String? = null,
    val portalUrl: String? = null,
    val sessionUrl: String? = null,
) {
    /** The first non-blank URL field, or null if the server sent none. */
    val resolvedUrl: String?
        get() = listOf(url, checkoutUrl, portalUrl, sessionUrl)
            .firstOrNull { !it.isNullOrBlank() }
}
