package com.interlinedlist.android.feature.profile.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.interlinedlist.android.core.designsystem.theme.InterlinedListTheme
import com.interlinedlist.android.core.model.CustomerStatus
import com.interlinedlist.android.feature.profile.domain.FollowCounts
import com.interlinedlist.android.feature.profile.domain.FollowStatus
import com.interlinedlist.android.feature.profile.domain.MutualConnections
import com.interlinedlist.android.feature.profile.domain.ProfileUser
import com.interlinedlist.android.feature.profile.domain.PublicDocumentSummary
import com.interlinedlist.android.feature.profile.domain.PublicListSummary
import com.interlinedlist.android.feature.profile.domain.PublicPost
import com.interlinedlist.android.feature.profile.ui.profile.ProfileContentTab
import com.interlinedlist.android.feature.profile.ui.profile.ProfileContentTestTags
import com.interlinedlist.android.feature.profile.ui.profile.ProfileUiState
import com.interlinedlist.android.feature.profile.ui.profile.PublicContentState
import com.interlinedlist.android.feature.profile.ui.profile.UserProfileScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UserProfileTabsTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun ada() = ProfileUser(
        id = "u2",
        username = "ada",
        displayName = "Ada Lovelace",
        avatarUrl = null,
        bio = "First programmer.",
        customerStatus = CustomerStatus.FREE,
        isCurrentUser = false,
    )

    @Test
    fun tabs_renderAndPostsShowByDefault() {
        composeRule.setContent {
            InterlinedListTheme {
                UserProfileScreen(
                    state = ProfileUiState(
                        user = ada(),
                        isLoading = false,
                        followStatus = FollowStatus.NOT_FOLLOWING,
                        followCounts = FollowCounts(followers = 10, following = 5),
                        selectedTab = ProfileContentTab.POSTS,
                        content = PublicContentState(
                            posts = listOf(PublicPost("m1", "Hello", null)),
                            loadedTabs = setOf(ProfileContentTab.POSTS),
                        ),
                    ),
                    onBack = {},
                    onRetry = {},
                )
            }
        }

        composeRule.onNodeWithTag(ProfileContentTestTags.TABS).assertIsDisplayed()
        composeRule.onNodeWithTag(ProfileContentTestTags.postRow("m1")).assertIsDisplayed()
    }

    @Test
    fun tapping_listsTab_invokesSelectAndRendersLists() {
        var selected: ProfileContentTab? = null
        composeRule.setContent {
            InterlinedListTheme {
                UserProfileScreen(
                    state = ProfileUiState(
                        user = ada(),
                        isLoading = false,
                        followStatus = FollowStatus.NOT_FOLLOWING,
                        selectedTab = ProfileContentTab.LISTS,
                        content = PublicContentState(
                            lists = listOf(PublicListSummary("l1", "Todos", "desc")),
                            loadedTabs = setOf(ProfileContentTab.LISTS),
                        ),
                    ),
                    onBack = {},
                    onRetry = {},
                    onSelectTab = { selected = it },
                )
            }
        }

        composeRule.onNodeWithTag(ProfileContentTestTags.tab(ProfileContentTab.LISTS)).performClick()
        assert(selected == ProfileContentTab.LISTS)
        composeRule.onNodeWithTag(ProfileContentTestTags.listRow("l1")).assertIsDisplayed()
    }

    @Test
    fun documentsTab_rendersDocumentRowsAndOpensOnTap() {
        var openedDoc: String? = null
        composeRule.setContent {
            InterlinedListTheme {
                UserProfileScreen(
                    state = ProfileUiState(
                        user = ada(),
                        isLoading = false,
                        followStatus = FollowStatus.NOT_FOLLOWING,
                        selectedTab = ProfileContentTab.DOCUMENTS,
                        content = PublicContentState(
                            documents = listOf(PublicDocumentSummary("d1", "Notes")),
                            loadedTabs = setOf(ProfileContentTab.DOCUMENTS),
                        ),
                    ),
                    onBack = {},
                    onRetry = {},
                    onOpenDocument = { openedDoc = it },
                )
            }
        }

        composeRule.onNodeWithTag(ProfileContentTestTags.documentRow("d1")).assertIsDisplayed()
        composeRule.onNodeWithTag(ProfileContentTestTags.documentRow("d1")).performClick()
        assert(openedDoc == "d1")
    }

    @Test
    fun mutualConnections_indicatorShowsWhenPresent() {
        composeRule.setContent {
            InterlinedListTheme {
                UserProfileScreen(
                    state = ProfileUiState(
                        user = ada(),
                        isLoading = false,
                        followStatus = FollowStatus.NOT_FOLLOWING,
                        selectedTab = ProfileContentTab.POSTS,
                        mutualConnections = MutualConnections(mutualFollowers = 3, mutualFollowing = 1),
                        content = PublicContentState(loadedTabs = setOf(ProfileContentTab.POSTS)),
                    ),
                    onBack = {},
                    onRetry = {},
                )
            }
        }

        composeRule.onNodeWithTag(ProfileContentTestTags.MUTUAL).assertIsDisplayed()
    }
}
