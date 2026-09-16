package com.interlinedlist.android.feature.lists.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The repository link and its **Private repo** tag.
 *
 * The tag describes the *repository's* visibility on GitHub, not the list's, and
 * the two are set separately. Copy that blurs them tells a user the opposite of
 * the truth about who can see their data, so the wording is pinned here rather
 * than left to a reviewer's eye.
 */
class GithubRepoLinkTest {

    @Test
    fun `the private tag names the repository, never the list`() {
        val explanation = GithubRepoLink.PRIVATE_TAG_EXPLANATION

        assertThat(GithubRepoLink.PRIVATE_TAG).isEqualTo("Private repo")
        // It is the repository that is private.
        assertThat(explanation).contains("This repository is private on GitHub")
        // And it says so about the list explicitly, so the two cannot be confused.
        assertThat(explanation).contains("separate from who can see this list")
        // It never claims the list itself is private.
        assertThat(explanation).doesNotContain("private list")
        assertThat(explanation).doesNotContain("This list is private")
    }

    @Test
    fun `the explanation warns what a collaborator without repo access will hit`() {
        val explanation = GithubRepoLink.PRIVATE_TAG_EXPLANATION

        assertThat(explanation).contains("sign in")
        assertThat(explanation).contains("not found")
        // And where access actually comes from.
        assertThat(explanation).contains("granted on GitHub")
    }

    @Test
    fun `the tag shows only when the repository is known to be private`() {
        assertThat(GithubRepoLink.showsPrivateTag(true)).isTrue()
        assertThat(GithubRepoLink.showsPrivateTag(false)).isFalse()
        // Unknown visibility (a list that has not synced since the tag existed)
        // shows no tag — it is never presented as public.
        assertThat(GithubRepoLink.showsPrivateTag(null)).isFalse()
    }

    @Test
    fun `the link points at the repository's issues page`() {
        assertThat(GithubRepoLink.label("octocat/Hello-World")).isEqualTo("octocat/Hello-World issues")
        assertThat(GithubRepoLink.issuesUrl("octocat/Hello-World"))
            .isEqualTo("https://github.com/octocat/Hello-World/issues")
    }

    @Test
    fun `repo validation matches the server's owner slash repo rule`() {
        assertThat(isValidGithubRepo("octocat/Hello-World")).isTrue()
        assertThat(isValidGithubRepo("  octocat/Hello-World  ")).isTrue()
        // The server answers 400 for anything else.
        assertThat(isValidGithubRepo("nosuchslash")).isFalse()
        assertThat(isValidGithubRepo("too/many/slashes")).isFalse()
        assertThat(isValidGithubRepo("/Hello-World")).isFalse()
        assertThat(isValidGithubRepo("octocat/")).isFalse()
        assertThat(isValidGithubRepo("")).isFalse()
    }
}
