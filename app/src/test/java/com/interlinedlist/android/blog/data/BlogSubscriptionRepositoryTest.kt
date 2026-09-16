package com.interlinedlist.android.blog.data

import com.google.common.truth.Truth.assertThat
import com.interlinedlist.android.blog.BlogSubscriptionOutcome
import com.interlinedlist.android.core.common.dispatcher.DispatcherProvider
import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.core.common.result.AppError
import com.interlinedlist.android.core.network.api.InterlinedListApi
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit

/**
 * Drives the repository over a real Retrofit/OkHttp stack against MockWebServer, so the
 * asserted request line and body are the ones the app actually sends.
 *
 * The responses mirror the live API, verified by probing it: subscribe answers
 * `200 { ok, message }` for any valid address and `400 { error, code }` for an invalid
 * one, and both token endpoints answer `307` with a `Location` of
 * `/blog?subscription=confirmed|unsubscribed|invalid`.
 */
class BlogSubscriptionRepositoryTest {

    private lateinit var server: MockWebServer
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `subscribe posts the address and reports the server's message`() = runTest(dispatcher) {
        server.enqueue(
            jsonResponse(
                200,
                """{"ok":true,"message":"Check your email to confirm your subscription."}""",
            ),
        )

        val result = repository().subscribe("  reader@example.com  ")

        val request = server.takeRequest()
        assertThat(request.method).isEqualTo("POST")
        assertThat(request.path).isEqualTo("/api/blog/subscribe")
        assertThat(request.body.readUtf8()).isEqualTo("""{"email":"reader@example.com"}""")
        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        assertThat((result as ApiResult.Success).data)
            .isEqualTo("Check your email to confirm your subscription.")
    }

    @Test
    fun `subscribe surfaces the server's rejection of an invalid address`() = runTest(dispatcher) {
        server.enqueue(
            jsonResponse(400, """{"error":"A valid email is required","code":"bad_request"}"""),
        )

        val result = repository().subscribe("not-an-email")

        assertThat(result).isInstanceOf(ApiResult.Failure::class.java)
        assertThat((result as ApiResult.Failure).error.message)
            .isEqualTo("A valid email is required")
    }

    @Test
    fun `subscribe surfaces a server error`() = runTest(dispatcher) {
        server.enqueue(MockResponse().setResponseCode(500))

        val result = repository().subscribe("reader@example.com")

        assertThat((result as ApiResult.Failure).error).isInstanceOf(AppError.Server::class.java)
    }

    @Test
    fun `confirm sends the token and reads the outcome off the redirect`() = runTest(dispatcher) {
        server.enqueue(redirect("https://interlinedlist.com/blog?subscription=confirmed"))

        val result = repository().confirm("tok en")

        val request = server.takeRequest()
        assertThat(request.method).isEqualTo("GET")
        assertThat(request.path).isEqualTo("/api/blog/subscribe/confirm?token=tok%20en")
        assertThat((result as ApiResult.Success).data)
            .isEqualTo(BlogSubscriptionOutcome.CONFIRMED)
    }

    @Test
    fun `unsubscribe sends the token and reads the outcome off the redirect`() =
        runTest(dispatcher) {
            server.enqueue(redirect("https://interlinedlist.com/blog?subscription=unsubscribed"))

            val result = repository().unsubscribe("abc123")

            val request = server.takeRequest()
            assertThat(request.method).isEqualTo("GET")
            assertThat(request.path).isEqualTo("/api/blog/unsubscribe?token=abc123")
            assertThat((result as ApiResult.Success).data)
                .isEqualTo(BlogSubscriptionOutcome.UNSUBSCRIBED)
        }

    @Test
    fun `a rejected token is reported as invalid, not as success`() = runTest(dispatcher) {
        server.enqueue(redirect("https://interlinedlist.com/blog?subscription=invalid"))

        val result = repository().confirm("expired")

        assertThat((result as ApiResult.Success).data).isEqualTo(BlogSubscriptionOutcome.INVALID)
    }

    @Test
    fun `the redirect is not followed, so the blog page is never fetched`() = runTest(dispatcher) {
        server.enqueue(redirect(server.url("/blog?subscription=confirmed").toString()))

        repository().confirm("abc123")

        server.takeRequest()
        // A second request would mean OkHttp chased the Location header.
        assertThat(server.requestCount).isEqualTo(1)
    }

    @Test
    fun `a failed unsubscribe surfaces as an error rather than a silent success`() =
        runTest(dispatcher) {
            server.enqueue(MockResponse().setResponseCode(503))

            val result = repository().unsubscribe("abc123")

            assertThat((result as ApiResult.Failure).error)
                .isInstanceOf(AppError.Server::class.java)
        }

    @Test
    fun `accountEmail prefills from the signed-in account`() = runTest(dispatcher) {
        server.enqueue(jsonResponse(200, """{"user":{"id":"1","email":"me@example.com"}}"""))

        assertThat(repository().accountEmail()).isEqualTo("me@example.com")
        assertThat(server.takeRequest().path).isEqualTo("/api/user")
    }

    @Test
    fun `accountEmail stays null when the account cannot be read`() = runTest(dispatcher) {
        server.enqueue(MockResponse().setResponseCode(401))

        assertThat(repository().accountEmail()).isNull()
    }

    private fun repository(): DefaultBlogSubscriptionRepository {
        val json = testJson()
        return DefaultBlogSubscriptionRepository(
            blogApi = retrofit(json, followRedirects = false).create(BlogApi::class.java),
            userApi = retrofit(json).create(InterlinedListApi::class.java),
            json = json,
            dispatchers = testDispatchers(dispatcher),
        )
    }

    /** The same stack `BlogNetworkModule` builds: redirects off for the blog client. */
    private fun retrofit(json: Json, followRedirects: Boolean = true): Retrofit = Retrofit.Builder()
        .baseUrl(server.url("/"))
        .client(OkHttpClient.Builder().followRedirects(followRedirects).build())
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    /** The same Json configuration `NetworkModule` installs in the app. */
    private fun testJson(): Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
    }

    private fun testDispatchers(dispatcher: CoroutineDispatcher): DispatcherProvider =
        object : DispatcherProvider {
            override val io: CoroutineDispatcher get() = dispatcher
            override val default: CoroutineDispatcher get() = dispatcher
            override val main: CoroutineDispatcher get() = dispatcher
        }

    private fun jsonResponse(code: Int, body: String): MockResponse = MockResponse()
        .setResponseCode(code)
        .setHeader("Content-Type", "application/json")
        .setBody(body)

    private fun redirect(location: String): MockResponse = MockResponse()
        .setResponseCode(307)
        .setHeader("Location", location)
}
