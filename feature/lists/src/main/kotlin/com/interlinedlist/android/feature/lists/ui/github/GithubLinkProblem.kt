package com.interlinedlist.android.feature.lists.ui.github

import com.interlinedlist.android.core.common.result.AppError

/**
 * Why the repo picker cannot show repositories, in terms a user can act on.
 *
 * A GitHub-backed list needs a linked GitHub account with the **Issues** scope.
 * When that is missing the picker must explain what to do rather than render an
 * empty, broken control. Linking itself happens in the browser OAuth flow, so the
 * only action this module can offer is a route to the existing connected-accounts
 * screen.
 */
enum class GithubLinkProblem(val title: String, val explanation: String, val actionLabel: String) {

    /**
     * No GitHub identity at all. Every `/api/github/…` endpoint answers
     * `400 { "error": "GitHub account not linked" }`, which `safeApiCall` surfaces
     * as an [AppError.Unknown] carrying that message.
     */
    NOT_LINKED(
        title = "Connect GitHub first",
        explanation = "A GitHub-backed list mirrors a repository's issues, so it needs a " +
            "linked GitHub account with the Issues scope. Connect GitHub from your connected " +
            "accounts, then come back and pick a repository.",
        actionLabel = "Open connected accounts",
    ),

    /**
     * Linked, but GitHub refused the token — typically an account linked for
     * sign-in only, without the Issues scope, or a revoked/expired grant. The
     * proxy forwards GitHub's own status, so this arrives as a 401 with
     * `code: "github_error"`.
     */
    NEEDS_ISSUES_SCOPE(
        title = "Reconnect GitHub for Issues",
        explanation = "GitHub refused the linked account. If you linked GitHub only for " +
            "signing in, reconnect it and grant the Issues scope so InterlinedList can read " +
            "and open issues on your behalf.",
        actionLabel = "Open connected accounts",
    ),
    ;
}

/**
 * Classifies a failure from the GitHub proxy, or returns null when the failure is
 * an ordinary one (offline, server error) that the usual error message covers.
 *
 * The unlinked case is recognised by message because the API reports it as a
 * `400`, which `safeApiCall` does not map to a dedicated [AppError] type.
 */
fun AppError.toGithubLinkProblem(): GithubLinkProblem? {
    val text = message.orEmpty()
    val mentionsGithub = text.contains("github", ignoreCase = true)
    return when {
        mentionsGithub && (
            text.contains("not linked", ignoreCase = true) ||
                text.contains("not connected", ignoreCase = true)
            ) -> GithubLinkProblem.NOT_LINKED

        // GitHub itself refused the linked identity; the proxy forwards its 401.
        this is AppError.Unauthorized -> GithubLinkProblem.NEEDS_ISSUES_SCOPE

        this is AppError.Forbidden -> GithubLinkProblem.NEEDS_ISSUES_SCOPE

        else -> null
    }
}
