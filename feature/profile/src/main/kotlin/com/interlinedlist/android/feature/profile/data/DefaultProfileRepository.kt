package com.interlinedlist.android.feature.profile.data

import com.interlinedlist.android.core.common.dispatcher.DispatcherProvider
import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.core.common.result.AppError
import com.interlinedlist.android.core.common.result.map
import com.interlinedlist.android.core.network.error.safeApiCall
import com.interlinedlist.android.feature.profile.data.local.ProfileDao
import com.interlinedlist.android.feature.profile.data.local.toDomain
import com.interlinedlist.android.feature.profile.data.local.toEntity
import com.interlinedlist.android.feature.profile.data.mapper.toFollowCounts
import com.interlinedlist.android.feature.profile.data.mapper.toFollowStatus
import com.interlinedlist.android.feature.profile.data.mapper.toFollowUser
import com.interlinedlist.android.feature.profile.data.mapper.toFollowUserOrNull
import com.interlinedlist.android.feature.profile.data.mapper.toMutualConnections
import com.interlinedlist.android.feature.profile.data.mapper.toProfileUser
import com.interlinedlist.android.feature.profile.data.mapper.toPublicDocumentDetail
import com.interlinedlist.android.feature.profile.data.mapper.toPublicDocumentSummary
import com.interlinedlist.android.feature.profile.data.mapper.toPublicListRow
import com.interlinedlist.android.feature.profile.data.mapper.toPublicListSummary
import com.interlinedlist.android.feature.profile.data.mapper.toPublicPost
import com.interlinedlist.android.feature.profile.data.mapper.toSearchResult
import com.interlinedlist.android.feature.profile.data.remote.ProfileApi
import com.interlinedlist.android.feature.profile.data.remote.dto.AvatarFromUrlRequest
import com.interlinedlist.android.feature.profile.data.remote.dto.ChangeEmailRequest
import com.interlinedlist.android.feature.profile.data.remote.dto.DeleteAccountRequest
import com.interlinedlist.android.feature.profile.data.remote.dto.ProfileUserDto
import com.interlinedlist.android.feature.profile.data.remote.dto.UpdateProfileRequest
import com.interlinedlist.android.feature.profile.domain.FollowCounts
import com.interlinedlist.android.feature.profile.domain.FollowStatus
import com.interlinedlist.android.feature.profile.domain.FollowUser
import com.interlinedlist.android.feature.profile.domain.LinkedIdentity
import com.interlinedlist.android.feature.profile.domain.LoginSession
import com.interlinedlist.android.feature.profile.domain.MutualConnections
import com.interlinedlist.android.feature.profile.domain.ProfileUser
import com.interlinedlist.android.feature.profile.domain.PublicDocumentDetail
import com.interlinedlist.android.feature.profile.domain.PublicDocumentSummary
import com.interlinedlist.android.feature.profile.domain.PublicListDetail
import com.interlinedlist.android.feature.profile.domain.PublicListSummary
import com.interlinedlist.android.feature.profile.domain.PublicPost
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

    override suspend fun getFollowStatus(userId: String): ApiResult<FollowStatus> =
        withContext(dispatchers.io) {
            safeApiCall(json) { api.getFollowStatus(userId).toFollowStatus() }
        }

    override suspend fun getFollowCounts(userId: String): ApiResult<FollowCounts> =
        withContext(dispatchers.io) {
            safeApiCall(json) { api.getFollowCounts(userId).toFollowCounts() }
        }

    override suspend fun followUser(userId: String): ApiResult<Unit> =
        withContext(dispatchers.io) { safeApiCall(json) { api.followUser(userId) } }

    override suspend fun unfollowUser(userId: String): ApiResult<Unit> =
        withContext(dispatchers.io) { safeApiCall(json) { api.unfollowUser(userId) } }

    override suspend fun getFollowers(username: String): ApiResult<List<FollowUser>> =
        withContext(dispatchers.io) {
            when (val id = resolveUserId(username)) {
                is ApiResult.Success -> safeApiCall(json) {
                    api.getFollowers(id.data, limit = LIST_LIMIT).usersOrEmpty.map { it.toFollowUser() }
                }
                is ApiResult.Failure -> id
            }
        }

    override suspend fun getFollowing(username: String): ApiResult<List<FollowUser>> =
        withContext(dispatchers.io) {
            when (val id = resolveUserId(username)) {
                is ApiResult.Success -> safeApiCall(json) {
                    api.getFollowing(id.data, limit = LIST_LIMIT).usersOrEmpty.map { it.toFollowUser() }
                }
                is ApiResult.Failure -> id
            }
        }

    override suspend fun getFollowRequests(): ApiResult<List<FollowUser>> =
        withContext(dispatchers.io) {
            safeApiCall(json) {
                api.getFollowRequests().requestsOrEmpty.mapNotNull { it.toFollowUserOrNull() }
            }
        }

    override suspend fun approveFollowRequest(userId: String): ApiResult<Unit> =
        withContext(dispatchers.io) { safeApiCall(json) { api.approveFollowRequest(userId) } }

    override suspend fun rejectFollowRequest(userId: String): ApiResult<Unit> =
        withContext(dispatchers.io) { safeApiCall(json) { api.rejectFollowRequest(userId) } }

    override suspend fun removeFollower(userId: String): ApiResult<Unit> =
        withContext(dispatchers.io) { safeApiCall(json) { api.removeFollower(userId) } }

    // --- Public content (read-only, nothing cached) ---

    override suspend fun getUserPosts(username: String): ApiResult<List<PublicPost>> =
        withContext(dispatchers.io) {
            safeApiCall(json) {
                api.getUserMessages(username, limit = CONTENT_LIMIT).posts.map { it.toPublicPost() }
            }
        }

    override suspend fun getUserLists(username: String): ApiResult<List<PublicListSummary>> =
        withContext(dispatchers.io) {
            safeApiCall(json) {
                api.getUserLists(username, limit = CONTENT_LIMIT).items.map { it.toPublicListSummary() }
            }
        }

    override suspend fun getUserList(
        username: String,
        listId: String,
    ): ApiResult<PublicListDetail> = withContext(dispatchers.io) {
        // Metadata and rows come from two endpoints; fetch the list first so a missing
        // or private list surfaces its error before we attempt the rows.
        when (val meta = safeApiCall(json) { api.getUserList(username, listId).listOrSelf }) {
            is ApiResult.Success -> {
                val list = meta.data
                    ?: return@withContext ApiResult.Failure(AppError.NotFound("List not found"))
                when (val data = safeApiCall(json) {
                    api.getUserListData(username, listId, limit = CONTENT_LIMIT).items
                }) {
                    is ApiResult.Success -> ApiResult.Success(
                        PublicListDetail(
                            id = list.id,
                            title = list.title,
                            description = list.description,
                            rows = data.data.map { it.toPublicListRow() },
                        ),
                    )
                    is ApiResult.Failure -> data
                }
            }
            is ApiResult.Failure -> meta
        }
    }

    override suspend fun getUserDocuments(username: String): ApiResult<List<PublicDocumentSummary>> =
        withContext(dispatchers.io) {
            safeApiCall(json) {
                api.getUserDocuments(username).items.map { it.toPublicDocumentSummary() }
            }
        }

    override suspend fun getDocument(documentId: String): ApiResult<PublicDocumentDetail> =
        withContext(dispatchers.io) {
            when (val result = safeApiCall(json) { api.getDocument(documentId).documentOrSelf }) {
                is ApiResult.Success -> {
                    val doc = result.data
                        ?: return@withContext ApiResult.Failure(AppError.NotFound("Document not found"))
                    ApiResult.Success(doc.toPublicDocumentDetail())
                }
                is ApiResult.Failure -> result
            }
        }

    override suspend fun getMutualConnections(userId: String): ApiResult<MutualConnections> =
        withContext(dispatchers.io) {
            safeApiCall(json) { api.getMutualConnections(userId).toMutualConnections() }
        }

    // --- Account & Security (always fresh, nothing cached) ---

    override suspend fun getSessions(): ApiResult<List<LoginSession>> =
        withContext(dispatchers.io) {
            safeApiCall(json) { api.getSessions().sessionsOrEmpty.map { it.toDomain() } }
        }

    override suspend fun revokeSession(sessionId: String): ApiResult<Unit> =
        withContext(dispatchers.io) { safeApiCall(json) { api.revokeSession(sessionId) } }

    override suspend fun getIdentities(): ApiResult<List<LinkedIdentity>> =
        withContext(dispatchers.io) {
            safeApiCall(json) { api.getIdentities().identitiesOrEmpty.map { it.toDomain() } }
        }

    override suspend fun unlinkIdentity(provider: String): ApiResult<Unit> =
        withContext(dispatchers.io) { safeApiCall(json) { api.unlinkIdentity(provider) } }

    override suspend fun requestEmailChange(newEmail: String): ApiResult<Unit> =
        withContext(dispatchers.io) {
            safeApiCall(json) { api.requestEmailChange(ChangeEmailRequest(newEmail)) }
        }

    override suspend fun deleteAccount(username: String, email: String): ApiResult<Unit> =
        withContext(dispatchers.io) {
            safeApiCall(json) { api.deleteAccount(DeleteAccountRequest(username = username, email = email)) }
        }

    /** Caches [dto] as the current user, clearing the flag from any stale row first. */
    private suspend fun cacheCurrentUser(dto: ProfileUserDto): ProfileUser {
        val domain = dto.toProfileUser(isCurrentUser = true)
        profileDao.clearCurrentUserFlag()
        profileDao.upsert(domain.toEntity())
        return domain
    }

    /**
     * Resolves a [username] to its stable id — the follow endpoints key on the id,
     * while the UI navigates by username. Prefers the Room cache (populated when the
     * profile was viewed) and falls back to `GET /api/users/{username}`.
     */
    private suspend fun resolveUserId(username: String): ApiResult<String> {
        profileDao.getByUsername(username)?.let { return ApiResult.Success(it.id) }
        return safeApiCall(json) { api.getUserByUsername(username).userOrSelf }.let { result ->
            when (result) {
                is ApiResult.Success -> {
                    val dto = result.data
                        ?: return ApiResult.Failure(AppError.NotFound("User not found"))
                    ApiResult.Success(dto.id)
                }
                is ApiResult.Failure -> result
            }
        }
    }

    private companion object {
        const val SEARCH_LIMIT = 20
        const val LIST_LIMIT = 50
        const val CONTENT_LIMIT = 50
    }
}
