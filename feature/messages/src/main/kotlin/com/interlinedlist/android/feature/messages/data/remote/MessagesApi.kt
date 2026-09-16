package com.interlinedlist.android.feature.messages.data.remote

import com.interlinedlist.android.feature.messages.data.remote.dto.CreateMessageRequest
import com.interlinedlist.android.feature.messages.data.remote.dto.CreateMessageResponse
import com.interlinedlist.android.feature.messages.data.remote.dto.EditMessageRequest
import com.interlinedlist.android.feature.messages.data.remote.dto.IdentitiesResponse
import com.interlinedlist.android.feature.messages.data.remote.dto.MediaUploadResponse
import com.interlinedlist.android.feature.messages.data.remote.dto.MessageResponse
import com.interlinedlist.android.feature.messages.data.remote.dto.MessagesResponse
import com.interlinedlist.android.feature.messages.data.remote.dto.MetadataResponse
import com.interlinedlist.android.feature.messages.data.remote.dto.ReportRequest
import com.interlinedlist.android.feature.messages.data.remote.dto.ScheduledMessagesResponse
import com.interlinedlist.android.feature.messages.data.remote.dto.TagAutocompleteResponse
import com.interlinedlist.android.feature.messages.data.remote.dto.TrendingTagsResponse
import com.interlinedlist.android.feature.messages.data.remote.dto.UserReportRequest
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
 * Retrofit description of the Messages endpoints. Provided from the shared,
 * already-authenticated [retrofit2.Retrofit] (base URL + Bearer interceptor),
 * so every call here is authed.
 */
interface MessagesApi {

    /**
     * Feed of top-level messages, newest first, **keyset (cursor) paginated**.
     *
     * [cursor] is the previous response's `nextCursor`, passed back verbatim; it
     * is opaque and must never be constructed, parsed or modified. A null
     * [cursor] loads the first page (Retrofit omits the query parameter). The
     * endpoint has no `offset` parameter.
     *
     * [onlyMine] restricts the feed to the caller's own messages. It is the only
     * feed-scoping parameter the endpoint has (`/help/api/messages` lists `limit`,
     * `offset`, `onlyMine` and `tag`): there is no following/followers parameter,
     * because the server scopes the feed by the account's saved
     * `viewingPreference`. Null omits the parameter.
     *
     * [tag] filters the feed to messages carrying that tag. Tags are free-form and
     * routinely contain spaces and punctuation (`life is short, o brave girl` is a
     * real one), so the value is passed **raw** and Retrofit percent-encodes it
     * exactly once — pre-encoding here would double-encode it and match nothing.
     * Null omits the parameter, which is the unfiltered feed.
     */
    @GET("api/messages")
    suspend fun getMessages(
        @Query("limit") limit: Int,
        @Query("cursor") cursor: String? = null,
        @Query("onlyMine") onlyMine: Boolean? = null,
        @Query("tag") tag: String? = null,
    ): MessagesResponse

    /** Creates a new message (or a reply when `parentId` is set). The created
     *  message is returned under `data` (see [CreateMessageResponse]). */
    @POST("api/messages")
    suspend fun createMessage(@Body body: CreateMessageRequest): CreateMessageResponse

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

    /**
     * Edits the content of one of the caller's own messages. The endpoint's
     * response body is not modelled in the OpenAPI spec; the repository updates
     * the cache optimistically and treats a 2xx as success, so this returns Unit.
     */
    @PATCH("api/messages/{id}")
    suspend fun editMessage(@Path("id") id: String, @Body body: EditMessageRequest)

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

    /**
     * The caller's already-linked social identities (Mastodon/LinkedIn/X/Bluesky),
     * used to offer cross-post destinations in the composer. Linking new accounts
     * is a web-only OAuth flow and is not exposed here.
     */
    @GET("api/user/identities")
    suspend fun getIdentities(): IdentitiesResponse

    /**
     * Tag suggestions for a prefix the user is typing.
     *
     * The query parameter is **`q`** — the endpoint 400s with
     * `{"error":"Query parameter 'q' is required","code":"bad_request"}` for
     * anything else (notably `prefix`). Matching is a **case-insensitive literal
     * prefix** over existing public tags (`%` and `_` are not wildcards) and a
     * single leading `#` is stripped server-side; an empty `q` is a 400.
     * [limit] defaults to 10 server-side and is clamped to 50.
     */
    @GET("api/tags/autocomplete")
    suspend fun autocompleteTags(
        @Query("q") query: String,
        @Query("limit") limit: Int? = null,
    ): TagAutocompleteResponse

    /**
     * The most-used tags across **public** messages inside a trailing [window].
     *
     * [window] must be one of `day`, `week` or `month`: the server falls back to
     * `week` for anything else **without reporting it**, so a typo would silently
     * mislabel the surface. [limit] defaults to 20 server-side and is clamped to
     * 100. The response is a bare `{ "tags": [ { tag, count, lastUsedAt } ] }` —
     * it does **not** echo the window back, so the caller is the only thing that
     * knows which period the counts cover.
     */
    @GET("api/tags/trending")
    suspend fun trendingTags(
        @Query("window") window: String,
        @Query("limit") limit: Int,
    ): TrendingTagsResponse

    /** Reports a message with a reason (and optional free-text detail). */
    @POST("api/messages/{id}/report")
    suspend fun report(@Path("id") id: String, @Body body: ReportRequest)

    /** Fetches and attaches link-preview metadata for a message's links. */
    @POST("api/messages/{id}/metadata")
    suspend fun fetchMetadata(@Path("id") id: String): MetadataResponse

    // --- author moderation (on messages by other users) --------------------

    /** Blocks a user by username. */
    @POST("api/users/{username}/block")
    suspend fun blockUser(@Path("username") username: String)

    /** Mutes a user by username. */
    @POST("api/users/{username}/mute")
    suspend fun muteUser(@Path("username") username: String)

    /** Reports a user with a reason (and optional free-text detail). */
    @POST("api/users/{username}/report")
    suspend fun reportUser(@Path("username") username: String, @Body body: UserReportRequest)
}
