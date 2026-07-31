package com.interlinedlist.android.feature.profile.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.interlinedlist.android.core.designsystem.theme.InterlinedListTheme
import com.interlinedlist.android.core.model.CustomerStatus
import com.interlinedlist.android.feature.profile.domain.FollowCounts
import com.interlinedlist.android.feature.profile.domain.ProfileUser
import com.interlinedlist.android.feature.profile.domain.UserSearchResult
import com.interlinedlist.android.feature.profile.ui.profile.AccountMenuTestTags
import com.interlinedlist.android.feature.profile.ui.profile.ProfileScreen
import com.interlinedlist.android.feature.profile.ui.profile.ProfileTestTags
import com.interlinedlist.android.feature.profile.ui.profile.ProfileUiState
import com.interlinedlist.android.feature.profile.ui.search.UserSearchScreen
import com.interlinedlist.android.feature.profile.ui.search.UserSearchTestTags
import com.interlinedlist.android.feature.profile.ui.search.UserSearchUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProfileScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun sampleUser(
        customerStatus: CustomerStatus = CustomerStatus.SUBSCRIBER,
    ) = ProfileUser(
        id = "1",
        username = "adron",
        displayName = "Adron Hall",
        avatarUrl = null,
        bio = "Building things.",
        customerStatus = customerStatus,
        isCurrentUser = true,
    )

    private fun stubbedProfileScreen(
        state: ProfileUiState,
        onEditProfile: () -> Unit = {},
        onSearchUsers: () -> Unit = {},
        onOpenFollowers: () -> Unit = {},
        onOpenFollowing: () -> Unit = {},
        onOpenRequests: () -> Unit = {},
        onSignOut: () -> Unit = {},
    ) {
        composeRule.setContent {
            InterlinedListTheme {
                ProfileScreen(
                    state = state,
                    onEditProfile = onEditProfile,
                    onSearchUsers = onSearchUsers,
                    onOpenFollowers = onOpenFollowers,
                    onOpenFollowing = onOpenFollowing,
                    onOpenRequests = onOpenRequests,
                    onOpenNotifications = {},
                    onOpenOrganizations = {},
                    onOpenIntegrations = {},
                    onOpenSessions = {},
                    onOpenConnectedAccounts = {},
                    onOpenAccountSettings = {},
                    onSignOut = onSignOut,
                    onRetry = {},
                )
            }
        }
    }

    @Test
    fun profile_showsNameUsernameAndSubscriberBadge() {
        stubbedProfileScreen(ProfileUiState(user = sampleUser(), isLoading = false))

        composeRule.onNodeWithTag(ProfileTestTags.DISPLAY_NAME).assertIsDisplayed()
        composeRule.onNodeWithTag(ProfileTestTags.USERNAME).assertIsDisplayed()
        composeRule.onNodeWithTag(ProfileTestTags.SUBSCRIBER_BADGE).assertIsDisplayed()
        composeRule.onNodeWithTag(ProfileTestTags.BIO).assertIsDisplayed()
    }

    @Test
    fun profile_showsTappableFollowerCounts() {
        var openFollowers = false
        var openFollowing = false
        stubbedProfileScreen(
            state = ProfileUiState(
                user = sampleUser(),
                isLoading = false,
                followCounts = FollowCounts(followers = 12, following = 7),
            ),
            onOpenFollowers = { openFollowers = true },
            onOpenFollowing = { openFollowing = true },
        )

        composeRule.onNodeWithTag(ProfileTestTags.FOLLOWERS_COUNT).performClick()
        composeRule.onNodeWithTag(ProfileTestTags.FOLLOWING_COUNT).performClick()

        assert(openFollowers)
        assert(openFollowing)
    }

    @Test
    fun profile_menuRows_invokeCallbacks() {
        var edit = false
        var search = false
        var followers = false
        var requests = false
        var signOut = false
        stubbedProfileScreen(
            state = ProfileUiState(user = sampleUser(), isLoading = false),
            onEditProfile = { edit = true },
            onSearchUsers = { search = true },
            onOpenFollowers = { followers = true },
            onOpenRequests = { requests = true },
            onSignOut = { signOut = true },
        )

        composeRule.onNodeWithTag(AccountMenuTestTags.EDIT_PROFILE).performClick()
        composeRule.onNodeWithTag(AccountMenuTestTags.SEARCH_USERS).performClick()
        composeRule.onNodeWithTag(AccountMenuTestTags.FOLLOWERS).performClick()
        composeRule.onNodeWithTag(AccountMenuTestTags.REQUESTS).performClick()
        composeRule.onNodeWithTag(ProfileTestTags.SIGN_OUT).performClick()

        assert(edit)
        assert(search)
        assert(followers)
        assert(requests)
        assert(signOut)
    }

    @Test
    fun profile_showsProgress_whileLoadingWithNoCache() {
        stubbedProfileScreen(ProfileUiState(user = null, isLoading = true))

        composeRule.onNodeWithTag(ProfileTestTags.PROGRESS).assertIsDisplayed()
    }

    @Test
    fun search_typingRunsAndRowsOpenProfile() {
        var opened: String? = null
        var typed: String? = null
        composeRule.setContent {
            InterlinedListTheme {
                UserSearchScreen(
                    state = UserSearchUiState(
                        query = "ad",
                        results = listOf(
                            UserSearchResult("1", "ada", "Ada Lovelace", null),
                            UserSearchResult("2", "adron", "Adron Hall", null),
                        ),
                    ),
                    onQueryChange = { typed = it },
                    onOpenUser = { opened = it },
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithTag(UserSearchTestTags.QUERY).performTextInput("a")
        assert(typed != null)

        composeRule.onNodeWithTag(UserSearchTestTags.row("ada")).performClick()
        assert(opened == "ada")
    }
}
