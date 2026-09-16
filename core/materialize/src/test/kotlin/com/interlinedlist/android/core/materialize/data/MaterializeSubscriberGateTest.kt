package com.interlinedlist.android.core.materialize.data

import com.google.common.truth.Truth.assertThat
import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.core.common.result.AppError
import com.interlinedlist.android.core.materialize.domain.ListConfig
import com.interlinedlist.android.core.materialize.domain.MaterializeAccess
import com.interlinedlist.android.core.materialize.domain.MaterializeRequest
import com.interlinedlist.android.core.materialize.domain.MaterializeSource
import com.interlinedlist.android.core.model.CustomerStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * The subscriber gate. The "Create from…" menu opens for everyone; confirming
 * is what is gated, and a free account must reach the upsell **without** a write
 * ever leaving the device.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MaterializeSubscriberGateTest {

    private lateinit var server: MockWebServer
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        server = MockWebServer().also { it.start() }
    }

    @After
    fun tearDown() = server.shutdown()

    private val toList = MaterializeRequest.ToList(
        source = MaterializeSource.Messages(listOf("msg_1")),
        listConfig = ListConfig(title = "Launch notes"),
    )

    @Test
    fun `a free account confirming a creation gets the upsell and issues no request at all`() =
        runTest(dispatcher) {
            val gate = gateFor(server, dispatcher).also { it.record(CustomerStatus.FREE) }

            val result = repositoryFor(server, dispatcher, gate).materialize(toList)

            assertThat((result as ApiResult.Failure).error)
                .isInstanceOf(AppError.SubscriptionRequired::class.java)
            // Nothing was sent: not the write, not even a status read.
            assertThat(server.requestCount).isEqualTo(0)
        }

    @Test
    fun `a free account is blocked for every creating target`() = runTest(dispatcher) {
        val source = MaterializeSource.Messages(listOf("msg_1"))
        val creating = listOf(
            MaterializeRequest.ToList(source, ListConfig(title = "t")),
            MaterializeRequest.ToDocument(source),
            MaterializeRequest.ToListAndDocument(source, ListConfig(title = "t")),
        )

        creating.forEach { request ->
            val gate = gateFor(server, dispatcher).also { it.record(CustomerStatus.FREE) }
            val result = repositoryFor(server, dispatcher, gate).materialize(request)

            assertThat(result).isInstanceOf(ApiResult.Failure::class.java)
            assertThat((result as ApiResult.Failure).error)
                .isInstanceOf(AppError.SubscriptionRequired::class.java)
        }
        assertThat(server.requestCount).isEqualTo(0)
    }

    @Test
    fun `the gate resolves from api user and still blocks the write`() = runTest(dispatcher) {
        server.enqueue(userResponse("free"))
        val gate = gateFor(server, dispatcher)

        val result = repositoryFor(server, dispatcher, gate).materialize(toList)

        assertThat((result as ApiResult.Failure).error)
            .isInstanceOf(AppError.SubscriptionRequired::class.java)
        assertThat(gate.access.value).isEqualTo(MaterializeAccess.FREE)
        // The only request made was the status read; the write never happened.
        assertThat(server.requestCount).isEqualTo(1)
        assertThat(server.takeRequest().path).isEqualTo("/api/user")
    }

    @Test
    fun `a subscriber confirming a creation reaches the endpoint`() = runTest(dispatcher) {
        server.enqueue(userResponse("subscriber"))
        server.enqueue(jsonResponse(201, """{ "list": { "id": "lst_1", "title": "Launch notes" } }"""))
        val gate = gateFor(server, dispatcher)

        val result = repositoryFor(server, dispatcher, gate).materialize(toList)

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        assertThat(gate.access.value).isEqualTo(MaterializeAccess.SUBSCRIBER)
        assertThat(server.requestCount).isEqualTo(2)
        assertThat(server.takeRequest().path).isEqualTo("/api/user")
        assertThat(server.takeRequest().path).isEqualTo("/api/materialize")
    }

    @Test
    fun `an unreadable status does not block - the server stays the real gate`() =
        runTest(dispatcher) {
            // `/api/user` fails, so the client cannot prove the account is free.
            server.enqueue(jsonResponse(500, """{ "error": "Server error", "code": "internal_error" }"""))
            server.enqueue(jsonResponse(201, """{ "list": { "id": "lst_1", "title": "Launch notes" } }"""))
            val gate = gateFor(server, dispatcher)

            val result = repositoryFor(server, dispatcher, gate).materialize(toList)

            assertThat(result).isInstanceOf(ApiResult.Success::class.java)
            assertThat(gate.access.value).isEqualTo(MaterializeAccess.UNKNOWN)
        }

    @Test
    fun `the message target is never blocked client-side because it creates nothing`() =
        runTest(dispatcher) {
            val gate = gateFor(server, dispatcher).also { it.record(CustomerStatus.FREE) }
            server.enqueue(
                jsonResponse(
                    201,
                    """{ "message": { "content": "Books", "thread": ["Books"],
                         "isThread": false, "charLimit": 300 } }""",
                ),
            )

            val result = repositoryFor(server, dispatcher, gate).materialize(
                MaterializeRequest.ToMessageDraft(MaterializeSource.Lists(listOf("lst_1"))),
            )

            // To Message writes nothing, and the help centre documents posting
            // itself as free, so the client does not pre-empt it. If the server
            // refuses, the 403 still becomes the upsell (see the next test).
            assertThat(result).isInstanceOf(ApiResult.Success::class.java)
            assertThat(server.takeRequest().path).isEqualTo("/api/materialize")
        }

    @Test
    fun `a server subscription refusal is folded back into the gate`() = runTest(dispatcher) {
        val gate = gateFor(server, dispatcher).also { it.record(CustomerStatus.SUBSCRIBER) }
        server.enqueue(
            jsonResponse(403, """{ "error": "Subscriber feature", "code": "subscription_required" }"""),
        )
        val repository = repositoryFor(server, dispatcher, gate)

        val first = repository.materialize(toList)
        assertThat((first as ApiResult.Failure).error)
            .isInstanceOf(AppError.SubscriptionRequired::class.java)
        assertThat(gate.access.value).isEqualTo(MaterializeAccess.FREE)

        // The next confirm short-circuits instead of issuing another refused write.
        val second = repository.materialize(toList)
        assertThat((second as ApiResult.Failure).error)
            .isInstanceOf(AppError.SubscriptionRequired::class.java)
        assertThat(server.requestCount).isEqualTo(1)
    }
}
