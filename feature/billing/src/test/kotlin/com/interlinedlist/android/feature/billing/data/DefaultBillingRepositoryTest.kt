package com.interlinedlist.android.feature.billing.data

import com.google.common.truth.Truth.assertThat
import com.interlinedlist.android.core.common.dispatcher.DispatcherProvider
import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.core.common.result.AppError
import com.interlinedlist.android.feature.billing.data.remote.BillingApi
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit

/**
 * Repository behaviour against a real HTTP stack (Retrofit + OkHttp) driven by
 * MockWebServer. No live Stripe sessions are ever created — every response is a
 * canned MockResponse. Verifies the checkout/portal URLs are surfaced, the right
 * endpoints/bodies are hit, errors are mapped, and a URL-less 2xx degrades to a
 * failure the UI can render.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DefaultBillingRepositoryTest {

    private lateinit var server: MockWebServer
    private lateinit var api: BillingApi
    private lateinit var repository: DefaultBillingRepository

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    private val dispatcher = StandardTestDispatcher()

    private val testDispatchers = object : DispatcherProvider {
        override val io: CoroutineDispatcher get() = dispatcher
        override val default: CoroutineDispatcher get() = dispatcher
        override val main: CoroutineDispatcher get() = dispatcher
    }

    @Before
    fun setUp() {
        server = MockWebServer().also { it.start() }
        api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(BillingApi::class.java)
        repository = DefaultBillingRepository(api, json, testDispatchers)
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun `createCheckoutSession posts to the checkout endpoint and returns the url`() =
        runTest(dispatcher) {
            server.enqueue(
                MockResponse().setResponseCode(201)
                    .setBody("""{ "url": "https://checkout.stripe.com/c/pay/cs_test_123" }"""),
            )

            val result = repository.createCheckoutSession("price_pro_monthly")

            assertThat(result).isInstanceOf(ApiResult.Success::class.java)
            assertThat((result as ApiResult.Success).data)
                .isEqualTo("https://checkout.stripe.com/c/pay/cs_test_123")

            val request = server.takeRequest()
            assertThat(request.method).isEqualTo("POST")
            assertThat(request.path).isEqualTo("/api/stripe/create-checkout-session")
            assertThat(request.body.readUtf8()).contains("price_pro_monthly")
        }

    @Test
    fun `createPortalSession posts to the portal endpoint and returns the url`() =
        runTest(dispatcher) {
            server.enqueue(
                MockResponse().setResponseCode(201)
                    .setBody("""{ "url": "https://billing.stripe.com/p/session/bps_test_456" }"""),
            )

            val result = repository.createPortalSession()

            assertThat(result).isInstanceOf(ApiResult.Success::class.java)
            assertThat((result as ApiResult.Success).data)
                .isEqualTo("https://billing.stripe.com/p/session/bps_test_456")

            val request = server.takeRequest()
            assertThat(request.method).isEqualTo("POST")
            assertThat(request.path).isEqualTo("/api/stripe/create-portal-session")
        }

    @Test
    fun `createCheckoutSession maps a 401 to Unauthorized`() = runTest(dispatcher) {
        server.enqueue(
            MockResponse().setResponseCode(401)
                .setBody("""{ "error": "Not authenticated" }"""),
        )

        val result = repository.createCheckoutSession()

        assertThat(result).isInstanceOf(ApiResult.Failure::class.java)
        assertThat((result as ApiResult.Failure).error)
            .isInstanceOf(AppError.Unauthorized::class.java)
    }

    @Test
    fun `createPortalSession maps a 500 to Server`() = runTest(dispatcher) {
        server.enqueue(MockResponse().setResponseCode(500).setBody("boom"))

        val result = repository.createPortalSession()

        assertThat(result).isInstanceOf(ApiResult.Failure::class.java)
        assertThat((result as ApiResult.Failure).error)
            .isInstanceOf(AppError.Server::class.java)
    }

    @Test
    fun `a successful response with no url degrades to a Server failure`() = runTest(dispatcher) {
        server.enqueue(MockResponse().setResponseCode(201).setBody("""{ "id": "cs_test_789" }"""))

        val result = repository.createCheckoutSession()

        assertThat(result).isInstanceOf(ApiResult.Failure::class.java)
        assertThat((result as ApiResult.Failure).error)
            .isInstanceOf(AppError.Server::class.java)
    }

    @Test
    fun `checkout accepts a checkoutUrl alias when url is absent`() = runTest(dispatcher) {
        server.enqueue(
            MockResponse().setResponseCode(201)
                .setBody("""{ "checkoutUrl": "https://checkout.stripe.com/c/pay/alias" }"""),
        )

        val result = repository.createCheckoutSession()

        assertThat((result as ApiResult.Success).data)
            .isEqualTo("https://checkout.stripe.com/c/pay/alias")
    }
}
