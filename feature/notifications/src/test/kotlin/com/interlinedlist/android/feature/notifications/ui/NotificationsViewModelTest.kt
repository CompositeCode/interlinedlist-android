package com.interlinedlist.android.feature.notifications.ui

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.core.common.result.AppError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NotificationsViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `list emits cached notifications and the unread count`() = runTest(dispatcher) {
        val repo = FakeNotificationsRepository()
        repo.emit(
            listOf(
                sampleNotification(id = "1", read = false),
                sampleNotification(id = "2", read = true),
            ),
        )
        val vm = NotificationsViewModel(repo)

        vm.uiState.test {
            advanceUntilIdle()
            val state = expectMostRecentItem()
            assertThat(state.notifications.map { it.id }).containsExactly("1", "2").inOrder()
            assertThat(state.unreadCount).isEqualTo(1)
            assertThat(state.hasUnread).isTrue()
            assertThat(state.isRefreshing).isFalse()
        }
    }

    @Test
    fun `refresh runs on init and toggles the refreshing flag`() = runTest(dispatcher) {
        val repo = FakeNotificationsRepository().apply { refreshResult = ApiResult.Success(true) }
        val vm = NotificationsViewModel(repo)

        vm.uiState.test {
            advanceUntilIdle()
            val state = expectMostRecentItem()
            assertThat(repo.refreshCount).isEqualTo(1)
            assertThat(state.isRefreshing).isFalse()
            assertThat(state.canLoadMore).isTrue()
        }
    }

    @Test
    fun `refresh failure surfaces a mapped error`() = runTest(dispatcher) {
        val repo = FakeNotificationsRepository().apply {
            refreshResult = ApiResult.Failure(AppError.Network("offline"))
        }
        val vm = NotificationsViewModel(repo)

        vm.uiState.test {
            advanceUntilIdle()
            val state = expectMostRecentItem()
            assertThat(state.errorMessage).isEqualTo("No connection. Check your network and try again.")
            assertThat(state.subscriptionRequired).isFalse()
        }
    }

    @Test
    fun `subscription-gated refresh sets the locked flag`() = runTest(dispatcher) {
        val repo = FakeNotificationsRepository().apply {
            refreshResult = ApiResult.Failure(AppError.SubscriptionRequired("Subscribers only"))
        }
        val vm = NotificationsViewModel(repo)

        vm.uiState.test {
            advanceUntilIdle()
            val state = expectMostRecentItem()
            assertThat(state.subscriptionRequired).isTrue()
            assertThat(state.errorMessage).isEqualTo("Subscribers only")
        }
    }

    @Test
    fun `loadMore is a no-op when there are no more pages`() = runTest(dispatcher) {
        val repo = FakeNotificationsRepository().apply { refreshResult = ApiResult.Success(false) }
        val vm = NotificationsViewModel(repo)
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        vm.loadMore()
        advanceUntilIdle()

        assertThat(repo.loadMoreCount).isEqualTo(0)
    }

    @Test
    fun `loadMore fetches the next page when more are available`() = runTest(dispatcher) {
        val repo = FakeNotificationsRepository().apply {
            refreshResult = ApiResult.Success(true)
            loadMoreResult = ApiResult.Success(false)
        }
        val vm = NotificationsViewModel(repo)
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        vm.loadMore()
        advanceUntilIdle()

        assertThat(repo.loadMoreCount).isEqualTo(1)
        assertThat(vm.uiState.value.canLoadMore).isFalse()
    }

    @Test
    fun `opening an unread notification marks it read`() = runTest(dispatcher) {
        val repo = FakeNotificationsRepository()
        repo.emit(listOf(sampleNotification(id = "42", read = false)))
        val vm = NotificationsViewModel(repo)
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        vm.onOpen(sampleNotification(id = "42", read = false))
        advanceUntilIdle()

        assertThat(repo.markReadIds).containsExactly("42")
        assertThat(vm.uiState.value.unreadCount).isEqualTo(0)
    }

    @Test
    fun `opening an already-read notification does not call the repository`() = runTest(dispatcher) {
        val repo = FakeNotificationsRepository()
        val vm = NotificationsViewModel(repo)
        advanceUntilIdle()

        vm.onOpen(sampleNotification(id = "9", read = true))
        advanceUntilIdle()

        assertThat(repo.markReadIds).isEmpty()
    }

    @Test
    fun `mark all read delegates to the repository`() = runTest(dispatcher) {
        val repo = FakeNotificationsRepository()
        repo.emit(
            listOf(
                sampleNotification(id = "1", read = false),
                sampleNotification(id = "2", read = false),
            ),
        )
        val vm = NotificationsViewModel(repo)
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        vm.onMarkAllRead()
        advanceUntilIdle()

        assertThat(repo.markAllReadCount).isEqualTo(1)
        assertThat(vm.uiState.value.unreadCount).isEqualTo(0)
        assertThat(vm.uiState.value.hasUnread).isFalse()
    }

    @Test
    fun `dismiss delegates to the repository and removes the row`() = runTest(dispatcher) {
        val repo = FakeNotificationsRepository()
        repo.emit(listOf(sampleNotification(id = "1"), sampleNotification(id = "2")))
        val vm = NotificationsViewModel(repo)
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        vm.onDismiss(sampleNotification(id = "1"))
        advanceUntilIdle()

        assertThat(repo.dismissedIds).containsExactly("1")
        assertThat(vm.uiState.value.notifications.map { it.id }).containsExactly("2")
    }

    @Test
    fun `a failed dismiss surfaces an error`() = runTest(dispatcher) {
        val repo = FakeNotificationsRepository().apply {
            dismissResult = ApiResult.Failure(AppError.Server("boom"))
        }
        repo.emit(listOf(sampleNotification(id = "1")))
        val vm = NotificationsViewModel(repo)
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        vm.onDismiss(sampleNotification(id = "1"))
        advanceUntilIdle()

        assertThat(vm.uiState.value.errorMessage).isNotEmpty()
    }

    @Test
    fun `dismissError clears the error and locked flags`() = runTest(dispatcher) {
        val repo = FakeNotificationsRepository().apply {
            refreshResult = ApiResult.Failure(AppError.SubscriptionRequired("locked"))
        }
        val vm = NotificationsViewModel(repo)
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()
        assertThat(vm.uiState.value.subscriptionRequired).isTrue()

        vm.dismissError()
        advanceUntilIdle()

        assertThat(vm.uiState.value.errorMessage).isNull()
        assertThat(vm.uiState.value.subscriptionRequired).isFalse()
    }
}
