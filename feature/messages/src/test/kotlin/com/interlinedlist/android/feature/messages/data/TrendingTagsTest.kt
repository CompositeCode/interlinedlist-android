package com.interlinedlist.android.feature.messages.data

import com.google.common.truth.Truth.assertThat
import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.core.common.result.AppError
import com.interlinedlist.android.core.network.api.InterlinedListApi
import com.interlinedlist.android.core.network.preferences.ViewingPreferenceStore
import com.interlinedlist.android.feature.messages.data.remote.MessagesApi
import com.interlinedlist.android.feature.messages.domain.TrendingWindow
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
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
 * Trending tags (`GET /api/tags/trending`).
 *
 * The window is a **request** parameter the response never echoes back, and the
 * server silently falls back to `week` for any value it does not recognise — so
 * the request itself is the only place the app's "this week" wording can be kept
 * honest. These tests pin the parameters on the wire, the mapping, and the
 * failure path the surface renders as its error state.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TrendingTagsTest {

    private val dispatcher = StandardTestDispatcher()

    // Mirrors the production Json (see core:network NetworkModule).
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
    }

    private lateinit var server: MockWebServer
    private lateinit var api: MessagesApi
    private lateinit var userApi: InterlinedListApi

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val retrofit = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
        api = retrofit.create(MessagesApi::class.java)
        userApi = retrofit.create(InterlinedListApi::class.java)
    }

    @After
    fun tearDown() = server.shutdown()

    private fun repository() = DefaultMessagesRepository(
        api = api,
        userApi = userApi,
        viewingPreferenceStore = ViewingPreferenceStore(userApi, json),
        messageDao = FakeMessageDao(),
        sessionStore = fakeSessionStore("me"),
        json = json,
        dispatchers = TestDispatcherProvider(dispatcher),
    )

    private fun enqueue(code: Int, body: String) {
        server.enqueue(MockResponse().setResponseCode(code).setBody(body))
    }

    @Test
    fun `asks for the window and limit it will label the surface with`() = runTest(dispatcher) {
        enqueue(200, """{ "tags": [] }""")

        repository().trendingTags()

        val request = server.takeRequest()
        assertThat(request.path).startsWith("/api/tags/trending?")
        // Sent explicitly, never left to the server default: the UI says "this
        // week", so the request must be the one that makes that true.
        assertThat(request.requestUrl?.queryParameter("window")).isEqualTo(TrendingWindow.WEEK.wire)
        assertThat(request.requestUrl?.queryParameter("limit"))
            .isEqualTo(MessagesRepository.TRENDING_TAG_LIMIT.toString())
    }

    @Test
    fun `a different window is sent as the API's documented wire value`() = runTest(dispatcher) {
        enqueue(200, """{ "tags": [] }""")

        repository().trendingTags(window = TrendingWindow.MONTH)

        assertThat(server.takeRequest().requestUrl?.queryParameter("window")).isEqualTo("month")
    }

    @Test
    fun `maps the live payload in the server's order`() = runTest(dispatcher) {
        enqueue(
            200,
            """
            {
              "tags": [
                { "tag": "Lego", "count": 2, "lastUsedAt": "2026-09-12T20:40:05.777Z" },
                { "tag": "life is short, o brave girl", "count": 1,
                  "lastUsedAt": "2026-09-11T03:44:52.334Z" }
              ]
            }
            """.trimIndent(),
        )

        val result = repository().trendingTags()

        val tags = (result as ApiResult.Success).data
        assertThat(tags.map { it.tag })
            .containsExactly("Lego", "life is short, o brave girl").inOrder()
        assertThat(tags.first().count).isEqualTo(2)
        assertThat(tags.last().lastUsedAt).isEqualTo("2026-09-11T03:44:52.334Z")
    }

    @Test
    fun `a quiet instance reports no trending tags, not a failure`() = runTest(dispatcher) {
        enqueue(200, """{ "tags": [] }""")

        val result = repository().trendingTags()

        assertThat((result as ApiResult.Success).data).isEmpty()
    }

    @Test
    fun `a server failure surfaces as an error, never as an empty list`() = runTest(dispatcher) {
        enqueue(500, """{ "error": "boom", "code": "server_error" }""")

        val result = repository().trendingTags()

        assertThat(result).isInstanceOf(ApiResult.Failure::class.java)
        assertThat((result as ApiResult.Failure).error).isInstanceOf(AppError.Server::class.java)
    }
}
