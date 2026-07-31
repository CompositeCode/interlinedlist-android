package com.interlinedlist.android.feature.notifications.ui

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.core.common.result.AppError
import com.interlinedlist.android.feature.notifications.domain.NotificationChannel
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
class NotificationPreferencesViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `load emits the fetched preferences and clears loading`() = runTest(dispatcher) {
        val repo = FakeNotificationPreferencesRepository().apply {
            getResult = ApiResult.Success(
                listOf(
                    samplePreference(key = "dig"),
                    samplePreference(key = "follow"),
                ),
            )
        }
        val vm = NotificationPreferencesViewModel(repo)

        vm.uiState.test {
            advanceUntilIdle()
            val state = expectMostRecentItem()
            assertThat(repo.getCount).isEqualTo(1)
            assertThat(state.isLoading).isFalse()
            assertThat(state.preferences.map { it.key }).containsExactly("dig", "follow").inOrder()
        }
    }

    @Test
    fun `load failure surfaces a mapped error`() = runTest(dispatcher) {
        val repo = FakeNotificationPreferencesRepository().apply {
            getResult = ApiResult.Failure(AppError.Network("offline"))
        }
        val vm = NotificationPreferencesViewModel(repo)

        vm.uiState.test {
            advanceUntilIdle()
            val state = expectMostRecentItem()
            assertThat(state.isLoading).isFalse()
            assertThat(state.errorMessage).isEqualTo("No connection. Check your network and try again.")
        }
    }

    @Test
    fun `toggling a channel updates state optimistically then persists`() = runTest(dispatcher) {
        val repo = FakeNotificationPreferencesRepository().apply {
            getResult = ApiResult.Success(
                listOf(
                    samplePreference(
                        key = "dig",
                        channels = mapOf(
                            NotificationChannel.PUSH to true,
                            NotificationChannel.IN_APP to false,
                        ),
                    ),
                ),
            )
        }
        val vm = NotificationPreferencesViewModel(repo)
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        vm.onToggle("dig", NotificationChannel.IN_APP, enabled = true)
        // Immediately (before the PATCH completes) the state reflects the new value.
        val optimistic = vm.uiState.value.preferences.single { it.key == "dig" }
        assertThat(optimistic.isEnabled(NotificationChannel.IN_APP)).isTrue()

        advanceUntilIdle()
        // Persisted: the repo received the toggled event with the flipped channel.
        assertThat(repo.updated).hasSize(1)
        val sent = repo.updated.single()
        assertThat(sent.key).isEqualTo("dig")
        assertThat(sent.isEnabled(NotificationChannel.IN_APP)).isTrue()
        assertThat(sent.isEnabled(NotificationChannel.PUSH)).isTrue()
        // State remains flipped after a successful PATCH.
        assertThat(
            vm.uiState.value.preferences.single { it.key == "dig" }
                .isEnabled(NotificationChannel.IN_APP),
        ).isTrue()
        assertThat(vm.uiState.value.errorMessage).isNull()
    }

    @Test
    fun `a failed toggle rolls back to the previous value and surfaces an error`() = runTest(dispatcher) {
        val repo = FakeNotificationPreferencesRepository().apply {
            getResult = ApiResult.Success(
                listOf(
                    samplePreference(
                        key = "dig",
                        channels = mapOf(
                            NotificationChannel.PUSH to true,
                            NotificationChannel.IN_APP to false,
                        ),
                    ),
                ),
            )
            updateResult = ApiResult.Failure(AppError.Server("boom"))
        }
        val vm = NotificationPreferencesViewModel(repo)
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        vm.onToggle("dig", NotificationChannel.IN_APP, enabled = true)
        // Optimistically true...
        assertThat(
            vm.uiState.value.preferences.single { it.key == "dig" }
                .isEnabled(NotificationChannel.IN_APP),
        ).isTrue()

        advanceUntilIdle()
        // ...then rolled back to the original false on failure.
        val rolledBack = vm.uiState.value.preferences.single { it.key == "dig" }
        assertThat(rolledBack.isEnabled(NotificationChannel.IN_APP)).isFalse()
        assertThat(vm.uiState.value.errorMessage).isNotEmpty()
    }

    @Test
    fun `toggling an unsupported channel is a no-op`() = runTest(dispatcher) {
        val repo = FakeNotificationPreferencesRepository().apply {
            getResult = ApiResult.Success(
                listOf(
                    samplePreference(
                        key = "reply",
                        channels = mapOf(NotificationChannel.EMAIL to true),
                    ),
                ),
            )
        }
        val vm = NotificationPreferencesViewModel(repo)
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        // PUSH is not a channel this event supports.
        vm.onToggle("reply", NotificationChannel.PUSH, enabled = true)
        advanceUntilIdle()

        assertThat(repo.updated).isEmpty()
        val reply = vm.uiState.value.preferences.single { it.key == "reply" }
        assertThat(reply.availableChannels).containsExactly(NotificationChannel.EMAIL)
    }

    @Test
    fun `dismissError clears the error`() = runTest(dispatcher) {
        val repo = FakeNotificationPreferencesRepository().apply {
            getResult = ApiResult.Failure(AppError.Network("offline"))
        }
        val vm = NotificationPreferencesViewModel(repo)
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()
        assertThat(vm.uiState.value.errorMessage).isNotNull()

        vm.dismissError()
        advanceUntilIdle()

        assertThat(vm.uiState.value.errorMessage).isNull()
    }

    @Test
    fun `retry re-fetches after a failed load`() = runTest(dispatcher) {
        val repo = FakeNotificationPreferencesRepository().apply {
            getResult = ApiResult.Failure(AppError.Network("offline"))
        }
        val vm = NotificationPreferencesViewModel(repo)
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()
        assertThat(repo.getCount).isEqualTo(1)

        repo.getResult = ApiResult.Success(listOf(samplePreference(key = "dig")))
        vm.refresh()
        advanceUntilIdle()

        assertThat(repo.getCount).isEqualTo(2)
        assertThat(vm.uiState.value.preferences.map { it.key }).containsExactly("dig")
        assertThat(vm.uiState.value.errorMessage).isNull()
    }
}
