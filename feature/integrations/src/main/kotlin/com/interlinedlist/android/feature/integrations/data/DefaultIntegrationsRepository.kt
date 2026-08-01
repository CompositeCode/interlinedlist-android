package com.interlinedlist.android.feature.integrations.data

import com.interlinedlist.android.core.common.dispatcher.DispatcherProvider
import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.core.common.result.map
import com.interlinedlist.android.core.network.error.safeApiCall
import com.interlinedlist.android.feature.integrations.data.mapper.toDomain
import com.interlinedlist.android.feature.integrations.data.mapper.toDomainOrNull
import com.interlinedlist.android.feature.integrations.data.remote.IntegrationsApi
import com.interlinedlist.android.feature.integrations.data.remote.dto.CreateCommentRequest
import com.interlinedlist.android.feature.integrations.data.remote.dto.CreateIssueRequest
import com.interlinedlist.android.feature.integrations.data.remote.dto.GitHubIssueDto
import com.interlinedlist.android.feature.integrations.domain.ConnectedAccount
import com.interlinedlist.android.feature.integrations.domain.ExportType
import com.interlinedlist.android.feature.integrations.domain.GitHubAssignee
import com.interlinedlist.android.feature.integrations.domain.GitHubIssue
import com.interlinedlist.android.feature.integrations.domain.GitHubLabel
import com.interlinedlist.android.feature.integrations.domain.GitHubRepo
import com.interlinedlist.android.feature.integrations.domain.PlanLimits
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject

class DefaultIntegrationsRepository @Inject constructor(
    private val api: IntegrationsApi,
    private val fileStore: ExportFileStore,
    private val json: Json,
    private val dispatchers: DispatcherProvider,
) : IntegrationsRepository {

    override suspend fun downloadExport(type: ExportType): ApiResult<File> =
        withContext(dispatchers.io) {
            safeApiCall(json) { api.downloadExport(type.pathSegment) }
                .map { body ->
                    // Stream the response straight to a cache file so a large CSV
                    // never has to sit fully in memory.
                    val dir = fileStore.exportsDir().apply { mkdirs() }
                    val file = File(dir, "${type.fileBaseName}.csv")
                    body.byteStream().use { input ->
                        file.outputStream().use { output -> input.copyTo(output) }
                    }
                    file
                }
        }

    override suspend fun getConnectedAccounts(): List<ConnectedAccount> =
        withContext(dispatchers.io) {
            // Statuses are independent; one provider failing shouldn't hide the
            // rest, so a failed lookup is treated as "not connected".
            ConnectedAccount.Provider.entries.map { provider ->
                when (val result = safeApiCall(json) { api.getConnectionStatus(provider.statusPath) }) {
                    is ApiResult.Success -> ConnectedAccount(
                        provider = provider,
                        isConnected = result.data.isConnected,
                        handle = result.data.bestHandle,
                    )
                    is ApiResult.Failure -> ConnectedAccount(
                        provider = provider,
                        isConnected = false,
                    )
                }
            }
        }

    override suspend fun getLimits(): ApiResult<PlanLimits> =
        withContext(dispatchers.io) {
            safeApiCall(json) { api.getLimits() }.map { it.toDomain() }
        }

    override suspend fun getGitHubRepos(): ApiResult<List<GitHubRepo>> =
        withContext(dispatchers.io) {
            safeApiCall(json) { api.getGitHubRepos() }
                .map { dtos -> dtos.mapNotNull { it.toDomainOrNull() } }
        }

    override suspend fun getGitHubIssues(repo: String, state: String?): ApiResult<List<GitHubIssue>> =
        withContext(dispatchers.io) {
            safeApiCall(json) { api.getGitHubIssues(repo = repo, state = state) }
                .map { dtos -> dtos.mapNotNull { it.toDomainOrNull() } }
        }

    override suspend fun createGitHubIssue(
        repo: String,
        title: String,
        body: String?,
        labels: List<String>,
        assignees: List<String>,
    ): ApiResult<GitHubIssue> =
        withContext(dispatchers.io) {
            val request = CreateIssueRequest(
                repo = repo,
                title = title,
                body = body?.takeIf { it.isNotBlank() },
                // The API expects comma-separated strings; omit when empty.
                labels = labels.filter { it.isNotBlank() }.takeIf { it.isNotEmpty() }?.joinToString(","),
                assignees = assignees.filter { it.isNotBlank() }.takeIf { it.isNotEmpty() }?.joinToString(","),
            )
            safeApiCall(json) { api.createGitHubIssue(request) }
                .map { dto -> dto.toDomainOrFallback(title, body) }
        }

    override suspend fun addGitHubIssueComment(
        owner: String,
        repo: String,
        number: Int,
        body: String,
    ): ApiResult<Unit> =
        withContext(dispatchers.io) {
            safeApiCall(json) {
                api.addGitHubIssueComment(owner, repo, number, CreateCommentRequest(body))
            }.map { it.close() }
        }

    override suspend fun getGitHubAssignees(owner: String, repo: String): ApiResult<List<GitHubAssignee>> =
        withContext(dispatchers.io) {
            safeApiCall(json) { api.getGitHubAssignees(owner, repo) }
                .map { dtos -> dtos.mapNotNull { it.toDomainOrNull() } }
        }

    override suspend fun getGitHubLabels(owner: String, repo: String): ApiResult<List<GitHubLabel>> =
        withContext(dispatchers.io) {
            safeApiCall(json) { api.getGitHubLabels(owner, repo) }
                .map { dtos -> dtos.mapNotNull { it.toDomainOrNull() } }
        }
}

/**
 * The create-issue response shape isn't modelled in the spec. If the returned DTO
 * lacks a number (or the field names differ), fall back to a synthetic issue built
 * from what was sent so the UI can still show the new issue optimistically.
 */
private fun GitHubIssueDto.toDomainOrFallback(title: String, body: String?): GitHubIssue =
    toDomainOrNull() ?: GitHubIssue(
        number = 0,
        title = this.title?.takeIf { it.isNotBlank() } ?: title,
        body = this.body?.takeIf { it.isNotBlank() } ?: body?.takeIf { it.isNotBlank() },
        state = "open",
    )
