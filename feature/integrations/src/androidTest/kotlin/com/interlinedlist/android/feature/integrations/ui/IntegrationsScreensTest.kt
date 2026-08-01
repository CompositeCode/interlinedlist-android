package com.interlinedlist.android.feature.integrations.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.interlinedlist.android.core.designsystem.theme.InterlinedListTheme
import com.interlinedlist.android.feature.integrations.domain.ConnectedAccount
import com.interlinedlist.android.feature.integrations.domain.ExportType
import com.interlinedlist.android.feature.integrations.domain.GitHubIssue
import com.interlinedlist.android.feature.integrations.domain.GitHubRepo
import com.interlinedlist.android.feature.integrations.ui.accounts.ConnectedAccountsScreen
import com.interlinedlist.android.feature.integrations.ui.accounts.ConnectedAccountsTestTags
import com.interlinedlist.android.feature.integrations.ui.accounts.ConnectedAccountsUiState
import com.interlinedlist.android.feature.integrations.ui.export.ExportScreen
import com.interlinedlist.android.feature.integrations.ui.export.ExportTestTags
import com.interlinedlist.android.feature.integrations.ui.export.ExportUiState
import com.interlinedlist.android.feature.integrations.ui.github.GitHubScreen
import com.interlinedlist.android.feature.integrations.ui.github.GitHubTestTags
import com.interlinedlist.android.feature.integrations.ui.github.GitHubUiState
import com.interlinedlist.android.feature.integrations.ui.hub.IntegrationsHubScreen
import com.interlinedlist.android.feature.integrations.ui.hub.IntegrationsHubTestTags
import com.interlinedlist.android.feature.integrations.ui.hub.IntegrationsHubUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class IntegrationsScreensTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun hub_entries_navigateToSubScreens() {
        var export = false
        var accounts = false
        composeRule.setContent {
            InterlinedListTheme {
                IntegrationsHubScreen(
                    state = IntegrationsHubUiState(isLoadingLimits = false),
                    onBack = {},
                    onOpenExport = { export = true },
                    onOpenConnectedAccounts = { accounts = true },
                )
            }
        }

        composeRule.onNodeWithTag(IntegrationsHubTestTags.EXPORT).performClick()
        composeRule.onNodeWithTag(IntegrationsHubTestTags.ACCOUNTS).performClick()

        assert(export)
        assert(accounts)
    }

    @Test
    fun export_row_tap_triggersExport() {
        var requested: ExportType? = null
        composeRule.setContent {
            InterlinedListTheme {
                ExportScreen(
                    state = ExportUiState(),
                    onExport = { requested = it },
                    onDismissError = {},
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithTag(ExportTestTags.button(ExportType.FOLLOWS)).performClick()

        assert(requested == ExportType.FOLLOWS)
    }

    @Test
    fun accounts_showsConnectedStatus() {
        composeRule.setContent {
            InterlinedListTheme {
                ConnectedAccountsScreen(
                    state = ConnectedAccountsUiState(
                        isLoading = false,
                        accounts = listOf(
                            ConnectedAccount(ConnectedAccount.Provider.GITHUB, isConnected = true, handle = "@adron"),
                            ConnectedAccount(ConnectedAccount.Provider.BLUESKY, isConnected = false),
                        ),
                    ),
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithTag(ConnectedAccountsTestTags.row(ConnectedAccount.Provider.GITHUB))
            .assertIsDisplayed()
        composeRule.onNodeWithTag(ConnectedAccountsTestTags.status(ConnectedAccount.Provider.GITHUB))
            .assertIsDisplayed()
        composeRule.onNodeWithTag(ConnectedAccountsTestTags.status(ConnectedAccount.Provider.BLUESKY))
            .assertIsDisplayed()
    }

    @Test
    fun github_repos_renderAndSelect() {
        var selected: GitHubRepo? = null
        val repo = GitHubRepo("adron", "hello", description = "sample")
        composeRule.setContent {
            InterlinedListTheme {
                GitHubScreen(
                    state = GitHubUiState(isLoadingRepos = false, repos = listOf(repo)),
                    onBack = {},
                    onRetryRepos = {},
                    onSelectRepo = { selected = it },
                    onClearRepo = {},
                    onCreateIssue = { _, _, _, _ -> },
                    onAddComment = { _, _ -> },
                    onMessageShown = {},
                    onCreateErrorShown = {},
                    onCommentErrorShown = {},
                )
            }
        }

        composeRule.onNodeWithTag(GitHubTestTags.REPO_LIST).assertIsDisplayed()
        composeRule.onNodeWithTag(GitHubTestTags.repo("adron/hello")).assertIsDisplayed().performClick()
        assert(selected == repo)
    }

    @Test
    fun github_notConnected_showsConnectPrompt() {
        composeRule.setContent {
            InterlinedListTheme {
                GitHubScreen(
                    state = GitHubUiState(isLoadingRepos = false, notConnected = true),
                    onBack = {},
                    onRetryRepos = {},
                    onSelectRepo = {},
                    onClearRepo = {},
                    onCreateIssue = { _, _, _, _ -> },
                    onAddComment = { _, _ -> },
                    onMessageShown = {},
                    onCreateErrorShown = {},
                    onCommentErrorShown = {},
                )
            }
        }

        composeRule.onNodeWithTag(GitHubTestTags.NOT_CONNECTED).assertIsDisplayed()
    }

    @Test
    fun github_issues_renderForSelectedRepo() {
        val repo = GitHubRepo("adron", "hello")
        composeRule.setContent {
            InterlinedListTheme {
                GitHubScreen(
                    state = GitHubUiState(
                        isLoadingRepos = false,
                        repos = listOf(repo),
                        selectedRepo = repo,
                        isLoadingIssues = false,
                        issues = listOf(GitHubIssue(number = 7, title = "Fix bug", labels = listOf("bug"))),
                    ),
                    onBack = {},
                    onRetryRepos = {},
                    onSelectRepo = {},
                    onClearRepo = {},
                    onCreateIssue = { _, _, _, _ -> },
                    onAddComment = { _, _ -> },
                    onMessageShown = {},
                    onCreateErrorShown = {},
                    onCommentErrorShown = {},
                )
            }
        }

        composeRule.onNodeWithTag(GitHubTestTags.ISSUE_LIST).assertIsDisplayed()
        composeRule.onNodeWithTag(GitHubTestTags.issue(7)).assertIsDisplayed()
        composeRule.onNodeWithTag(GitHubTestTags.NEW_ISSUE_FAB).assertIsDisplayed()
    }
}
