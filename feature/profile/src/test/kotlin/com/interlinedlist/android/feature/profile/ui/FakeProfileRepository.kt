package com.interlinedlist.android.feature.profile.ui

import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.core.common.result.AppError
import com.interlinedlist.android.core.model.CustomerStatus
import com.interlinedlist.android.feature.profile.data.ProfileRepository
import com.interlinedlist.android.feature.profile.domain.FollowCounts
import com.interlinedlist.android.feature.profile.domain.FollowStatus
import com.interlinedlist.android.feature.profile.domain.FollowUser
import com.interlinedlist.android.feature.profile.domain.LinkedIdentity
import com.interlinedlist.android.feature.profile.domain.LoginSession
import com.interlinedlist.android.feature.profile.domain.ModeratedUser
import com.interlinedlist.android.feature.profile.domain.ModerationStatus
import com.interlinedlist.android.feature.profile.domain.MutualConnections
import com.interlinedlist.android.feature.profile.domain.ProfileUser
import com.interlinedlist.android.feature.profile.domain.ReportReason
import com.interlinedlist.android.feature.profile.domain.PublicDocumentDetail
import com.interlinedlist.android.feature.profile.domain.PublicDocumentSummary
import com.interlinedlist.android.feature.profile.domain.PublicListDetail
import com.interlinedlist.android.feature.profile.domain.PublicListSummary
import com.interlinedlist.android.feature.profile.domain.PublicPost
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

    // --- Following ---

    var followStatusResult: ApiResult<FollowStatus> = ApiResult.Success(FollowStatus.NOT_FOLLOWING)
    var followCountsResult: ApiResult<FollowCounts> = ApiResult.Success(FollowCounts())
    var followResult: ApiResult<Unit> = ApiResult.Success(Unit)
    var unfollowResult: ApiResult<Unit> = ApiResult.Success(Unit)
    var followersResult: ApiResult<List<FollowUser>> = ApiResult.Success(emptyList())
    var followingResult: ApiResult<List<FollowUser>> = ApiResult.Success(emptyList())
    var followRequestsResult: ApiResult<List<FollowUser>> = ApiResult.Success(emptyList())
    var approveResult: ApiResult<Unit> = ApiResult.Success(Unit)
    var rejectResult: ApiResult<Unit> = ApiResult.Success(Unit)
    var removeFollowerResult: ApiResult<Unit> = ApiResult.Success(Unit)

    var followStatusUserId: String? = null
    var followCountsUserId: String? = null
    var followedUserId: String? = null
    var unfollowedUserId: String? = null
    var followersUsername: String? = null
    var followingUsername: String? = null
    var approvedUserId: String? = null
    var rejectedUserId: String? = null
    var removedFollowerUserId: String? = null
    var followCount = 0
    var unfollowCount = 0
    var followRequestsCount = 0

    override suspend fun getFollowStatus(userId: String): ApiResult<FollowStatus> {
        followStatusUserId = userId
        return followStatusResult
    }

    override suspend fun getFollowCounts(userId: String): ApiResult<FollowCounts> {
        followCountsUserId = userId
        return followCountsResult
    }

    override suspend fun followUser(userId: String): ApiResult<Unit> {
        followedUserId = userId
        followCount++
        return followResult
    }

    override suspend fun unfollowUser(userId: String): ApiResult<Unit> {
        unfollowedUserId = userId
        unfollowCount++
        return unfollowResult
    }

    override suspend fun getFollowers(username: String): ApiResult<List<FollowUser>> {
        followersUsername = username
        return followersResult
    }

    override suspend fun getFollowing(username: String): ApiResult<List<FollowUser>> {
        followingUsername = username
        return followingResult
    }

    override suspend fun getFollowRequests(): ApiResult<List<FollowUser>> {
        followRequestsCount++
        return followRequestsResult
    }

    override suspend fun approveFollowRequest(userId: String): ApiResult<Unit> {
        approvedUserId = userId
        return approveResult
    }

    override suspend fun rejectFollowRequest(userId: String): ApiResult<Unit> {
        rejectedUserId = userId
        return rejectResult
    }

    override suspend fun removeFollower(userId: String): ApiResult<Unit> {
        removedFollowerUserId = userId
        return removeFollowerResult
    }

    // --- Public content ---

    var postsResult: ApiResult<List<PublicPost>> = ApiResult.Success(emptyList())
    var listsResult: ApiResult<List<PublicListSummary>> = ApiResult.Success(emptyList())
    var listDetailResult: ApiResult<PublicListDetail> =
        ApiResult.Failure(AppError.NotFound("not set"))
    var documentsResult: ApiResult<List<PublicDocumentSummary>> = ApiResult.Success(emptyList())
    var documentDetailResult: ApiResult<PublicDocumentDetail> =
        ApiResult.Failure(AppError.NotFound("not set"))
    var mutualResult: ApiResult<MutualConnections> = ApiResult.Success(MutualConnections())

    var postsUsername: String? = null
    var listsUsername: String? = null
    var listDetailArgs: Pair<String, String>? = null
    var documentsUsername: String? = null
    var documentDetailId: String? = null
    var mutualUserId: String? = null
    var postsCount = 0
    var listsCount = 0
    var documentsCount = 0

    override suspend fun getUserPosts(username: String): ApiResult<List<PublicPost>> {
        postsUsername = username
        postsCount++
        return postsResult
    }

    override suspend fun getUserLists(username: String): ApiResult<List<PublicListSummary>> {
        listsUsername = username
        listsCount++
        return listsResult
    }

    override suspend fun getUserList(username: String, listId: String): ApiResult<PublicListDetail> {
        listDetailArgs = username to listId
        return listDetailResult
    }

    override suspend fun getUserDocuments(username: String): ApiResult<List<PublicDocumentSummary>> {
        documentsUsername = username
        documentsCount++
        return documentsResult
    }

    override suspend fun getDocument(documentId: String): ApiResult<PublicDocumentDetail> {
        documentDetailId = documentId
        return documentDetailResult
    }

    override suspend fun getMutualConnections(userId: String): ApiResult<MutualConnections> {
        mutualUserId = userId
        return mutualResult
    }

    // --- Account & Security ---

    var sessionsResult: ApiResult<List<LoginSession>> = ApiResult.Success(emptyList())
    var revokeSessionResult: ApiResult<Unit> = ApiResult.Success(Unit)
    var identitiesResult: ApiResult<List<LinkedIdentity>> = ApiResult.Success(emptyList())
    var unlinkIdentityResult: ApiResult<Unit> = ApiResult.Success(Unit)
    var requestEmailChangeResult: ApiResult<Unit> = ApiResult.Success(Unit)
    var deleteAccountResult: ApiResult<Unit> = ApiResult.Success(Unit)

    var sessionsCount = 0
    var revokedSessionId: String? = null
    var identitiesCount = 0
    var unlinkedProvider: String? = null
    var requestedEmail: String? = null
    var deleteAccountArgs: Pair<String, String>? = null

    override suspend fun getSessions(): ApiResult<List<LoginSession>> {
        sessionsCount++
        return sessionsResult
    }

    override suspend fun revokeSession(sessionId: String): ApiResult<Unit> {
        revokedSessionId = sessionId
        return revokeSessionResult
    }

    override suspend fun getIdentities(): ApiResult<List<LinkedIdentity>> {
        identitiesCount++
        return identitiesResult
    }

    override suspend fun unlinkIdentity(provider: String): ApiResult<Unit> {
        unlinkedProvider = provider
        return unlinkIdentityResult
    }

    override suspend fun requestEmailChange(newEmail: String): ApiResult<Unit> {
        requestedEmail = newEmail
        return requestEmailChangeResult
    }

    override suspend fun deleteAccount(username: String, email: String): ApiResult<Unit> {
        deleteAccountArgs = username to email
        return deleteAccountResult
    }

    // --- Moderation (block / mute / report) ---

    var blockedUsersResult: ApiResult<List<ModeratedUser>> = ApiResult.Success(emptyList())
    var mutedUsersResult: ApiResult<List<ModeratedUser>> = ApiResult.Success(emptyList())
    var moderationStatusResult: ApiResult<ModerationStatus> = ApiResult.Success(ModerationStatus())
    var blockResult: ApiResult<Unit> = ApiResult.Success(Unit)
    var unblockResult: ApiResult<Unit> = ApiResult.Success(Unit)
    var muteResult: ApiResult<Unit> = ApiResult.Success(Unit)
    var unmuteResult: ApiResult<Unit> = ApiResult.Success(Unit)
    var reportResult: ApiResult<Unit> = ApiResult.Success(Unit)

    var blockedUsersCount = 0
    var mutedUsersCount = 0
    var moderationStatusUsername: String? = null
    var blockedUsername: String? = null
    var unblockedUsername: String? = null
    var mutedUsername: String? = null
    var unmutedUsername: String? = null
    var blockCount = 0
    var unblockCount = 0
    var muteCount = 0
    var unmuteCount = 0
    var reportArgs: Triple<String, ReportReason, String?>? = null
    var reportCount = 0

    override suspend fun getBlockedUsers(): ApiResult<List<ModeratedUser>> {
        blockedUsersCount++
        return blockedUsersResult
    }

    override suspend fun getMutedUsers(): ApiResult<List<ModeratedUser>> {
        mutedUsersCount++
        return mutedUsersResult
    }

    override suspend fun getModerationStatus(username: String): ApiResult<ModerationStatus> {
        moderationStatusUsername = username
        return moderationStatusResult
    }

    override suspend fun blockUser(username: String): ApiResult<Unit> {
        blockedUsername = username
        blockCount++
        return blockResult
    }

    override suspend fun unblockUser(username: String): ApiResult<Unit> {
        unblockedUsername = username
        unblockCount++
        return unblockResult
    }

    override suspend fun muteUser(username: String): ApiResult<Unit> {
        mutedUsername = username
        muteCount++
        return muteResult
    }

    override suspend fun unmuteUser(username: String): ApiResult<Unit> {
        unmutedUsername = username
        unmuteCount++
        return unmuteResult
    }

    override suspend fun reportUser(
        username: String,
        reason: ReportReason,
        detail: String?,
    ): ApiResult<Unit> {
        reportArgs = Triple(username, reason, detail)
        reportCount++
        return reportResult
    }
}

