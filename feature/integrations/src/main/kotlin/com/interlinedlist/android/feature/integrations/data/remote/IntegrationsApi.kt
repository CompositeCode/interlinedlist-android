package com.interlinedlist.android.feature.integrations.data.remote

import com.interlinedlist.android.feature.integrations.data.remote.dto.ConnectionStatusDto
import com.interlinedlist.android.feature.integrations.data.remote.dto.CreateCommentRequest
import com.interlinedlist.android.feature.integrations.data.remote.dto.CreateIssueRequest
import com.interlinedlist.android.feature.integrations.data.remote.dto.GitHubAssigneeDto
import com.interlinedlist.android.feature.integrations.data.remote.dto.GitHubIssueDto
import com.interlinedlist.android.feature.integrations.data.remote.dto.GitHubLabelDto
import com.interlinedlist.android.feature.integrations.data.remote.dto.GitHubRepoDto
import com.interlinedlist.android.feature.integrations.data.remote.dto.LimitsDto
import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming

/**
 * Retrofit description of the Phase 8 integrations endpoints. The shared Retrofit
 * instance already carries the base URL and Bearer token, so these calls are
 * authed.
 *
 * Exports return raw CSV, so they surface as a [Streaming] [ResponseBody] rather
 * than a deserialised type — the repository copies the bytes straight to disk
 * without buffering the whole file in memory.
 */
interface IntegrationsApi {

    /** CSV of the user's data for the given export type (path segment from ExportType). */
    @Streaming
    @GET("api/exports/{type}")
    suspend fun downloadExport(@Path("type") type: String): ResponseBody

    /** Connection status for one provider, e.g. `api/auth/github/status`. */
    @GET("{statusPath}")
    suspend fun getConnectionStatus(
        @Path(value = "statusPath", encoded = true) statusPath: String,
    ): ConnectionStatusDto

    /** Plan limits/usage for the current user. */
    @GET("api/limits")
    suspend fun getLimits(): LimitsDto

    // --- GitHub ---
    //
    // The bearer is auto-injected. When GitHub isn't linked these return HTTP 400
    // with { "error": "GitHub account not linked" }; the repository detects that
    // and surfaces a graceful "connect GitHub" state instead of a hard error.

    /** Repositories the user has connected/authorised on GitHub. */
    @GET("api/github/repos")
    suspend fun getGitHubRepos(): List<GitHubRepoDto>

    /**
     * Issues for a repo. [repo] is "owner/name"; [state] is "open" (default),
     * "closed", or "all". Both are optional query params per the OpenAPI spec.
     */
    @GET("api/github/issues")
    suspend fun getGitHubIssues(
        @Query("repo") repo: String,
        @Query("state") state: String? = null,
    ): List<GitHubIssueDto>

    /** Creates an issue; body carries repo/title/body plus optional labels/assignees. */
    @POST("api/github/issues")
    suspend fun createGitHubIssue(@Body request: CreateIssueRequest): GitHubIssueDto

    /** Adds a comment to an existing issue. */
    @POST("api/github/issues/{owner}/{repo}/{number}/comments")
    suspend fun addGitHubIssueComment(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("number") number: Int,
        @Body request: CreateCommentRequest,
    ): ResponseBody

    /** Assignable users for a repo, for the create-issue composer. */
    @GET("api/github/repos/{owner}/{repo}/assignees")
    suspend fun getGitHubAssignees(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
    ): List<GitHubAssigneeDto>

    /** Labels defined on a repo, for the create-issue composer. */
    @GET("api/github/repos/{owner}/{repo}/labels")
    suspend fun getGitHubLabels(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
    ): List<GitHubLabelDto>
}
