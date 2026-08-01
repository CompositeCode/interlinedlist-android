package com.interlinedlist.android.feature.auth.data

import com.interlinedlist.android.core.common.dispatcher.DispatcherProvider
import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.core.database.dao.UserDao
import com.interlinedlist.android.core.database.entity.CachedUserEntity
import com.interlinedlist.android.core.datastore.SessionStore
import com.interlinedlist.android.core.model.User
import com.interlinedlist.android.core.network.api.InterlinedListApi
import com.interlinedlist.android.core.network.dto.SyncTokenRequest
import com.interlinedlist.android.core.network.dto.toDomain
import com.interlinedlist.android.core.network.error.safeApiCall
import com.interlinedlist.android.feature.auth.data.remote.AuthApi
import com.interlinedlist.android.feature.auth.data.remote.dto.ForgotPasswordRequest
import com.interlinedlist.android.feature.auth.data.remote.dto.RegisterRequest
import com.interlinedlist.android.feature.auth.data.remote.dto.ResetPasswordRequest
import com.interlinedlist.android.feature.auth.data.remote.dto.VerifyEmailRequest
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import javax.inject.Inject

class DefaultAuthRepository @Inject constructor(
    private val api: InterlinedListApi,
    private val authApi: AuthApi,
    private val sessionStore: SessionStore,
    private val userDao: UserDao,
    private val json: Json,
    private val dispatchers: DispatcherProvider,
) : AuthRepository {

    override fun isLoggedIn(): Boolean = sessionStore.isLoggedIn

    override suspend fun login(email: String, password: String): ApiResult<User> =
        withContext(dispatchers.io) {
            signIn(email, password)
        }

    override suspend fun register(
        email: String,
        username: String,
        password: String,
        displayName: String?,
    ): ApiResult<User> = withContext(dispatchers.io) {
        // 1) Create the account. On failure (e.g. email/username taken, weak
        // password) surface the mapped error without touching the session.
        val created = safeApiCall(json) {
            authApi.register(
                RegisterRequest(
                    email = email,
                    username = username,
                    password = password,
                    displayName = displayName,
                ),
            )
        }
        when (created) {
            is ApiResult.Success -> Unit
            is ApiResult.Failure -> return@withContext created
        }

        // 2) Sign in exactly like login so the caller lands in the authed state.
        signIn(email, password)
    }

    override suspend fun forgotPassword(email: String): ApiResult<Unit> =
        withContext(dispatchers.io) {
            safeApiCall(json) { authApi.forgotPassword(ForgotPasswordRequest(email)) }
        }

    override suspend fun resetPassword(token: String, newPassword: String): ApiResult<Unit> =
        withContext(dispatchers.io) {
            safeApiCall(json) {
                authApi.resetPassword(ResetPasswordRequest(token = token, password = newPassword))
            }
        }

    override suspend fun verifyEmail(token: String): ApiResult<Unit> =
        withContext(dispatchers.io) {
            safeApiCall(json) { authApi.verifyEmail(VerifyEmailRequest(token)) }
        }

    override suspend fun resendVerificationEmail(): ApiResult<Unit> =
        withContext(dispatchers.io) {
            safeApiCall(json) { authApi.sendVerificationEmail() }
        }

    override suspend fun logout() = withContext(dispatchers.io) {
        sessionStore.clear()
        userDao.clear()
    }

    /**
     * Shared sign-in used by both [login] and [register]: exchange credentials
     * for a bearer token, persist it, then fetch and cache the user. Leaves no
     * half-authenticated session behind if the user fetch fails.
     */
    private suspend fun signIn(email: String, password: String): ApiResult<User> {
        val tokenResult = safeApiCall(json) {
            api.createSyncToken(
                SyncTokenRequest(
                    email,
                    password,
                    deviceLabel = "InterlinedList Android · ${android.os.Build.MODEL}",
                ),
            )
        }
        val token = when (tokenResult) {
            is ApiResult.Success -> tokenResult.data.token
            is ApiResult.Failure -> return tokenResult
        }
        sessionStore.saveToken(token)

        return when (val userResult = safeApiCall(json) { api.getCurrentUser().user }) {
            is ApiResult.Success -> {
                val user = userResult.data.toDomain()
                sessionStore.userId = user.id
                userDao.upsert(user.toCacheEntity())
                ApiResult.Success(user)
            }
            is ApiResult.Failure -> {
                // Don't leave a half-authenticated session behind.
                sessionStore.clear()
                userResult
            }
        }
    }
}

private fun User.toCacheEntity() = CachedUserEntity(
    id = id,
    username = username,
    displayName = displayName,
    avatarUrl = avatarUrl,
    bio = bio,
    customerStatus = customerStatus.apiValue,
)
