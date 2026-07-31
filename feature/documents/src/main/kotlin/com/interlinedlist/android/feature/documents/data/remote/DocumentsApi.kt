package com.interlinedlist.android.feature.documents.data.remote

import com.interlinedlist.android.feature.documents.data.remote.dto.CollaboratorEnvelope
import com.interlinedlist.android.feature.documents.data.remote.dto.CollaboratorUsersResponse
import com.interlinedlist.android.feature.documents.data.remote.dto.CollaboratorsResponse
import com.interlinedlist.android.feature.documents.data.remote.dto.CreateDocumentRequest
import com.interlinedlist.android.feature.documents.data.remote.dto.CreateFolderRequest
import com.interlinedlist.android.feature.documents.data.remote.dto.CreateShareLinkRequest
import com.interlinedlist.android.feature.documents.data.remote.dto.DocumentListResponse
import com.interlinedlist.android.feature.documents.data.remote.dto.DocumentResponse
import com.interlinedlist.android.feature.documents.data.remote.dto.FolderListResponse
import com.interlinedlist.android.feature.documents.data.remote.dto.FolderResponse
import com.interlinedlist.android.feature.documents.data.remote.dto.FromTemplateRequest
import com.interlinedlist.android.feature.documents.data.remote.dto.InviteCollaboratorRequest
import com.interlinedlist.android.feature.documents.data.remote.dto.PresenceResponse
import com.interlinedlist.android.feature.documents.data.remote.dto.ShareLinkEnvelope
import com.interlinedlist.android.feature.documents.data.remote.dto.ShareLinksResponse
import com.interlinedlist.android.feature.documents.data.remote.dto.SharedDocumentResponse
import com.interlinedlist.android.feature.documents.data.remote.dto.SyncPullResponse
import com.interlinedlist.android.feature.documents.data.remote.dto.SyncPushRequest
import com.interlinedlist.android.feature.documents.data.remote.dto.TreeResponse
import com.interlinedlist.android.feature.documents.data.remote.dto.UpdateCollaboratorRoleRequest
import com.interlinedlist.android.feature.documents.data.remote.dto.UpdateDocumentRequest
import com.interlinedlist.android.feature.documents.data.remote.dto.UpdateFolderRequest
import okhttp3.MultipartBody
import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.PATCH
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

    /**
     * Partial update with optimistic concurrency. The document's current [ifMatch]
     * version is sent as the `If-Match` header; a stale value is rejected by the
     * server (surfaced as a conflict) rather than overwriting a concurrent edit.
     */
    @PATCH("api/documents/{id}")
    suspend fun patchDocument(
        @Path("id") id: String,
        @Body body: UpdateDocumentRequest,
        @Header("If-Match") ifMatch: String?,
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

    // --- Sharing -----------------------------------------------------------

    /** Existing public share links for a document. */
    @GET("api/documents/{id}/share-links")
    suspend fun getShareLinks(@Path("id") id: String): ShareLinksResponse

    /** Creates a share link granting the requested role (optionally expiring). */
    @POST("api/documents/{id}/share-links")
    suspend fun createShareLink(
        @Path("id") id: String,
        @Body body: CreateShareLinkRequest,
    ): ShareLinkEnvelope

    /** Revokes (deletes) a share link by its token. */
    @DELETE("api/documents/{id}/share-links/{token}")
    suspend fun revokeShareLink(
        @Path("id") id: String,
        @Path("token") token: String,
    )

    /** Resolves a public share link to a read-only preview of the shared document. */
    @GET("api/documents/shared/{token}")
    suspend fun resolveSharedDocument(@Path("token") token: String): SharedDocumentResponse

    /** Claims edit/admin access to a shared document as the logged-in user. */
    @POST("api/documents/shared/{token}")
    suspend fun claimSharedDocument(@Path("token") token: String)

    // --- Delta sync --------------------------------------------------------

    /**
     * Delta PULL: folders + documents changed since [lastSyncAt] (both upserts and
     * `deletedAt` tombstones), plus a fresh `lastSyncAt` cursor to persist. A null
     * cursor returns the full set.
     */
    @GET("api/documents/sync")
    suspend fun pullSync(@Query("lastSyncAt") lastSyncAt: String?): SyncPullResponse

    /** Batch PUSH of queued local operations. */
    @POST("api/documents/sync")
    suspend fun pushSync(@Body body: SyncPushRequest): SyncPullResponse

    /** Combined folder + document sidebar tree (folders embed their documents). */
    @GET("api/documents/tree")
    suspend fun getTree(): TreeResponse

    // --- Collaborators -----------------------------------------------------

    @GET("api/documents/{id}/collaborators")
    suspend fun getCollaborators(@Path("id") id: String): CollaboratorsResponse

    /** Searches users who can be invited (optionally excluding current collaborators). */
    @GET("api/documents/{id}/collaborators/users")
    suspend fun searchCollaboratorUsers(
        @Path("id") id: String,
        @Query("search") search: String?,
        @Query("limit") limit: Int? = null,
        @Query("excludeCollaborators") excludeCollaborators: Boolean? = null,
    ): CollaboratorUsersResponse

    @POST("api/documents/{id}/collaborators")
    suspend fun inviteCollaborator(
        @Path("id") id: String,
        @Body body: InviteCollaboratorRequest,
    ): CollaboratorEnvelope

    @PUT("api/documents/{id}/collaborators/{userId}")
    suspend fun updateCollaboratorRole(
        @Path("id") id: String,
        @Path("userId") userId: String,
        @Body body: UpdateCollaboratorRoleRequest,
    ): CollaboratorEnvelope

    @DELETE("api/documents/{id}/collaborators/{userId}")
    suspend fun removeCollaborator(
        @Path("id") id: String,
        @Path("userId") userId: String,
    )

    // --- Presence ----------------------------------------------------------

    /** Heartbeat: marks the current user present on the document; returns everyone here. */
    @POST("api/documents/{id}/presence")
    suspend fun sendPresence(@Path("id") id: String): PresenceResponse

    /** Leaves the document (stops the heartbeat). */
    @DELETE("api/documents/{id}/presence")
    suspend fun leavePresence(@Path("id") id: String)
}
