package com.interlinedlist.android.feature.profile.data.remote

import com.interlinedlist.android.feature.profile.data.remote.dto.AvatarFromUrlRequest
import com.interlinedlist.android.feature.profile.data.remote.dto.AvatarResponse
import com.interlinedlist.android.feature.profile.data.remote.dto.FollowCountsResponse
import com.interlinedlist.android.feature.profile.data.remote.dto.FollowListResponse
import com.interlinedlist.android.feature.profile.data.remote.dto.FollowRequestsResponse
import com.interlinedlist.android.feature.profile.data.remote.dto.FollowStatusResponse
import com.interlinedlist.android.feature.profile.data.remote.dto.ProfileResponse
import com.interlinedlist.android.feature.profile.data.remote.dto.UpdateProfileRequest
import com.interlinedlist.android.feature.profile.data.remote.dto.UserSearchResponse
import okhttp3.MultipartBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Retrofit description of the Users & Profile endpoints. The shared Retrofit
 * instance already carries the base URL and Bearer token, so these calls are
 * authed. Endpoint paths were verified live (the roadmap's `/api/users/me` is
 * wrong — the current user is `GET /api/user`).
 */
interface ProfileApi {

    /** The current signed-in user: `{ "user": { ... } }`. */
    @GET("api/user")
    suspend fun getCurrentUser(): ProfileResponse

    /** Updates the current user's editable profile fields. */
    @PATCH("api/user/update")
    suspend fun updateProfile(@Body body: UpdateProfileRequest): ProfileResponse

    /** Sets the current user's avatar from a remote URL. */
    @POST("api/user/avatar/from-url")
    suspend fun setAvatarFromUrl(@Body body: AvatarFromUrlRequest): AvatarResponse

    /** Uploads a new avatar image as multipart form data. */
    @Multipart
    @POST("api/user/avatar/upload")
    suspend fun uploadAvatar(@Part file: MultipartBody.Part): AvatarResponse

    /** A public profile for another user by username. */
    @GET("api/users/{username}")
    suspend fun getUserByUsername(@Path("username") username: String): ProfileResponse

    /** Searches users by free-text query. */
    @GET("api/users/search")
    suspend fun searchUsers(
        @Query("q") query: String,
        @Query("limit") limit: Int? = null,
    ): UserSearchResponse

    // --- Following ---

    /** Follows a user. */
    @POST("api/follow/{userId}")
    suspend fun followUser(@Path("userId") userId: String)

    /** Unfollows a user. */
    @DELETE("api/follow/{userId}")
    suspend fun unfollowUser(@Path("userId") userId: String)

    /** The current user's follow relationship to a target user. */
    @GET("api/follow/{userId}/status")
    suspend fun getFollowStatus(@Path("userId") userId: String): FollowStatusResponse

    /** Follower / following counts for a user. */
    @GET("api/follow/{userId}/counts")
    suspend fun getFollowCounts(@Path("userId") userId: String): FollowCountsResponse

    /** Users following a user. */
    @GET("api/follow/{userId}/followers")
    suspend fun getFollowers(
        @Path("userId") userId: String,
        @Query("limit") limit: Int? = null,
        @Query("offset") offset: Int? = null,
    ): FollowListResponse

    /** Users a user is following. */
    @GET("api/follow/{userId}/following")
    suspend fun getFollowing(
        @Path("userId") userId: String,
        @Query("limit") limit: Int? = null,
        @Query("offset") offset: Int? = null,
    ): FollowListResponse

    /** Pending follow requests for the current user (private accounts). */
    @GET("api/follow/requests")
    suspend fun getFollowRequests(): FollowRequestsResponse

    /** Approves a pending follow request from [userId]. */
    @POST("api/follow/{userId}/approve")
    suspend fun approveFollowRequest(@Path("userId") userId: String)

    /** Rejects a pending follow request from [userId]. */
    @POST("api/follow/{userId}/reject")
    suspend fun rejectFollowRequest(@Path("userId") userId: String)

    /** Removes a follower (only callable by the user being followed). */
    @DELETE("api/follow/{userId}/remove")
    suspend fun removeFollower(@Path("userId") userId: String)
}
