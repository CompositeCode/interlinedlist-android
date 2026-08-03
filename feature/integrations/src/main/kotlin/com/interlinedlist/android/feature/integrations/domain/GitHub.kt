package com.interlinedlist.android.feature.integrations.domain

/**
 * Domain models for the GitHub integration. The InterlinedList API proxies the
 * GitHub REST API, so these mirror the fields the web app relies on (repos,
 * issues, labels, assignees) while staying deliberately small — the module only
 * lists repos/issues and creates an issue or comment.
 */

/** A GitHub repository the user has connected/authorised. */
data class GitHubRepo(
    val owner: String,
    val name: String,
    val isPrivate: Boolean = false,
    val description: String? = null,
) {
    /** "owner/name", the form GitHub and the create-issue endpoint expect. */
    val fullName: String get() = "$owner/$name"
}

/** A single GitHub issue on a repo. */
data class GitHubIssue(
    val number: Int,
    val title: String,
    val body: String? = null,
    val state: String = "open",
    val labels: List<String> = emptyList(),
    val assignees: List<String> = emptyList(),
) {
    val isOpen: Boolean get() = state.equals("open", ignoreCase = true)
}

/** A label available on a repo, used when composing an issue. */
data class GitHubLabel(
    val name: String,
    /** Hex colour without the leading '#', when GitHub supplies one. */
    val color: String? = null,
)

/** A user who can be assigned to issues on a repo. */
data class GitHubAssignee(
    val login: String,
    val avatarUrl: String? = null,
)
