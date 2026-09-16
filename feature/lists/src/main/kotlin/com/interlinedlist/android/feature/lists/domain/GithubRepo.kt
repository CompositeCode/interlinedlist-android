package com.interlinedlist.android.feature.lists.domain

/**
 * A GitHub repository the linked account can reach, as returned by
 * `GET /api/github/repos` (optionally scoped with `?org=<login>`).
 *
 * [isPrivate] is the repository's visibility **on GitHub** and has nothing to do
 * with whether an InterlinedList list is public — see [GithubRepoLink] for the
 * copy that keeps the two apart.
 */
data class GithubRepo(
    val owner: String,
    val name: String,
    val isPrivate: Boolean = false,
    val description: String? = null,
) {
    /** `"owner/name"` — the form `githubRepo` on `POST /api/lists` expects. */
    val fullName: String get() = "$owner/$name"
}

/**
 * An organisation the linked GitHub account belongs to
 * (`GET /api/github/orgs`). Used to scope the repo picker: an account with many
 * repositories is far easier to search one org at a time, and `?org=` is the only
 * server-side filter the proxy offers.
 */
data class GithubOrg(
    val login: String,
    val avatarUrl: String? = null,
)

/**
 * The repository link shown under a GitHub-backed list's title, and the copy that
 * goes with it.
 *
 * The wording here is deliberate. The **Private repo** tag describes the
 * *repository's* visibility on GitHub, not the list's — the two are set
 * separately, and somebody invited to the list may well have no access to the
 * repository. Saying "private list" here would tell people the opposite of the
 * truth about who can see their data, so the copy names the repository
 * explicitly and warns what GitHub will show a visitor who lacks access.
 */
object GithubRepoLink {

    /** The tag shown beside the link when the repository is private on GitHub. */
    const val PRIVATE_TAG: String = "Private repo"

    /**
     * Why the tag is there. Names the repository (not the list) as the private
     * thing, and says what a collaborator without repo access will actually hit.
     */
    const val PRIVATE_TAG_EXPLANATION: String =
        "This repository is private on GitHub. That is separate from who can see this list: " +
            "someone you invite to the list may still be asked to sign in, or see a " +
            "\"not found\" page, when they open the repository. Repository access is granted " +
            "on GitHub, not in InterlinedList."

    /** Label for the link itself, e.g. `"octocat/Hello-World issues"`. */
    fun label(repo: String): String = "$repo issues"

    /** The repository's issues page, which the link opens on GitHub. */
    fun issuesUrl(repo: String): String = "https://github.com/$repo/issues"

    /**
     * Whether a repository is known to be private. `githubRepoPrivate` is only
     * recorded from the first sync onward, so an **unknown** (null) visibility
     * shows no tag at all rather than being presented as public.
     */
    fun showsPrivateTag(githubRepoPrivate: Boolean?): Boolean = githubRepoPrivate == true
}

/**
 * The `githubSource` sent on `POST /api/lists`. A GitHub-backed list mirrors a
 * repository's **issues** — that is the only mapping the API documents (rows are
 * issues; adding a row opens one, deleting closes it).
 */
const val GITHUB_SOURCE_ISSUES: String = "issues"

/**
 * Whether [repo] is in the `owner/repo` form the API requires. The server rejects
 * anything else with `400 githubRepo is required for GitHub-backed lists (format:
 * owner/repo)`, so the picker checks before spending a request.
 */
fun isValidGithubRepo(repo: String): Boolean {
    val parts = repo.trim().split('/')
    return parts.size == 2 && parts.all { it.isNotBlank() && !it.contains(' ') }
}
