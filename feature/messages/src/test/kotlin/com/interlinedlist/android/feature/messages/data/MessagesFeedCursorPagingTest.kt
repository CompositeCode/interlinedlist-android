package com.interlinedlist.android.feature.messages.data

import com.google.common.truth.Truth.assertThat
import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.core.network.api.InterlinedListApi
import com.interlinedlist.android.core.network.preferences.ViewingPreferenceStore
import com.interlinedlist.android.feature.messages.data.remote.MessagesApi
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit

/**
 * `GET /api/messages` paginates by an **opaque** keyset `cursor`: the caller hands
 * back the previous response's `nextCursor` verbatim and never constructs, parses
 * or modifies one. The endpoint has no `offset` parameter at all.
 *
 * These tests pin that contract, and in particular the regression it exists for:
 * a post arriving at the head of the feed between two pages must not duplicate or
 * skip rows, which offset paging cannot guarantee.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MessagesFeedCursorPagingTest {

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
    private lateinit var dao: FakeMessageDao

    /** An opaque, base64-padded token: nothing in the app may interpret it. */
    private val cursorAfterM3 = "MjAyNi0wOS0xM1QxMjowMDowMC4wMDBafG0z="

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val contentType = "application/json".toMediaType()
        val retrofit = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
        api = retrofit.create(MessagesApi::class.java)
        userApi = retrofit.create(InterlinedListApi::class.java)
        dao = FakeMessageDao()
    }

    @After
    fun tearDown() = server.shutdown()

    private fun repository() = DefaultMessagesRepository(
        api = api,
        userApi = userApi,
        viewingPreferenceStore = ViewingPreferenceStore(userApi, json),
        messageDao = dao,
        sessionStore = fakeSessionStore("me"),
        json = json,
        dispatchers = TestDispatcherProvider(dispatcher),
    )

    private fun page(ids: List<String>, nextCursor: String?): String {
        val rows = ids.joinToString(",") { """{ "id": "$it", "content": "$it" }""" }
        val cursor = nextCursor?.let { "\"$it\"" } ?: "null"
        return """
            { "messages": [$rows],
              "pagination": { "limit": 3, "hasMore": ${nextCursor != null}, "nextCursor": $cursor } }
        """.trimIndent()
    }

    private fun enqueue(body: String) {
        server.enqueue(MockResponse().setResponseCode(200).setBody(body))
    }

    @Test
    fun `the first page asks for no cursor and never sends an offset`() = runTest(dispatcher) {
        enqueue(page(listOf("m5", "m4", "m3"), cursorAfterM3))

        val result = repository().refreshFeed()

        assertThat((result as ApiResult.Success).data).isEqualTo(cursorAfterM3)
        val url = server.takeRequest().requestUrl!!
        assertThat(url.queryParameter("cursor")).isNull()
        assertThat(url.queryParameter("offset")).isNull()
        assertThat(url.queryParameter("limit")).isEqualTo("20")
    }

    @Test
    fun `page two hands the cursor back verbatim and sends no offset`() = runTest(dispatcher) {
        enqueue(page(listOf("m5", "m4", "m3"), cursorAfterM3))
        enqueue(page(listOf("m2", "m1"), nextCursor = null))
        val repo = repository()

        val cursor = (repo.refreshFeed() as ApiResult.Success).data!!
        repo.loadMoreFeed(cursor)

        server.takeRequest() // page 1
        val url = server.takeRequest().requestUrl!!
        // Verbatim: the padded token survives the round trip untouched.
        assertThat(url.queryParameter("cursor")).isEqualTo(cursorAfterM3)
        assertThat(url.queryParameter("offset")).isNull()
    }

    @Test
    fun `a null nextCursor marks the end of the feed`() = runTest(dispatcher) {
        enqueue(page(listOf("m5", "m4", "m3"), cursorAfterM3))
        enqueue(page(listOf("m2", "m1"), nextCursor = null))
        val repo = repository()

        val cursor = (repo.refreshFeed() as ApiResult.Success).data!!
        val next = repo.loadMoreFeed(cursor)

        assertThat((next as ApiResult.Success).data).isNull()
    }

    @Test
    fun `appending page two adds no duplicates and skips no rows`() = runTest(dispatcher) {
        enqueue(page(listOf("m5", "m4", "m3"), cursorAfterM3))
        enqueue(page(listOf("m2", "m1"), nextCursor = null))
        val repo = repository()

        val cursor = (repo.refreshFeed() as ApiResult.Success).data!!
        repo.loadMoreFeed(cursor)

        val ids = repo.observeFeed().first().map { it.id }
        assertThat(ids).containsExactly("m5", "m4", "m3", "m2", "m1").inOrder()
        assertThat(ids).containsNoDuplicates()
    }

    @Test
    fun `a post arriving at the head between pages neither duplicates nor skips rows`() =
        runTest(dispatcher) {
            // Server truth at page 1: m5 m4 m3 m2 m1. Between the two page loads a
            // new post (m6) lands at the head, shifting every offset by one.
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val url: HttpUrl = request.requestUrl!!
                    val body = when {
                        // Offset paging against the drifted feed: it would re-serve
                        // m3 and skip m1. Reaching this branch means the app
                        // regressed to offset paging.
                        url.queryParameter("offset") != null -> page(listOf("m3", "m2"), null)
                        url.queryParameter("cursor") == cursorAfterM3 ->
                            page(listOf("m2", "m1"), null)
                        else -> page(listOf("m5", "m4", "m3"), cursorAfterM3)
                    }
                    return MockResponse().setResponseCode(200).setBody(body)
                }
            }
            val repo = repository()

            val cursor = (repo.refreshFeed() as ApiResult.Success).data!!
            repo.loadMoreFeed(cursor)

            val ids = repo.observeFeed().first().map { it.id }
            assertThat(ids).containsNoDuplicates()
            assertThat(ids).containsExactly("m5", "m4", "m3", "m2", "m1").inOrder()
            server.takeRequest()
            assertThat(server.takeRequest().requestUrl!!.queryParameter("offset")).isNull()
        }

    @Test
    fun `refreshing from the top resets the cursor and replaces the head`() = runTest(dispatcher) {
        enqueue(page(listOf("m5", "m4", "m3"), cursorAfterM3))
        enqueue(page(listOf("m2", "m1"), nextCursor = null))
        // The refresh sees the new head post and a fresh cursor.
        enqueue(page(listOf("m6", "m5", "m4"), "cursor-after-m4"))
        val repo = repository()

        val cursor = (repo.refreshFeed() as ApiResult.Success).data!!
        repo.loadMoreFeed(cursor)
        val refreshed = repo.refreshFeed()

        // A refresh restarts paging from the top: no cursor on the wire, the cached
        // head is replaced by the fresh page, and a new cursor is handed back.
        assertThat((refreshed as ApiResult.Success).data).isEqualTo("cursor-after-m4")
        assertThat(repo.observeFeed().first().map { it.id })
            .containsExactly("m6", "m5", "m4").inOrder()
        server.takeRequest()
        server.takeRequest()
        assertThat(server.takeRequest().requestUrl!!.queryParameter("cursor")).isNull()
    }
}
