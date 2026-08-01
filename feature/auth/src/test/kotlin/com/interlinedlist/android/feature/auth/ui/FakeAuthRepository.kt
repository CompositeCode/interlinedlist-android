package com.interlinedlist.android.feature.auth.ui

import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.core.model.CustomerStatus
import com.interlinedlist.android.core.model.User
import com.interlinedlist.android.feature.auth.data.AuthRepository

/** Sample verified user handy across the auth ViewModel tests. */
val sampleUser = User(
    id = "1", username = "messenger", displayName = "Messenger",
    email = null, avatarUrl = null, bio = null,
    emailVerified = true, customerStatus = CustomerStatus.SUBSCRIBER,
)

/**
 * Programmable [AuthRepository] test double. Each operation returns its
 * configured result and records that it was invoked (with the arguments the
 * ViewModel forwarded), so tests can assert both navigation and the request.
 */
class FakeAuthRepository(
    var loginResult: ApiResult<User> = ApiResult.Success(sampleUser),
    var registerResult: ApiResult<User> = ApiResult.Success(sampleUser),
    var forgotResult: ApiResult<Unit> = ApiResult.Success(Unit),
    var resetResult: ApiResult<Unit> = ApiResult.Success(Unit),
    var verifyResult: ApiResult<Unit> = ApiResult.Success(Unit),
    var resendResult: ApiResult<Unit> = ApiResult.Success(Unit),
) : AuthRepository {

    var loginCount = 0
    var registerCount = 0
    var lastRegister: RegisterArgs? = null
    var lastForgotEmail: String? = null
    var lastReset: ResetArgs? = null
    var lastVerifyToken: String? = null
    var resendCount = 0

    data class RegisterArgs(
        val email: String,
        val username: String,
        val password: String,
        val displayName: String?,
    )

    data class ResetArgs(val token: String, val newPassword: String)

    override fun isLoggedIn(): Boolean = false

    override suspend fun login(email: String, password: String): ApiResult<User> {
        loginCount++
        return loginResult
    }

    override suspend fun register(
        email: String,
        username: String,
        password: String,
        displayName: String?,
    ): ApiResult<User> {
        registerCount++
        lastRegister = RegisterArgs(email, username, password, displayName)
        return registerResult
    }

    override suspend fun forgotPassword(email: String): ApiResult<Unit> {
        lastForgotEmail = email
        return forgotResult
    }

    override suspend fun resetPassword(token: String, newPassword: String): ApiResult<Unit> {
        lastReset = ResetArgs(token, newPassword)
        return resetResult
    }

    override suspend fun verifyEmail(token: String): ApiResult<Unit> {
        lastVerifyToken = token
        return verifyResult
    }

    override suspend fun resendVerificationEmail(): ApiResult<Unit> {
        resendCount++
        return resendResult
    }

    override suspend fun logout() = Unit
}
