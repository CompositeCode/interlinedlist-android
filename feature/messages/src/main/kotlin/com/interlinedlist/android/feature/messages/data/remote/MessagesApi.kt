package com.interlinedlist.android.feature.messages.data.remote

import com.interlinedlist.android.feature.messages.data.remote.dto.CreateMessageRequest
import com.interlinedlist.android.feature.messages.data.remote.dto.MediaUploadResponse
import com.interlinedlist.android.feature.messages.data.remote.dto.MessageResponse
import com.interlinedlist.android.feature.messages.data.remote.dto.MessagesResponse
import com.interlinedlist.android.feature.messages.data.remote.dto.MetadataResponse
import com.interlinedlist.android.feature.messages.data.remote.dto.ReportRequest
import com.interlinedlist.android.feature.messages.data.remote.dto.ScheduledMessagesResponse
import okhttp3.MultipartBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Retrofit description of the Messages endpoints. Provided from the shared,
 * already-authenticated [retrofit2.Retrofit] (base URL + Bearer interceptor),
 * so every call here is authed.
 */
interface MessagesApi {

    /** Feed of top-level messages, newest first, offset/limit paginated. */
    @GET("api/messages")
    suspend fun getMessages(
        @Query("limit") limit: Int,
        @Query("offset") offset: Int,
    ): MessagesResponse

    /** Creates a new message (or a reply when `parentId` is set). */
    @POST("api/messages")
    suspend fun createMessage(@Body body: CreateMessageRequest): MessageResponse

    /** A single message by id (for the detail screen). */
    @GET("api/messages/{id}")
    suspend fun getMessage(@Path("id") id: String): MessageResponse

    /** Direct replies to a message. */
    @GET("api/messages/{id}/replies")
    suspend fun getReplies(@Path("id") id: String): MessagesResponse

    /** Digs a message. */
    @POST("api/messages/{id}/dig")
    suspend fun dig(@Path("id") id: String)

    /** Removes the caller's dig from a message. */
    @DELETE("api/messages/{id}/dig")
    suspend fun undig(@Path("id") id: String)

    /** Deletes one of the caller's own messages. */
    @DELETE("api/messages/{id}")
    suspend fun deleteMessage(@Path("id") id: String)

    /** Full-text search over top-level messages. */
    @GET("api/messages/search")
    suspend fun search(
        @Query("q") query: String,
        @Query("limit") limit: Int,
        @Query("offset") offset: Int,
    ): MessagesResponse

    /** Uploads an image and returns its hosted URL to attach on compose. */
    @Multipart
    @POST("api/messages/images/upload")
    suspend fun uploadImage(@Part file: MultipartBody.Part): MediaUploadResponse

    /** Uploads a video and returns its hosted URL to attach on compose. */
    @Multipart
    @POST("api/messages/videos/upload")
    suspend fun uploadVideo(@Part file: MultipartBody.Part): MediaUploadResponse

    /** The caller's scheduled (not-yet-published) messages. */
    @GET("api/messages/scheduled")
    suspend fun getScheduled(): ScheduledMessagesResponse

    /** Reports a message with a reason (and optional free-text detail). */
    @POST("api/messages/{id}/report")
    suspend fun report(@Path("id") id: String, @Body body: ReportRequest)

    /** Fetches and attaches link-preview metadata for a message's links. */
    @POST("api/messages/{id}/metadata")
    suspend fun fetchMetadata(@Path("id") id: String): MetadataResponse
}
