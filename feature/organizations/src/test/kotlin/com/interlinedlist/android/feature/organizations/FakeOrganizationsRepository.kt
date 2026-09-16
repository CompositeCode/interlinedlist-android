package com.interlinedlist.android.feature.organizations

import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.core.common.result.AppError
import com.interlinedlist.android.feature.organizations.data.OrganizationsRepository
import com.interlinedlist.android.feature.organizations.domain.MemberCandidate
import com.interlinedlist.android.feature.organizations.domain.OrgLinkedInStatus
import com.interlinedlist.android.feature.organizations.domain.OrgMember
import com.interlinedlist.android.feature.organizations.domain.OrgRole
import com.interlinedlist.android.feature.organizations.domain.Organization
import com.interlinedlist.android.feature.organizations.domain.Paged
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * In-memory [OrganizationsRepository] for ViewModel tests. The cache is a
 * StateFlow so tests can assert the offline-first stream, and each operation's
 * result is configurable to exercise success / failure / subscription-gate paths.
 */
class FakeOrganizationsRepository : OrganizationsRepository {

    val cache = MutableStateFlow<List<Organization>>(emptyList())

    var refreshResult: ApiResult<Paged<Organization>> =
        ApiResult.Success(Paged(emptyList(), hasMore = false, total = 0, offset = 0))
    var loadMoreResult: ApiResult<Paged<Organization>> = refreshResult
    var createResult: ApiResult<Organization>? = null
    var getResult: ApiResult<Organization>? = null
    var updateResult: ApiResult<Organization>? = null
    var deleteResult: ApiResult<Unit> = ApiResult.Success(Unit)
    var membersResult: ApiResult<List<OrgMember>> = ApiResult.Success(emptyList())
    var candidatesResult: ApiResult<List<MemberCandidate>> = ApiResult.Success(emptyList())
    var addMemberResult: ApiResult<Unit> = ApiResult.Success(Unit)
    var updateRoleResult: ApiResult<Unit> = ApiResult.Success(Unit)
    var removeMemberResult: ApiResult<Unit> = ApiResult.Success(Unit)
    var updateRoleCount = 0
    var joinResult: ApiResult<Unit> = ApiResult.Success(Unit)
    var linkedInStatusResult: ApiResult<OrgLinkedInStatus> = ApiResult.Success(OrgLinkedInStatus.NOT_CONNECTED)
    var assignPageResult: ApiResult<Boolean>? = null
    var removeCredentialResult: ApiResult<Unit> = ApiResult.Success(Unit)
    var syncPagesResult: ApiResult<OrgLinkedInStatus>? = null
    var leaveResult: ApiResult<Unit> = ApiResult.Success(Unit)

    var refreshCount = 0
    var loadMoreCount = 0
    var addMemberCount = 0
    var removeMemberCount = 0
    var joinedOrgIds = mutableListOf<String>()
    var leftOrgIds = mutableListOf<String>()
    var lastMemberSearch: String? = null
    var lastUpdate: Triple<String?, String?, Boolean?>? = null
    var linkedInStatusCount = 0
    var syncPagesCount = 0
    var removeCredentialCount = 0
    /** Every assignment the UI asked for, as (userId, pageId) — pageId null clears it. */
    val assignments = mutableListOf<Pair<String, String?>>()

    override fun observeOrganizations(): Flow<List<Organization>> = cache

    override suspend fun refreshOrganizations(limit: Int): ApiResult<Paged<Organization>> {
        refreshCount++
        (refreshResult as? ApiResult.Success)?.let { cache.value = it.data.items }
        return refreshResult
    }

    override suspend fun loadMoreOrganizations(offset: Int, limit: Int): ApiResult<Paged<Organization>> {
        loadMoreCount++
        (loadMoreResult as? ApiResult.Success)?.let { cache.value = cache.value + it.data.items }
        return loadMoreResult
    }

    override suspend fun createOrganization(
        name: String,
        description: String?,
        isPublic: Boolean,
    ): ApiResult<Organization> = createResult ?: ApiResult.Success(
        Organization("new", name, description, null, isPublic, 1, OrgRole.OWNER, null),
    )

