package com.interlinedlist.android.feature.lists

import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.feature.lists.data.GithubRepository
import com.interlinedlist.android.feature.lists.domain.GithubOrg
import com.interlinedlist.android.feature.lists.domain.GithubRepo

/**
 * In-memory [GithubRepository] for ViewModel tests. Each call's result is
 * configurable so the unlinked / refused / empty-repo paths can be exercised
 * without a network.
 */
class FakeGithubRepository : GithubRepository {

    var orgsResult: ApiResult<List<GithubOrg>> = ApiResult.Success(emptyList())
    var reposResult: ApiResult<List<GithubRepo>> = ApiResult.Success(emptyList())
    var nextIssueNumberResult: ApiResult<Int?> = ApiResult.Success(null)

    var orgsCount = 0
    var reposCount = 0
    var lastReposOrg: String? = null
    var lastNextIssueRepo: String? = null

    override suspend fun getOrgs(): ApiResult<List<GithubOrg>> {
        orgsCount++
        return orgsResult
    }

    override suspend fun getRepos(org: String?): ApiResult<List<GithubRepo>> {
        reposCount++
        lastReposOrg = org
        return reposResult
    }

    override suspend fun getNextIssueNumber(repo: String): ApiResult<Int?> {
        lastNextIssueRepo = repo
        return nextIssueNumberResult
    }
}
