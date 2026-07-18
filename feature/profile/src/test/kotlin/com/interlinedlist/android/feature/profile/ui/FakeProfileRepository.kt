package com.interlinedlist.android.feature.profile.ui

import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.core.common.result.AppError
import com.interlinedlist.android.core.model.CustomerStatus
import com.interlinedlist.android.feature.profile.data.ProfileRepository
import com.interlinedlist.android.feature.profile.domain.ProfileUser
import com.interlinedlist.android.feature.profile.domain.UserSearchResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * In-memory [ProfileRepository] for ViewModel tests. Backed by simple StateFlows so
 * tests can observe the same reactive behaviour as Room without a device. Failure
 * modes are injectable per operation.
 */
class FakeProfileRepository : ProfileRepository {

    val currentUserFlow = MutableStateFlow<ProfileUser?>(null)
    val userFlow = MutableStateFlow<ProfileUser?>(null)

    var refreshCurrentUserResult: ApiResult<ProfileUser>? = null
    var refreshUserResult: ApiResult<ProfileUser>? = null
    var updateResult: ApiResult<ProfileUser>? = null
    var avatarFromUrlResult: ApiResult<ProfileUser>? = null
    var uploadAvatarResult: ApiResult<ProfileUser>? = null
    var searchResult: ApiResult<List<UserSearchResult>> = ApiResult.Success(emptyList())

    var refreshCurrentUserCount = 0
    var lastUpdate: Pair<String, String>? = null
    var lastAvatarUrl: String? = null
    var lastUpload: Upload? = null
    var lastSearchQuery: String? = null

    data class Upload(val fileName: String, val mimeType: String, val size: Int)

    override fun observeCurrentUser() = currentUserFlow.map { it }

    override fun observeUser(username: String) = userFlow.map { it }

    override suspend fun refreshCurrentUser(): ApiResult<ProfileUser> {
        refreshCurrentUserCount++
        return refreshCurrentUserResult ?: ApiResult.Failure(AppError.Network("not set"))
    }

    override suspend fun refreshUser(username: String): ApiResult<ProfileUser> =
        refreshUserResult ?: ApiResult.Failure(AppError.NotFound("not set"))

    override suspend fun updateProfile(displayName: String, bio: String): ApiResult<ProfileUser> {
        lastUpdate = displayName to bio
        return updateResult ?: ApiResult.Failure(AppError.Unknown("not set"))
    }

    override suspend fun setAvatarFromUrl(url: String): ApiResult<ProfileUser> {
        lastAvatarUrl = url
        return avatarFromUrlResult ?: ApiResult.Failure(AppError.Unknown("not set"))
    }

    override suspend fun uploadAvatar(bytes: ByteArray, fileName: String, mimeType: String): ApiResult<ProfileUser> {
        lastUpload = Upload(fileName, mimeType, bytes.size)
        return uploadAvatarResult ?: ApiResult.Failure(AppError.Unknown("not set"))
    }

    override suspend fun searchUsers(query: String): ApiResult<List<UserSearchResult>> {
        lastSearchQuery = query
        return searchResult
    }
}

/** Shorthand for building a domain profile user in tests. */
fun testUser(
    id: String = "u1",
    username: String = "adron",
    displayName: String? = "Adron Hall",
    avatarUrl: String? = null,
    bio: String? = "bio",
    customerStatus: CustomerStatus = CustomerStatus.FREE,
    isCurrentUser: Boolean = true,
) = ProfileUser(
    id = id,
    username = username,
    displayName = displayName,
    avatarUrl = avatarUrl,
    bio = bio,
    customerStatus = customerStatus,
    isCurrentUser = isCurrentUser,
)

/** Shorthand for building a search result in tests. */
fun testSearchResult(
    id: String,
    username: String = "user$id",
    displayName: String? = "User $id",
) = UserSearchResult(id = id, username = username, displayName = displayName, avatarUrl = null)
