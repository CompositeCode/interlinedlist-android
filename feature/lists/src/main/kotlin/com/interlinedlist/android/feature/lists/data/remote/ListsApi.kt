package com.interlinedlist.android.feature.lists.data.remote

import com.interlinedlist.android.feature.lists.data.remote.dto.CreateFolderRequest
import com.interlinedlist.android.feature.lists.data.remote.dto.CreateListRequest
import com.interlinedlist.android.feature.lists.data.remote.dto.FolderDto
import com.interlinedlist.android.feature.lists.data.remote.dto.FoldersResponse
import com.interlinedlist.android.feature.lists.data.remote.dto.ListEnvelope
import com.interlinedlist.android.feature.lists.data.remote.dto.ListsResponse
import com.interlinedlist.android.feature.lists.data.remote.dto.RowEnvelope
import com.interlinedlist.android.feature.lists.data.remote.dto.RowWriteRequest
import com.interlinedlist.android.feature.lists.data.remote.dto.RowsResponse
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
}
