package com.interlinedlist.android.feature.lists.data.remote

import com.interlinedlist.android.feature.lists.data.remote.dto.GithubOrgDto
import com.interlinedlist.android.feature.lists.data.remote.dto.GithubRepoDto
import com.interlinedlist.android.feature.lists.data.remote.dto.NextIssueNumberDto
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * The slice of the InterlinedList GitHub proxy that GitHub-backed lists need.
 *
 * Only the read endpoints are here. The issue *writes* — create on add, update on
 * edit, close on delete — are performed by the server behind
 * `POST`/`PUT`/`DELETE /api/lists/{id}/data`, so the client maps a row edit to an
 * issue operation by writing the row, not by calling GitHub itself.
 *
 * With GitHub unlinked these answer `400 { "error": "GitHub account not linked" }`;
 * with a linked-but-unauthorised token they answer `401 { "code": "github_error" }`.
 * Both are translated for the UI by
 * [com.interlinedlist.android.feature.lists.ui.github.toGithubLinkProblem].
 */
interface GithubApi {

    /**
     * Repositories the linked account can reach, across all affiliations.
     * [org] restricts the result to one organisation's repositories.
     */
    @GET("api/github/repos")
    suspend fun getRepos(@Query("org") org: String? = null): List<GithubRepoDto>

    /** Organisations the linked account belongs to — the repo picker's filter. */
    @GET("api/github/orgs")
    suspend fun getOrgs(): List<GithubOrgDto>

    /** `max(issue number) + 1` for a repository, pull requests excluded. */
    @GET("api/github/repos/{owner}/{repo}/next-issue-number")
    suspend fun getNextIssueNumber(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
    ): NextIssueNumberDto
}
