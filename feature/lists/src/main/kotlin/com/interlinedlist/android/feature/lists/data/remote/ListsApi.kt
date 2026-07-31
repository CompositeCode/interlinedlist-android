package com.interlinedlist.android.feature.lists.data.remote

import com.interlinedlist.android.feature.lists.data.remote.dto.AddWatcherRequest
import com.interlinedlist.android.feature.lists.data.remote.dto.ConnectionEnvelope
import com.interlinedlist.android.feature.lists.data.remote.dto.ConnectionsResponse
import com.interlinedlist.android.feature.lists.data.remote.dto.CreateConnectionRequest
import com.interlinedlist.android.feature.lists.data.remote.dto.CreateFolderRequest
import com.interlinedlist.android.feature.lists.data.remote.dto.CreateListRequest
import com.interlinedlist.android.feature.lists.data.remote.dto.FolderDto
import com.interlinedlist.android.feature.lists.data.remote.dto.FoldersResponse
import com.interlinedlist.android.feature.lists.data.remote.dto.ListEnvelope
import com.interlinedlist.android.feature.lists.data.remote.dto.ListsResponse
import com.interlinedlist.android.feature.lists.data.remote.dto.RefreshResultDto
import com.interlinedlist.android.feature.lists.data.remote.dto.RowEnvelope
import com.interlinedlist.android.feature.lists.data.remote.dto.RowWriteRequest
import com.interlinedlist.android.feature.lists.data.remote.dto.CreateShareLinkRequest
import com.interlinedlist.android.feature.lists.data.remote.dto.RowsResponse
import com.interlinedlist.android.feature.lists.data.remote.dto.SchemaEnvelope
import com.interlinedlist.android.feature.lists.data.remote.dto.ShareLinkEnvelope
import com.interlinedlist.android.feature.lists.data.remote.dto.ShareLinksResponse
import com.interlinedlist.android.feature.lists.data.remote.dto.SharedListResponse
import com.interlinedlist.android.feature.lists.data.remote.dto.UpdateSchemaRequest
import com.interlinedlist.android.feature.lists.data.remote.dto.UpdateWatcherRoleRequest
import com.interlinedlist.android.feature.lists.data.remote.dto.WatchingResponse
import com.interlinedlist.android.feature.lists.data.remote.dto.WatcherUsersResponse
import com.interlinedlist.android.feature.lists.data.remote.dto.WatchersResponse
import com.interlinedlist.android.feature.lists.data.remote.dto.WatchingStatusDto
import kotlinx.serialization.json.JsonElement
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Retrofit description of the InterlinedList Lists API used by this module. The
 * shared Retrofit singleton supplies the base URL and Bearer auth, so these calls
 * are authenticated.
 */
interface ListsApi {

    @GET("api/lists")
    suspend fun getLists(
        @Query("limit") limit: Int,
        @Query("offset") offset: Int,
    ): ListsResponse

    @GET("api/lists/search")
    suspend fun searchLists(
        @Query("q") query: String,
        @Query("limit") limit: Int,
        @Query("offset") offset: Int,
    ): ListsResponse

    @POST("api/lists")
    suspend fun createList(@Body body: CreateListRequest): ListEnvelope

    @GET("api/lists/{id}")
    suspend fun getList(@Path("id") id: String): ListEnvelope

    @DELETE("api/lists/{id}")
    suspend fun deleteList(@Path("id") id: String)

    /** The schema DSL — shape is dynamic, so it is received as a raw element. */
    @GET("api/lists/{id}/schema")
    suspend fun getSchema(@Path("id") id: String): JsonElement

    /** Replaces a list's schema with the serialised DSL in [body]. */
    @PUT("api/lists/{id}/schema")
    suspend fun updateSchema(
        @Path("id") id: String,
        @Body body: UpdateSchemaRequest,
    ): SchemaEnvelope

