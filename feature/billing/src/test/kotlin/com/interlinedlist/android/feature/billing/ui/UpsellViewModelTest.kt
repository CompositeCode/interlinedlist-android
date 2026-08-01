package com.interlinedlist.android.feature.billing.ui

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
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
class UpsellViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var repo: FakeBillingRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repo = FakeBillingRepository()
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `subscribe emits the checkout URL as an open effect`() = runTest(dispatcher) {
        repo.checkoutResult = ApiResult.Success("https://checkout.stripe.example/abc")
        val vm = UpsellViewModel(repo)

        vm.open.test {
            vm.subscribe()
            advanceUntilIdle()

            assertThat(awaitItem().url).isEqualTo("https://checkout.stripe.example/abc")
            cancelAndIgnoreRemainingEvents()
        }
        assertThat(repo.requestedPriceIds).containsExactly(null as String?)
        assertThat(vm.uiState.value.isLoading).isFalse()
        assertThat(vm.uiState.value.errorMessage).isNull()
    }

    @Test
    fun `manage subscription emits the portal URL as an open effect`() = runTest(dispatcher) {
        repo.portalResult = ApiResult.Success("https://portal.stripe.example/xyz")
        val vm = UpsellViewModel(repo)

        vm.open.test {
            vm.manageSubscription()
            advanceUntilIdle()

            assertThat(awaitItem().url).isEqualTo("https://portal.stripe.example/xyz")
            cancelAndIgnoreRemainingEvents()
        }
        assertThat(repo.portalCalls).isEqualTo(1)
    }

    @Test
    fun `subscribe shows a loading spinner while the session is in flight`() = runTest(dispatcher) {
        repo.checkoutResult = ApiResult.Success("https://checkout.example/s")
        val vm = UpsellViewModel(repo)

        vm.uiState.test {
            assertThat(awaitItem().isLoading).isFalse() // initial

            vm.subscribe()
            assertThat(awaitItem().isLoading).isTrue() // in-flight

            advanceUntilIdle()
            assertThat(awaitItem().isLoading).isFalse() // done
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a second request is ignored while one is in flight`() = runTest(dispatcher) {
        repo.checkoutResult = ApiResult.Success("https://checkout.example/s")
        val vm = UpsellViewModel(repo)

        vm.subscribe()
        vm.manageSubscription() // dropped: busy
        advanceUntilIdle()

        assertThat(repo.requestedPriceIds).hasSize(1)
        assertThat(repo.portalCalls).isEqualTo(0)
    }

    @Test
    fun `failed checkout surfaces a mapped error and emits no open effect`() = runTest(dispatcher) {
        repo.checkoutResult = ApiResult.Failure(AppError.Network(null))
        val vm = UpsellViewModel(repo)

        vm.subscribe()
        advanceUntilIdle()

        assertThat(vm.uiState.value.isLoading).isFalse()
        assertThat(vm.uiState.value.errorMessage)
            .isEqualTo("No connection. Check your network and try again.")
    }

    @Test
    fun `failed portal session surfaces a mapped error`() = runTest(dispatcher) {
        repo.portalResult = ApiResult.Failure(AppError.Server(null))
        val vm = UpsellViewModel(repo)

        vm.manageSubscription()
        advanceUntilIdle()

        assertThat(vm.uiState.value.errorMessage)
            .isEqualTo("We couldn't start your billing session. Please try again shortly.")
    }
}
