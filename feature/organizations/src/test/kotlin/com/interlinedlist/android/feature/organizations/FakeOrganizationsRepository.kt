package com.interlinedlist.android.feature.organizations

import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.core.common.result.AppError
import com.interlinedlist.android.feature.organizations.data.OrganizationsRepository
import com.interlinedlist.android.feature.organizations.domain.MemberCandidate
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

    var refreshCount = 0
    var loadMoreCount = 0
    var addMemberCount = 0
    var removeMemberCount = 0
    var lastMemberSearch: String? = null
    var lastUpdate: Triple<String?, String?, Boolean?>? = null

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

    override suspend fun getOrganization(id: String): ApiResult<Organization> =
        getResult ?: ApiResult.Success(
            Organization(id, "Org $id", null, null, false, 0, OrgRole.MEMBER, null),
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

    override suspend fun updateMemberRole(orgId: String, userId: String, role: OrgRole): ApiResult<Unit> =
        updateRoleResult

    override suspend fun removeMember(orgId: String, userId: String): ApiResult<Unit> {
        removeMemberCount++
        return removeMemberResult
    }

    companion object {
        fun subscriptionFailure(): ApiResult.Failure =
            ApiResult.Failure(AppError.SubscriptionRequired("Organizations require an active subscription"))
    }
}
