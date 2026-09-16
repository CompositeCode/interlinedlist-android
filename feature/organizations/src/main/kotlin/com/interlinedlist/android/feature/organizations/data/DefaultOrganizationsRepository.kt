package com.interlinedlist.android.feature.organizations.data

import com.interlinedlist.android.core.common.dispatcher.DispatcherProvider
import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.core.common.result.AppError
import com.interlinedlist.android.core.common.result.map
import com.interlinedlist.android.core.network.error.safeApiCall
import com.interlinedlist.android.feature.organizations.data.local.OrganizationDao
import com.interlinedlist.android.feature.organizations.data.remote.OrganizationsApi
import com.interlinedlist.android.feature.organizations.data.remote.dto.AddMemberRequest
import com.interlinedlist.android.feature.organizations.data.remote.dto.CreateOrganizationRequest
import com.interlinedlist.android.feature.organizations.data.remote.dto.JoinOrganizationRequest
import com.interlinedlist.android.feature.organizations.data.remote.dto.OrganizationsResponse
import com.interlinedlist.android.feature.organizations.data.remote.dto.UpdateMemberRequest
import com.interlinedlist.android.feature.organizations.data.remote.dto.UpdateOrganizationRequest
import com.interlinedlist.android.feature.organizations.domain.MemberCandidate
import com.interlinedlist.android.feature.organizations.domain.OrgMember
import com.interlinedlist.android.feature.organizations.domain.OrgRole
import com.interlinedlist.android.feature.organizations.domain.Organization
import com.interlinedlist.android.feature.organizations.domain.Paged
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Offline-first [OrganizationsRepository]. The index is served from Room and
 * refreshed from the API (Room stays the source of truth); mutations write through
 * to the API and then update the cache. Failures are normalised to [ApiResult] via
 * [safeApiCall], which maps a subscription 403 to `AppError.SubscriptionRequired`.
 */
