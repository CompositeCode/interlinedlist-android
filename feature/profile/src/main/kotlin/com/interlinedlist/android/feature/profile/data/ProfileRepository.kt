package com.interlinedlist.android.feature.profile.data

import com.interlinedlist.android.core.common.result.ApiResult
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
import kotlinx.coroutines.flow.Flow

/**
 * Offline-first access to user profiles. The current user and viewed users are
 * served as [Flow]s from Room (the source of truth); the `refresh*` calls pull
 * from the API and upsert into the cache. Mutations write through to the API and
 * update the cache so the observing UI reflects the change immediately. Search is
 * one-shot and not cached.
 */
interface ProfileRepository {

    /** The current signed-in user's cached profile (null until first loaded). */
    fun observeCurrentUser(): Flow<ProfileUser?>

    /** A cached profile by username (null until first loaded). */
    fun observeUser(username: String): Flow<ProfileUser?>

    /** Fetches the current user from `GET /api/user` and caches it. */
    suspend fun refreshCurrentUser(): ApiResult<ProfileUser>

    /** Fetches another user from `GET /api/users/{username}` and caches it. */
    suspend fun refreshUser(username: String): ApiResult<ProfileUser>

    /**
     * Updates the current user's editable fields via `PATCH /api/user/update` and
     * writes the result through to the cache.
     */
    suspend fun updateProfile(
        displayName: String,
        bio: String,
    ): ApiResult<ProfileUser>

    /** Sets the current user's avatar from a remote URL and refreshes the cache. */
    suspend fun setAvatarFromUrl(url: String): ApiResult<ProfileUser>

    /**
     * Uploads a new avatar image (raw bytes + mime type) and refreshes the cache.
     * The caller resolves the picked image to bytes; the repository stays free of
     * Android URI/ContentResolver concerns.
     */
    suspend fun uploadAvatar(
        bytes: ByteArray,
        fileName: String,
        mimeType: String,
    ): ApiResult<ProfileUser>

    /** One-shot user search against `GET /api/users/search` (not cached). */
    suspend fun searchUsers(query: String): ApiResult<List<UserSearchResult>>

    // --- Following ---

    /** The current user's follow relationship to [userId] via `GET /api/follow/{userId}/status`. */
    suspend fun getFollowStatus(userId: String): ApiResult<FollowStatus>

    /** Follower / following counts for [userId] via `GET /api/follow/{userId}/counts`. */
    suspend fun getFollowCounts(userId: String): ApiResult<FollowCounts>

    /** Follows [userId] via `POST /api/follow/{userId}`. */
    suspend fun followUser(userId: String): ApiResult<Unit>

    /** Unfollows [userId] via `DELETE /api/follow/{userId}`. */
    suspend fun unfollowUser(userId: String): ApiResult<Unit>

    /**
     * The users following the user named [username], reached by drilling down from a
     * profile. Resolves the username to an id, then reads
     * `GET /api/follow/{userId}/followers`.
     */
    suspend fun getFollowers(username: String): ApiResult<List<FollowUser>>

    /** The users the user named [username] follows via `GET /api/follow/{userId}/following`. */
    suspend fun getFollowing(username: String): ApiResult<List<FollowUser>>

    /** The current user's pending follow requests via `GET /api/follow/requests`. */
    suspend fun getFollowRequests(): ApiResult<List<FollowUser>>

    /** Approves a pending request from [userId] via `POST /api/follow/{userId}/approve`. */
    suspend fun approveFollowRequest(userId: String): ApiResult<Unit>

    /** Rejects a pending request from [userId] via `POST /api/follow/{userId}/reject`. */
    suspend fun rejectFollowRequest(userId: String): ApiResult<Unit>

    /** Removes [userId] as a follower via `DELETE /api/follow/{userId}/remove`. */
    suspend fun removeFollower(userId: String): ApiResult<Unit>

    // --- Public content (another user's posts / lists / documents) ---
    // Read-only surfaces on the other-user profile; nothing is cached (YAGNI).

    /** A user's public posts via `GET /api/user/{username}/messages` (singular `user`). */
    suspend fun getUserPosts(username: String): ApiResult<List<PublicPost>>

    /** A user's public lists via `GET /api/users/{username}/lists`. */
    suspend fun getUserLists(username: String): ApiResult<List<PublicListSummary>>

    /**
     * A single public list (metadata + rows) via `GET /api/users/{username}/lists/{id}`
     * and `.../data`, combined into a read-only [PublicListDetail].
     */
    suspend fun getUserList(username: String, listId: String): ApiResult<PublicListDetail>

    /** A user's public documents via `GET /api/users/{username}/documents`. */
    suspend fun getUserDocuments(username: String): ApiResult<List<PublicDocumentSummary>>

    /** A single public document (title + content) via `GET /api/documents/{id}`. */
    suspend fun getDocument(documentId: String): ApiResult<PublicDocumentDetail>

    /** Mutual-connection counts with [userId] via `GET /api/follow/{userId}/mutual`. */
    suspend fun getMutualConnections(userId: String): ApiResult<MutualConnections>

    // --- Account & Security ---
    // These are always-fresh settings surfaces, so nothing is cached (YAGNI).

    /** The current user's active login sessions via `GET /api/user/sessions`. */
    suspend fun getSessions(): ApiResult<List<LoginSession>>

    /** Revokes (signs out) the session [sessionId] via `DELETE /api/user/sessions/{id}`. */
    suspend fun revokeSession(sessionId: String): ApiResult<Unit>

    /** The current user's linked social identities via `GET /api/user/identities`. */
    suspend fun getIdentities(): ApiResult<List<LinkedIdentity>>

    /** Unlinks the identity for [provider] via `DELETE /api/user/identities?provider=...`. */
    suspend fun unlinkIdentity(provider: String): ApiResult<Unit>

    /** Requests an email change to [newEmail] via `POST /api/user/change-email/request`. */
    suspend fun requestEmailChange(newEmail: String): ApiResult<Unit>

    /**
     * Deletes the current user's account via `POST /api/user/delete`, confirming with
     * the account's [username] and [email]. On success the caller signs the user out.
     */
    suspend fun deleteAccount(username: String, email: String): ApiResult<Unit>

    // --- Moderation (block / mute / report) ---
    // Read-only lists; nothing is cached in Room (YAGNI).

    /** The current user's blocked users via `GET /api/user/blocks`. */
    suspend fun getBlockedUsers(): ApiResult<List<ModeratedUser>>

    /** The current user's muted users via `GET /api/user/mutes`. */
    suspend fun getMutedUsers(): ApiResult<List<ModeratedUser>>

    /**
     * The current user's blocked/muted relationship to [username], combining
     * `GET /api/users/{username}/block` and `.../mute`.
     */
    suspend fun getModerationStatus(username: String): ApiResult<ModerationStatus>

    /** Blocks [username] via `POST /api/users/{username}/block`. */
    suspend fun blockUser(username: String): ApiResult<Unit>

    /** Unblocks [username] via `DELETE /api/users/{username}/block`. */
    suspend fun unblockUser(username: String): ApiResult<Unit>

    /** Mutes [username] via `POST /api/users/{username}/mute`. */
    suspend fun muteUser(username: String): ApiResult<Unit>

    /** Unmutes [username] via `DELETE /api/users/{username}/mute`. */
    suspend fun unmuteUser(username: String): ApiResult<Unit>

    /**
     * Reports [username] with a [reason] and optional free-text [detail] via
     * `POST /api/users/{username}/report`.
     */
    suspend fun reportUser(
        username: String,
        reason: ReportReason,
        detail: String?,
    ): ApiResult<Unit>
}
