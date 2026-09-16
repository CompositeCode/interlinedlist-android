package com.interlinedlist.android.feature.notifications.data

import com.google.common.truth.Truth.assertThat
import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.core.common.result.AppError
import com.interlinedlist.android.feature.notifications.data.remote.PushApi
import com.interlinedlist.android.feature.notifications.push.PushEnvironment
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit

/**
 * Pins the wire contract of `POST /api/push/register` and `DELETE /api/push/unregister`
 * (https://interlinedlist.com/help/api/push-notifications) against a MockWebServer.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DefaultPushRegistrationRepositoryTest {

    private val dispatcher = StandardTestDispatcher()
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    private lateinit var server: MockWebServer
    private lateinit var api: PushApi

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(PushApi::class.java)
    }

    @After
    fun tearDown() = server.shutdown()

    private fun repository(environment: PushEnvironment = PushEnvironment.PRODUCTION) =
        DefaultPushRegistrationRepository(
            api = api,
            environment = environment,
            json = json,
            dispatchers = TestDispatcherProvider(dispatcher),
        )

    private fun enqueue(code: Int, body: String = "") {
        server.enqueue(MockResponse().setResponseCode(code).setBody(body))
    }

    // ---- register ----------------------------------------------------------

    @Test
    fun `register posts the token as an android device in the production environment`() =
        runTest(dispatcher) {
            enqueue(200, """{ "registered": true }""")

            val result = repository(PushEnvironment.PRODUCTION).register("dev-token-abc")

            assertThat(result).isInstanceOf(ApiResult.Success::class.java)
            val recorded = server.takeRequest()
            assertThat(recorded.method).isEqualTo("POST")
            assertThat(recorded.path).isEqualTo("/api/push/register")
            val body = Json.parseToJsonElement(recorded.body.readUtf8()).jsonObject
            assertThat(body["token"]?.jsonPrimitive?.content).isEqualTo("dev-token-abc")
            // The server accepts exactly "ios" or "android" — never "Android"/"fcm".
            assertThat(body["platform"]?.jsonPrimitive?.content).isEqualTo("android")
            assertThat(body["environment"]?.jsonPrimitive?.content).isEqualTo("production")
            assertThat(body.keys).containsExactly("token", "platform", "environment")
        }

    @Test
    fun `register sends the sandbox environment for a debuggable build`() = runTest(dispatcher) {
        enqueue(200, """{ "registered": true }""")

        repository(PushEnvironment.fromDebuggable(debuggable = true)).register("dev-token-abc")

        val body = Json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
        assertThat(body["platform"]?.jsonPrimitive?.content).isEqualTo("android")
        assertThat(body["environment"]?.jsonPrimitive?.content).isEqualTo("sandbox")
    }

    @Test
    fun `register tolerates a body without the registered flag`() = runTest(dispatcher) {
        enqueue(200, "{}")

        assertThat(repository().register("dev-token-abc"))
            .isInstanceOf(ApiResult.Success::class.java)
    }

    @Test
    fun `register maps a 401 to an unauthorized failure`() = runTest(dispatcher) {
        enqueue(401, """{ "error": "Unauthorized", "code": "unauthorized" }""")

        val result = repository().register("dev-token-abc")

        assertThat(result).isInstanceOf(ApiResult.Failure::class.java)
        assertThat((result as ApiResult.Failure).error).isInstanceOf(AppError.Unauthorized::class.java)
    }

    // ---- unregister --------------------------------------------------------

    @Test
    fun `unregister sends the token in the DELETE request body`() = runTest(dispatcher) {
        enqueue(200, """{ "unregistered": true }""")

        val result = repository().unregister("dev-token-abc")

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        val recorded = server.takeRequest()
        assertThat(recorded.method).isEqualTo("DELETE")
        assertThat(recorded.path).isEqualTo("/api/push/unregister")
        val body = Json.parseToJsonElement(recorded.body.readUtf8()).jsonObject
        assertThat(body["token"]?.jsonPrimitive?.content).isEqualTo("dev-token-abc")
        assertThat(body.keys).containsExactly("token")
    }

    @Test
    fun `unregistering an unknown token still succeeds`() = runTest(dispatcher) {
        // Documented as idempotent: an unknown token returns 200, so sign-out can call
        // this defensively without tracking what the server actually holds.
        enqueue(200, "")

        assertThat(repository().unregister("never-registered"))
            .isInstanceOf(ApiResult.Success::class.java)
    }
}
