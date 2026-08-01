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
