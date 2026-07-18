package com.interlinedlist.android.feature.notifications.data.remote

import com.interlinedlist.android.feature.notifications.data.remote.dto.NotificationsResponse
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Retrofit description of the Notifications endpoints. Provided from the shared,
 * already-authenticated [retrofit2.Retrofit] (base URL + Bearer interceptor), so
 * every call here is authed.
 *
 * The OpenAPI extract exposes `scope` as the only list parameter; we additionally
 * send `limit`/`offset` for offset/limit pagination, which the server ignores if it
 * does not paginate this collection.
 */
interface NotificationsApi {

    /** The recipient's notifications, newest first, offset/limit paginated. */
    @GET("api/notifications")
    suspend fun getNotifications(
        @Query("limit") limit: Int,
        @Query("offset") offset: Int,
        @Query("scope") scope: String? = null,
    ): NotificationsResponse

    /** Marks a single notification read (on tap). */
    @PATCH("api/notifications/{id}/read")
    suspend fun markRead(@Path("id") id: String)

    /** Marks every notification read (top-bar action). */
    @POST("api/notifications/mark-all-read")
    suspend fun markAllRead()

    /** Dismisses (deletes) a single notification. */
    @DELETE("api/notifications/{id}")
    suspend fun delete(@Path("id") id: String)
}
