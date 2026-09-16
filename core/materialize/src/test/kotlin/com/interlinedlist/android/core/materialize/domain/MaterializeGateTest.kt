package com.interlinedlist.android.core.materialize.domain

import com.google.common.truth.Truth.assertThat
import com.interlinedlist.android.core.materialize.data.gateFor
import com.interlinedlist.android.core.materialize.data.jsonResponse
import com.interlinedlist.android.core.materialize.data.userResponse
import com.interlinedlist.android.core.model.CustomerStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test

/** How the gate resolves `customerStatus` and what it does when it cannot. */
@OptIn(ExperimentalCoroutinesApi::class)
class MaterializeGateTest {

    private lateinit var server: MockWebServer
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        server = MockWebServer().also { it.start() }
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun `it starts unknown so the menu is never hidden`() = runTest(dispatcher) {
        assertThat(gateFor(server, dispatcher).access.value).isEqualTo(MaterializeAccess.UNKNOWN)
        assertThat(MaterializeAccess.UNKNOWN.isKnownFree).isFalse()
    }

    @Test
    fun `every subscriber tier resolves to SUBSCRIBER`() = runTest(dispatcher) {
        listOf("subscriber", "subscriber:monthly", "subscriber:annual").forEach { tier ->
            server.enqueue(userResponse(tier))
            assertThat(gateFor(server, dispatcher).refresh())
                .isEqualTo(MaterializeAccess.SUBSCRIBER)
        }
    }

    @Test
    fun `free resolves to FREE`() = runTest(dispatcher) {
        server.enqueue(userResponse("free"))
        val gate = gateFor(server, dispatcher)
        assertThat(gate.refresh()).isEqualTo(MaterializeAccess.FREE)
        assertThat(gate.access.value.isKnownFree).isTrue()
    }

    @Test
    fun `an unrecognised tier is not treated as evidence of a free account`() = runTest(dispatcher) {
        server.enqueue(userResponse("enterprise-something"))
        assertThat(gateFor(server, dispatcher).refresh()).isEqualTo(MaterializeAccess.UNKNOWN)
    }

    @Test
    fun `an unreadable status stays unknown rather than locking a subscriber out`() =
        runTest(dispatcher) {
            server.enqueue(jsonResponse(401, """{ "error": "Unauthorized" }"""))
            assertThat(gateFor(server, dispatcher).refresh()).isEqualTo(MaterializeAccess.UNKNOWN)
        }

    @Test
    fun `ensureResolved reads once and then stops calling api user`() = runTest(dispatcher) {
        server.enqueue(userResponse("subscriber"))
        val gate = gateFor(server, dispatcher)

        assertThat(gate.ensureResolved()).isEqualTo(MaterializeAccess.SUBSCRIBER)
        assertThat(gate.ensureResolved()).isEqualTo(MaterializeAccess.SUBSCRIBER)

        assertThat(server.requestCount).isEqualTo(1)
    }

    @Test
    fun `ensureResolved retries while the status is still unknown`() = runTest(dispatcher) {
        server.enqueue(jsonResponse(500, """{ "error": "Server error" }"""))
        server.enqueue(userResponse("subscriber"))
        val gate = gateFor(server, dispatcher)

        assertThat(gate.ensureResolved()).isEqualTo(MaterializeAccess.UNKNOWN)
        assertThat(gate.ensureResolved()).isEqualTo(MaterializeAccess.SUBSCRIBER)
    }

    @Test
    fun `a recorded status spares the network read`() = runTest(dispatcher) {
        val gate = gateFor(server, dispatcher)
        gate.record(CustomerStatus.SUBSCRIBER_ANNUAL)

        assertThat(gate.ensureResolved()).isEqualTo(MaterializeAccess.SUBSCRIBER)
        assertThat(server.requestCount).isEqualTo(0)
    }

    @Test
    fun `a refusal from the server is remembered`() = runTest(dispatcher) {
        val gate = gateFor(server, dispatcher)
        gate.record(CustomerStatus.SUBSCRIBER)

        gate.recordSubscriptionRequired()

        assertThat(gate.access.value).isEqualTo(MaterializeAccess.FREE)
    }
}
