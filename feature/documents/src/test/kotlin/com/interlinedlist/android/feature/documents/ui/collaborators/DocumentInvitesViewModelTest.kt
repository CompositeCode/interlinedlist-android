package com.interlinedlist.android.feature.documents.ui.collaborators

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.core.common.result.AppError
import com.interlinedlist.android.feature.documents.domain.DocumentInvite
import com.interlinedlist.android.feature.documents.domain.InviteRole
import com.interlinedlist.android.feature.documents.domain.InviteStatus
import com.interlinedlist.android.feature.documents.ui.FakeDocumentsRepository
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
import java.time.Instant

/** The email-invite section of the Manage-access sheet. */
@OptIn(ExperimentalCoroutinesApi::class)
class DocumentInvitesViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val now: Instant = Instant.parse("2026-06-01T12:00:00Z")

    private fun invite(
        email: String,
        token: String = "tok-$email",
        role: InviteRole = InviteRole.VIEWER,
        expiresAt: String? = null,
        accepted: Boolean = false,
    ) = DocumentInvite(email, token, role, expiresAt, null, accepted, null, null)

    private fun viewModel(repo: FakeDocumentsRepository) =
        DocumentCollaboratorsViewModel(
            repo, SavedStateHandle(mapOf(COLLABORATORS_DOCUMENT_ID_ARG to "D1")),
        )

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `loads pending invites alongside collaborators`() = runTest(dispatcher) {
        val repo = FakeDocumentsRepository().apply {
            invitesResult = ApiResult.Success(listOf(invite("a@x.io"), invite("b@x.io")))
        }
        val vm = viewModel(repo)
        advanceUntilIdle()

        val state = vm.uiState.value.invites
        assertThat(state.isLoading).isFalse()
        assertThat(state.invites.map { it.email }).containsExactly("a@x.io", "b@x.io").inOrder()
    }

    @Test
    fun `renders role status and expiry for pending accepted and expired invites`() =
        runTest(dispatcher) {
            val repo = FakeDocumentsRepository().apply {
                invitesResult = ApiResult.Success(
                    listOf(
                        invite("pending@x.io", role = InviteRole.EDITOR, expiresAt = "2026-07-01T00:00:00Z"),
                        invite("accepted@x.io", role = InviteRole.ADMIN, accepted = true),
                        invite("expired@x.io", expiresAt = "2026-05-01T00:00:00Z"),
                    ),
                )
            }
            val vm = viewModel(repo)
            advanceUntilIdle()

            val invites = vm.uiState.value.invites.invites
            assertThat(invites[0].role).isEqualTo(InviteRole.EDITOR)
            assertThat(invites[0].statusAt(now)).isEqualTo(InviteStatus.PENDING)
            assertThat(invites[1].statusAt(now)).isEqualTo(InviteStatus.ACCEPTED)
            assertThat(invites[2].statusAt(now)).isEqualTo(InviteStatus.EXPIRED)
        }

    @Test
    fun `an invalid email is rejected without calling the repository`() = runTest(dispatcher) {
        val repo = FakeDocumentsRepository()
        val vm = viewModel(repo)
        advanceUntilIdle()

        vm.onInviteEmailChange("not-an-email")
        vm.sendInvite()
        advanceUntilIdle()

        assertThat(repo.sendInviteCount).isEqualTo(0)
        assertThat(vm.uiState.value.invites.emailError).isEqualTo("Enter a valid email address.")
        assertThat(vm.uiState.value.invites.canSend).isFalse()
    }

    @Test
    fun `sendInvite passes the address and role and adds the new invite`() = runTest(dispatcher) {
        val repo = FakeDocumentsRepository()
        val vm = viewModel(repo)
        advanceUntilIdle()

        vm.onInviteEmailChange(" Friend@Example.com ")
        vm.selectInviteRole(InviteRole.ADMIN)
        assertThat(vm.uiState.value.invites.canSend).isTrue()
        vm.sendInvite()
        advanceUntilIdle()

        assertThat(repo.lastSentInvite?.documentId).isEqualTo("D1")
        assertThat(repo.lastSentInvite?.email).isEqualTo("friend@example.com")
        assertThat(repo.lastSentInvite?.role).isEqualTo(InviteRole.ADMIN)

        val state = vm.uiState.value.invites
        assertThat(state.invites.map { it.email }).containsExactly("friend@example.com")
        // The field is cleared so the owner can invite the next person.
        assertThat(state.email).isEmpty()
        assertThat(state.isSending).isFalse()
    }

    @Test
    fun `re-inviting the same address replaces the existing row`() = runTest(dispatcher) {
        val repo = FakeDocumentsRepository().apply {
            invitesResult = ApiResult.Success(listOf(invite("friend@example.com", token = "old")))
            sendInviteResult = ApiResult.Success(
                invite("friend@example.com", token = "fresh", role = InviteRole.EDITOR),
            )
        }
        val vm = viewModel(repo)
        advanceUntilIdle()

        vm.onInviteEmailChange("friend@example.com")
        vm.sendInvite()
        advanceUntilIdle()

        val invites = vm.uiState.value.invites.invites
        assertThat(invites).hasSize(1)
        assertThat(invites.single().token).isEqualTo("fresh")
    }

    @Test
    fun `a free account cannot send and is shown the subscription gate`() = runTest(dispatcher) {
        val repo = FakeDocumentsRepository().apply {
            sendInviteResult = ApiResult.Failure(
                AppError.SubscriptionRequired("Subscribe to invite people to documents."),
            )
        }
        val vm = viewModel(repo)
        advanceUntilIdle()

        vm.onInviteEmailChange("friend@example.com")
        vm.sendInvite()
        advanceUntilIdle()

        val state = vm.uiState.value.invites
        assertThat(state.subscriptionRequired).isTrue()
        assertThat(state.errorMessage).isEqualTo("Subscribe to invite people to documents.")
        assertThat(state.invites).isEmpty()
    }

    @Test
    fun `a server rejection is surfaced verbatim rather than generically`() = runTest(dispatcher) {
        val repo = FakeDocumentsRepository().apply {
            sendInviteResult = ApiResult.Failure(AppError.Conflict("That person is already a collaborator"))
        }
        val vm = viewModel(repo)
        advanceUntilIdle()

        vm.onInviteEmailChange("friend@example.com")
        vm.sendInvite()
        advanceUntilIdle()

        val state = vm.uiState.value.invites
        assertThat(state.errorMessage).isEqualTo("That person is already a collaborator")
        assertThat(state.subscriptionRequired).isFalse()
    }

    @Test
    fun `a free account can still revoke`() = runTest(dispatcher) {
        val repo = FakeDocumentsRepository().apply {
            invitesResult = ApiResult.Success(listOf(invite("a@x.io", token = "t1"), invite("b@x.io", token = "t2")))
        }
        val vm = viewModel(repo)
        advanceUntilIdle()

        vm.revokeInvite("t2")
        // Applied immediately (optimistic).
        assertThat(vm.uiState.value.invites.invites.map { it.token }).containsExactly("t1")

        advanceUntilIdle()
        assertThat(repo.revokeInviteCount).isEqualTo(1)
        assertThat(repo.lastRevokedInviteToken).isEqualTo("t2")
        assertThat(vm.uiState.value.invites.invites.map { it.token }).containsExactly("t1")
    }

    @Test
    fun `a failed revoke restores the invite and reports why`() = runTest(dispatcher) {
        val repo = FakeDocumentsRepository().apply {
            invitesResult = ApiResult.Success(listOf(invite("a@x.io", token = "t1")))
            revokeInviteResult = ApiResult.Failure(AppError.NotFound(null))
        }
        val vm = viewModel(repo)
        advanceUntilIdle()

        vm.revokeInvite("t1")
        advanceUntilIdle()

        val state = vm.uiState.value.invites
        assertThat(state.invites.map { it.token }).containsExactly("t1")
        assertThat(state.errorMessage).isEqualTo("That invite is no longer available.")
    }

    @Test
    fun `a failed load surfaces an error without breaking the collaborator list`() =
        runTest(dispatcher) {
            val repo = FakeDocumentsRepository().apply {
                invitesResult = ApiResult.Failure(AppError.Network(null))
            }
            val vm = viewModel(repo)
            advanceUntilIdle()

            assertThat(vm.uiState.value.invites.errorMessage)
                .isEqualTo("No connection. Check your network and try again.")
            assertThat(vm.uiState.value.isLoading).isFalse()
        }
}
