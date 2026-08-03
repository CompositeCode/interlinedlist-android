package com.interlinedlist.android.feature.auth.data

import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.core.model.User

/** Authentication, account-lifecycle, and session operations for the app. */
interface AuthRepository {

    /** Whether a bearer token is already persisted. */
    fun isLoggedIn(): Boolean

    /**
     * Logs in by exchanging credentials for a bearer token, then fetching and
     * caching the authenticated user. Leaves no session behind on failure.
     */
    suspend fun login(email: String, password: String): ApiResult<User>

    /**
     * Creates an account, then signs in exactly the way [login] does (mint a
     * sync-token + fetch the user), so the caller lands in the authed state with
     * no session left behind on failure. The returned [User] carries
     * `emailVerified` so the UI can surface an "unverified email" hint.
     */
    suspend fun register(
        email: String,
        username: String,
        password: String,
        displayName: String?,
    ): ApiResult<User>

    /** Starts a password reset; the server emails a tokenised reset link. */
    suspend fun forgotPassword(email: String): ApiResult<Unit>

    /** Completes a password reset with the emailed token and a new password. */
    suspend fun resetPassword(token: String, newPassword: String): ApiResult<Unit>

    /** Confirms an email address with the token from the verification link. */
    suspend fun verifyEmail(token: String): ApiResult<Unit>

    /** Resends the verification email to the signed-in (unverified) user. */
    suspend fun resendVerificationEmail(): ApiResult<Unit>

    /** Clears the persisted session and cached user. */
    suspend fun logout()
}
