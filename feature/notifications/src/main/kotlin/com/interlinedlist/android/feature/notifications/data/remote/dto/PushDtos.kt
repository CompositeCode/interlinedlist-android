package com.interlinedlist.android.feature.notifications.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * Body for `POST /api/push/register`, per the published contract
 * (https://interlinedlist.com/help/api/push-notifications):
 *
 * ```
 * { "token": "<base64-or-hex device token>", "platform": "android", "environment": "production" }
 * ```
 *
 * [platform] must be exactly `ios` or `android`; [environment] is optional
 * (`sandbox` / `production`, inferred server-side when omitted) but we always send it
 * so a debug install can never have its token treated as a production device.
 * Re-registering an existing token updates the record rather than duplicating it.
 */
@Serializable
data class PushRegistrationRequest(
    val token: String,
    val platform: String,
    val environment: String,
)

/**
 * Response for `POST /api/push/register`: `{ "registered": true }`. Defaulted and
 * decoded with the shared `ignoreUnknownKeys` Json, so an empty or extended body
 * still parses; the HTTP status is what decides success.
 */
@Serializable
data class PushRegistrationResponse(
    val registered: Boolean = true,
)

/**
 * Body for `DELETE /api/push/unregister`: `{ "token": … }`. The token travels in the
 * REQUEST BODY (not a query parameter), and the call is idempotent — unregistering an
 * unknown token still returns 200.
 */
@Serializable
data class PushUnregisterRequest(
    val token: String,
)
