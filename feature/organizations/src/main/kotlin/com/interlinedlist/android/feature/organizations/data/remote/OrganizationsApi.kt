package com.interlinedlist.android.feature.organizations.data.remote

import com.interlinedlist.android.feature.organizations.data.remote.dto.AddMemberRequest
import com.interlinedlist.android.feature.organizations.data.remote.dto.CreateOrganizationRequest
import com.interlinedlist.android.feature.organizations.data.remote.dto.MembersResponse
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
}
