package com.interlinedlist.android.feature.documents.ui.collaborators

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.interlinedlist.android.core.designsystem.theme.InterlinedListTheme
import com.interlinedlist.android.feature.documents.domain.Collaborator
import com.interlinedlist.android.feature.documents.domain.CollaboratorRole
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
                )
            }
        }
    }

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
}
