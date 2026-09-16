package com.interlinedlist.android.feature.ai.data

import com.google.common.truth.Truth.assertThat
import com.interlinedlist.android.feature.ai.domain.AiAvailability
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
 * `GET /api/ai/status` → [AiAvailability], every branch: provider configured or
 * not, subscriber or not, and the `customerStatus` fallback for a body that
 * omits the `subscriber` flag.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AiAvailabilityTest {

    private lateinit var server: MockWebServer
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        server = MockWebServer().also { it.start() }
    }

    @After
    fun tearDown() = server.shutdown()

    private fun repository() = repositoryFor(server, dispatcher)

    @Test
    fun `subscriber with a configured provider is available, with quota`() = runTest(dispatcher) {
        server.enqueue(
            jsonResponse(
                200,
                """
                {
                  "subscriber": true,
                  "providers": ["anthropic"],
                  "defaultModels": { "anthropic": "claude-sonnet-5" },
                  "quota": { "usedToday": 8, "dailyLimit": 50, "remaining": 42 }
                }
                """.trimIndent(),
            ),
        )

        val availability = repository().availability()

        assertThat(server.takeRequest().path).isEqualTo("/api/ai/status")
        assertThat(availability).isInstanceOf(AiAvailability.Available::class.java)
        val quota = (availability as AiAvailability.Available).quota
        assertThat(quota?.remainingActions).isEqualTo(42)
        assertThat(availability.isEnabled).isTrue()
        assertThat(availability.isQuotaExhausted).isFalse()
    }

    @Test
    fun `an unconfigured provider hides AI even for a subscriber`() = runTest(dispatcher) {
        server.enqueue(
            jsonResponse(200, """{ "subscriber": true, "providers": [], "quota": null }"""),
        )

        assertThat(repository().availability()).isEqualTo(AiAvailability.Unavailable)
    }

    @Test
    fun `a free account is NotSubscribed`() = runTest(dispatcher) {
        server.enqueue(
            jsonResponse(200, """{ "subscriber": false, "providers": ["anthropic"] }"""),
        )

        val availability = repository().availability()

        assertThat(availability).isEqualTo(AiAvailability.NotSubscribed)
        assertThat(availability.isEnabled).isFalse()
    }

    @Test
    fun `a spent allowance stays available but reports the quota as exhausted`() = runTest(dispatcher) {
        server.enqueue(
            jsonResponse(
                200,
                """
                { "subscriber": true, "providers": ["anthropic"],
                  "quota": { "usedToday": 50, "dailyLimit": 50, "remaining": 0 } }
                """.trimIndent(),
            ),
        )

        val availability = repository().availability()

        assertThat(availability.isEnabled).isTrue()
        assertThat(availability.isQuotaExhausted).isTrue()
    }

    @Test
    fun `a body without the subscriber flag falls back to customerStatus`() = runTest(dispatcher) {
        server.enqueue(jsonResponse(200, """{ "providers": ["anthropic"] }"""))
        server.enqueue(
            jsonResponse(
                200,
                """{ "user": { "id": "u1", "username": "adron", "customerStatus": "subscriber:annual" } }""",
            ),
        )

        val availability = repository().availability()

        assertThat(server.takeRequest().path).isEqualTo("/api/ai/status")
        assertThat(server.takeRequest().path).isEqualTo("/api/user")
        assertThat(availability).isInstanceOf(AiAvailability.Available::class.java)
    }

    @Test
    fun `the customerStatus fallback keeps a free account out`() = runTest(dispatcher) {
        server.enqueue(jsonResponse(200, """{ "providers": ["anthropic"] }"""))
        server.enqueue(
            jsonResponse(200, """{ "user": { "id": "u1", "username": "adron", "customerStatus": "free" } }"""),
        )

        assertThat(repository().availability()).isEqualTo(AiAvailability.NotSubscribed)
    }

    @Test
    fun `an unverifiable subscription hides AI rather than guessing`() = runTest(dispatcher) {
        server.enqueue(jsonResponse(200, """{ "providers": ["anthropic"] }"""))
        server.enqueue(jsonResponse(401, """{ "error": "Unauthorized", "code": "unauthorized" }"""))

        assertThat(repository().availability()).isEqualTo(AiAvailability.Unavailable)
    }

    @Test
    fun `an absent providers field is not treated as unconfigured`() = runTest(dispatcher) {
        server.enqueue(jsonResponse(200, """{ "subscriber": true }"""))

        assertThat(repository().availability()).isEqualTo(AiAvailability.Available(null))
    }

    @Test
    fun `an unauthenticated status read hides AI`() = runTest(dispatcher) {
        server.enqueue(jsonResponse(401, """{ "error": "Unauthorized", "code": "unauthorized" }"""))

        assertThat(repository().availability()).isEqualTo(AiAvailability.Unavailable)
    }

    @Test
    fun `a network failure hides AI`() = runTest(dispatcher) {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))

        assertThat(repository().availability()).isEqualTo(AiAvailability.Unavailable)
    }
}
