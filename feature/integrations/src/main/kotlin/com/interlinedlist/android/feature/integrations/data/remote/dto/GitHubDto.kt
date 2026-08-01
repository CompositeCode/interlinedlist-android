package com.interlinedlist.android.feature.integrations.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * DTOs for the `/api/github/…` endpoints. The InterlinedList API proxies the
 * GitHub REST API, whose response bodies are not modelled in the OpenAPI spec, so
 * these are deliberately lenient: every field is optional and unknown keys are
 * ignored by the shared [kotlinx.serialization.json.Json] config. This tolerates
 * both the full GitHub shapes (nested `owner`, label/assignee objects) and any
 * flattened variants the proxy might emit.
 */

/**
 * A repository. GitHub returns `owner` as a nested object with a `login`; the
 * proxy may also flatten it to a top-level `owner` string, so both are accepted.
 * `full_name` ("owner/name") is used as a fallback to recover the owner/name pair.
 */
@Serializable
data class GitHubRepoDto(
    val name: String? = null,
    @SerialName("full_name") val fullName: String? = null,
    val owner: GitHubOwnerDto? = null,
    @SerialName("ownerLogin") val ownerLogin: String? = null,
    val private: Boolean? = null,
    val description: String? = null,
)

@Serializable
data class GitHubOwnerDto(
    val login: String? = null,
)

/**
 * An issue. `labels` and `assignees` come back as arrays of objects from GitHub
 * (`{ name }` / `{ login }`); the mapper flattens them to plain strings.
 */
@Serializable
data class GitHubIssueDto(
    val number: Int? = null,
    val title: String? = null,
    val body: String? = null,
    val state: String? = null,
    val labels: List<GitHubLabelDto>? = null,
    val assignees: List<GitHubAssigneeDto>? = null,
)

@Serializable
data class GitHubLabelDto(
    val name: String? = null,
    val color: String? = null,
)

@Serializable
data class GitHubAssigneeDto(
    val login: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
)

/**
 * Body for `POST /api/github/issues`. `repo` is "owner/name"; `labels` and
 * `assignees` are comma-separated strings (the GitHub convention the API expects),
 * omitted when empty.
 */
@Serializable
data class CreateIssueRequest(
    val repo: String,
    val title: String,
    val body: String? = null,
    val labels: String? = null,
    val assignees: String? = null,
)

/** Body for `POST /api/github/issues/{owner}/{repo}/{number}/comments`. */
@Serializable
data class CreateCommentRequest(
    val body: String,
)
