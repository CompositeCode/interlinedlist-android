package com.interlinedlist.android.feature.organizations.data

import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.feature.organizations.domain.MemberCandidate
import com.interlinedlist.android.feature.organizations.domain.OrgLinkedInStatus
import com.interlinedlist.android.feature.organizations.domain.OrgMember
import com.interlinedlist.android.feature.organizations.domain.OrgRole
import com.interlinedlist.android.feature.organizations.domain.Organization
import com.interlinedlist.android.feature.organizations.domain.Paged
import kotlinx.coroutines.flow.Flow

/**
 * Offline-first access to the Organizations domain. The index streams from Room
 * (the source of truth) via [observeOrganizations]; refresh/load-more calls update
 * the cache and report the pagination state so the UI knows whether more pages
 * remain. Detail metadata + member mutations write through to the API.
 */
interface OrganizationsRepository {

    /** Cached organizations, emitted from Room and re-emitted on every local change. */
    fun observeOrganizations(): Flow<List<Organization>>

    /** Fetches the first page from the API and replaces the cache. Returns pagination. */
    suspend fun refreshOrganizations(limit: Int = DEFAULT_PAGE_SIZE): ApiResult<Paged<Organization>>

    /** Fetches a further page and appends it to the cache. */
    suspend fun loadMoreOrganizations(offset: Int, limit: Int = DEFAULT_PAGE_SIZE): ApiResult<Paged<Organization>>

    /** Creates an organization; caches the result and returns it. */
    suspend fun createOrganization(
        name: String,
        description: String?,
        isPublic: Boolean,
    ): ApiResult<Organization>

    /** Loads a single organization's metadata, caching it. */
    suspend fun getOrganization(id: String): ApiResult<Organization>

    /** Updates an organization's name/description/visibility and refreshes the cache. */
    suspend fun updateOrganization(
        id: String,
        name: String?,
        description: String?,
        isPublic: Boolean?,
    ): ApiResult<Organization>

    /** Deletes an organization and evicts it from the cache. */
    suspend fun deleteOrganization(id: String): ApiResult<Unit>

    /**
     * Joins a public organization (`POST /api/user/organizations`). On success the
     * cached row is re-read so it carries the new role and member count.
     */
    suspend fun joinOrganization(orgId: String): ApiResult<Unit>

    /**
     * Leaves an organization by removing the signed-in user's own membership. The
     * server refuses to orphan an organization: the last owner gets a 400
     * ("Cannot remove the last owner"), which is reported as a failure.
     */
    suspend fun leaveOrganization(orgId: String): ApiResult<Unit>

    /** Members of an organization (users granted access), with their roles. */
    suspend fun getMembers(orgId: String, limit: Int = DEFAULT_PAGE_SIZE): ApiResult<List<OrgMember>>

    /** Searches users who could be added as members (excludes existing members). */
    suspend fun searchMemberCandidates(
        orgId: String,
        query: String,
        limit: Int = DEFAULT_PAGE_SIZE,
    ): ApiResult<List<MemberCandidate>>

    /** Adds a user as a member with the given role. */
    suspend fun addMember(orgId: String, userId: String, role: OrgRole): ApiResult<Unit>

    /** Changes an existing member's role. */
    suspend fun updateMemberRole(orgId: String, userId: String, role: OrgRole): ApiResult<Unit>

    /** Removes a user's membership from the organization. */
    suspend fun removeMember(orgId: String, userId: String): ApiResult<Unit>

    /**
     * The organization's shared LinkedIn credential, its company pages and the
     * per-member page assignments. An organization with no credential is a
     * success carrying [OrgLinkedInStatus.NOT_CONNECTED], not a failure.
     */
    suspend fun getLinkedInStatus(orgId: String): ApiResult<OrgLinkedInStatus>

    /**
     * Assigns [userId] to the company page [pageId], or clears their assignment
     * when [pageId] is null. Returns whether the member ends up assigned, as the
     * server reports it.
     */
    suspend fun assignLinkedInPage(orgId: String, userId: String, pageId: String?): ApiResult<Boolean>

    /**
     * Disconnects the shared credential. The server clears the page assignments
     * with it, so the organization can no longer post to its company pages.
     */
    suspend fun removeLinkedInCredential(orgId: String): ApiResult<Unit>

    /** Re-discovers the company pages and returns the refreshed status. */
    suspend fun syncLinkedInPages(orgId: String): ApiResult<OrgLinkedInStatus>

    companion object {
        const val DEFAULT_PAGE_SIZE = 20
    }
}
