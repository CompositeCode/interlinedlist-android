package com.interlinedlist.android.feature.auth.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * Request bodies for the pre-auth account-lifecycle endpoints under the
 * `api/auth/` path prefix.
 *
 * Field names mirror the InterlinedList OpenAPI component schemas exactly. The
 * server does not model response bodies for these endpoints in the spec — they
 * return `201` on success and `400` (with the standard `{ "error": ... }`
 * envelope) on failure — so we only need typed request payloads here; success
 * is signalled purely by the HTTP status via `safeApiCall`.
 */

/** `POST /api/auth/register` — creates an account. `displayName` is optional. */
@Serializable
data class RegisterRequest(
    val email: String,
    val username: String,
    val password: String,
    val displayName: String? = null,
)

/** `POST /api/auth/forgot-password` — starts a password reset (emails a token link). */
@Serializable
data class ForgotPasswordRequest(
    val email: String,
)

/** `POST /api/auth/reset-password` — completes a reset with the emailed token. */
@Serializable
data class ResetPasswordRequest(
    val token: String,
    val password: String,
)

/** `POST /api/auth/verify-email` — confirms an email address with the emailed token. */
@Serializable
data class VerifyEmailRequest(
    val token: String,
)

/**
 * `POST /api/auth/verify-email-change` — confirms a pending email change with the
 * token from the message sent to the *new* address. Unauthenticated
 * (`x-auth-type: none` in the OpenAPI spec), so the tap works from a signed-out app.
 */
@Serializable
data class VerifyEmailChangeRequest(
    val token: String,
)

/**
 * `POST /api/auth/undo-email-change` — reverts an email change using the token from
 * the message sent to the *previous* address. Also unauthenticated by design: the
 * whole point is that somebody who has lost access to the account can still undo it.
 */
@Serializable
data class UndoEmailChangeRequest(
    val token: String,
)
