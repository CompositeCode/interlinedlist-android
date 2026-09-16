package com.interlinedlist.android.feature.lists.ui.detail

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.interlinedlist.android.core.designsystem.theme.InterlinedListTheme
import com.interlinedlist.android.feature.lists.domain.GithubRepoLink
import com.interlinedlist.android.feature.lists.domain.ListSchema
import com.interlinedlist.android.feature.lists.domain.ListSource
import com.interlinedlist.android.feature.lists.domain.ListSummary
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The repository link under a GitHub-backed list's title, and its **Private repo**
 * tag — which describes the repository's visibility on GitHub, never the list's.
 */
@RunWith(AndroidJUnit4::class)
class GithubRepoLinkRowTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun setRow(isPrivate: Boolean?, onOpenRepo: (String) -> Unit = {}) {
        composeRule.setContent {
            InterlinedListTheme {
                GithubRepoLinkRow(
                    repo = "octocat/Hello-World",
                    githubRepoPrivate = isPrivate,
                    onOpenRepo = onOpenRepo,
                )
            }
        }
    }

    @Test
    fun linksTheRepositoryIssuesPage() {
        var opened: String? = null
        setRow(isPrivate = false) { opened = it }

        composeRule.onNodeWithTag(GithubRepoLinkTestTags.LINK).assertIsDisplayed()
        composeRule.onNodeWithText("octocat/Hello-World issues").assertIsDisplayed()

        composeRule.onNodeWithTag(GithubRepoLinkTestTags.LINK).performClick()
        assertThat(opened).isEqualTo("https://github.com/octocat/Hello-World/issues")
    }

    @Test
    fun showsThePrivateRepoTag_whenTheRepositoryIsPrivate() {
        setRow(isPrivate = true)

        composeRule.onNodeWithTag(GithubRepoLinkTestTags.PRIVATE_TAG).assertIsDisplayed()
        composeRule.onNodeWithText(GithubRepoLink.PRIVATE_TAG).assertIsDisplayed()
    }

    @Test
    fun theTagExplainsTheRepositoryIsPrivate_notTheList() {
        setRow(isPrivate = true)

        composeRule.onNodeWithTag(GithubRepoLinkTestTags.PRIVATE_TAG).performClick()

        composeRule.onNodeWithTag(GithubRepoLinkTestTags.PRIVATE_EXPLANATION).assertIsDisplayed()
        // The copy names the repository, and says the list's visibility is separate.
        val explanation = GithubRepoLink.PRIVATE_TAG_EXPLANATION
        assertThat(explanation).contains("This repository is private on GitHub")
        assertThat(explanation).contains("separate from who can see this list")
        composeRule.onNodeWithText(explanation).assertIsDisplayed()
    }

    @Test
    fun showsNoTag_whenTheRepositoryIsPublic() {
        setRow(isPrivate = false)

        composeRule.onNodeWithTag(GithubRepoLinkTestTags.PRIVATE_TAG).assertDoesNotExist()
    }

    @Test
    fun showsNoTag_whenTheRepositoryVisibilityIsNotYetKnown() {
        // A list that has not synced since the tag existed: unknown is never
        // presented as public, and it is never presented as private either.
        setRow(isPrivate = null)

        composeRule.onNodeWithTag(GithubRepoLinkTestTags.LINK).assertIsDisplayed()
        composeRule.onNodeWithTag(GithubRepoLinkTestTags.PRIVATE_TAG).assertDoesNotExist()
    }

    @Test
    fun rendersUnderTheTitleOfAGithubBackedListOnly() {
        val githubList = ListSummary(
            id = "L1",
            title = "Repo issues",
            description = null,
            itemCount = 0,
            folderId = null,
            isPublic = false,
            updatedAt = null,
            parentId = null,
            source = ListSource.GITHUB,
            githubRepo = "octocat/Hello-World",
            githubRepoPrivate = true,
        )
        composeRule.setContent {
            InterlinedListTheme {
                ListDetailScreen(
                    state = ListDetailUiState(
                        summary = githubList,
                        schema = ListSchema.EMPTY,
                        isLoading = false,
                    ),
                    onBack = {},
                    onAddRow = {},
                    onEditRow = {},
                    onDeleteRow = {},
                    onDeleteList = {},
                )
            }
        }

        composeRule.onNodeWithTag(GithubRepoLinkTestTags.ROW).assertIsDisplayed()
        composeRule.onNodeWithTag(GithubRepoLinkTestTags.PRIVATE_TAG).assertIsDisplayed()
        // Refresh from GitHub is offered here and nowhere else.
        composeRule.onNodeWithTag(ListDetailTestTags.REFRESH).assertIsDisplayed()
    }

    @Test
    fun aLocalListShowsNoRepoLinkAndNoRefresh() {
        composeRule.setContent {
            InterlinedListTheme {
                ListDetailScreen(
                    state = ListDetailUiState(
                        summary = ListSummary("L1", "Reading", null, 0, null, false, null),
                        schema = ListSchema.EMPTY,
                        isLoading = false,
                    ),
                    onBack = {},
                    onAddRow = {},
                    onEditRow = {},
                    onDeleteRow = {},
                    onDeleteList = {},
                )
            }
        }

        composeRule.onNodeWithTag(GithubRepoLinkTestTags.ROW).assertDoesNotExist()
        composeRule.onNodeWithTag(ListDetailTestTags.REFRESH).assertDoesNotExist()
    }
}
