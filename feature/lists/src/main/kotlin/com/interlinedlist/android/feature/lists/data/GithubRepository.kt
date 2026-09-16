package com.interlinedlist.android.feature.lists.data

import com.interlinedlist.android.core.common.dispatcher.DispatcherProvider
import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.core.common.result.map
import com.interlinedlist.android.core.network.error.safeApiCall
import com.interlinedlist.android.feature.lists.data.remote.GithubApi
import com.interlinedlist.android.feature.lists.domain.GithubOrg
import com.interlinedlist.android.feature.lists.domain.GithubRepo
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import javax.inject.Inject

/**
 * Read access to the user's GitHub account, only as far as creating and running a
 * GitHub-backed list needs it.
 *
 * Kept separate from [ListsRepository] because it speaks to a different API
 * family (`/api/github/…`, a proxy over GitHub) with its own failure modes — an
 * unlinked account, a token without the Issues scope, an org that has not
 * approved the OAuth app — none of which mean anything to the Lists endpoints.
 */
interface GithubRepository {

    /** Organisations the linked account belongs to; scopes [getRepos]. */
    suspend fun getOrgs(): ApiResult<List<GithubOrg>>

    /** Repositories the linked account can reach, optionally one org's worth. */
    suspend fun getRepos(org: String? = null): ApiResult<List<GithubRepo>>

    /**
     * The number the next issue opened on [repo] will get, so the row form can
     * tell the user which issue they are about to create. [repo] is `"owner/name"`.
     * Null when the server answered without a number.
     */
    suspend fun getNextIssueNumber(repo: String): ApiResult<Int?>
}

class DefaultGithubRepository @Inject constructor(
    private val api: GithubApi,
    private val json: Json,
    private val dispatchers: DispatcherProvider,
) : GithubRepository {

    override suspend fun getOrgs(): ApiResult<List<GithubOrg>> = withContext(dispatchers.io) {
        safeApiCall(json) { api.getOrgs() }
            .map { orgs -> orgs.mapNotNull(GithubMapper::orgOrNull) }
    }

    override suspend fun getRepos(org: String?): ApiResult<List<GithubRepo>> =
        withContext(dispatchers.io) {
            safeApiCall(json) { api.getRepos(org?.takeIf { it.isNotBlank() }) }
                .map { repos -> repos.mapNotNull(GithubMapper::repoOrNull) }
        }

    override suspend fun getNextIssueNumber(repo: String): ApiResult<Int?> =
        withContext(dispatchers.io) {
            val owner = repo.substringBefore('/', missingDelimiterValue = "")
            val name = repo.substringAfter('/', missingDelimiterValue = "")
            if (owner.isBlank() || name.isBlank()) {
                return@withContext ApiResult.Success(null)
            }
            safeApiCall(json) { api.getNextIssueNumber(owner, name) }.map { it.resolved }
        }
}
