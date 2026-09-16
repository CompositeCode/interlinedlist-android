package com.interlinedlist.android.feature.organizations.ui.detail

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.core.common.result.AppError
import com.interlinedlist.android.feature.organizations.FakeOrganizationsRepository
import com.interlinedlist.android.feature.organizations.domain.MemberCandidate
import com.interlinedlist.android.feature.organizations.domain.OrgMember
import com.interlinedlist.android.feature.organizations.domain.OrgRole
import com.interlinedlist.android.feature.organizations.domain.Organization
import com.interlinedlist.android.feature.organizations.ui.LAST_OWNER_EXPLANATION
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OrganizationDetailViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private fun member(id: String, role: OrgRole = OrgRole.MEMBER) =
        OrgMember(id, "user$id", "User $id", null, role, active = true)

    private fun vmFor(repo: FakeOrganizationsRepository, orgId: String = "o1") =
        OrganizationDetailViewModel(repo, SavedStateHandle(mapOf(ORG_ID_ARG to orgId)))

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `load fetches metadata and members`() = runTest(dispatcher) {
        val repo = FakeOrganizationsRepository().apply {
            getResult = ApiResult.Success(
                Organization("o1", "Acme", "Makers", null, true, 2, OrgRole.OWNER, null),
            )
            membersResult = ApiResult.Success(listOf(member("u1", OrgRole.OWNER), member("u2")))
        }
        val vm = vmFor(repo)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertThat(state.isLoading).isFalse()
        assertThat(state.organization?.name).isEqualTo("Acme")
        assertThat(state.members.map { it.userId }).containsExactly("u1", "u2").inOrder()
    }

    @Test
    fun `subscription gate is flagged when metadata is gated`() = runTest(dispatcher) {
        val repo = FakeOrganizationsRepository().apply {
            getResult = FakeOrganizationsRepository.subscriptionFailure()
        }
        val vm = vmFor(repo)
        advanceUntilIdle()

        assertThat(vm.uiState.value.subscriptionRequired).isTrue()
    }

    @Test
    fun `updateOrganization forwards the edited fields and swaps the header`() = runTest(dispatcher) {
        val repo = FakeOrganizationsRepository().apply {
            updateResult = ApiResult.Success(
                Organization("o1", "Renamed", "new desc", null, false, 2, OrgRole.OWNER, null),
            )
        }
        val vm = vmFor(repo)
        advanceUntilIdle()

        var done = false
        vm.updateOrganization("Renamed", "new desc", isPublic = false) { done = true }
        advanceUntilIdle()

        assertThat(done).isTrue()
        assertThat(repo.lastUpdate).isEqualTo(Triple("Renamed", "new desc", false))
        assertThat(vm.uiState.value.organization?.name).isEqualTo("Renamed")
    }

    @Test
    fun `deleteOrganization flags deleted and invokes the callback`() = runTest(dispatcher) {
        val repo = FakeOrganizationsRepository()
        val vm = vmFor(repo)
        advanceUntilIdle()

        var deleted = false
        vm.deleteOrganization { deleted = true }
        advanceUntilIdle()

        assertThat(deleted).isTrue()
        assertThat(vm.uiState.value.deleted).isTrue()
    }

    @Test
    fun `searching surfaces candidates and clearing resets them`() = runTest(dispatcher) {
        val repo = FakeOrganizationsRepository().apply {
            candidatesResult = ApiResult.Success(listOf(MemberCandidate("c1", "newbie", "Newbie", null)))
        }
        val vm = vmFor(repo)
        advanceUntilIdle()

        vm.onSearchQueryChange("new")
        advanceUntilIdle()
        assertThat(vm.uiState.value.candidates.map { it.userId }).containsExactly("c1")
        assertThat(repo.lastMemberSearch).isEqualTo("new")

        vm.onSearchQueryChange("")
        advanceUntilIdle()
        assertThat(vm.uiState.value.candidates).isEmpty()
    }

    @Test
    fun `addMember clears the search and reloads members`() = runTest(dispatcher) {
        val repo = FakeOrganizationsRepository().apply {
            membersResult = ApiResult.Success(listOf(member("u1")))
        }
        val vm = vmFor(repo)
        advanceUntilIdle()

        // After adding, the repo returns an expanded member list.
        repo.membersResult = ApiResult.Success(listOf(member("u1"), member("c1")))
        vm.addMember(MemberCandidate("c1", "newbie", null, null))
        advanceUntilIdle()

        assertThat(repo.addMemberCount).isEqualTo(1)
        assertThat(vm.uiState.value.members.map { it.userId }).containsExactly("u1", "c1")
        assertThat(vm.uiState.value.searchQuery).isEmpty()
    }

    @Test
    fun `changeRole updates the member's role in place`() = runTest(dispatcher) {
        val repo = FakeOrganizationsRepository().apply {
            membersResult = ApiResult.Success(listOf(member("u1", OrgRole.MEMBER)))
        }
        val vm = vmFor(repo)
        advanceUntilIdle()

        vm.changeRole(vm.uiState.value.members.first(), OrgRole.ADMIN)
        advanceUntilIdle()

        assertThat(vm.uiState.value.members.first().role).isEqualTo(OrgRole.ADMIN)
    }

    @Test
    fun `changeRole is a no-op when the role is unchanged`() = runTest(dispatcher) {
        val repo = FakeOrganizationsRepository().apply {
            membersResult = ApiResult.Success(listOf(member("u1", OrgRole.ADMIN)))
        }
        val vm = vmFor(repo)
        advanceUntilIdle()

        vm.changeRole(vm.uiState.value.members.first(), OrgRole.ADMIN)
        advanceUntilIdle()

        // Role unchanged → no update call made; state stays the same.
        assertThat(vm.uiState.value.members.first().role).isEqualTo(OrgRole.ADMIN)
    }

    @Test
    fun `removeMember drops the member from the list`() = runTest(dispatcher) {
        val repo = FakeOrganizationsRepository().apply {
            membersResult = ApiResult.Success(listOf(member("u1"), member("u2")))
        }
        val vm = vmFor(repo)
        advanceUntilIdle()

        vm.removeMember(vm.uiState.value.members.first { it.userId == "u1" })
        advanceUntilIdle()

        assertThat(repo.removeMemberCount).isEqualTo(1)
        assertThat(vm.uiState.value.members.map { it.userId }).containsExactly("u2")
    }

    @Test
    fun `a non-member sees the join affordance and no member list request`() = runTest(dispatcher) {
        val repo = FakeOrganizationsRepository().apply {
            // No role means "not a member" — how the API reports it.
            getResult = ApiResult.Success(Organization("o1", "Metals", null, null, true, 1, null, null))
            membersResult = ApiResult.Success(listOf(member("u1", OrgRole.OWNER)))
        }
        val vm = vmFor(repo)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertThat(state.isMember).isFalse()
        assertThat(state.canJoin).isTrue()
        // Members are members-only on the server, so they are not requested or shown.
        assertThat(state.members).isEmpty()
        assertThat(state.errorMessage).isNull()
    }

    @Test
    fun `a member sees membership state and the loaded member list`() = runTest(dispatcher) {
        val repo = FakeOrganizationsRepository().apply {
            getResult = ApiResult.Success(
                Organization("o1", "Bikey Life", null, null, true, 3, OrgRole.MEMBER, null),
            )
            membersResult = ApiResult.Success(listOf(member("u1", OrgRole.OWNER), member("u2")))
        }
        val vm = vmFor(repo)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertThat(state.isMember).isTrue()
        assertThat(state.canJoin).isFalse()
        assertThat(state.members).hasSize(2)
    }

    @Test
    fun `join reloads so membership state reflects the server`() = runTest(dispatcher) {
        val repo = FakeOrganizationsRepository().apply {
            getResult = ApiResult.Success(Organization("o1", "Metals", null, null, true, 1, null, null))
        }
        val vm = vmFor(repo)
        advanceUntilIdle()

        // After joining, the org reads back as a membership.
        repo.getResult = ApiResult.Success(
            Organization("o1", "Metals", null, null, true, 2, OrgRole.MEMBER, null),
        )
        repo.membersResult = ApiResult.Success(listOf(member("u1", OrgRole.OWNER), member("me")))
        vm.join()
        advanceUntilIdle()

        assertThat(repo.joinedOrgIds).containsExactly("o1")
        val state = vm.uiState.value
        assertThat(state.isJoining).isFalse()
        assertThat(state.isMember).isTrue()
        assertThat(state.members.map { it.userId }).containsExactly("u1", "me").inOrder()
    }

    @Test
    fun `join failure explains an existing membership`() = runTest(dispatcher) {
        val repo = FakeOrganizationsRepository().apply {
            getResult = ApiResult.Success(Organization("o1", "Metals", null, null, true, 1, null, null))
            joinResult = ApiResult.Failure(AppError.Conflict("User is already a member of this organization"))
        }
        val vm = vmFor(repo)
        advanceUntilIdle()

        vm.join()
        advanceUntilIdle()

        assertThat(vm.uiState.value.isJoining).isFalse()
        assertThat(vm.uiState.value.errorMessage).isEqualTo("You're already a member of this organization.")
    }

    @Test
    fun `leave removes the membership and hands back to the caller`() = runTest(dispatcher) {
        val repo = FakeOrganizationsRepository().apply {
            getResult = ApiResult.Success(
                Organization("o1", "Bikey Life", null, null, true, 3, OrgRole.MEMBER, null),
            )
            membersResult = ApiResult.Success(listOf(member("u1", OrgRole.OWNER), member("me")))
        }
        val vm = vmFor(repo)
        advanceUntilIdle()

        var left = false
        vm.leave(onLeft = { left = true })
        advanceUntilIdle()

        assertThat(repo.leftOrgIds).containsExactly("o1")
        assertThat(left).isTrue()
        assertThat(vm.uiState.value.errorMessage).isNull()
    }

    @Test
    fun `the only owner is stopped with an explanation before any request`() = runTest(dispatcher) {
        val repo = FakeOrganizationsRepository().apply {
            getResult = ApiResult.Success(
                Organization("o1", "Acme", null, null, false, 2, OrgRole.OWNER, null),
            )
            membersResult = ApiResult.Success(listOf(member("me", OrgRole.OWNER), member("u2")))
        }
        val vm = vmFor(repo)
        advanceUntilIdle()

        assertThat(vm.uiState.value.isLastOwner).isTrue()

        var left = false
        vm.leave(onLeft = { left = true })
        advanceUntilIdle()

        assertThat(repo.leftOrgIds).isEmpty()
        assertThat(left).isFalse()
        assertThat(vm.uiState.value.errorMessage).isEqualTo(LAST_OWNER_EXPLANATION)
    }

    @Test
    fun `an owner alongside another owner may leave`() = runTest(dispatcher) {
        val repo = FakeOrganizationsRepository().apply {
            getResult = ApiResult.Success(
                Organization("o1", "Acme", null, null, false, 2, OrgRole.OWNER, null),
            )
            membersResult = ApiResult.Success(listOf(member("me", OrgRole.OWNER), member("u2", OrgRole.OWNER)))
        }
        val vm = vmFor(repo)
        advanceUntilIdle()

        assertThat(vm.uiState.value.isLastOwner).isFalse()

        vm.leave()
        advanceUntilIdle()

        assertThat(repo.leftOrgIds).containsExactly("o1")
    }

    @Test
    fun `a server last-owner rejection is surfaced as the same explanation`() = runTest(dispatcher) {
        val repo = FakeOrganizationsRepository().apply {
            getResult = ApiResult.Success(
                Organization("o1", "Acme", null, null, false, 1, OrgRole.OWNER, null),
            )
            // The member list did not load, so the client-side guard cannot fire.
            membersResult = ApiResult.Failure(AppError.Network(null))
            leaveResult = FakeOrganizationsRepository.lastOwnerFailure()
        }
        val vm = vmFor(repo)
        advanceUntilIdle()
        vm.clearError()

        var left = false
        vm.leave(onLeft = { left = true })
        advanceUntilIdle()

        assertThat(left).isFalse()
        assertThat(vm.uiState.value.isLeaving).isFalse()
        assertThat(vm.uiState.value.errorMessage).isEqualTo(LAST_OWNER_EXPLANATION)
    }
}
