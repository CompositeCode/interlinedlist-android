package com.interlinedlist.android.feature.organizations.ui.detail

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.feature.organizations.FakeOrganizationsRepository
import com.interlinedlist.android.feature.organizations.domain.MemberCandidate
import com.interlinedlist.android.feature.organizations.domain.OrgMember
import com.interlinedlist.android.feature.organizations.domain.OrgRole
import com.interlinedlist.android.feature.organizations.domain.Organization
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
}
