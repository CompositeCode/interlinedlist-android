package com.interlinedlist.android.feature.profile.data

import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.feature.profile.domain.ProfileUser
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
}
