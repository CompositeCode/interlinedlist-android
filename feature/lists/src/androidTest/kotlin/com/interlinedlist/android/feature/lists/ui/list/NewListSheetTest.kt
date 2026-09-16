package com.interlinedlist.android.feature.lists.ui.list

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.interlinedlist.android.core.designsystem.theme.InterlinedListTheme
import com.interlinedlist.android.feature.lists.domain.GithubRepo
import com.interlinedlist.android.feature.lists.ui.github.GithubLinkProblem
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The "New list" sheet, in particular the GitHub half: picking a repository, and
 * what a user without GitHub linked is shown instead of a broken picker.
 */
@RunWith(AndroidJUnit4::class)
class NewListSheetTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val helloWorld = GithubRepo("octocat", "Hello-World")
    private val secretPlans = GithubRepo("acme", "secret-plans", isPrivate = true)

    private fun setSheet(
        state: NewListUiState,
        onSelectRepo: (GithubRepo) -> Unit = {},
        onOpenConnectedAccounts: () -> Unit = {},
    ) {
        composeRule.setContent {
            InterlinedListTheme {
                NewListSheet(
                    state = state,
                    onSelectKind = {},
                    onTitleChange = {},
                    onPublicChange = {},
                    onRepoQueryChange = {},
                    onSelectRepo = onSelectRepo,
                    onSelectOrg = {},
                    onCreate = {},
                    onCancel = {},
                    onOpenConnectedAccounts = onOpenConnectedAccounts,
                )
            }
        }
    }

    @Test
    fun listsRepositories_andReportsTheOneThatIsPicked() {
        var picked: GithubRepo? = null
        setSheet(
            NewListUiState(
                kind = NewListKind.GITHUB,
                repos = listOf(helloWorld, secretPlans),
            ),
            onSelectRepo = { picked = it },
        )

        composeRule.onNodeWithTag(NewListTestTags.repo("octocat/Hello-World")).assertIsDisplayed()
        composeRule.onNodeWithTag(NewListTestTags.repo("acme/secret-plans")).performClick()

        assertThat(picked).isEqualTo(secretPlans)
    }

    @Test
    fun explainsAnUnlinkedAccount_andRoutesToConnectedAccounts() {
        var routed = false
        setSheet(
            NewListUiState(kind = NewListKind.GITHUB, linkProblem = GithubLinkProblem.NOT_LINKED),
            onOpenConnectedAccounts = { routed = true },
        )

        composeRule.onNodeWithTag(NewListTestTags.LINK_PROBLEM).assertIsDisplayed()
        // No picker at all, rather than an empty one.
        composeRule.onNodeWithTag(NewListTestTags.REPO_SEARCH).assertDoesNotExist()
        composeRule.onNodeWithTag(NewListTestTags.CREATE).assertIsNotEnabled()

        composeRule.onNodeWithTag(NewListTestTags.LINK_ACTION).performClick()
        assertThat(routed).isTrue()
    }

    @Test
    fun explainsAnEmptyRepoList_asAnOrgApprovalProblem() {
        setSheet(NewListUiState(kind = NewListKind.GITHUB, repos = emptyList()))

        composeRule.onNodeWithTag(NewListTestTags.REPO_EMPTY).assertIsDisplayed()
    }

    @Test
    fun theTitleFieldWaitsForARepositoryToBeChosen() {
        setSheet(NewListUiState(kind = NewListKind.GITHUB, repos = listOf(helloWorld)))

        // Nothing to title until there is a repository behind the list.
        composeRule.onNodeWithTag(NewListTestTags.TITLE).assertDoesNotExist()
        composeRule.onNodeWithTag(NewListTestTags.CREATE).assertIsNotEnabled()
    }

    @Test
    fun theTitleFieldAppearsOnceARepositoryIsChosen() {
        setSheet(
            NewListUiState(
                kind = NewListKind.GITHUB,
                repos = listOf(helloWorld),
                selectedRepo = helloWorld,
                title = "Hello-World",
            ),
        )

        composeRule.onNodeWithTag(NewListTestTags.TITLE).assertIsDisplayed()
        composeRule.onNodeWithTag(NewListTestTags.VISIBILITY).assertIsDisplayed()
    }

    @Test
    fun aLocalListNeedsNoRepositoryPicker() {
        setSheet(NewListUiState(kind = NewListKind.LOCAL, title = "Books"))

        composeRule.onNodeWithTag(NewListTestTags.TITLE).assertIsDisplayed()
        composeRule.onNodeWithTag(NewListTestTags.REPO_SEARCH).assertDoesNotExist()
        composeRule.onNodeWithTag(NewListTestTags.LINK_PROBLEM).assertDoesNotExist()
    }
}
