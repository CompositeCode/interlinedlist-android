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
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import javax.inject.Inject

class DefaultAuthRepository @Inject constructor(
    private val api: InterlinedListApi,
    private val sessionStore: SessionStore,
    private val userDao: UserDao,
    private val json: Json,
    private val dispatchers: DispatcherProvider,
) : AuthRepository {

    override fun isLoggedIn(): Boolean = sessionStore.isLoggedIn

    override suspend fun login(email: String, password: String): ApiResult<User> =
        withContext(dispatchers.io) {
            // 1) Exchange credentials for a bearer token.
            val tokenResult = safeApiCall(json) {
                api.createSyncToken(SyncTokenRequest(email, password))
            }
            val token = when (tokenResult) {
                is ApiResult.Success -> tokenResult.data.token
                is ApiResult.Failure -> return@withContext tokenResult
            }
            sessionStore.saveToken(token)

            // 2) Fetch the authenticated user using the new token.
            when (val userResult = safeApiCall(json) { api.getCurrentUser().user }) {
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

    override suspend fun logout() = withContext(dispatchers.io) {
        sessionStore.clear()
        userDao.clear()
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
