package com.interlinedlist.android.feature.documents.ui.collaborators

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.interlinedlist.android.core.designsystem.theme.InterlinedListTheme
import com.interlinedlist.android.feature.documents.domain.Collaborator
import com.interlinedlist.android.feature.documents.domain.CollaboratorRole
import com.interlinedlist.android.feature.documents.domain.DocumentInvite
import com.interlinedlist.android.feature.documents.domain.InviteRole
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Verifies the Manage-access sheet renders collaborators and their role controls. */
@RunWith(AndroidJUnit4::class)
class DocumentCollaboratorsScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun setContent(
        state: DocumentCollaboratorsUiState,
        onChangeRole: (String, CollaboratorRole) -> Unit = { _, _ -> },
        onRevoke: (String) -> Unit = {},
        onRevokeInvite: (String) -> Unit = {},
    ) {
        composeRule.setContent {
            InterlinedListTheme {
                DocumentCollaboratorsSheetContent(
                    state = state,
                    onSearchQueryChange = {},
                    onSearch = {},
                    onSelectInviteRole = {},
                    onInvite = {},
                    onChangeRole = onChangeRole,
                    onRevoke = onRevoke,
                    onInviteEmailChange = {},
                    onSelectInviteEmailRole = {},
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
    ) = DocumentInvite(email, token, role, expiresAt, null, accepted, null, null)

    @Test
    fun rendersCollaborators_withRoleControls() {
        setContent(
            DocumentCollaboratorsUiState(
                collaborators = listOf(
                    Collaborator("u1", CollaboratorRole.ADMIN, "Ada", "ada", "ada@x.io", null),
                    Collaborator("u2", CollaboratorRole.VIEWER, "Bob", "bob", null, null),
                ),
                isLoading = false,
            ),
        )

        composeRule.onNodeWithTag(DocumentCollaboratorsTestTags.row("u1")).assertIsDisplayed()
        composeRule.onNodeWithTag(DocumentCollaboratorsTestTags.row("u2")).assertIsDisplayed()
        composeRule.onNodeWithTag(DocumentCollaboratorsTestTags.revoke("u1")).assertIsDisplayed()
        composeRule
            .onNodeWithTag(DocumentCollaboratorsTestTags.role("u2", CollaboratorRole.EDITOR))
            .assertIsDisplayed()
        composeRule.onNodeWithTag(DocumentCollaboratorsTestTags.SEARCH_FIELD).assertIsDisplayed()
    }

    @Test
    fun roleChip_invokesChangeRole() {
        var changed: Pair<String, CollaboratorRole>? = null
        setContent(
            DocumentCollaboratorsUiState(
                collaborators = listOf(Collaborator("u2", CollaboratorRole.VIEWER, "Bob", "bob", null, null)),
                isLoading = false,
            ),
            onChangeRole = { userId, role -> changed = userId to role },
        )

        composeRule
            .onNodeWithTag(DocumentCollaboratorsTestTags.role("u2", CollaboratorRole.ADMIN))
            .performClick()

        assert(changed == "u2" to CollaboratorRole.ADMIN)
    }

    @Test
    fun revoke_invokesCallback() {
        var revoked: String? = null
        setContent(
            DocumentCollaboratorsUiState(
                collaborators = listOf(Collaborator("u2", CollaboratorRole.VIEWER, "Bob", "bob", null, null)),
                isLoading = false,
            ),
            onRevoke = { revoked = it },
        )

        composeRule.onNodeWithTag(DocumentCollaboratorsTestTags.revoke("u2")).performClick()
        assert(revoked == "u2")
    }

    @Test
    fun emptyState_isShown_whenNoCollaborators() {
        setContent(DocumentCollaboratorsUiState(collaborators = emptyList(), isLoading = false))
        composeRule.onNodeWithTag(DocumentCollaboratorsTestTags.EMPTY).assertIsDisplayed()
    }

    // --- Email invites -----------------------------------------------------

    @Test
    fun pendingInvites_renderRoleStatusAndExpiry() {
        setContent(
            DocumentCollaboratorsUiState(
                isLoading = false,
                invites = DocumentInvitesUiState(
                    isLoading = false,
                    invites = listOf(
                        invite("friend@example.com", "tok-a", InviteRole.EDITOR),
                        invite("late@example.com", "tok-b", expiresAt = "2020-01-02T00:00:00Z"),
                        invite("done@example.com", "tok-c", InviteRole.ADMIN, accepted = true),
                    ),
                ),
            ),
        )

        composeRule.onNodeWithTag(DocumentCollaboratorsTestTags.INVITE_LIST).assertIsDisplayed()
        composeRule.onNodeWithTag(DocumentCollaboratorsTestTags.inviteRow("tok-a")).assertIsDisplayed()
        composeRule.onNodeWithText("friend@example.com").assertIsDisplayed()
        composeRule.onNodeWithText("Editor · No expiry").assertIsDisplayed()
        composeRule.onNodeWithText("Pending").assertIsDisplayed()
        // An invite whose expiry has passed reads as expired, not pending.
        composeRule.onNodeWithText("Expired").assertIsDisplayed()
        composeRule.onNodeWithText("Accepted").assertIsDisplayed()
    }

    @Test
    fun revokeInvite_invokesCallback() {
        var revoked: String? = null
        setContent(
            DocumentCollaboratorsUiState(
                isLoading = false,
                invites = DocumentInvitesUiState(
                    isLoading = false,
                    invites = listOf(invite("friend@example.com", "tok-a")),
                ),
            ),
            onRevokeInvite = { revoked = it },
        )

        composeRule.onNodeWithTag(DocumentCollaboratorsTestTags.inviteRevoke("tok-a")).performClick()
        assert(revoked == "tok-a")
    }

    @Test
    fun sendInvite_isDisabled_forAnIncompleteEmail() {
        setContent(
            DocumentCollaboratorsUiState(
                isLoading = false,
                invites = DocumentInvitesUiState(isLoading = false, email = "friend@"),
            ),
        )
        composeRule.onNodeWithTag(DocumentCollaboratorsTestTags.INVITE_SEND).assertIsNotEnabled()
    }

    @Test
    fun sendInvite_isEnabled_forAValidEmail() {
        setContent(
            DocumentCollaboratorsUiState(
                isLoading = false,
                invites = DocumentInvitesUiState(isLoading = false, email = "friend@example.com"),
            ),
        )
        composeRule.onNodeWithTag(DocumentCollaboratorsTestTags.INVITE_SEND).assertIsEnabled()
    }

    @Test
    fun subscriptionGate_isShown_whenSendingIsRefused() {
        setContent(
            DocumentCollaboratorsUiState(
                isLoading = false,
                invites = DocumentInvitesUiState(
                    isLoading = false,
                    subscriptionRequired = true,
                    errorMessage = "Subscribe to invite people to documents.",
                ),
            ),
        )

        composeRule.onNodeWithTag(DocumentCollaboratorsTestTags.INVITE_GATE).assertIsDisplayed()
        composeRule.onNodeWithTag(DocumentCollaboratorsTestTags.INVITE_EMPTY).assertIsDisplayed()
    }
}
