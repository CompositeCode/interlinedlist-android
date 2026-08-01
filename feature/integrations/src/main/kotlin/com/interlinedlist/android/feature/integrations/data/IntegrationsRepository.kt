package com.interlinedlist.android.feature.integrations.data

import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.feature.integrations.domain.ConnectedAccount
import com.interlinedlist.android.feature.integrations.domain.ExportType
import com.interlinedlist.android.feature.integrations.domain.GitHubAssignee
import com.interlinedlist.android.feature.integrations.domain.GitHubIssue
import com.interlinedlist.android.feature.integrations.domain.GitHubLabel
import com.interlinedlist.android.feature.integrations.domain.GitHubRepo
import com.interlinedlist.android.feature.integrations.domain.PlanLimits
import java.io.File

/**
 * Data operations for the integrations hub: downloading CSV exports to disk,
 * reading connected-account status, and reading plan limits. Everything is a
 * live read — there is no offline cache — so results come back as [ApiResult].
 */
interface IntegrationsRepository {

    /**
     * Downloads the CSV for [type] and writes it into the app cache, returning
     * the file so the caller can hand it to the share sheet.
     */
    suspend fun downloadExport(type: ExportType): ApiResult<File>

    /** Fetches connection status for every supported provider. */
    suspend fun getConnectedAccounts(): List<ConnectedAccount>

    /** Reads plan limits/usage, or a failure the UI can render inline. */
    suspend fun getLimits(): ApiResult<PlanLimits>

    /**
     * Lists the connected GitHub repositories. When GitHub isn't linked the API
     * answers 400 "not linked"; that arrives as a failure whose error satisfies
     * [isGitHubNotLinked], which the UI treats as a "connect GitHub" state.
     */
    suspend fun getGitHubRepos(): ApiResult<List<GitHubRepo>>

    /** Lists issues for [repo] ("owner/name"), filtered by [state] (open/closed/all). */
    suspend fun getGitHubIssues(repo: String, state: String? = null): ApiResult<List<GitHubIssue>>

    /**
     * Creates an issue on [repo] with [title]/[body] and optional [labels]/
     * [assignees], returning the created issue.
     */
    suspend fun createGitHubIssue(
        repo: String,
        title: String,
        body: String?,
        labels: List<String> = emptyList(),
        assignees: List<String> = emptyList(),
    ): ApiResult<GitHubIssue>

    /** Adds a [body] comment to issue [number] on [owner]/[repo]. */
    suspend fun addGitHubIssueComment(
        owner: String,
        repo: String,
        number: Int,
        body: String,
    ): ApiResult<Unit>

    /** Assignable users for [owner]/[repo], for the create-issue composer. */
    suspend fun getGitHubAssignees(owner: String, repo: String): ApiResult<List<GitHubAssignee>>

    /** Labels defined on [owner]/[repo], for the create-issue composer. */
    suspend fun getGitHubLabels(owner: String, repo: String): ApiResult<List<GitHubLabel>>
}