class DefaultOrganizationsRepository @Inject constructor(
    private val api: OrganizationsApi,
    private val dao: OrganizationDao,
    private val json: kotlinx.serialization.json.Json,
    private val currentUserId: CurrentUserIdProvider,
    private val dispatchers: DispatcherProvider,
) : OrganizationsRepository {

    override fun observeOrganizations(): Flow<List<Organization>> =
        dao.observeOrganizations().map { entities -> entities.map(OrganizationMapper::fromEntity) }

    override suspend fun refreshOrganizations(limit: Int): ApiResult<Paged<Organization>> =
        withContext(dispatchers.io) {
            when (val result = safeApiCall(json) { api.getOrganizations(limit = limit, offset = 0) }) {
                is ApiResult.Success -> {
                    val orgs = result.data.items.map(OrganizationMapper::fromDto)
                    // First page → replace so server-side deletions are reflected.
                    dao.replaceAll(orgs.map(OrganizationMapper::toEntity))
                    ApiResult.Success(result.data.toPaged(orgs, offset = 0, limit = limit))
                }
                is ApiResult.Failure -> result
            }
        }

    override suspend fun loadMoreOrganizations(offset: Int, limit: Int): ApiResult<Paged<Organization>> =
        withContext(dispatchers.io) {
            when (val result = safeApiCall(json) { api.getOrganizations(limit = limit, offset = offset) }) {
                is ApiResult.Success -> {
                    val orgs = result.data.items.map(OrganizationMapper::fromDto)
                    dao.upsertAll(orgs.map(OrganizationMapper::toEntity))
                    ApiResult.Success(result.data.toPaged(orgs, offset = offset, limit = limit))
                }
                is ApiResult.Failure -> result
            }
        }

    override suspend fun createOrganization(
        name: String,
        description: String?,
        isPublic: Boolean,
    ): ApiResult<Organization> = withContext(dispatchers.io) {
        val body = CreateOrganizationRequest(
            name = name,
            description = description,
            isPublic = isPublic,
        )
        when (val result = safeApiCall(json) { api.createOrganization(body) }) {
            is ApiResult.Success -> {
                val dto = result.data.org
                    ?: return@withContext ApiResult.Success(
                        Organization(
                            id = "", name = name, description = description, avatarUrl = null,
                            isPublic = isPublic, memberCount = 1, role = OrgRole.OWNER, updatedAt = null,
                        ),
                    )
                val org = OrganizationMapper.fromDto(dto)
                dao.upsert(OrganizationMapper.toEntity(org))
                ApiResult.Success(org)
            }
            is ApiResult.Failure -> result
        }
    }

    override suspend fun getOrganization(id: String): ApiResult<Organization> =
        withContext(dispatchers.io) {
            when (val result = safeApiCall(json) { api.getOrganization(id) }) {
                is ApiResult.Success -> {
                    val dto = result.data.org
                        ?: return@withContext ApiResult.Failure(AppError.NotFound("Organization not found"))
                    val org = OrganizationMapper.fromDto(dto)
                    dao.upsert(OrganizationMapper.toEntity(org))
                    ApiResult.Success(org)
                }
                is ApiResult.Failure -> result
            }
        }

    override suspend fun updateOrganization(
        id: String,
        name: String?,
        description: String?,
        isPublic: Boolean?,
    ): ApiResult<Organization> = withContext(dispatchers.io) {
        val body = UpdateOrganizationRequest(
            name = name?.trim()?.ifBlank { null },
            description = description?.trim(),
            isPublic = isPublic,
        )
        when (val result = safeApiCall(json) { api.updateOrganization(id, body) }) {
            // The PUT echo omits `userRole` and `memberCount` (verified live), so
            // mapping it straight through would make the editor look like a
            // non-member and hide the very actions they just used. Re-read instead,
            // which is authoritative and refreshes the cache.
            is ApiResult.Success -> getOrganization(id)
            is ApiResult.Failure -> result
        }
    }

    override suspend fun deleteOrganization(id: String): ApiResult<Unit> =
        withContext(dispatchers.io) {
            when (val result = safeApiCall(json) { api.deleteOrganization(id) }) {
                is ApiResult.Success -> {
                    dao.deleteById(id)
                    ApiResult.Success(Unit)
                }
                is ApiResult.Failure -> result
            }
        }

    override suspend fun joinOrganization(orgId: String): ApiResult<Unit> =
        withContext(dispatchers.io) {
            when (val result = safeApiCall(json) { api.joinOrganization(JoinOrganizationRequest(orgId)) }) {
                // Re-read the org so the cached row carries the new role and member
                // count; the index then renders "Member" instead of "Join".
                is ApiResult.Success -> {
                    getOrganization(orgId)
                    ApiResult.Success(Unit)
                }
                is ApiResult.Failure -> result
            }
        }

    override suspend fun leaveOrganization(orgId: String): ApiResult<Unit> =
        withContext(dispatchers.io) {
            val userId = currentUserId.currentUserId()?.takeIf { it.isNotBlank() }
                ?: return@withContext ApiResult.Failure(
                    AppError.Unauthorized("We couldn't confirm who you're signed in as. Sign in again and retry."),
                )
            when (val result = safeApiCall(json) { api.removeMember(orgId, userId) }) {
                is ApiResult.Success -> {
                    refreshAfterLeaving(orgId)
                    ApiResult.Success(Unit)
                }
                is ApiResult.Failure -> result
            }
        }

    /**
     * Re-reads an org just left so the cache drops the membership. A private org is
     * invisible to a non-member, so a 403/404 means it should leave the cache too.
     */
    private suspend fun refreshAfterLeaving(orgId: String) {
        val refreshed = getOrganization(orgId)
        if (refreshed is ApiResult.Failure &&
            (refreshed.error is AppError.Forbidden || refreshed.error is AppError.NotFound)
        ) {
            dao.deleteById(orgId)
        }
    }

    override suspend fun getMembers(orgId: String, limit: Int): ApiResult<List<OrgMember>> =
        withContext(dispatchers.io) {
            safeApiCall(json) { api.getMembers(orgId, limit = limit, offset = 0) }
                .map { response -> response.items.mapNotNull(MemberMapper::fromDto) }
        }

    override suspend fun searchMemberCandidates(
        orgId: String,
        query: String,
        limit: Int,
    ): ApiResult<List<MemberCandidate>> = withContext(dispatchers.io) {
        safeApiCall(json) {
            api.searchOrgUsers(
                id = orgId,
                search = query,
                excludeMembers = true,
                limit = limit,
                offset = 0,
            )
        }.map { response -> response.items.map(MemberMapper::candidateFromDto) }
    }

    override suspend fun addMember(orgId: String, userId: String, role: OrgRole): ApiResult<Unit> =
        withContext(dispatchers.io) {
            safeApiCall(json) {
                api.addMember(orgId, AddMemberRequest(userId = userId, role = role.apiValue))
            }.map { }
        }

    override suspend fun updateMemberRole(orgId: String, userId: String, role: OrgRole): ApiResult<Unit> =
        withContext(dispatchers.io) {
            safeApiCall(json) {
                api.updateMember(orgId, userId, UpdateMemberRequest(role = role.apiValue))
            }.map { }
        }

    override suspend fun removeMember(orgId: String, userId: String): ApiResult<Unit> =
        withContext(dispatchers.io) {
            safeApiCall(json) { api.removeMember(orgId, userId) }.map { }
        }
}

/** Builds a [Paged] from the response's pagination block, tolerating its absence. */
private fun OrganizationsResponse.toPaged(
    items: List<Organization>,
    offset: Int,
    limit: Int,
): Paged<Organization> {
    val page = pagination
    val nextOffset = offset + items.size
    val hasMore = page?.hasMore ?: (page?.let { nextOffset < it.total } ?: (items.size >= limit))
    val total = page?.total ?: nextOffset
    return Paged(items = items, hasMore = hasMore, total = total, offset = nextOffset)
}
