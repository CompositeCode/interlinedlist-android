package com.interlinedlist.android.feature.profile.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.interlinedlist.android.core.designsystem.theme.InterlinedListTheme
import com.interlinedlist.android.feature.profile.domain.FollowUser
import com.interlinedlist.android.feature.profile.ui.follow.FollowListScreen
import com.interlinedlist.android.feature.profile.ui.follow.FollowListTestTags
import com.interlinedlist.android.feature.profile.ui.follow.FollowListUiState
import com.interlinedlist.android.feature.profile.ui.follow.FollowRequestsScreen
import com.interlinedlist.android.feature.profile.ui.follow.FollowRequestsTestTags
import com.interlinedlist.android.feature.profile.ui.follow.FollowRequestsUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FollowScreensTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun followList_rowOpensUserProfile() {
        var opened: String? = null
        composeRule.setContent {
            InterlinedListTheme {
                FollowListScreen(
                    title = "Followers",
                    state = FollowListUiState(
                        users = listOf(
                            FollowUser("1", "ada", "Ada Lovelace", null),
                            FollowUser("2", "adron", "Adron Hall", null),
                        ),
                        isLoading = false,
                    ),
                    onOpenUser = { opened = it },
                    onBack = {},
                    onRetry = {},
                )
            }
        }

        composeRule.onNodeWithTag(FollowListTestTags.LIST).assertIsDisplayed()
        composeRule.onNodeWithTag(FollowListTestTags.row("ada")).performClick()
        assert(opened == "ada")
    }

    @Test
    fun followList_showsEmptyState() {
        composeRule.setContent {
            InterlinedListTheme {
                FollowListScreen(
                    title = "Following",
                    state = FollowListUiState(users = emptyList(), isLoading = false),
                    onOpenUser = {},
                    onBack = {},
                    onRetry = {},
                )
            }
        }

        composeRule.onNodeWithTag(FollowListTestTags.EMPTY).assertIsDisplayed()
    }

    @Test
    fun followRequests_approveAndRejectInvokeCallbacks() {
        var approved: String? = null
        var rejected: String? = null
        composeRule.setContent {
            InterlinedListTheme {
                FollowRequestsScreen(
                    state = FollowRequestsUiState(
                        requests = listOf(
                            FollowUser("1", "eve", "Eve", null),
                            FollowUser("2", "frank", "Frank", null),
                        ),
                        isLoading = false,
                    ),
                    onApprove = { approved = it },
                    onReject = { rejected = it },
                    onOpenUser = {},
                    onBack = {},
                    onRetry = {},
                )
            }
        }

        composeRule.onNodeWithTag(FollowRequestsTestTags.approve("eve")).performClick()
        composeRule.onNodeWithTag(FollowRequestsTestTags.reject("frank")).performClick()
        assert(approved == "1")
        assert(rejected == "2")
    }
}
