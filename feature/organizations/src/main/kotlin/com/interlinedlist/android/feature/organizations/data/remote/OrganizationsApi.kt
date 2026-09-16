package com.interlinedlist.android.feature.organizations.data.remote

import com.interlinedlist.android.feature.organizations.data.remote.dto.AddMemberRequest
import com.interlinedlist.android.feature.organizations.data.remote.dto.CreateOrganizationRequest
import com.interlinedlist.android.feature.organizations.data.remote.dto.JoinOrganizationRequest
import com.interlinedlist.android.feature.organizations.data.remote.dto.LinkedInAssignmentRequest
import com.interlinedlist.android.feature.organizations.data.remote.dto.LinkedInAssignmentResponse
import com.interlinedlist.android.feature.organizations.data.remote.dto.MembersResponse
import com.interlinedlist.android.feature.organizations.data.remote.dto.OrgLinkedInStatusResponse
import com.interlinedlist.android.feature.organizations.data.remote.dto.OrgUsersResponse
import com.interlinedlist.android.feature.organizations.data.remote.dto.OrganizationEnvelope
import com.interlinedlist.android.feature.organizations.data.remote.dto.OrganizationsResponse
import com.interlinedlist.android.feature.organizations.data.remote.dto.UpdateMemberRequest
import com.interlinedlist.android.feature.organizations.data.remote.dto.UpdateOrganizationRequest
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Retrofit description of the InterlinedList Organizations API used by this
 * module. The shared Retrofit singleton supplies the base URL and Bearer auth, so
 * these calls are authenticated.
 */
interface OrganizationsApi {

    @GET("api/organizations")
    suspend fun getOrganizations(
        @Query("limit") limit: Int,
        @Query("offset") offset: Int,
    ): OrganizationsResponse

    /** The current user's org memberships (sync-token authed); mirrors the index shape. */
    @GET("api/user/organizations")
    suspend fun getUserOrganizations(): OrganizationsResponse

    /**
     * Joins a public organization. Confirmed live: the body key is
     * `organizationId`, the response is 201 `{ message, membership }`, a private
     * org answers 403 and an existing membership answers 409.
     */
    @POST("api/user/organizations")
    suspend fun joinOrganization(@Body body: JoinOrganizationRequest)

    @POST("api/organizations")
    suspend fun createOrganization(@Body body: CreateOrganizationRequest): OrganizationEnvelope

    @GET("api/organizations/{id}")
    suspend fun getOrganization(@Path("id") id: String): OrganizationEnvelope

    @PUT("api/organizations/{id}")
    suspend fun updateOrganization(
        @Path("id") id: String,
        @Body body: UpdateOrganizationRequest,
    ): OrganizationEnvelope

    @DELETE("api/organizations/{id}")
    suspend fun deleteOrganization(@Path("id") id: String)

    @GET("api/organizations/{id}/members")
    suspend fun getMembers(
        @Path("id") id: String,
        @Query("limit") limit: Int,
        @Query("offset") offset: Int,
    ): MembersResponse

    @POST("api/organizations/{id}/members")
    suspend fun addMember(
        @Path("id") id: String,
        @Body body: AddMemberRequest,
    )

    @PUT("api/organizations/{id}/members/{userId}")
    suspend fun updateMember(
        @Path("id") id: String,
        @Path("userId") userId: String,
        @Body body: UpdateMemberRequest,
    )

    /**
     * Removes a membership. Used both for removing someone else and for *leaving*
     * (passing the signed-in user's own id) — the API has no separate leave route.
     */
    @DELETE("api/organizations/{id}/members/{userId}")
    suspend fun removeMember(
        @Path("id") id: String,
        @Path("userId") userId: String,
    )

    /** Users who could be added as members (excludes existing members). */
    @GET("api/organizations/{id}/users")
    suspend fun searchOrgUsers(
        @Path("id") id: String,
        @Query("search") search: String,
        @Query("excludeMembers") excludeMembers: Boolean,
        @Query("limit") limit: Int,
        @Query("offset") offset: Int,
    ): OrgUsersResponse

    /**
     * The organization's shared LinkedIn credential and the pages discovered for
     * it. Members-only: an outsider gets
     * `403 {"error":"Not a member of this organization"}` (verified live), and an
     * organization with no credential answers `200 {"credential":null,"role":…}`.
     */
    @GET("api/organizations/{id}/linkedin/status")
    suspend fun getLinkedInStatus(@Path("id") id: String): OrgLinkedInStatusResponse

    /**
     * Assigns one member to one company page — or clears their assignment when the
     * body carries no `pageId`. Owner/admin only (`403 "Admin or owner required"`).
     */
    @PUT("api/organizations/{id}/linkedin/assignments")
    suspend fun putLinkedInAssignment(
        @Path("id") id: String,
        @Body body: LinkedInAssignmentRequest,
    ): LinkedInAssignmentResponse

    /**
     * Disconnects the shared credential; the server clears the assignments with
     * it. Answers `404 {"error":"No LinkedIn credential found"}` when there is
     * none (verified live).
     */
    @DELETE("api/organizations/{id}/linkedin/credential")
    suspend fun deleteLinkedInCredential(@Path("id") id: String)

    /**
     * Re-discovers the organization's company pages. Answers
     * `404 {"error":"No active LinkedIn credential for this organization"}` when
     * the organization has not connected LinkedIn (verified live).
     */
    @POST("api/organizations/{id}/linkedin/sync-pages")
    suspend fun syncLinkedInPages(@Path("id") id: String)
}
