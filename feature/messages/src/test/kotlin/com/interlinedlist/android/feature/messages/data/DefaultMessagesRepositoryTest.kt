package com.interlinedlist.android.feature.messages.data

import com.google.common.truth.Truth.assertThat
import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.core.common.result.AppError
import com.interlinedlist.android.feature.messages.data.remote.MessagesApi
import com.interlinedlist.android.feature.messages.domain.ReportReason
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
        // The create endpoint returns the new message under `data` (and keys the
        // author sub-object as `user`); `message` is a status string.
        enqueueJson(
            201,
            """{ "message": "Message created successfully",
                "data": { "id": "new", "content": "brand new",
                    "user": { "id": "me", "username": "me" } } }""",
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
            """{ "message": "Message created successfully",
                "data": { "id": "r", "content": "a reply", "user": { "id": "me" } } }""",
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

    @Test
    fun `uploadImage returns the hosted url and posts to the images endpoint`() = runTest(dispatcher) {
        enqueueJson(201, """{ "url": "https://cdn/pic.png" }""")
        val repo = repository()

        val result = repo.uploadImage("bytes".toByteArray(), "pic.png", "image/png")

        assertThat((result as ApiResult.Success).data).isEqualTo("https://cdn/pic.png")
        assertThat(server.takeRequest().path).contains("api/messages/images/upload")
    }

    @Test
    fun `uploadVideo falls back to the videoUrl field`() = runTest(dispatcher) {
        enqueueJson(201, """{ "videoUrl": "https://cdn/clip.mp4" }""")
        val repo = repository()

        val result = repo.uploadVideo("bytes".toByteArray(), "clip.mp4", "video/mp4")

        assertThat((result as ApiResult.Success).data).isEqualTo("https://cdn/clip.mp4")
        assertThat(server.takeRequest().path).contains("api/messages/videos/upload")
    }

    @Test
    fun `upload with no url in the response is a failure`() = runTest(dispatcher) {
        enqueueJson(201, """{ }""")
        val repo = repository()

        val result = repo.uploadImage("bytes".toByteArray(), "pic.png", "image/png")

        assertThat(result).isInstanceOf(ApiResult.Failure::class.java)
    }

    @Test
    fun `createMessage with media sends the attached urls`() = runTest(dispatcher) {
        enqueueJson(
            201,
            """{ "message": "Message created successfully",
                "data": { "id": "m1", "content": "with media",
                    "imageUrls": ["https://cdn/a.png"] } }""",
        )
        val repo = repository()

        val result = repo.createMessage(
            content = "with media",
            imageUrls = listOf("https://cdn/a.png"),
        )

        assertThat((result as ApiResult.Success).data.imageUrls).containsExactly("https://cdn/a.png")
        val body = server.takeRequest().body.readUtf8()
        assertThat(body).contains("https://cdn/a.png")
        assertThat(body).contains("imageUrls")
    }

    @Test
    fun `createMessage scheduled is cached in the scheduled view not the feed`() = runTest(dispatcher) {
        enqueueJson(
            201,
            """{ "message": "Message created successfully",
                "data": { "id": "sch1", "content": "later",
                    "scheduledAt": "2026-07-19T09:00:00Z" } }""",
        )
        val repo = repository()

        val result = repo.createMessage(content = "later", scheduledAt = "2026-07-19T09:00:00Z")

        assertThat((result as ApiResult.Success).data.scheduledAt).isEqualTo("2026-07-19T09:00:00Z")
        assertThat(repo.observeFeed().first()).isEmpty()
        assertThat(repo.observeScheduled().first().map { it.id }).containsExactly("sch1")
    }

    @Test
    fun `refreshScheduled caches the scheduled messages`() = runTest(dispatcher) {
        enqueueJson(
            200,
            """{ "data": [ { "id": "s1", "content": "one", "scheduledAt": "2026-07-19T09:00:00Z" },
                          { "id": "s2", "content": "two", "scheduledAt": "2026-07-20T09:00:00Z" } ] }""",
        )
        val repo = repository()

        val result = repo.refreshScheduled()

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        assertThat(repo.observeScheduled().first().map { it.id }).containsExactly("s1", "s2").inOrder()
    }

    @Test
    fun `cancelScheduled removes the scheduled message from the cache`() = runTest(dispatcher) {
        enqueueJson(
            200,
            """{ "data": [ { "id": "s1", "content": "one", "scheduledAt": "2026-07-19T09:00:00Z" } ] }""",
        )
        enqueueJson(200, "")
        val repo = repository()
        repo.refreshScheduled()

        val result = repo.cancelScheduled("s1")

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        assertThat(repo.observeScheduled().first()).isEmpty()
    }

    @Test
    fun `report posts the reason and detail`() = runTest(dispatcher) {
        enqueueJson(201, "")
        val repo = repository()

        val result = repo.report("m1", ReportReason.SPAM, detail = "obvious spam")

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        val request = server.takeRequest()
        assertThat(request.path).contains("api/messages/m1/report")
        val body = request.body.readUtf8()
        assertThat(body).contains("\"reason\":\"spam\"")
        assertThat(body).contains("obvious spam")
    }

    @Test
    fun `report omits blank detail`() = runTest(dispatcher) {
        enqueueJson(201, "")
        val repo = repository()

        repo.report("m1", ReportReason.OTHER, detail = "  ")

        val body = server.takeRequest().body.readUtf8()
        assertThat(body).doesNotContain("detail")
    }

    @Test
    fun `editMessage PATCHes the content and updates the cached message`() = runTest(dispatcher) {
        enqueueJson(
            200,
            """{ "data": [ { "id": "1", "content": "original", "author": { "id": "me", "username": "me" } } ],
                "pagination": { "hasMore": false } }""",
        )
        enqueueJson(200, "") // PATCH response body is not modelled; a 2xx is success.
        val repo = repository()
        repo.refreshFeed()

        val result = repo.editMessage("1", content = "edited body")

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        assertThat((result as ApiResult.Success).data.content).isEqualTo("edited body")
        // The cache reflects the new content and now carries an "edited" marker.
        val cached = repo.observeMessage("1").first()
        assertThat(cached?.content).isEqualTo("edited body")
        assertThat(cached?.isEdited).isTrue()

        server.takeRequest() // the refresh GET
        val patch = server.takeRequest()
        assertThat(patch.method).isEqualTo("PATCH")
        assertThat(patch.path).contains("api/messages/1")
        assertThat(patch.body.readUtf8()).contains("\"content\":\"edited body\"")
    }

    @Test
    fun `editMessage rolls back the cached content on failure`() = runTest(dispatcher) {
        enqueueJson(
            200,
            """{ "data": [ { "id": "1", "content": "original", "author": { "id": "me", "username": "me" } } ],
                "pagination": { "hasMore": false } }""",
        )
        enqueueJson(500, """{ "error": "boom" }""")
        val repo = repository()
        repo.refreshFeed()

        val result = repo.editMessage("1", content = "will not stick")

        assertThat(result).isInstanceOf(ApiResult.Failure::class.java)
        val cached = repo.observeMessage("1").first()
        assertThat(cached?.content).isEqualTo("original")
        assertThat(cached?.isEdited).isFalse()
    }

    @Test
    fun `blockUser posts and hides the author's messages from the feed`() = runTest(dispatcher) {
        enqueueJson(
            200,
            """{ "data": [
                  { "id": "1", "content": "by amy", "author": { "id": "a", "username": "amy" } },
                  { "id": "2", "content": "by bob", "author": { "id": "b", "username": "bob" } }
                ], "pagination": { "hasMore": false } }""",
        )
        enqueueJson(201, "")
        val repo = repository()
        repo.refreshFeed()

        val result = repo.blockUser("amy")

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        val request = server.let { it.takeRequest(); it.takeRequest() }
        assertThat(request.method).isEqualTo("POST")
        assertThat(request.path).contains("api/users/amy/block")
        // Amy's message is gone; bob's remains.
        assertThat(repo.observeFeed().first().map { it.id }).containsExactly("2")
    }

    @Test
    fun `muteUser posts and hides the author's messages from the feed`() = runTest(dispatcher) {
        enqueueJson(
            200,
            """{ "data": [
                  { "id": "1", "content": "by amy", "author": { "id": "a", "username": "amy" } }
                ], "pagination": { "hasMore": false } }""",
        )
        enqueueJson(201, "")
        val repo = repository()
        repo.refreshFeed()

        val result = repo.muteUser("amy")

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        server.takeRequest()
        assertThat(server.takeRequest().path).contains("api/users/amy/mute")
        assertThat(repo.observeFeed().first()).isEmpty()
    }

    @Test
    fun `blockUser failure leaves the feed intact`() = runTest(dispatcher) {
        enqueueJson(
            200,
            """{ "data": [
                  { "id": "1", "content": "by amy", "author": { "id": "a", "username": "amy" } }
                ], "pagination": { "hasMore": false } }""",
        )
        enqueueJson(500, """{ "error": "boom" }""")
        val repo = repository()
        repo.refreshFeed()

        val result = repo.blockUser("amy")

        assertThat(result).isInstanceOf(ApiResult.Failure::class.java)
        assertThat(repo.observeFeed().first().map { it.id }).containsExactly("1")
    }

    @Test
    fun `reportUser posts the reason and detail to the user report endpoint`() = runTest(dispatcher) {
        enqueueJson(201, "")
        val repo = repository()

        val result = repo.reportUser("amy", ReportReason.HARASSMENT, detail = "abusive dms")

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        val request = server.takeRequest()
        assertThat(request.path).contains("api/users/amy/report")
        val body = request.body.readUtf8()
        assertThat(body).contains("\"reason\":\"harassment\"")
        assertThat(body).contains("abusive dms")
    }

    @Test
    fun `reportUser omits blank detail`() = runTest(dispatcher) {
        enqueueJson(201, "")
        val repo = repository()

        repo.reportUser("amy", ReportReason.OTHER, detail = "   ")

        val body = server.takeRequest().body.readUtf8()
        assertThat(body).doesNotContain("detail")
    }

    @Test
    fun `fetchMetadata attaches a link preview to the cached message`() = runTest(dispatcher) {
        enqueueJson(
            200,
            """{ "data": [ { "id": "m1", "content": "see https://example.com" } ],
                "pagination": { "hasMore": false } }""",
        )
        enqueueJson(
            201,
            """{ "linkMetadata": { "url": "https://example.com", "title": "Example",
                "description": "A page" } }""",
        )
        val repo = repository()
        repo.refreshFeed()

        val result = repo.fetchMetadata("m1")

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        val cached = repo.observeMessage("m1").first()
        assertThat(cached?.linkPreview?.title).isEqualTo("Example")
    }
}
