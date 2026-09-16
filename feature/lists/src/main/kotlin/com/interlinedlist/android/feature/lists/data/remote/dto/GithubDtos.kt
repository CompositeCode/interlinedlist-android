package com.interlinedlist.android.feature.lists.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire models for the `/api/github/…` proxy, as far as GitHub-backed lists need
 * it: picking a repository to create a list from, scoping that picker to an
 * organisation, and reading the number a newly created issue will get.
 *
 * These live here rather than being shared with `:feature:integrations` because
 * every feature module in this repo owns its own DTOs and Retrofit interface and
 * no feature module depends on another.
 *
 * The endpoints are thin proxies over the GitHub REST API and the OpenAPI spec
 * types their bodies as a bare `object`, so every field is optional and both the
 * nested GitHub shapes (`owner: { login }`) and any flattened variant are
 * accepted. `GET /api/github/repos` and `GET /api/github/orgs` were confirmed
 * live to return a **bare array**, not an envelope.
 */

/**
 * A repository. `full_name` ("owner/name") is used to recover the owner/name pair
 * when `owner` is absent.
 */
@Serializable
data class GithubRepoDto(
    val name: String? = null,
    @SerialName("full_name") val fullName: String? = null,
    val owner: GithubOwnerDto? = null,
    @SerialName("ownerLogin") val ownerLogin: String? = null,
    val private: Boolean? = null,
    val description: String? = null,
)

@Serializable
data class GithubOwnerDto(
    val login: String? = null,
)

/**
 * An organisation. GitHub names it `login`; `name`/`slug` are accepted as
 * fallbacks in case the proxy reshapes it.
 */
@Serializable
data class GithubOrgDto(
    val login: String? = null,
    val name: String? = null,
    val slug: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
)

/**
 * `GET /api/github/repos/{owner}/{repo}/next-issue-number` →
 * `{ "nextNumber": 11231 }` (confirmed live). `nextIssueNumber`/`number` are
 * accepted as fallbacks since the shape is unmodelled in the spec.
 */
@Serializable
data class NextIssueNumberDto(
    val nextNumber: Int? = null,
    val nextIssueNumber: Int? = null,
    val number: Int? = null,
) {
    val resolved: Int? get() = nextNumber ?: nextIssueNumber ?: number
}
