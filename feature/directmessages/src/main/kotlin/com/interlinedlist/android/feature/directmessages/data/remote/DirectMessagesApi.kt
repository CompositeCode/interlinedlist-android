package com.interlinedlist.android.feature.directmessages.data.remote

import com.interlinedlist.android.feature.directmessages.data.remote.dto.ConversationsResponse
import com.interlinedlist.android.feature.directmessages.data.remote.dto.ImageUploadResponse
import com.interlinedlist.android.feature.directmessages.data.remote.dto.MessageDto
import com.interlinedlist.android.feature.directmessages.data.remote.dto.RecipientsResponse
import com.interlinedlist.android.feature.directmessages.data.remote.dto.SendMessageRequest
import com.interlinedlist.android.feature.directmessages.data.remote.dto.SendMessageResponse
import com.interlinedlist.android.feature.directmessages.data.remote.dto.ThreadResponse
import com.interlinedlist.android.feature.directmessages.data.remote.dto.ThreadUpdatesResponse
import com.interlinedlist.android.feature.directmessages.data.remote.dto.UnreadCountResponse
import okhttp3.MultipartBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Retrofit description of the Direct Messages REST API.
 *
 * This interface is owned by `:feature:directmessages` and is created from the
 * shared, already-authenticated `Retrofit` provided by `:core:network`; the
 * bearer token is injected by the shared OkHttp interceptor, so no auth headers
 * are declared here.
 */
interface DirectMessagesApi {

    /**
     * The inbox: one row per conversation (grouped by `pairKey`), newest
     * activity first, cursor-paginated by `nextCursor`.
     */
    @GET("api/dm/conversations")
    suspend fun getConversations(
        @Query("cursor") cursor: String? = null,
        @Query("take") take: Int? = null,
    ): ConversationsResponse

    /** Sends a direct message. */
    @POST("api/dm")
    suspend fun send(@Body body: SendMessageRequest): SendMessageResponse

    /** Fetches a single message the current user participates in. */
    @GET("api/dm/{id}")
    suspend fun getMessage(@Path("id") id: String): MessageDto

    /** Marks a received message read (recipient-scoped). */
    @POST("api/dm/{id}/read")
    suspend fun markRead(@Path("id") id: String)

    /** Soft-deletes the caller's own side of a message. */
    @POST("api/dm/{id}/trash")
    suspend fun trash(@Path("id") id: String)

    /** Clears the caller's own side soft-delete. */
    @POST("api/dm/{id}/restore")
    suspend fun restore(@Path("id") id: String)

    /** The people the current user can DM. */
    @GET("api/dm/recipients")
    suspend fun getRecipients(): RecipientsResponse

    /** The conversation thread with `username`; marks received-unread messages read. */
    @GET("api/dm/thread/{username}")
    suspend fun getThread(
        @Path("username") username: String,
        @Query("cursor") cursor: String? = null,
        @Query("take") take: Int? = null,
    ): ThreadResponse

    /** Incremental fetch of messages after [after], for polling an open thread. */
    @GET("api/dm/thread/{username}/updates")
    suspend fun getThreadUpdates(
        @Path("username") username: String,
        @Query("after") after: String? = null,
        @Query("since") since: String? = null,
    ): ThreadUpdatesResponse

    /** The number of unread received DMs. */
    @GET("api/dm/unread-count")
    suspend fun getUnreadCount(): UnreadCountResponse

    /** Uploads an image attachment for a direct message. */
    @Multipart
    @POST("api/dm/images/upload")
    suspend fun uploadImage(@Part image: MultipartBody.Part): ImageUploadResponse
}
