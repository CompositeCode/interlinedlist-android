package com.interlinedlist.android.feature.notifications.data

import com.google.common.truth.Truth.assertThat
import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.core.common.result.AppError
import com.interlinedlist.android.feature.notifications.data.remote.NotificationsApi
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
class DefaultNotificationsRepositoryTest {

    private val dispatcher = StandardTestDispatcher()
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    private lateinit var server: MockWebServer
    private lateinit var api: NotificationsApi
    private lateinit var dao: FakeNotificationDao

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val contentType = "application/json".toMediaType()
        api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
            .create(NotificationsApi::class.java)
        dao = FakeNotificationDao()
    }

    @After
    fun tearDown() = server.shutdown()

    private fun repository() = DefaultNotificationsRepository(
        api = api,
        notificationDao = dao,
        json = json,
        dispatchers = TestDispatcherProvider(dispatcher),
    )

    private fun enqueueJson(code: Int, body: String) {
        server.enqueue(MockResponse().setResponseCode(code).setBody(body))
    }

    @Test
    fun `refresh caches notifications and reports hasMore`() = runTest(dispatcher) {
        enqueueJson(
            200,
            """
            {
              "data": [
                { "id": "1", "type": "follow", "subject": "Amy followed you", "read": false },
                { "id": "2", "type": "reply", "subject": "Ben replied", "read": true }
              ],
              "pagination": { "total": 5, "limit": 20, "offset": 0, "hasMore": true }
            }
            """.trimIndent(),
        )
        val repo = repository()

        val result = repo.refresh()

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        assertThat((result as ApiResult.Success).data).isTrue() // hasMore
        val cached = repo.observeNotifications().first()
        assertThat(cached.map { it.id }).containsExactly("1", "2").inOrder()
        assertThat(repo.observeUnreadCount().first()).isEqualTo(1)
    }

    @Test
    fun `refresh maps a 403 subscription error`() = runTest(dispatcher) {
        enqueueJson(403, """{ "error": "An active subscription is required." }""")
        val repo = repository()

        val result = repo.refresh()

        assertThat(result).isInstanceOf(ApiResult.Failure::class.java)
        assertThat((result as ApiResult.Failure).error)
            .isInstanceOf(AppError.SubscriptionRequired::class.java)
    }

    @Test
    fun `loadMore appends after existing rows and carries the offset`() = runTest(dispatcher) {
        enqueueJson(
            200,
            """{ "data": [ { "id": "1", "subject": "a" } ],
                "pagination": { "hasMore": true } }""",
        )
        enqueueJson(
            200,
            """{ "data": [ { "id": "2", "subject": "b" } ],
                "pagination": { "hasMore": false } }""",
        )
        val repo = repository()
        repo.refresh()

        val more = repo.loadMore(currentCount = 1)

        assertThat((more as ApiResult.Success).data).isFalse() // no more pages
        val ids = repo.observeNotifications().first().map { it.id }
        assertThat(ids).containsExactly("1", "2").inOrder()

        server.takeRequest() // first (refresh) request
        val secondPath = server.takeRequest().path
        assertThat(secondPath).contains("offset=1")
    }

    @Test
    fun `markRead flips the cached row and hits the read endpoint`() = runTest(dispatcher) {
        enqueueJson(
            200,
            """{ "data": [ { "id": "1", "subject": "x", "read": false } ],
                "pagination": { "hasMore": false } }""",
        )
        enqueueJson(200, "")
        val repo = repository()
        repo.refresh()

        val result = repo.markRead("1")

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        assertThat(repo.observeNotifications().first().first().read).isTrue()
        assertThat(repo.observeUnreadCount().first()).isEqualTo(0)
        server.takeRequest() // refresh
        val request = server.takeRequest()
        assertThat(request.method).isEqualTo("PATCH")
        assertThat(request.path).contains("api/notifications/1/read")
    }

    @Test
    fun `markRead rolls back the cache on failure`() = runTest(dispatcher) {
        enqueueJson(
            200,
            """{ "data": [ { "id": "1", "subject": "x", "read": false } ],
                "pagination": { "hasMore": false } }""",
        )
        enqueueJson(500, """{ "error": "boom" }""")
        val repo = repository()
        repo.refresh()

        val result = repo.markRead("1")

        assertThat(result).isInstanceOf(ApiResult.Failure::class.java)
        // Rolled back to unread.
        assertThat(repo.observeNotifications().first().first().read).isFalse()
        assertThat(repo.observeUnreadCount().first()).isEqualTo(1)
    }

    @Test
    fun `markAllRead marks every cached row read`() = runTest(dispatcher) {
        enqueueJson(
            200,
            """{ "data": [ { "id": "1", "subject": "x", "read": false },
                          { "id": "2", "subject": "y", "read": false } ],
                "pagination": { "hasMore": false } }""",
        )
        enqueueJson(201, "")
        val repo = repository()
        repo.refresh()

        val result = repo.markAllRead()

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        assertThat(repo.observeUnreadCount().first()).isEqualTo(0)
        server.takeRequest() // refresh
        val request = server.takeRequest()
        assertThat(request.method).isEqualTo("POST")
        assertThat(request.path).contains("api/notifications/mark-all-read")
    }

    @Test
    fun `markAllRead restores read-states on failure`() = runTest(dispatcher) {
        enqueueJson(
            200,
            """{ "data": [ { "id": "1", "subject": "x", "read": false },
                          { "id": "2", "subject": "y", "read": true } ],
                "pagination": { "hasMore": false } }""",
        )
        enqueueJson(500, """{ "error": "nope" }""")
        val repo = repository()
        repo.refresh()

        val result = repo.markAllRead()

        assertThat(result).isInstanceOf(ApiResult.Failure::class.java)
        // The originally-unread row is unread again; the count is restored.
        assertThat(repo.observeUnreadCount().first()).isEqualTo(1)
    }

    @Test
    fun `dismiss removes the notification from the cache`() = runTest(dispatcher) {
        enqueueJson(
            200,
            """{ "data": [ { "id": "1", "subject": "x" }, { "id": "2", "subject": "y" } ],
                "pagination": { "hasMore": false } }""",
        )
        enqueueJson(200, "")
        val repo = repository()
        repo.refresh()

        val result = repo.dismiss("1")

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        assertThat(repo.observeNotifications().first().map { it.id }).containsExactly("2")
        server.takeRequest() // refresh
        val request = server.takeRequest()
        assertThat(request.method).isEqualTo("DELETE")
        assertThat(request.path).contains("api/notifications/1")
    }

    @Test
    fun `dismiss restores the row on failure`() = runTest(dispatcher) {
        enqueueJson(
            200,
            """{ "data": [ { "id": "1", "subject": "x" } ],
                "pagination": { "hasMore": false } }""",
        )
        enqueueJson(404, """{ "error": "gone" }""")
        val repo = repository()
        repo.refresh()

        val result = repo.dismiss("1")

        assertThat(result).isInstanceOf(ApiResult.Failure::class.java)
        assertThat(repo.observeNotifications().first().map { it.id }).containsExactly("1")
    }
}
