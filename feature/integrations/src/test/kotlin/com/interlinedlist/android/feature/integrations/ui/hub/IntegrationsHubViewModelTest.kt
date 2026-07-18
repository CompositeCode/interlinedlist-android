package com.interlinedlist.android.feature.integrations.ui.hub

import com.google.common.truth.Truth.assertThat
import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.core.common.result.AppError
import com.interlinedlist.android.feature.integrations.domain.PlanLimits
import com.interlinedlist.android.feature.integrations.ui.FakeIntegrationsRepository
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
class IntegrationsHubViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var repo: FakeIntegrationsRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repo = FakeIntegrationsRepository()
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `loads limits on init`() = runTest(dispatcher) {
        repo.limitsResult = ApiResult.Success(
            PlanLimits("Free", listOf(PlanLimits.Limit("lists", "Lists", used = 1, max = 5))),
        )

        val vm = IntegrationsHubViewModel(repo)
        advanceUntilIdle()

        assertThat(vm.uiState.value.isLoadingLimits).isFalse()
        assertThat(vm.uiState.value.limits?.planName).isEqualTo("Free")
        assertThat(vm.uiState.value.limits?.limits).hasSize(1)
    }

    @Test
    fun `a limits failure is non-fatal and hides the section`() = runTest(dispatcher) {
        repo.limitsResult = ApiResult.Failure(AppError.Server("boom"))

        val vm = IntegrationsHubViewModel(repo)
        advanceUntilIdle()

        assertThat(vm.uiState.value.isLoadingLimits).isFalse()
        assertThat(vm.uiState.value.limits).isNull()
    }
}
