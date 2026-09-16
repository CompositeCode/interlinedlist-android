package com.interlinedlist.android.feature.ai.domain

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** The gate every AI surface collects: it must stay off unless AI is truly usable. */
@OptIn(ExperimentalCoroutinesApi::class)
class AiGateTest {

    private val repository = FakeAiRepository()
    private val gate = AiGate(repository)

    @Test
    fun `AI is hidden until status has been read`() {
        assertThat(gate.availability.value).isEqualTo(AiAvailability.Unknown)
        assertThat(gate.availability.value.isEnabled).isFalse()
    }

    @Test
    fun `refresh turns the surfaces on for a subscriber`() = runTest {
        repository.availability = AiAvailability.Available(AiQuota(8, 50, 42))

        gate.availability.test {
            assertThat(awaitItem()).isEqualTo(AiAvailability.Unknown)
            gate.refresh()
            assertThat(awaitItem().isEnabled).isTrue()
        }
    }

    @Test
    fun `refresh flips the surfaces off for a free account`() = runTest {
        repository.availability = AiAvailability.NotSubscribed

        gate.refresh()

        assertThat(gate.availability.value).isEqualTo(AiAvailability.NotSubscribed)
        assertThat(gate.availability.value.isEnabled).isFalse()
    }

    @Test
    fun `ensureResolved reads status once`() = runTest {
        gate.ensureResolved()
        gate.ensureResolved()

        assertThat(repository.availabilityCalls).isEqualTo(1)
    }

    @Test
    fun `a quota echoed by an action refreshes the gate`() = runTest {
        gate.refresh()

        gate.recordQuota(AiQuota(49, 50, 1))

        assertThat(gate.availability.value).isEqualTo(AiAvailability.Available(AiQuota(49, 50, 1)))
    }

    @Test
    fun `an unconfigured provider discovered mid-action takes the surfaces down`() = runTest {
        gate.refresh()

        gate.recordFailure(AiError.ProviderUnconfigured())

        assertThat(gate.availability.value).isEqualTo(AiAvailability.Unavailable)
    }

    @Test
    fun `a subscription that lapsed mid-session takes the surfaces down`() = runTest {
        gate.refresh()

        gate.recordFailure(AiError.NotSubscribed())

        assertThat(gate.availability.value).isEqualTo(AiAvailability.NotSubscribed)
    }

    @Test
    fun `a quota_exceeded failure marks the allowance spent without hiding AI`() = runTest {
        repository.availability = AiAvailability.Available(AiQuota(50, 50, 3))
        gate.refresh()

        gate.recordFailure(AiError.QuotaExceeded())

        assertThat(gate.availability.value.isEnabled).isTrue()
        assertThat(gate.availability.value.isQuotaExhausted).isTrue()
    }

    @Test
    fun `a transient failure leaves the gate alone`() = runTest {
        gate.refresh()
        val before = gate.availability.value

        gate.recordFailure(AiError.Network("offline"))

        assertThat(gate.availability.value).isEqualTo(before)
    }
}
