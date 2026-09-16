package com.interlinedlist.android.feature.organizations.ui.detail

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.interlinedlist.android.core.designsystem.theme.InterlinedListTheme
import com.interlinedlist.android.feature.organizations.domain.OrgMember
import com.interlinedlist.android.feature.organizations.domain.OrgRole
import com.interlinedlist.android.feature.organizations.domain.Organization
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Compose UI coverage for the stateless [OrganizationDetailScreen]. Runs on-device;
 * the orchestrator executes instrumented tests after merge, so this is written to
 * compile and be correct.
 */
@RunWith(AndroidJUnit4::class)
class OrganizationDetailScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun setScreen(
        state: OrganizationDetailUiState,
        onRemoveMember: (OrgMember) -> Unit = {},
        onDelete: () -> Unit = {},
        onJoin: () -> Unit = {},
        onLeave: () -> Unit = {},
    ) {
        composeRule.setContent {
            InterlinedListTheme {
                OrganizationDetailScreen(
                    state = state,
                    onBack = {},
                    onSearchQueryChange = {},
                    onAddCandidate = {},
                    onChangeRole = { _, _ -> },
                    onRemoveMember = onRemoveMember,
                    onSaveEdit = { _, _, _ -> },
                    onDelete = onDelete,
                    onJoin = onJoin,
                    onLeave = onLeave,
                )
            }
        }
    }

    private fun loaded() = OrganizationDetailUiState(
        organization = Organization("o1", "Acme Corp", "Makers", null, false, 2, OrgRole.OWNER, null),
        members = listOf(
            OrgMember("u1", "ada", "Ada", null, OrgRole.OWNER, active = true),
            OrgMember("u2", "grace", null, null, OrgRole.MEMBER, active = true),
        ),
        isLoading = false,
    )

    @Test
    fun rendersMembers_andSearchField() {
        setScreen(state = loaded())

        composeRule.onNodeWithTag(OrganizationDetailTestTags.SEARCH).assertIsDisplayed()
        composeRule.onNodeWithTag(OrganizationDetailTestTags.member("u1")).assertIsDisplayed()
        composeRule.onNodeWithTag(OrganizationDetailTestTags.member("u2")).assertIsDisplayed()
    }

    @Test
    fun removesMember_onCloseTap() {
        var removed: OrgMember? = null
        setScreen(state = loaded(), onRemoveMember = { removed = it })

        composeRule.onNodeWithTag(OrganizationDetailTestTags.remove("u1")).performClick()
        assert(removed?.userId == "u1")
    }

    @Test
    fun deleteFlow_confirmsBeforeDeleting() {
        var deleted = false
        setScreen(state = loaded(), onDelete = { deleted = true })

        composeRule.onNodeWithTag(OrganizationDetailTestTags.OVERFLOW).performClick()
        composeRule.onNodeWithTag(OrganizationDetailTestTags.DELETE).performClick()
        // A confirmation dialog appears before the destructive delete fires.
        composeRule.onNodeWithTag(OrganizationDetailTestTags.DELETE_DIALOG).assertIsDisplayed()
        composeRule.onNodeWithTag(OrganizationDetailTestTags.DELETE_CONFIRM).performClick()
        assert(deleted)
    }

    @Test
    fun showsEmptyState_whenNoMembers() {
        setScreen(
            state = OrganizationDetailUiState(
                organization = Organization("o1", "Acme", null, null, false, 0, OrgRole.OWNER, null),
                members = emptyList(),
                isLoading = false,
            ),
        )

        composeRule.onNodeWithTag(OrganizationDetailTestTags.EMPTY).assertIsDisplayed()
    }

    @Test
    fun nonMember_seesJoinPrompt_andNoMemberTools() {
        setScreen(
            state = OrganizationDetailUiState(
                // No role: the API reports membership only for the caller's own orgs.
                organization = Organization("o1", "Metals", null, null, true, 1, null, null),
                isLoading = false,
            ),
        )

        composeRule.onNodeWithTag(OrganizationDetailTestTags.JOIN_PROMPT).assertIsDisplayed()
        composeRule.onNodeWithTag(OrganizationDetailTestTags.JOIN).assertIsDisplayed()
        composeRule.onNodeWithTag(OrganizationDetailTestTags.SEARCH).assertDoesNotExist()
    }

    @Test
    fun nonMember_join_reportsTheAction() {
        var joined = false
        setScreen(
            state = OrganizationDetailUiState(
                organization = Organization("o1", "Metals", null, null, true, 1, null, null),
                isLoading = false,
            ),
            onJoin = { joined = true },
        )

        composeRule.onNodeWithTag(OrganizationDetailTestTags.JOIN).performClick()
        assert(joined)
    }

    @Test
    fun nonMemberOfPrivateOrg_isNotOfferedJoin() {
        setScreen(
            state = OrganizationDetailUiState(
                organization = Organization("o1", "Acme", null, null, false, 2, null, null),
                isLoading = false,
            ),
        )

        composeRule.onNodeWithTag(OrganizationDetailTestTags.JOIN_PROMPT).assertIsDisplayed()
        composeRule.onNodeWithTag(OrganizationDetailTestTags.JOIN).assertDoesNotExist()
    }

    @Test
    fun member_leaveFlow_confirmsBeforeLeaving() {
        var left = false
        setScreen(
            state = OrganizationDetailUiState(
                organization = Organization("o1", "Bikey Life", null, null, true, 3, OrgRole.MEMBER, null),
                members = listOf(
                    OrgMember("u1", "ada", "Ada", null, OrgRole.OWNER, active = true),
                    OrgMember("me", "me", null, null, OrgRole.MEMBER, active = true),
                ),
                isLoading = false,
            ),
            onLeave = { left = true },
        )

        composeRule.onNodeWithTag(OrganizationDetailTestTags.OVERFLOW).performClick()
        composeRule.onNodeWithTag(OrganizationDetailTestTags.LEAVE).performClick()
        composeRule.onNodeWithTag(OrganizationDetailTestTags.LEAVE_DIALOG).assertIsDisplayed()
        composeRule.onNodeWithTag(OrganizationDetailTestTags.LEAVE_CONFIRM).performClick()
        assert(left)
    }

    @Test
    fun soleOwner_isExplainedInsteadOfBeingAllowedToLeave() {
        var left = false
        setScreen(
            state = OrganizationDetailUiState(
                organization = Organization("o1", "Acme", null, null, false, 2, OrgRole.OWNER, null),
                members = listOf(
                    OrgMember("me", "me", null, null, OrgRole.OWNER, active = true),
                    OrgMember("u2", "grace", null, null, OrgRole.MEMBER, active = true),
                ),
                isLoading = false,
            ),
            onLeave = { left = true },
        )

        composeRule.onNodeWithTag(OrganizationDetailTestTags.OVERFLOW).performClick()
        composeRule.onNodeWithTag(OrganizationDetailTestTags.LEAVE).performClick()
        // The dialog explains why, and offers no destructive confirm at all.
        composeRule.onNodeWithTag(OrganizationDetailTestTags.LAST_OWNER_NOTICE).assertIsDisplayed()
        composeRule.onNodeWithTag(OrganizationDetailTestTags.LEAVE_CONFIRM).assertDoesNotExist()
        assert(!left)
    }

    @Test
    fun nonMember_isNotOfferedLeave() {
        setScreen(
            state = OrganizationDetailUiState(
                organization = Organization("o1", "Metals", null, null, true, 1, null, null),
                isLoading = false,
            ),
        )

        composeRule.onNodeWithTag(OrganizationDetailTestTags.OVERFLOW).performClick()
        composeRule.onNodeWithTag(OrganizationDetailTestTags.LEAVE).assertDoesNotExist()
    }
}
