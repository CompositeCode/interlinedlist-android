package com.interlinedlist.android.blog.ui

import com.google.common.truth.Truth.assertThat
import com.interlinedlist.android.blog.BlogSubscriptionAction
import com.interlinedlist.android.blog.BlogSubscriptionOutcome
import com.interlinedlist.android.blog.data.BlogSubscriptionRepository
import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.core.common.result.AppError
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
class BlogSubscriptionViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `the form is prefilled with the signed-in account's address`() = runTest(dispatcher) {
        val repo = FakeBlogSubscriptionRepository(accountEmailResult = "me@example.com")

        val vm = viewModel(repo)
        advanceUntilIdle()

        assertThat(vm.uiState.value.email).isEqualTo("me@example.com")
        assertThat(vm.uiState.value.status).isEqualTo(BlogSubscriptionStatus.EDITING)
    }

    @Test
    fun `a missing account address leaves the form usable`() = runTest(dispatcher) {
        val vm = viewModel(FakeBlogSubscriptionRepository(accountEmailResult = null))
        advanceUntilIdle()

        assertThat(vm.uiState.value.email).isEmpty()
        assertThat(vm.uiState.value.canSubmit).isFalse()
    }

    @Test
    fun `a successful subscribe says to check the email, NOT that you are subscribed`() =
        runTest(dispatcher) {
            val repo = FakeBlogSubscriptionRepository(
                accountEmailResult = "me@example.com",
                subscribeResult = ApiResult.Success(
                    "Check your email to confirm your subscription.",
                ),
            )
            val vm = viewModel(repo)
            advanceUntilIdle()

            vm.subscribe()
            advanceUntilIdle()

            val state = vm.uiState.value
            assertThat(repo.subscribedEmail).isEqualTo("me@example.com")
            // The double opt-in is the whole point: the address is NOT on the list yet.
            assertThat(state.status).isEqualTo(BlogSubscriptionStatus.CHECK_YOUR_EMAIL)
            assertThat(state.submittedEmail).isEqualTo("me@example.com")
            assertThat(headingFor(state)).isEqualTo("Check your email")
            assertThat(headingFor(state)).isNotEqualTo(headingFor(confirmedState()))
            assertThat(bodyFor(state)).contains("me@example.com")
            assertThat(bodyFor(state)).contains("not subscribed yet")
            assertThat(bodyFor(state)).contains("Nothing will arrive until you do")
        }

    @Test
    fun `the form explains the double opt-in before anything is sent`() = runTest(dispatcher) {
        val vm = viewModel(FakeBlogSubscriptionRepository())
        advanceUntilIdle()

        assertThat(bodyFor(vm.uiState.value)).contains("confirmation link")
        assertThat(bodyFor(vm.uiState.value)).contains("until you open it")
    }

    @Test
    fun `the form points at the emailed link for unsubscribing`() {
        // There is no address-only unsubscribe endpoint: POST /api/blog/unsubscribe is
        // the RFC-8058 one-click target and is keyed on the token in the email footer,
        // answering a bare 200 to anything else. So the form must not offer a button it
        // cannot honour — it points at the link, which this app handles as a deep link.
        assertThat(UNSUBSCRIBE_HINT).contains("unsubscribe link at the bottom of any blog email")
        assertThat(UNSUBSCRIBE_HINT).contains("without leaving it")
    }

    @Test
    fun `the address is trimmed before it is sent`() = runTest(dispatcher) {
        val repo = FakeBlogSubscriptionRepository(subscribeResult = ApiResult.Success(""))
        val vm = viewModel(repo)
        advanceUntilIdle()

        vm.onEmailChange("  reader@example.com  ")
        vm.subscribe()
        advanceUntilIdle()

        assertThat(repo.subscribedEmail).isEqualTo("reader@example.com")
    }

    @Test
    fun `a rejected address surfaces the server's own message`() = runTest(dispatcher) {
        val repo = FakeBlogSubscriptionRepository(
            subscribeResult = ApiResult.Failure(AppError.Unknown("A valid email is required")),
        )
        val vm = viewModel(repo)
        advanceUntilIdle()

        vm.onEmailChange("not-an-email")
        vm.subscribe()
        advanceUntilIdle()

        assertThat(vm.uiState.value.status).isEqualTo(BlogSubscriptionStatus.FAILED)
        assertThat(vm.uiState.value.message).isEqualTo("A valid email is required")
    }

    @Test
    fun `a server error surfaces rather than looking like a success`() = runTest(dispatcher) {
        val repo = FakeBlogSubscriptionRepository(
            subscribeResult = ApiResult.Failure(AppError.Server(null)),
        )
        val vm = viewModel(repo)
        advanceUntilIdle()

        vm.onEmailChange("reader@example.com")
        vm.subscribe()
        advanceUntilIdle()

        assertThat(vm.uiState.value.status).isEqualTo(BlogSubscriptionStatus.FAILED)
        assertThat(vm.uiState.value.message)
            .isEqualTo("InterlinedList is having trouble right now. Try again shortly.")
    }

    @Test
    fun `editing the address clears a previous failure`() = runTest(dispatcher) {
        val repo = FakeBlogSubscriptionRepository(
            subscribeResult = ApiResult.Failure(AppError.Unknown("A valid email is required")),
        )
        val vm = viewModel(repo)
        advanceUntilIdle()

        vm.onEmailChange("nope")
        vm.subscribe()
        advanceUntilIdle()
        vm.onEmailChange("reader@example.com")

        assertThat(vm.uiState.value.status).isEqualTo(BlogSubscriptionStatus.EDITING)
        assertThat(vm.uiState.value.message).isNull()
    }

    @Test
    fun `a confirmation link completes the double opt-in with its token`() = runTest(dispatcher) {
        val repo = FakeBlogSubscriptionRepository(
            confirmResult = ApiResult.Success(BlogSubscriptionOutcome.CONFIRMED),
        )

        val vm = viewModel(repo, BlogSubscriptionAction.CONFIRM, token = "confirm-tok")
        advanceUntilIdle()

        assertThat(repo.confirmedToken).isEqualTo("confirm-tok")
        assertThat(repo.unsubscribedToken).isNull()
        assertThat(vm.uiState.value.status).isEqualTo(BlogSubscriptionStatus.CONFIRMED)
        assertThat(headingFor(vm.uiState.value)).isEqualTo("You're subscribed")
    }

    @Test
    fun `an unsubscribe link removes the address with its token`() = runTest(dispatcher) {
        val repo = FakeBlogSubscriptionRepository(
            unsubscribeResult = ApiResult.Success(BlogSubscriptionOutcome.UNSUBSCRIBED),
        )

        val vm = viewModel(repo, BlogSubscriptionAction.UNSUBSCRIBE, token = "bye-tok")
        advanceUntilIdle()

        assertThat(repo.unsubscribedToken).isEqualTo("bye-tok")
        assertThat(repo.confirmedToken).isNull()
        assertThat(vm.uiState.value.status).isEqualTo(BlogSubscriptionStatus.UNSUBSCRIBED)
        assertThat(headingFor(vm.uiState.value)).isEqualTo("You've been unsubscribed")
    }

    @Test
    fun `a link with no token is reported, not sent`() = runTest(dispatcher) {
        val repo = FakeBlogSubscriptionRepository()

        val vm = viewModel(repo, BlogSubscriptionAction.UNSUBSCRIBE, token = "  ")
        advanceUntilIdle()

        assertThat(repo.unsubscribedToken).isNull()
        assertThat(vm.uiState.value.status).isEqualTo(BlogSubscriptionStatus.INVALID_LINK)
        assertThat(headingFor(vm.uiState.value)).isEqualTo("This unsubscribe link didn't work")
    }

    @Test
    fun `a token the server rejects is never reported as done`() = runTest(dispatcher) {
        val repo = FakeBlogSubscriptionRepository(
            confirmResult = ApiResult.Success(BlogSubscriptionOutcome.INVALID),
        )

        val vm = viewModel(repo, BlogSubscriptionAction.CONFIRM, token = "expired")
        advanceUntilIdle()

        assertThat(vm.uiState.value.status).isEqualTo(BlogSubscriptionStatus.INVALID_LINK)
    }

    @Test
    fun `a failed unsubscribe says so`() = runTest(dispatcher) {
        val repo = FakeBlogSubscriptionRepository(
            unsubscribeResult = ApiResult.Failure(AppError.Network(null)),
        )

        val vm = viewModel(repo, BlogSubscriptionAction.UNSUBSCRIBE, token = "tok")
        advanceUntilIdle()

        assertThat(vm.uiState.value.status).isEqualTo(BlogSubscriptionStatus.FAILED)
        assertThat(headingFor(vm.uiState.value)).isEqualTo("Couldn't unsubscribe you")
        assertThat(vm.uiState.value.message)
            .isEqualTo("No connection. Check your network and try again.")
    }

    @Test
    fun `a link opened screen never shows the subscribe form`() = runTest(dispatcher) {
        val vm = viewModel(
            FakeBlogSubscriptionRepository(accountEmailResult = "me@example.com"),
            BlogSubscriptionAction.CONFIRM,
            token = "tok",
        )
        advanceUntilIdle()

        assertThat(vm.uiState.value.isSubscribeForm).isFalse()
        // The account was never read: the link's token is the only thing that matters.
        assertThat(vm.uiState.value.email).isEmpty()
    }

    private fun viewModel(
        repository: BlogSubscriptionRepository,
        linkAction: BlogSubscriptionAction? = null,
        token: String? = null,
    ) = BlogSubscriptionViewModel(repository, linkAction, token)

    private fun confirmedState() =
        BlogSubscriptionUiState(status = BlogSubscriptionStatus.CONFIRMED)
}

private class FakeBlogSubscriptionRepository(
    private val accountEmailResult: String? = null,
    private val subscribeResult: ApiResult<String> = ApiResult.Success(""),
    private val confirmResult: ApiResult<BlogSubscriptionOutcome> =
        ApiResult.Success(BlogSubscriptionOutcome.CONFIRMED),
    private val unsubscribeResult: ApiResult<BlogSubscriptionOutcome> =
        ApiResult.Success(BlogSubscriptionOutcome.UNSUBSCRIBED),
) : BlogSubscriptionRepository {

    var subscribedEmail: String? = null
        private set
    var confirmedToken: String? = null
        private set
    var unsubscribedToken: String? = null
        private set

    override suspend fun accountEmail(): String? = accountEmailResult

    override suspend fun subscribe(email: String): ApiResult<String> {
        subscribedEmail = email
        return subscribeResult
    }

    override suspend fun confirm(token: String): ApiResult<BlogSubscriptionOutcome> {
        confirmedToken = token
        return confirmResult
    }

    override suspend fun unsubscribe(token: String): ApiResult<BlogSubscriptionOutcome> {
        unsubscribedToken = token
        return unsubscribeResult
    }
}