    /**
     * Defaults to an organization the caller owns, so management tests exercise the
     * mutation rather than the permission gate. Tests that care about a narrower
     * role (or about non-membership) set [getResult] explicitly.
     */
    override suspend fun getOrganization(id: String): ApiResult<Organization> =
        getResult ?: ApiResult.Success(
            Organization(id, "Org $id", null, null, false, 0, OrgRole.OWNER, null),
        )

    override suspend fun updateOrganization(
        id: String,
        name: String?,
        description: String?,
        isPublic: Boolean?,
    ): ApiResult<Organization> {
        lastUpdate = Triple(name, description, isPublic)
        return updateResult ?: ApiResult.Success(
            Organization(id, name ?: "Org $id", description, null, isPublic ?: false, 0, OrgRole.OWNER, null),
        )
    }

    override suspend fun deleteOrganization(id: String): ApiResult<Unit> {
        if (deleteResult is ApiResult.Success) cache.value = cache.value.filterNot { it.id == id }
        return deleteResult
    }

    override suspend fun joinOrganization(orgId: String): ApiResult<Unit> {
        joinedOrgIds += orgId
        if (joinResult is ApiResult.Success) {
            // Mirrors the repository: the cached row gains the caller's membership.
            cache.value = cache.value.map { org ->
                if (org.id == orgId) org.copy(role = OrgRole.MEMBER, memberCount = org.memberCount + 1) else org
            }
        }
        return joinResult
    }

    override suspend fun leaveOrganization(orgId: String): ApiResult<Unit> {
        leftOrgIds += orgId
        if (leaveResult is ApiResult.Success) {
            cache.value = cache.value.map { org ->
                if (org.id == orgId) org.copy(role = null, memberCount = (org.memberCount - 1).coerceAtLeast(0)) else org
            }
        }
        return leaveResult
    }

    override suspend fun getMembers(orgId: String, limit: Int): ApiResult<List<OrgMember>> = membersResult

    override suspend fun searchMemberCandidates(
        orgId: String,
        query: String,
        limit: Int,
    ): ApiResult<List<MemberCandidate>> {
        lastMemberSearch = query
        return candidatesResult
    }

    override suspend fun addMember(orgId: String, userId: String, role: OrgRole): ApiResult<Unit> {
        addMemberCount++
        return addMemberResult
    }

    override suspend fun updateMemberRole(orgId: String, userId: String, role: OrgRole): ApiResult<Unit> {
        updateRoleCount++
        return updateRoleResult
    }

    override suspend fun removeMember(orgId: String, userId: String): ApiResult<Unit> {
        removeMemberCount++
        return removeMemberResult
    }

    override suspend fun getLinkedInStatus(orgId: String): ApiResult<OrgLinkedInStatus> {
        linkedInStatusCount++
        return linkedInStatusResult
    }

    override suspend fun assignLinkedInPage(
        orgId: String,
        userId: String,
        pageId: String?,
    ): ApiResult<Boolean> {
        assignments += userId to pageId
        return assignPageResult ?: ApiResult.Success(pageId != null)
    }

    override suspend fun removeLinkedInCredential(orgId: String): ApiResult<Unit> {
        removeCredentialCount++
        if (removeCredentialResult is ApiResult.Success) {
            linkedInStatusResult = ApiResult.Success(OrgLinkedInStatus.NOT_CONNECTED)
        }
        return removeCredentialResult
    }

    override suspend fun syncLinkedInPages(orgId: String): ApiResult<OrgLinkedInStatus> {
        syncPagesCount++
        return syncPagesResult ?: linkedInStatusResult
    }

    companion object {
        fun subscriptionFailure(): ApiResult.Failure =
            ApiResult.Failure(AppError.SubscriptionRequired("Organizations require an active subscription"))

        /** The live 400 the server returns when the only owner tries to leave. */
        fun lastOwnerFailure(): ApiResult.Failure =
            ApiResult.Failure(AppError.Unknown("Cannot remove the last owner"))

        /** The live 400 the server returns when the only owner would be demoted. */
        fun lastOwnerDemoteFailure(): ApiResult.Failure =
            ApiResult.Failure(AppError.Unknown("Cannot demote the last owner"))
    }
}
