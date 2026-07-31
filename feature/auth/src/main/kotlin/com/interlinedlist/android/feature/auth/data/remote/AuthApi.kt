package com.interlinedlist.android.feature.auth.data.remote

import com.interlinedlist.android.feature.auth.data.remote.dto.ForgotPasswordRequest
import com.interlinedlist.android.feature.auth.data.remote.dto.RegisterRequest
import com.interlinedlist.android.feature.auth.data.remote.dto.ResetPasswordRequest
import com.interlinedlist.android.feature.auth.data.remote.dto.VerifyEmailRequest
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * Module-local Retrofit description of the pre-auth account-lifecycle endpoints.
 *
 * Built from the shared [retrofit2.Retrofit] singleton (base URL + auth
 * interceptor) so it stays self-contained within `:feature:auth`; the shared
 * `InterlinedListApi` in `:core:network` is intentionally left untouched. These
 * calls need no bearer token — the interceptor simply omits it when no session
 * exists. Every endpoint returns `201` on success (no body) and `400` with the
 * `{ "error": ... }` envelope on validation failure.
 */
interface AuthApi {

    /** Creates a new account. Sign-in still goes through the sync-token exchange. */
    @POST("api/auth/register")
    suspend fun register(@Body body: RegisterRequest)

    /** Starts a password reset; the server emails a tokenised reset link. */
    @POST("api/auth/forgot-password")
    suspend fun forgotPassword(@Body body: ForgotPasswordRequest)

    /** Completes a password reset with the emailed token and a new password. */
    @POST("api/auth/reset-password")
    suspend fun resetPassword(@Body body: ResetPasswordRequest)

    /** Confirms an email address with the token from the verification link. */
    @POST("api/auth/verify-email")
    suspend fun verifyEmail(@Body body: VerifyEmailRequest)

    /** Resends the verification email to the signed-in (unverified) user. */
    @POST("api/auth/send-verification-email")
    suspend fun sendVerificationEmail()
}
