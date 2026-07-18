package com.interlinedlist.android.feature.profile.data

import com.interlinedlist.android.core.common.dispatcher.DispatcherProvider
import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.core.common.result.AppError
import com.interlinedlist.android.core.common.result.map
import com.interlinedlist.android.core.network.error.safeApiCall
import com.interlinedlist.android.feature.profile.data.local.ProfileDao
import com.interlinedlist.android.feature.profile.data.local.toDomain
import com.interlinedlist.android.feature.profile.data.local.toEntity
import com.interlinedlist.android.feature.profile.data.mapper.toProfileUser
import com.interlinedlist.android.feature.profile.data.mapper.toSearchResult
import com.interlinedlist.android.feature.profile.data.remote.ProfileApi
import com.interlinedlist.android.feature.profile.data.remote.dto.AvatarFromUrlRequest
import com.interlinedlist.android.feature.profile.data.remote.dto.ProfileUserDto
import com.interlinedlist.android.feature.profile.data.remote.dto.UpdateProfileRequest
import com.interlinedlist.android.feature.profile.domain.ProfileUser
import com.interlinedlist.android.feature.profile.domain.UserSearchResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject

/**
 * Room-backed, offline-first implementation. Reads observe Room; refreshes and
 * mutations call the API and write through to Room so the UI updates reactively.
 */
class DefaultProfileRepository @Inject constructor(
    private val api: ProfileApi,
    private val profileDao: ProfileDao,
    private val json: Json,
    private val dispatchers: DispatcherProvider,
) : ProfileRepository {

    override fun observeCurrentUser(): Flow<ProfileUser?> =
        profileDao.observeCurrentUser().map { it?.toDomain() }

    override fun observeUser(username: String): Flow<ProfileUser?> =
        profileDao.observeByUsername(username).map { it?.toDomain() }

    override suspend fun refreshCurrentUser(): ApiResult<ProfileUser> =
        withContext(dispatchers.io) {
            when (val result = safeApiCall(json) { api.getCurrentUser().userOrSelf }) {
                is ApiResult.Success -> {
                    val dto = result.data
                        ?: return@withContext ApiResult.Failure(AppError.Unknown("No user in response"))
                    ApiResult.Success(cacheCurrentUser(dto))
                }
                is ApiResult.Failure -> result
            }
        }

    override suspend fun refreshUser(username: String): ApiResult<ProfileUser> =
        withContext(dispatchers.io) {
            when (val result = safeApiCall(json) { api.getUserByUsername(username).userOrSelf }) {
                is ApiResult.Success -> {
                    val dto = result.data
                        ?: return@withContext ApiResult.Failure(AppError.NotFound("User not found"))
                    // A viewed user is never flagged as the current user, so the account
                    // tab keeps observing "my" row rather than the last one viewed.
                    val domain = dto.toProfileUser(isCurrentUser = false)
                    profileDao.upsert(domain.toEntity())
                    ApiResult.Success(domain)
                }
                is ApiResult.Failure -> result
            }
        }

    override suspend fun updateProfile(
        displayName: String,
        bio: String,
    ): ApiResult<ProfileUser> = withContext(dispatchers.io) {
        val result = safeApiCall(json) {
            api.updateProfile(
                UpdateProfileRequest(displayName = displayName, bio = bio),
            ).userOrSelf
        }
        when (result) {
            is ApiResult.Success -> {
                // The server may echo a thin body; fall back to a re-fetch so the
                // cache always ends up with the authoritative, complete profile.
                val dto = result.data
                if (dto != null) {
                    ApiResult.Success(cacheCurrentUser(dto))
                } else {
                    refreshCurrentUser()
                }
            }
            is ApiResult.Failure -> result
        }
    }

    override suspend fun setAvatarFromUrl(url: String): ApiResult<ProfileUser> =
        withContext(dispatchers.io) {
            when (val result = safeApiCall(json) { api.setAvatarFromUrl(AvatarFromUrlRequest(url)) }) {
                is ApiResult.Success ->
                    // The avatar endpoint returns just the new URL, so re-fetch the
                    // full user to refresh the cache consistently.
                    refreshCurrentUser()
                is ApiResult.Failure -> result
            }
        }

    override suspend fun uploadAvatar(
        bytes: ByteArray,
        fileName: String,
        mimeType: String,
    ): ApiResult<ProfileUser> = withContext(dispatchers.io) {
        val part = MultipartBody.Part.createFormData(
            name = "file",
            filename = fileName,
            body = bytes.toRequestBody(mimeType.toMediaTypeOrNull()),
        )
        when (val result = safeApiCall(json) { api.uploadAvatar(part) }) {
            is ApiResult.Success -> refreshCurrentUser()
            is ApiResult.Failure -> result
        }
    }

    override suspend fun searchUsers(query: String): ApiResult<List<UserSearchResult>> =
        withContext(dispatchers.io) {
            safeApiCall(json) { api.searchUsers(query, limit = SEARCH_LIMIT) }
                .map { response -> response.usersOrEmpty.map { it.toSearchResult() } }
        }

    /** Caches [dto] as the current user, clearing the flag from any stale row first. */
    private suspend fun cacheCurrentUser(dto: ProfileUserDto): ProfileUser {
        val domain = dto.toProfileUser(isCurrentUser = true)
        profileDao.clearCurrentUserFlag()
        profileDao.upsert(domain.toEntity())
        return domain
    }

    private companion object {
        const val SEARCH_LIMIT = 20
    }
}
