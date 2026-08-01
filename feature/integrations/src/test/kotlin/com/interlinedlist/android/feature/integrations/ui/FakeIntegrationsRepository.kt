package com.interlinedlist.android.feature.integrations.ui

import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.core.common.result.AppError
import com.interlinedlist.android.feature.integrations.data.IntegrationsRepository
import com.interlinedlist.android.feature.integrations.domain.ConnectedAccount
import com.interlinedlist.android.feature.integrations.domain.ExportType
import com.interlinedlist.android.feature.integrations.domain.GitHubAssignee
import com.interlinedlist.android.feature.integrations.domain.GitHubIssue
import com.interlinedlist.android.feature.integrations.domain.GitHubLabel
import com.interlinedlist.android.feature.integrations.domain.GitHubRepo
import com.interlinedlist.android.feature.integrations.domain.PlanLimits
import java.io.File

/** In-memory [IntegrationsRepository] for ViewModel tests; each result is settable. */
class FakeIntegrationsRepository : IntegrationsRepository {

    var exportResult: ApiResult<File> = ApiResult.Failure(AppError.Unknown("not set"))
    val exportedTypes = mutableListOf<ExportType>()

    var accounts: List<ConnectedAccount> = emptyList()
    var accountsCalls = 0

    var limitsResult: ApiResult<PlanLimits> = ApiResult.Failure(AppError.Unknown("not set"))

    // --- GitHub ---
    var reposResult: ApiResult<List<GitHubRepo>> = ApiResult.Success(emptyList())
    var reposCalls = 0

    /** Issues keyed by repo full name ("owner/name"); defaults to [defaultIssuesResult]. */
    val issuesByRepo = mutableMapOf<String, ApiResult<List<GitHubIssue>>>()
    var defaultIssuesResult: ApiResult<List<GitHubIssue>> = ApiResult.Success(emptyList())
    val issuesRequested = mutableListOf<Pair<String, String?>>()

    var createIssueResult: ApiResult<GitHubIssue> = ApiResult.Failure(AppError.Unknown("not set"))
    val createdIssues = mutableListOf<CreatedIssue>()

    var addCommentResult: ApiResult<Unit> = ApiResult.Success(Unit)
    val addedComments = mutableListOf<AddedComment>()

    var assigneesResult: ApiResult<List<GitHubAssignee>> = ApiResult.Success(emptyList())
    var labelsResult: ApiResult<List<GitHubLabel>> = ApiResult.Success(emptyList())

    data class CreatedIssue(
        val repo: String,
        val title: String,
        val body: String?,
        val labels: List<String>,
        val assignees: List<String>,
    )

    data class AddedComment(val owner: String, val repo: String, val number: Int, val body: String)

    override suspend fun downloadExport(type: ExportType): ApiResult<File> {
        exportedTypes.add(type)
        return exportResult
    }

    override suspend fun getConnectedAccounts(): List<ConnectedAccount> {
        accountsCalls++
        return accounts
    }

    override suspend fun getLimits(): ApiResult<PlanLimits> = limitsResult

    override suspend fun getGitHubRepos(): ApiResult<List<GitHubRepo>> {
        reposCalls++
        return reposResult
    }

    override suspend fun getGitHubIssues(repo: String, state: String?): ApiResult<List<GitHubIssue>> {
        issuesRequested.add(repo to state)
        return issuesByRepo[repo] ?: defaultIssuesResult
    }

    override suspend fun createGitHubIssue(
        repo: String,
        title: String,
        body: String?,
        labels: List<String>,
        assignees: List<String>,
    ): ApiResult<GitHubIssue> {
        createdIssues.add(CreatedIssue(repo, title, body, labels, assignees))
        return createIssueResult
    }

    override suspend fun addGitHubIssueComment(
        owner: String,
        repo: String,
        number: Int,
        body: String,
    ): ApiResult<Unit> {
        addedComments.add(AddedComment(owner, repo, number, body))
        return addCommentResult
    }

    override suspend fun getGitHubAssignees(owner: String, repo: String): ApiResult<List<GitHubAssignee>> =
        assigneesResult

    override suspend fun getGitHubLabels(owner: String, repo: String): ApiResult<List<GitHubLabel>> =
        labelsResult
}