/** Shorthand for building a follow-list/request user in tests. */
fun testFollowUser(
    id: String = "f1",
    username: String = "ada",
    displayName: String? = "Ada Lovelace",
    avatarUrl: String? = null,
) = FollowUser(id = id, username = username, displayName = displayName, avatarUrl = avatarUrl)

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

/** Shorthand for building a blocked/muted user row in tests. */
fun testModeratedUser(
    id: String = "m1",
    username: String = "ada",
    displayName: String? = "Ada Lovelace",
    avatarUrl: String? = null,
) = ModeratedUser(id = id, username = username, displayName = displayName, avatarUrl = avatarUrl)

/** Shorthand for building a login session in tests. */
fun testSession(
    id: String = "s1",
    deviceLabel: String = "Pixel 8",
    createdAt: String? = "2026-07-31T21:37:00.000Z",
    lastUsedAt: String? = "2026-07-31T21:49:00.000Z",
    isCurrent: Boolean = false,
) = LoginSession(
    id = id,
    deviceLabel = deviceLabel,
    createdAt = createdAt,
    lastUsedAt = lastUsedAt,
    isCurrent = isCurrent,
)

/** Shorthand for building a linked identity in tests. */
fun testIdentity(
    id: String = "i1",
    provider: String = "linkedin",
    providerUsername: String? = "Adron Hall",
    connectedAt: String? = "2026-06-12T07:33:42.447Z",
) = LinkedIdentity(
    id = id,
    provider = provider,
    providerUsername = providerUsername,
    profileUrl = null,
    avatarUrl = null,
    connectedAt = connectedAt,
)
