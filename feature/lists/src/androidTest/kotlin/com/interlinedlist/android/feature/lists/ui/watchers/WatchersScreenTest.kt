package com.interlinedlist.android.feature.lists.ui.watchers

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.interlinedlist.android.core.designsystem.theme.InterlinedListTheme
import com.interlinedlist.android.feature.lists.domain.InviteRole
import com.interlinedlist.android.feature.lists.domain.ListInvite
import com.interlinedlist.android.feature.lists.domain.Watcher
import com.interlinedlist.android.feature.lists.domain.WatcherRole
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Verifies the watchers screen renders watcher rows and the empty state. */
@RunWith(AndroidJUnit4::class)
class WatchersScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun setScreen(
        state: WatchersUiState,
        onRevokeInvite: (String) -> Unit = {},
        onSelectInviteEmailRole: (InviteRole) -> Unit = {},
    ) {
        composeRule.setContent {
            InterlinedListTheme {
                WatchersScreen(
                    state = state,
                    onBack = {},
                    onSearchQueryChange = {},
                    onAddCandidate = {},
                    onChangeRole = { _, _ -> },
                    onRemoveWatcher = {},
                    onInviteEmailChange = {},
                    onSelectInviteEmailRole = onSelectInviteEmailRole,
                    onSendInvite = {},
                    onRevokeInvite = onRevokeInvite,
                )
            }
        }
    }

    private fun invite(
        email: String,
        token: String,
        role: InviteRole = InviteRole.VIEWER,
        expiresAt: String? = null,
        accepted: Boolean = false,
    ) = ListInvite(email, token, role, expiresAt, null, accepted, null, null)

    @Test
    fun rendersWatcherRows() {
        setScreen(
            WatchersUiState(
                watchers = listOf(
                    Watcher("u1", "ada", "Ada Lovelace", null, WatcherRole.EDITOR),
                ),
                isLoading = false,
            ),
        )

        composeRule.onNodeWithTag(WatchersTestTags.watcher("u1")).assertIsDisplayed()
        composeRule.onNodeWithText("Ada Lovelace").assertIsDisplayed()
        composeRule.onNodeWithText("@ada").assertIsDisplayed()
    }

    @Test
    fun showsEmptyState_whenNoWatchers() {
        setScreen(WatchersUiState(watchers = emptyList(), isLoading = false))

        composeRule.onNodeWithTag(WatchersTestTags.EMPTY).assertIsDisplayed()
    }

    // --- Email invites -----------------------------------------------------

    @Test
    fun pendingInvites_renderRoleStatusAndExpiry() {
        setScreen(
            WatchersUiState(
                isLoading = false,
                invites = ListInvitesUiState(
                    isLoading = false,
                    invites = listOf(
                        invite("friend@example.com", "tok-a", InviteRole.EDITOR),
                        invite("late@example.com", "tok-b", expiresAt = "2020-01-02T00:00:00Z"),
                        invite("done@example.com", "tok-c", InviteRole.ADMIN, accepted = true),
                    ),
                ),
            ),
        )

        composeRule.onNodeWithTag(WatchersTestTags.INVITE_LIST).assertIsDisplayed()
        composeRule.onNodeWithTag(WatchersTestTags.inviteRow("tok-a")).assertIsDisplayed()
        composeRule.onNodeWithText("friend@example.com").assertIsDisplayed()
        composeRule.onNodeWithText("Edit · No expiry").assertIsDisplayed()
        composeRule.onNodeWithText("Pending").assertIsDisplayed()
        // An invite whose expiry has passed reads as expired, not pending.
        composeRule.onNodeWithText("Expired").assertIsDisplayed()
        composeRule.onNodeWithText("Accepted").assertIsDisplayed()
    }

    @Test
    fun revokeInvite_invokesCallback() {
        var revoked: String? = null
        setScreen(
            WatchersUiState(
                isLoading = false,
                invites = ListInvitesUiState(
                    isLoading = false,
                    invites = listOf(invite("friend@example.com", "tok-a")),
                ),
            ),
            onRevokeInvite = { revoked = it },
        )

        composeRule.onNodeWithTag(WatchersTestTags.inviteRevoke("tok-a")).performClick()
        assert(revoked == "tok-a")
    }

    @Test
    fun inviteRoleChips_areIndividuallySelectable() {
        var picked: InviteRole? = null
        setScreen(
            WatchersUiState(isLoading = false, invites = ListInvitesUiState(isLoading = false)),
            onSelectInviteEmailRole = { picked = it },
        )

        composeRule.onNodeWithTag(WatchersTestTags.inviteEmailRole(InviteRole.ADMIN)).performClick()
        assert(picked == InviteRole.ADMIN)

        composeRule.onNodeWithTag(WatchersTestTags.inviteEmailRole(InviteRole.VIEWER)).performClick()
        assert(picked == InviteRole.VIEWER)
    }

    @Test
    fun sendInvite_isDisabled_forAnIncompleteEmail() {
        setScreen(
            WatchersUiState(
                isLoading = false,
                invites = ListInvitesUiState(isLoading = false, email = "friend@"),
            ),
        )
        composeRule.onNodeWithTag(WatchersTestTags.INVITE_SEND).assertIsNotEnabled()
    }

    @Test
    fun sendInvite_isEnabled_forAValidEmail() {
        setScreen(
            WatchersUiState(
                isLoading = false,
                invites = ListInvitesUiState(isLoading = false, email = "friend@example.com"),
            ),
        )
        composeRule.onNodeWithTag(WatchersTestTags.INVITE_SEND).assertIsEnabled()
    }

    @Test
    fun subscriptionGate_isShown_whenSendingIsRefused() {
        setScreen(
            WatchersUiState(
                isLoading = false,
                invites = ListInvitesUiState(
                    isLoading = false,
                    subscriptionRequired = true,
                    errorMessage = "Subscribe to invite people to lists.",
                ),
            ),
        )

        composeRule.onNodeWithTag(WatchersTestTags.INVITE_GATE).assertIsDisplayed()
        composeRule.onNodeWithText("Subscribe to invite people to lists.").assertIsDisplayed()
        // Revoking stays available even behind the gate.
        composeRule.onNodeWithTag(WatchersTestTags.INVITE_EMPTY).assertIsDisplayed()
    }
}
