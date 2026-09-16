package com.interlinedlist.android.feature.organizations.ui.detail

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.interlinedlist.android.core.designsystem.theme.InterlinedListTheme
import com.interlinedlist.android.feature.organizations.domain.OrgLinkedInPage
import com.interlinedlist.android.feature.organizations.domain.OrgLinkedInStatus
import com.interlinedlist.android.feature.organizations.domain.OrgMember
import com.interlinedlist.android.feature.organizations.domain.OrgRole
import com.interlinedlist.android.feature.organizations.domain.Organization
import com.interlinedlist.android.feature.organizations.ui.LINKEDIN_DISCONNECT_CONSEQUENCE
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Compose UI coverage for the stateless [OrganizationDetailScreen]. Runs on-device;
 * the orchestrator executes instrumented tests after merge, so this is written to
 * compile and be correct.
 *
 * The role × action expectations mirror
 * `https://interlinedlist.com/help/organizations`, and are asserted in plain JVM
 * form by `OrgPermissionsTest`; here they are checked at the rendering level.
 */
@RunWith(AndroidJUnit4::class)
class OrganizationDetailScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun setScreen(
        state: OrganizationDetailUiState,
        onChangeRole: (OrgMember, OrgRole) -> Unit = { _, _ -> },
        onRemoveMember: (OrgMember) -> Unit = {},
        onSaveEdit: (String?, String?, Boolean?) -> Unit = { _, _, _ -> },
        onDelete: () -> Unit = {},
        onJoin: () -> Unit = {},
        onLeave: () -> Unit = {},
        onAssignLinkedInPage: (OrgMember, String?) -> Unit = { _, _ -> },
        onSyncLinkedInPages: () -> Unit = {},
        onRemoveLinkedInCredential: () -> Unit = {},
    ) {
        composeRule.setContent {
            InterlinedListTheme {
                OrganizationDetailScreen(
                    state = state,
                    onBack = {},
                    onSearchQueryChange = {},
                    onAddCandidate = {},
                    onChangeRole = onChangeRole,
                    onRemoveMember = onRemoveMember,
                    onSaveEdit = onSaveEdit,
                    onDelete = onDelete,
                    onJoin = onJoin,
                    onLeave = onLeave,
                    onAssignLinkedInPage = onAssignLinkedInPage,
                    onSyncLinkedInPages = onSyncLinkedInPages,
                    onRemoveLinkedInCredential = onRemoveLinkedInCredential,
                )
            }
        }
    }

    private fun state(
        role: OrgRole?,
        members: List<OrgMember> = emptyList(),
        isPublic: Boolean = false,
        isSystem: Boolean = false,
        linkedIn: OrgLinkedInStatus? = null,
    ) = OrganizationDetailUiState(
        organization = Organization(
            id = "o1",
            name = "Acme Corp",
            description = "Makers",
            avatarUrl = null,
            isPublic = isPublic,
            memberCount = members.size,
            role = role,
            updatedAt = null,
            isSystem = isSystem,
        ),
        members = members,
        isLoading = false,
        linkedIn = linkedIn,
    )

    private val ada = OrgMember("u1", "ada", "Ada", null, OrgRole.OWNER, active = true)
    private val secondOwner = OrgMember("u3", "linus", null, null, OrgRole.OWNER, active = true)
    private val grace = OrgMember("u2", "grace", null, null, OrgRole.MEMBER, active = true)

    private fun loaded() = state(OrgRole.OWNER, listOf(ada, secondOwner, grace))

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

        composeRule.onNodeWithTag(OrganizationDetailTestTags.remove("u2")).performClick()
        assert(removed?.userId == "u2")
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
        setScreen(state = state(OrgRole.OWNER))

        composeRule.onNodeWithTag(OrganizationDetailTestTags.EMPTY).assertIsDisplayed()
    }

    @Test
    fun nonMember_seesJoinPrompt_andNoMemberTools() {
        setScreen(state = state(role = null, isPublic = true))

        composeRule.onNodeWithTag(OrganizationDetailTestTags.JOIN_PROMPT).assertIsDisplayed()
        composeRule.onNodeWithTag(OrganizationDetailTestTags.JOIN).assertIsDisplayed()
        composeRule.onNodeWithTag(OrganizationDetailTestTags.SEARCH).assertDoesNotExist()
    }

    @Test
    fun nonMember_join_reportsTheAction() {
        var joined = false
        setScreen(state = state(role = null, isPublic = true), onJoin = { joined = true })

        composeRule.onNodeWithTag(OrganizationDetailTestTags.JOIN).performClick()
        assert(joined)
    }

    @Test
    fun nonMemberOfPrivateOrg_isNotOfferedJoin() {
        setScreen(state = state(role = null, isPublic = false))

        composeRule.onNodeWithTag(OrganizationDetailTestTags.JOIN_PROMPT).assertIsDisplayed()
        composeRule.onNodeWithTag(OrganizationDetailTestTags.JOIN).assertDoesNotExist()
    }

    @Test
    fun member_leaveFlow_confirmsBeforeLeaving() {
        var left = false
        setScreen(
            state = state(OrgRole.MEMBER, listOf(ada, grace), isPublic = true),
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
            state = state(OrgRole.OWNER, listOf(ada, grace)),
            onLeave = { left = true },
        )

        composeRule.onNodeWithTag(OrganizationDetailTestTags.OVERFLOW).performClick()
        composeRule.onNodeWithTag(OrganizationDetailTestTags.LEAVE).performClick()
        // The dialog explains why, and offers no destructive confirm at all.
        composeRule.onNodeWithTag(OrganizationDetailTestTags.LAST_OWNER_NOTICE).assertIsDisplayed()
        composeRule.onNodeWithTag(OrganizationDetailTestTags.LEAVE_CONFIRM).assertDoesNotExist()
        assert(!left)
    }

    // ---- Role-gated affordances --------------------------------------------

    @Test
    fun nonMember_isOfferedNoOverflowAtAll() {
        // #81 left Edit and Delete exposed to non-members; with nothing permitted
        // the menu itself is gone, so neither can be reached.
        setScreen(state = state(role = null, isPublic = true))

        composeRule.onNodeWithTag(OrganizationDetailTestTags.OVERFLOW).assertDoesNotExist()
        composeRule.onNodeWithTag(OrganizationDetailTestTags.EDIT).assertDoesNotExist()
        composeRule.onNodeWithTag(OrganizationDetailTestTags.DELETE).assertDoesNotExist()
        composeRule.onNodeWithTag(OrganizationDetailTestTags.LEAVE).assertDoesNotExist()
    }

    @Test
    fun member_seesLeaveButNeitherEditNorDelete() {
        setScreen(state = state(OrgRole.MEMBER, listOf(ada, grace), isPublic = true))

        composeRule.onNodeWithTag(OrganizationDetailTestTags.OVERFLOW).performClick()
        composeRule.onNodeWithTag(OrganizationDetailTestTags.LEAVE).assertIsDisplayed()
        composeRule.onNodeWithTag(OrganizationDetailTestTags.EDIT).assertDoesNotExist()
        composeRule.onNodeWithTag(OrganizationDetailTestTags.DELETE).assertDoesNotExist()
    }

    @Test
    fun member_getsNoMemberManagementControls() {
        setScreen(state = state(OrgRole.MEMBER, listOf(ada, grace), isPublic = true))

        // "Member: Basic access" — the roster is visible, read-only.
        composeRule.onNodeWithTag(OrganizationDetailTestTags.SEARCH).assertDoesNotExist()
        composeRule.onNodeWithTag(OrganizationDetailTestTags.remove("u2")).assertDoesNotExist()
        composeRule.onNodeWithTag(OrganizationDetailTestTags.roleChip("u2", OrgRole.ADMIN))
            .assertDoesNotExist()
        composeRule.onNodeWithTag(OrganizationDetailTestTags.roleLabel("u2")).assertIsDisplayed()
    }

    @Test
    fun admin_mayEditButNotDelete() {
        setScreen(state = state(OrgRole.ADMIN, listOf(ada, grace), isPublic = true))

        composeRule.onNodeWithTag(OrganizationDetailTestTags.OVERFLOW).performClick()
        composeRule.onNodeWithTag(OrganizationDetailTestTags.EDIT).assertIsDisplayed()
        // "Owner: … can delete the org" — an admin cannot.
        composeRule.onNodeWithTag(OrganizationDetailTestTags.DELETE).assertDoesNotExist()
    }

    @Test
    fun admin_mayNotManageAnOwner_butMayManageOthers() {
        setScreen(state = state(OrgRole.ADMIN, listOf(ada, grace), isPublic = true))

        // "Admin: Can add and remove members and change roles (except owner)"
        composeRule.onNodeWithTag(OrganizationDetailTestTags.remove("u1")).assertDoesNotExist()
        composeRule.onNodeWithTag(OrganizationDetailTestTags.roleLabel("u1")).assertIsDisplayed()

        composeRule.onNodeWithTag(OrganizationDetailTestTags.remove("u2")).assertIsDisplayed()
        composeRule.onNodeWithTag(OrganizationDetailTestTags.roleChip("u2", OrgRole.ADMIN))
            .assertIsDisplayed()
        // An admin cannot hand out ownership.
        composeRule.onNodeWithTag(OrganizationDetailTestTags.roleChip("u2", OrgRole.OWNER))
            .assertDoesNotExist()
    }

    @Test
    fun owner_mayGrantOwnership() {
        setScreen(state = loaded())

        composeRule.onNodeWithTag(OrganizationDetailTestTags.roleChip("u2", OrgRole.OWNER))
            .assertIsDisplayed()
    }

    @Test
    fun theOnlyOwner_isOfferedNeitherDemotionNorRemoval() {
        // Ada is the single owner: the server refuses both, so neither is offered.
        setScreen(state = state(OrgRole.OWNER, listOf(ada, grace)))

        composeRule.onNodeWithTag(OrganizationDetailTestTags.remove("u1")).assertDoesNotExist()
        composeRule.onNodeWithTag(OrganizationDetailTestTags.roleChip("u1", OrgRole.MEMBER))
            .assertDoesNotExist()
        composeRule.onNodeWithTag(OrganizationDetailTestTags.roleChip("u1", OrgRole.OWNER))
            .assertIsDisplayed()
        composeRule.onNodeWithTag(OrganizationDetailTestTags.MEMBER_LAST_OWNER_NOTICE)
            .assertIsDisplayed()
    }

    @Test
    fun systemOrganization_offersNoLeaveOrDelete() {
        // "You cannot leave the system \"The Public\" organization."
        setScreen(state = state(OrgRole.OWNER, listOf(ada, secondOwner), isPublic = true, isSystem = true))

        composeRule.onNodeWithTag(OrganizationDetailTestTags.SYSTEM).assertIsDisplayed()
        composeRule.onNodeWithTag(OrganizationDetailTestTags.OVERFLOW).performClick()
        composeRule.onNodeWithTag(OrganizationDetailTestTags.LEAVE).assertDoesNotExist()
        composeRule.onNodeWithTag(OrganizationDetailTestTags.DELETE).assertDoesNotExist()
        // Editing is still a role question, and an owner may.
        composeRule.onNodeWithTag(OrganizationDetailTestTags.EDIT).assertIsDisplayed()
    }

    // ---- Visibility ---------------------------------------------------------

    @Test
    fun visibility_isShownOnTheHeader() {
        setScreen(state = state(OrgRole.MEMBER, listOf(ada, grace), isPublic = true))

        composeRule.onNodeWithTag(OrganizationDetailTestTags.VISIBILITY)
            .assertTextContains("Public · anyone can see and join")
        composeRule.onNodeWithTag(OrganizationDetailTestTags.VIEWER_ROLE)
            .assertTextContains("Your role: Member")
    }

    @Test
    fun privateVisibility_isShownOnTheHeader() {
        setScreen(state = state(OrgRole.MEMBER, listOf(ada, grace), isPublic = false))

        composeRule.onNodeWithTag(OrganizationDetailTestTags.VISIBILITY)
            .assertTextContains("Private · invite-only; an owner or admin adds members")
    }

    @Test
    fun owner_canToggleVisibility_andItRoundTripsToTheSave() {
        var saved: Triple<String?, String?, Boolean?>? = null
        setScreen(
            state = state(OrgRole.OWNER, listOf(ada, secondOwner), isPublic = false),
            onSaveEdit = { name, description, isPublic -> saved = Triple(name, description, isPublic) },
        )

        composeRule.onNodeWithTag(OrganizationDetailTestTags.OVERFLOW).performClick()
        composeRule.onNodeWithTag(OrganizationDetailTestTags.EDIT).performClick()
        composeRule.onNodeWithTag(OrganizationDetailTestTags.EDIT_DIALOG).assertIsDisplayed()
        composeRule.onNodeWithTag(OrganizationDetailTestTags.EDIT_VISIBILITY).performClick()
        composeRule.onNodeWithText("Save").performClick()

        assert(saved?.third == true) { "expected the toggled visibility to reach the save, got $saved" }
    }

    // ---- LinkedIn company pages --------------------------------------------

    private val acmePage = OrgLinkedInPage(id = "p1", linkedInPageId = "12345678", name = "Acme Corp")
    private val labsPage = OrgLinkedInPage(id = "p2", linkedInPageId = "87654321", name = "Acme Labs")

    private fun connected(assignments: Map<String, String> = emptyMap()) = OrgLinkedInStatus(
        connected = true,
        expiresAt = "2026-12-01T00:00:00.000Z",
        pages = listOf(acmePage, labsPage),
        assignments = assignments,
    )

    @Test
    fun owner_seesTheLinkedInSection() {
        setScreen(state = state(OrgRole.OWNER, listOf(ada, grace), linkedIn = connected()))

        composeRule.onNodeWithTag(OrganizationDetailTestTags.LINKEDIN).assertIsDisplayed()
        composeRule.onNodeWithTag(OrganizationDetailTestTags.LINKEDIN_CONNECTED).assertIsDisplayed()
        composeRule.onNodeWithTag(OrganizationDetailTestTags.linkedInPage("p1")).assertIsDisplayed()
    }

    @Test
    fun admin_seesTheLinkedInSection() {
        setScreen(state = state(OrgRole.ADMIN, listOf(ada, grace), linkedIn = connected()))

        composeRule.onNodeWithTag(OrganizationDetailTestTags.LINKEDIN).assertIsDisplayed()
    }

    @Test
    fun member_isOfferedNoLinkedInSection() {
        // The server refuses a member outright ("Admin or owner required"), so the
        // section they could not use is not shown at all.
        setScreen(state = state(OrgRole.MEMBER, listOf(ada, grace), linkedIn = connected()))

        composeRule.onNodeWithTag(OrganizationDetailTestTags.LINKEDIN).assertDoesNotExist()
        composeRule.onNodeWithTag(OrganizationDetailTestTags.LINKEDIN_SYNC).assertDoesNotExist()
        composeRule.onNodeWithTag(OrganizationDetailTestTags.LINKEDIN_DISCONNECT).assertDoesNotExist()
    }

    @Test
    fun notConnected_readsAsAState_notAnError() {
        setScreen(
            state = state(
                OrgRole.OWNER,
                listOf(ada, grace),
                linkedIn = OrgLinkedInStatus.NOT_CONNECTED,
            ),
        )

        composeRule.onNodeWithTag(OrganizationDetailTestTags.LINKEDIN_NOT_CONNECTED).assertIsDisplayed()
        composeRule.onNodeWithTag(OrganizationDetailTestTags.LINKEDIN_ERROR).assertDoesNotExist()
        // Nothing to sync or disconnect without a credential.
        composeRule.onNodeWithTag(OrganizationDetailTestTags.LINKEDIN_SYNC).assertDoesNotExist()
        composeRule.onNodeWithTag(OrganizationDetailTestTags.LINKEDIN_DISCONNECT).assertDoesNotExist()
    }

    @Test
    fun disconnect_statesTheConsequenceAndOnlyActsOnConfirmation() {
        var disconnected = false
        setScreen(
            state = state(OrgRole.OWNER, listOf(ada, grace), linkedIn = connected()),
            onRemoveLinkedInCredential = { disconnected = true },
        )

        composeRule.onNodeWithTag(OrganizationDetailTestTags.LINKEDIN_DISCONNECT).performClick()
        composeRule.onNodeWithTag(OrganizationDetailTestTags.LINKEDIN_DISCONNECT_DIALOG).assertIsDisplayed()
        // The dialog names the cost before anything happens.
        composeRule.onNodeWithText(LINKEDIN_DISCONNECT_CONSEQUENCE).assertIsDisplayed()
        assert(!disconnected) { "the credential must not be removed before confirmation" }

        composeRule.onNodeWithTag(OrganizationDetailTestTags.LINKEDIN_DISCONNECT_CONFIRM).performClick()
        assert(disconnected)
    }

    @Test
    fun disconnect_cancelLeavesTheCredentialAlone() {
        var disconnected = false
        setScreen(
            state = state(OrgRole.OWNER, listOf(ada, grace), linkedIn = connected()),
            onRemoveLinkedInCredential = { disconnected = true },
        )

        composeRule.onNodeWithTag(OrganizationDetailTestTags.LINKEDIN_DISCONNECT).performClick()
        composeRule.onNodeWithText("Cancel").performClick()

        assert(!disconnected)
    }

    @Test
    fun sync_refreshesThePageList() {
        var synced = false
        setScreen(
            state = state(OrgRole.OWNER, listOf(ada, grace), linkedIn = connected()),
            onSyncLinkedInPages = { synced = true },
        )

        composeRule.onNodeWithTag(OrganizationDetailTestTags.LINKEDIN_SYNC).performClick()
        assert(synced)
    }

    @Test
    fun assigningAPage_reportsTheMemberAndPage() {
        var assigned: Pair<String, String?>? = null
        setScreen(
            state = state(OrgRole.OWNER, listOf(ada, grace), linkedIn = connected()),
            onAssignLinkedInPage = { member, pageId -> assigned = member.userId to pageId },
        )

        composeRule.onNodeWithTag(OrganizationDetailTestTags.linkedInAssignment("u2")).performClick()
        composeRule.onNodeWithTag(OrganizationDetailTestTags.linkedInPageOption("u2", "p2")).performClick()

        assert(assigned == "u2" to "p2") { "expected grace to be assigned Acme Labs, got $assigned" }
    }

    @Test
    fun clearingAnAssignment_sendsNoPage() {
        var assigned: Pair<String, String?>? = null
        setScreen(
            state = state(
                OrgRole.OWNER,
                listOf(ada, grace),
                linkedIn = connected(mapOf("u2" to "p1")),
            ),
            onAssignLinkedInPage = { member, pageId -> assigned = member.userId to pageId },
        )

        // The current assignment is what the control reads.
        composeRule.onNodeWithTag(OrganizationDetailTestTags.linkedInAssignment("u2"))
            .assertTextContains("Acme Corp")
        composeRule.onNodeWithTag(OrganizationDetailTestTags.linkedInAssignment("u2")).performClick()
        composeRule.onNodeWithTag(OrganizationDetailTestTags.linkedInClearOption("u2")).performClick()

        assert(assigned == "u2" to null) { "expected the assignment to be cleared, got $assigned" }
    }
}
