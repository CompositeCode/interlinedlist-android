package com.interlinedlist.android.core.materialize.data

import com.google.common.truth.Truth.assertThat
import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.core.common.result.AppError
import com.interlinedlist.android.core.materialize.domain.ListConfig
import com.interlinedlist.android.core.materialize.domain.MaterializeRequest
import com.interlinedlist.android.core.materialize.domain.MaterializeSource
import com.interlinedlist.android.core.model.CustomerStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * `{ "error": …, "code": … }` onto the shared `AppError`, so the feature modules
 * that open this flow keep using their existing `toUserMessage()` and
 * `isSubscriptionGate` helpers.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MaterializeErrorMappingTest {

    private lateinit var server: MockWebServer
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        server = MockWebServer().also { it.start() }
    }

    @After
    fun tearDown() = server.shutdown()

    private val request = MaterializeRequest.ToList(
        source = MaterializeSource.Messages(listOf("msg_1")),
        listConfig = ListConfig(title = "Launch notes"),
    )

    private suspend fun errorFor(response: MockResponse): AppError {
        server.enqueue(response)
        val gate = gateFor(server, dispatcher).also { it.record(CustomerStatus.SUBSCRIBER) }
        val result = repositoryFor(server, dispatcher, gate).materialize(request)
        return (result as ApiResult.Failure).error
    }

    @Test
    fun `401 unauthorized maps to Unauthorized`() = runTest(dispatcher) {
        val error = errorFor(
            jsonResponse(401, """{ "error": "Unauthorized", "code": "unauthorized" }"""),
        )
        assertThat(error).isInstanceOf(AppError.Unauthorized::class.java)
        assertThat(error.message).isEqualTo("Unauthorized")
    }

    @Test
    fun `403 subscription_required maps to SubscriptionRequired`() = runTest(dispatcher) {
        val error = errorFor(
            jsonResponse(
                403,
                """{ "error": "This feature is for subscribers", "code": "subscription_required" }""",
            ),
        )
        assertThat(error).isInstanceOf(AppError.SubscriptionRequired::class.java)
    }

    @Test
    fun `a bare 403 is still the subscriber gate - this endpoint documents no other`() =
        runTest(dispatcher) {
            // `code` is optional on the wire and some routes omit it. The
            // shared safeApiCall would need the word "subscription" in the
            // message to reach the upsell; here the status is enough.
            val error = errorFor(jsonResponse(403, """{ "error": "Forbidden" }"""))
            assertThat(error).isInstanceOf(AppError.SubscriptionRequired::class.java)
        }

    @Test
    fun `an account_ code is Forbidden, not an upsell`() = runTest(dispatcher) {
        val error = errorFor(
            jsonResponse(
                403,
                """{ "error": "Your account is suspended", "code": "account_suspended" }""",
            ),
        )
        // Subscribing would not lift this, so it must not be sold as an upgrade.
        assertThat(error).isInstanceOf(AppError.Forbidden::class.java)
    }

    @Test
    fun `404 maps to NotFound - a referenced id is missing or not owned`() = runTest(dispatcher) {
        val error = errorFor(
            jsonResponse(404, """{ "error": "List not found", "code": "not_found" }"""),
        )
        assertThat(error).isInstanceOf(AppError.NotFound::class.java)
        assertThat(error.message).isEqualTo("List not found")
    }

    @Test
    fun `400 bad_request keeps the server's own explanation`() = runTest(dispatcher) {
        val error = errorFor(
            jsonResponse(400, """{ "error": "A list title is required", "code": "bad_request" }"""),
        )
        // The shared AppError has no validation case; the message is what the
        // user needs, and every feature's toUserMessage() renders it verbatim.
        assertThat(error).isInstanceOf(AppError.Unknown::class.java)
        assertThat(error.message).isEqualTo("A list title is required")
    }

    @Test
    fun `validation_failed keeps its message too`() = runTest(dispatcher) {
        val error = errorFor(
            jsonResponse(
                400,
                """{ "error": "Field 'year' has invalid type", "code": "validation_failed" }""",
            ),
        )
        assertThat(error.message).isEqualTo("Field 'year' has invalid type")
    }

    @Test
    fun `500 maps to Server`() = runTest(dispatcher) {
        val error = errorFor(
            jsonResponse(500, """{ "error": "Internal error", "code": "internal_error" }"""),
        )
        assertThat(error).isInstanceOf(AppError.Server::class.java)
    }

    @Test
    fun `a dropped connection maps to Network`() = runTest(dispatcher) {
        val error = errorFor(
            MockResponse().apply { socketPolicy = SocketPolicy.DISCONNECT_AT_START },
        )
        assertThat(error).isInstanceOf(AppError.Network::class.java)
    }

    @Test
    fun `a 201 that created nothing is a failure, not an empty success`() = runTest(dispatcher) {
        val error = errorFor(jsonResponse(201, "{}"))
        assertThat(error).isInstanceOf(AppError.Server::class.java)
    }

    @Test
    fun `a both target that came back with only a list is a failure`() = runTest(dispatcher) {
        server.enqueue(jsonResponse(201, """{ "list": { "id": "lst_1", "title": "t" } }"""))
        val gate = gateFor(server, dispatcher).also { it.record(CustomerStatus.SUBSCRIBER) }

        val result = repositoryFor(server, dispatcher, gate).materialize(
            MaterializeRequest.ToListAndDocument(
                source = MaterializeSource.Messages(listOf("msg_1")),
                listConfig = ListConfig(title = "t"),
            ),
        )

        assertThat((result as ApiResult.Failure).error).isInstanceOf(AppError.Server::class.java)
    }
}
