package com.interlinedlist.android.feature.ai.data

import com.google.common.truth.Truth.assertThat
import com.interlinedlist.android.feature.ai.domain.AiError
import com.interlinedlist.android.feature.ai.domain.AiFeature
import com.interlinedlist.android.feature.ai.domain.AiResult
import com.interlinedlist.android.feature.ai.domain.AiSuggestInput
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
 * Failure mapping for `/suggest` and `/generate`. The AI routes answer
 * `{ error, code }`, and the code is what separates the two 429s and the
 * unconfigured-provider 409 from anything else.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AiErrorMappingTest {

    private lateinit var server: MockWebServer
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        server = MockWebServer().also { it.start() }
    }

    @After
    fun tearDown() = server.shutdown()

    private suspend fun suggestError(response: MockResponse): AiError {
        server.enqueue(response)
        val result = repositoryFor(server, dispatcher)
            .suggest(AiFeature.WRITING_ASSIST, AiSuggestInput("draft"))
        return (result as AiResult.Failure).error
    }

    @Test
    fun `403 subscription_required is the subscriber gate`() = runTest(dispatcher) {
        val error = suggestError(
            jsonResponse(403, """{ "error": "This feature requires an active subscription.", "code": "subscription_required" }"""),
        )

        assertThat(error).isInstanceOf(AiError.NotSubscribed::class.java)
        assertThat(error.message).isEqualTo("This feature requires an active subscription.")
    }

    @Test
    fun `409 no_provider_configured is a site misconfiguration, not a user error`() = runTest(dispatcher) {
        val error = suggestError(
            jsonResponse(409, """{ "error": "AI is not configured", "code": "no_provider_configured" }"""),
        )

        assertThat(error).isInstanceOf(AiError.ProviderUnconfigured::class.java)
    }

    @Test
    fun `429 quota_exceeded is the daily allowance`() = runTest(dispatcher) {
        val error = suggestError(
            jsonResponse(429, """{ "error": "Daily AI limit reached", "code": "quota_exceeded" }"""),
        )

        assertThat(error).isInstanceOf(AiError.QuotaExceeded::class.java)
    }

    @Test
    fun `429 rate_limited keeps Retry-After and stays distinct from the quota`() = runTest(dispatcher) {
        val error = suggestError(
            jsonResponse(429, """{ "error": "Too many requests", "code": "rate_limited" }""")
                .setHeader("Retry-After", "37"),
        )

        assertThat(error).isEqualTo(AiError.RateLimited("Too many requests", 37))
    }

    @Test
    fun `an uncoded 429 falls back on Retry-After to tell the two apart`() = runTest(dispatcher) {
        assertThat(suggestError(jsonResponse(429, """{ "error": "Too many requests" }""")))
            .isInstanceOf(AiError.QuotaExceeded::class.java)
        assertThat(
            suggestError(
                jsonResponse(429, """{ "error": "Too many requests" }""").setHeader("Retry-After", "5"),
            ),
        ).isEqualTo(AiError.RateLimited("Too many requests", 5))
    }

    @Test
    fun `422 separates bad input from unusable model output`() = runTest(dispatcher) {
        assertThat(suggestError(jsonResponse(422, """{ "error": "Input too short", "code": "invalid_input" }""")))
            .isInstanceOf(AiError.InvalidInput::class.java)
        assertThat(suggestError(jsonResponse(422, """{ "error": "Bad output", "code": "invalid_ai_output" }""")))
            .isInstanceOf(AiError.InvalidOutput::class.java)
        assertThat(suggestError(jsonResponse(422, """{ "error": "Refused", "code": "refused" }""")))
            .isInstanceOf(AiError.InvalidOutput::class.java)
        // No code at all: the status alone still says "your request".
        assertThat(suggestError(jsonResponse(422, """{ "error": "Nope" }""")))
            .isInstanceOf(AiError.InvalidInput::class.java)
    }

    @Test
    fun `provider_error covers both the 502 and the 500`() = runTest(dispatcher) {
        assertThat(suggestError(jsonResponse(502, """{ "error": "Upstream timeout", "code": "provider_error" }""")))
            .isInstanceOf(AiError.ProviderFailure::class.java)
        assertThat(suggestError(jsonResponse(500, """{ "error": "Server error" }""")))
            .isInstanceOf(AiError.ProviderFailure::class.java)
    }

    @Test
    fun `an account-status 403 is not an upsell`() = runTest(dispatcher) {
        val error = suggestError(
            jsonResponse(403, """{ "error": "Your account is restricted.", "code": "account_restricted" }"""),
        )

        assertThat(error).isInstanceOf(AiError.Forbidden::class.java)
    }

    @Test
    fun `401 is a sign-in problem`() = runTest(dispatcher) {
        assertThat(suggestError(jsonResponse(401, """{ "error": "Unauthorized", "code": "unauthorized" }""")))
            .isInstanceOf(AiError.NotAuthenticated::class.java)
    }

    @Test
    fun `a dropped connection is a network error`() = runTest(dispatcher) {
        assertThat(suggestError(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START)))
            .isInstanceOf(AiError.Network::class.java)
    }

    @Test
    fun `an unparseable error body still maps by status`() = runTest(dispatcher) {
        assertThat(suggestError(jsonResponse(409, "<html>gateway</html>")))
            .isInstanceOf(AiError.ProviderUnconfigured::class.java)
    }
}
