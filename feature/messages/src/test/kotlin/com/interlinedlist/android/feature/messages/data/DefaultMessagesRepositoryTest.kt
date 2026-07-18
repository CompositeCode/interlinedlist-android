package com.interlinedlist.android.feature.messages.data

import com.google.common.truth.Truth.assertThat
import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.core.common.result.AppError
import com.interlinedlist.android.feature.messages.data.remote.MessagesApi
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
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

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultMessagesRepositoryTest {

    private val dispatcher = StandardTestDispatcher()
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    private lateinit var server: MockWebServer
    private lateinit var api: MessagesApi
    private lateinit var dao: FakeMessageDao

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val contentType = "application/json".toMediaType()
        api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
            .create(MessagesApi::class.java)
        dao = FakeMessageDao()
    }

    @After
    fun tearDown() = server.shutdown()

    private fun repository(currentUserId: String? = "me") = DefaultMessagesRepository(
        api = api,
        messageDao = dao,
        sessionStore = fakeSessionStore(currentUserId),
        json = json,
        dispatchers = TestDispatcherProvider(dispatcher),
    )

    private fun enqueueJson(code: Int, body: String) {
        server.enqueue(MockResponse().setResponseCode(code).setBody(body))
    }

    @Test
    fun `refreshFeed caches messages and reports hasMore`() = runTest(dispatcher) {
        enqueueJson(
            200,
            """
            {
              "data": [
                { "id": "1", "content": "first", "author": { "id": "a", "username": "amy" },
                  "digCount": 2, "replyCount": 1 },
                { "id": "2", "content": "second", "author": { "id": "me", "username": "me" } }
              ],
              "pagination": { "total": 5, "limit": 20, "offset": 0, "hasMore": true }
            }
            """.trimIndent(),
        )
        val repo = repository()

        val result = repo.refreshFeed()

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        assertThat((result as ApiResult.Success).data).isTrue() // hasMore
        val cached = repo.observeFeed().first()
        assertThat(cached.map { it.id }).containsExactly("1", "2").inOrder()
        // Author id "me" matches the session user -> flagged mine.
        assertThat(cached.first { it.id == "2" }.mine).isTrue()
        assertThat(cached.first { it.id == "1" }.mine).isFalse()
    }

    @Test
    fun `refreshFeed maps a 403 subscription error`() = runTest(dispatcher) {
        enqueueJson(403, """{ "error": "An active subscription is required." }""")
        val repo = repository()

        val result = repo.refreshFeed()

        assertThat(result).isInstanceOf(ApiResult.Failure::class.java)
        assertThat((result as ApiResult.Failure).error).isInstanceOf(AppError.SubscriptionRequired::class.java)
    }

    @Test
    fun `loadMoreFeed appends after existing feed rows`() = runTest(dispatcher) {
        enqueueJson(
            200,
            """{ "data": [ { "id": "1", "content": "a" } ],
                "pagination": { "total": 3, "limit": 20, "offset": 0, "hasMore": true } }""",
        )
        enqueueJson(
            200,
            """{ "data": [ { "id": "2", "content": "b" } ],
                "pagination": { "total": 3, "limit": 20, "offset": 1, "hasMore": false } }""",
        )
        val repo = repository()
        repo.refreshFeed()

        val more = repo.loadMoreFeed(currentCount = 1)

        assertThat((more as ApiResult.Success).data).isFalse() // no more pages
        val ids = repo.observeFeed().first().map { it.id }
        assertThat(ids).containsExactly("1", "2").inOrder()

        // Second request carried the offset from the current feed size.
        server.takeRequest()
        val secondPath = server.takeRequest().path
        assertThat(secondPath).contains("offset=1")
    }

    @Test
    fun `createMessage caches the new message at the top of the feed`() = runTest(dispatcher) {
        enqueueJson(
            200,
            """{ "data": [ { "id": "old", "content": "old" } ],
                "pagination": { "hasMore": false } }""",
        )
        enqueueJson(
            201,
            """{ "message": { "id": "new", "content": "brand new",
                "author": { "id": "me", "username": "me" } } }""",
        )
        val repo = repository()
        repo.refreshFeed()

        val result = repo.createMessage("brand new")

        assertThat((result as ApiResult.Success).data.id).isEqualTo("new")
        val ids = repo.observeFeed().first().map { it.id }
        assertThat(ids.first()).isEqualTo("new")
    }

    @Test
    fun `setDig optimistically updates then rolls back on failure`() = runTest(dispatcher) {
        enqueueJson(
            200,
            """{ "data": [ { "id": "1", "content": "x", "digCount": 0, "dugByCurrentUser": false } ],
                "pagination": { "hasMore": false } }""",
        )
        enqueueJson(500, """{ "error": "boom" }""")
        val repo = repository()
        repo.refreshFeed()

        val result = repo.setDug("1", dug = true)

        assertThat(result).isInstanceOf(ApiResult.Failure::class.java)
        // Rolled back to the pre-dig state.
        val cached = repo.observeMessage("1").first()
        assertThat(cached?.dugByMe).isFalse()
        assertThat(cached?.digCount).isEqualTo(0)
    }

    @Test
    fun `setDig persists the dig on success`() = runTest(dispatcher) {
        enqueueJson(
            200,
            """{ "data": [ { "id": "1", "content": "x", "digCount": 4, "dugByCurrentUser": false } ],
                "pagination": { "hasMore": false } }""",
        )
        enqueueJson(201, "")
        val repo = repository()
        repo.refreshFeed()

        val result = repo.setDug("1", dug = true)

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        val cached = repo.observeMessage("1").first()
        assertThat(cached?.dugByMe).isTrue()
        assertThat(cached?.digCount).isEqualTo(5)
    }

    @Test
    fun `deleteMessage removes it from the cache`() = runTest(dispatcher) {
        enqueueJson(
            200,
            """{ "data": [ { "id": "1", "content": "x", "author": { "id": "me" } } ],
                "pagination": { "hasMore": false } }""",
        )
        enqueueJson(200, "")
        val repo = repository()
        repo.refreshFeed()

        val result = repo.deleteMessage("1")

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        assertThat(repo.observeFeed().first()).isEmpty()
    }

    @Test
    fun `postReply caches the reply under the parent and bumps reply count`() = runTest(dispatcher) {
        enqueueJson(
            200,
            """{ "data": [ { "id": "p", "content": "parent", "replyCount": 0 } ],
                "pagination": { "hasMore": false } }""",
        )
        enqueueJson(
            201,
            """{ "message": { "id": "r", "content": "a reply", "author": { "id": "me" } } }""",
        )
        val repo = repository()
        repo.refreshFeed()

        val result = repo.postReply(parentId = "p", content = "a reply")

        assertThat((result as ApiResult.Success).data.parentId).isEqualTo("p")
        val replies = repo.observeReplies("p").first()
        assertThat(replies.map { it.id }).containsExactly("r")
        assertThat(repo.observeMessage("p").first()?.replyCount).isEqualTo(1)
    }

    @Test
    fun `search maps results without touching the feed cache`() = runTest(dispatcher) {
        enqueueJson(
            200,
            """{ "data": [ { "id": "s1", "content": "found it" } ],
                "pagination": { "hasMore": false } }""",
        )
        val repo = repository()

        val result = repo.search("found")

        assertThat((result as ApiResult.Success).data.map { it.id }).containsExactly("s1")
        assertThat(repo.observeFeed().first()).isEmpty()
    }
}
