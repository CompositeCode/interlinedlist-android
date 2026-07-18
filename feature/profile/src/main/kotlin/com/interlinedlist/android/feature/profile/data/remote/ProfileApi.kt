package com.interlinedlist.android.feature.profile.data.remote

import com.interlinedlist.android.feature.profile.data.remote.dto.AvatarFromUrlRequest
import com.interlinedlist.android.feature.profile.data.remote.dto.AvatarResponse
import com.interlinedlist.android.feature.profile.data.remote.dto.ProfileResponse
import com.interlinedlist.android.feature.profile.data.remote.dto.UpdateProfileRequest
import com.interlinedlist.android.feature.profile.data.remote.dto.UserSearchResponse
import okhttp3.MultipartBody
import retrofit2.http.Body
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
}