    /** Manual re-sync of a GitHub-backed list. */
    @POST("api/lists/{id}/refresh")
    suspend fun refreshList(@Path("id") id: String): RefreshResultDto

    @GET("api/lists/{id}/watchers")
    suspend fun getWatchers(
        @Path("id") id: String,
        @Query("limit") limit: Int,
        @Query("offset") offset: Int,
    ): WatchersResponse

    @POST("api/lists/{id}/watchers")
    suspend fun addWatcher(
        @Path("id") id: String,
        @Body body: AddWatcherRequest,
    )

    @GET("api/lists/{id}/watchers/me")
    suspend fun getWatchingStatus(@Path("id") id: String): WatchingStatusDto

    @GET("api/lists/{id}/watchers/users")
    suspend fun searchWatcherUsers(
        @Path("id") id: String,
        @Query("search") search: String,
        @Query("excludeWatchers") excludeWatchers: Boolean,
        @Query("limit") limit: Int,
        @Query("offset") offset: Int,
    ): WatcherUsersResponse

    @PUT("api/lists/{id}/watchers/{userId}")
    suspend fun updateWatcherRole(
        @Path("id") id: String,
        @Path("userId") userId: String,
        @Body body: UpdateWatcherRoleRequest,
    )

    @DELETE("api/lists/{id}/watchers/{userId}")
    suspend fun removeWatcher(
        @Path("id") id: String,
        @Path("userId") userId: String,
    )

    @GET("api/lists/connections")
    suspend fun getConnections(): ConnectionsResponse

    @POST("api/lists/connections")
    suspend fun createConnection(@Body body: CreateConnectionRequest): ConnectionEnvelope

    @DELETE("api/lists/connections/{id}")
    suspend fun deleteConnection(@Path("id") id: String)

    @GET("api/lists/{id}/data")
    suspend fun getRows(
        @Path("id") id: String,
        @Query("limit") limit: Int,
        @Query("offset") offset: Int,
    ): RowsResponse

    @POST("api/lists/{id}/data")
    suspend fun createRow(
        @Path("id") id: String,
        @Body body: RowWriteRequest,
    ): RowEnvelope

    @PUT("api/lists/{id}/data/{rowId}")
    suspend fun updateRow(
        @Path("id") id: String,
        @Path("rowId") rowId: String,
        @Body body: RowWriteRequest,
    ): RowEnvelope

    @DELETE("api/lists/{id}/data/{rowId}")
    suspend fun deleteRow(
        @Path("id") id: String,
        @Path("rowId") rowId: String,
    )

    @GET("api/folders")
    suspend fun getFolders(): FoldersResponse

    @POST("api/folders")
    suspend fun createFolder(@Body body: CreateFolderRequest): FolderDto

    // --- Sharing -----------------------------------------------------------

    /** Existing public share links for a list. */
    @GET("api/lists/{id}/share-links")
    suspend fun getShareLinks(@Path("id") id: String): ShareLinksResponse

    /** Creates a share link granting the requested role (optionally expiring). */
    @POST("api/lists/{id}/share-links")
    suspend fun createShareLink(
        @Path("id") id: String,
        @Body body: CreateShareLinkRequest,
    ): ShareLinkEnvelope

    /** Revokes (deletes) a share link by its token. */
    @DELETE("api/lists/{id}/share-links/{token}")
    suspend fun revokeShareLink(
        @Path("id") id: String,
        @Path("token") token: String,
    )

    /** Lists owned by other users that the current user has access to ("Shared with me"). */
    @GET("api/lists/watching")
    suspend fun getWatchingLists(): WatchingResponse

    /** Resolves a public share link to a read-only preview of the shared list. */
    @GET("api/lists/shared/{token}")
    suspend fun resolveSharedList(@Path("token") token: String): SharedListResponse

    /** Claims edit/admin access to a shared list as the logged-in user. */
    @POST("api/lists/shared/{token}")
    suspend fun claimSharedList(@Path("token") token: String)
}
