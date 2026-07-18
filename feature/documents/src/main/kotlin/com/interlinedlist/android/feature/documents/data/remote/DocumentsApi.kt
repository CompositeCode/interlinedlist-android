package com.interlinedlist.android.feature.documents.data.remote

import com.interlinedlist.android.feature.documents.data.remote.dto.CreateDocumentRequest
import com.interlinedlist.android.feature.documents.data.remote.dto.CreateFolderRequest
import com.interlinedlist.android.feature.documents.data.remote.dto.DocumentListResponse
import com.interlinedlist.android.feature.documents.data.remote.dto.DocumentResponse
import com.interlinedlist.android.feature.documents.data.remote.dto.FolderListResponse
import com.interlinedlist.android.feature.documents.data.remote.dto.FolderResponse
import com.interlinedlist.android.feature.documents.data.remote.dto.FromTemplateRequest
import com.interlinedlist.android.feature.documents.data.remote.dto.UpdateDocumentRequest
import com.interlinedlist.android.feature.documents.data.remote.dto.UpdateFolderRequest
import okhttp3.MultipartBody
import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Retrofit description of the Documents endpoints. The shared Retrofit instance
 * already carries the base URL and Bearer token, so these calls are authed.
 */
interface DocumentsApi {

    // --- Documents ---------------------------------------------------------

    /** Root/unfiled documents (folderId == null). */
    @GET("api/documents")
    suspend fun getRootDocuments(): DocumentListResponse

    @POST("api/documents")
    suspend fun createDocument(@Body body: CreateDocumentRequest): DocumentResponse

    @GET("api/documents/{id}")
    suspend fun getDocument(@Path("id") id: String): DocumentResponse

    @PUT("api/documents/{id}")
    suspend fun updateDocument(
        @Path("id") id: String,
        @Body body: UpdateDocumentRequest,
    ): DocumentResponse

    @DELETE("api/documents/{id}")
    suspend fun deleteDocument(@Path("id") id: String)

    @GET("api/documents/search")
    suspend fun searchDocuments(
        @Query("q") query: String,
        @Query("limit") limit: Int? = null,
        @Query("offset") offset: Int? = null,
    ): DocumentListResponse

    @Multipart
    @POST("api/documents/{id}/images/upload")
    suspend fun uploadImage(
        @Path("id") documentId: String,
        @Part image: MultipartBody.Part,
    ): ResponseBody

    // --- Folders -----------------------------------------------------------

    /**
     * The whole folder tree in one call: folders nested via `parentId`, each
     * carrying its own embedded `documents`.
     */
    @GET("api/documents/folders")
    suspend fun getFolders(): FolderListResponse

    @POST("api/documents/folders")
    suspend fun createFolder(@Body body: CreateFolderRequest): FolderResponse

    @GET("api/documents/folders/{id}")
    suspend fun getFolder(@Path("id") id: String): FolderResponse

    /** Rename and/or move (re-parent) a folder. */
    @PUT("api/documents/folders/{id}")
    suspend fun updateFolder(
        @Path("id") id: String,
        @Body body: UpdateFolderRequest,
    ): FolderResponse

    @DELETE("api/documents/folders/{id}")
    suspend fun deleteFolder(@Path("id") id: String)

    @GET("api/documents/folders/{id}/documents")
    suspend fun getFolderDocuments(@Path("id") folderId: String): DocumentListResponse

    // --- Templates ---------------------------------------------------------

    @GET("api/documents/templates")
    suspend fun getTemplates(): DocumentListResponse

    @POST("api/documents/from-template")
    suspend fun createFromTemplate(@Body body: FromTemplateRequest): DocumentResponse
}
