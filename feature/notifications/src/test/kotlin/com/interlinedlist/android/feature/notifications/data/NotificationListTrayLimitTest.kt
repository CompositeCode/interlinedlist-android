package com.interlinedlist.android.feature.notifications.data

import com.google.common.truth.Truth.assertThat
import com.interlinedlist.android.core.network.api.InterlinedListApi
import com.interlinedlist.android.core.network.preferences.NotificationTrayLimitStore
import com.interlinedlist.android.feature.notifications.data.remote.NotificationsApi
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
 * Issue #35: the in-app notifications list is sized by the account's
 * `notificationTrayLimit` instead of a hard-coded page size.
 *
 * `/help/settings` describes the preference as "How many notifications the bell tray
 * holds before older ones drop off", so the app's list has to hold the same number the
 * web's tray does. `/help/api/notifications` documents the endpoint's own `limit` as
 * 1-50, which comfortably contains the 10-40 the preference is clamped to, so the
 * value can be sent verbatim.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NotificationListTrayLimitTest {

    private val dispatcher = StandardTestDispatcher()
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    private lateinit var server: MockWebServer
    private lateinit var api: NotificationsApi
    private lateinit var userApi: InterlinedListApi
    private lateinit var dao: FakeNotificationDao

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val retrofit = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
        api = retrofit.create(NotificationsApi::class.java)
        userApi = retrofit.create(InterlinedListApi::class.java)
        dao = FakeNotificationDao()
    }

    @After
    fun tearDown() = server.shutdown()

    /** A store already holding [limit], or one that still has to read it from the API. */
    private fun store(limit: Int?) =
        NotificationTrayLimitStore(userApi, json).apply { limit?.let { publish(it) } }

    private fun repository(store: NotificationTrayLimitStore) = DefaultNotificationsRepository(
        api = api,
        notificationDao = dao,
        trayLimitStore = store,
        json = json,
        dispatchers = TestDispatcherProvider(dispatcher),
    )

    private fun enqueuePage(hasMore: Boolean = false, ids: List<String> = listOf("1")) {
        val rows = ids.joinToString(",") { """{ "id": "$it", "type": "follow", "subject": "s$it" }""" }
        server.enqueue(
            MockResponse().setBody("""{ "data": [ $rows ], "pagination": { "hasMore": $hasMore } }"""),
        )
    }

    @Test
    fun `refresh asks for a page the size of the account's tray limit`() = runTest(dispatcher) {
        enqueuePage()

        repository(store(40)).refresh()

        val url = server.takeRequest().requestUrl!!
        assertThat(url.encodedPath).isEqualTo("/api/notifications")
        assertThat(url.queryParameter("limit")).isEqualTo("40")
        assertThat(url.queryParameter("offset")).isEqualTo("0")
    }

    @Test
    fun `loadMore pages by the same limit from the current offset`() = runTest(dispatcher) {
        enqueuePage(hasMore = true)
        enqueuePage(ids = listOf("2"))
        val repo = repository(store(10))

        repo.refresh()
        repo.loadMore(currentCount = 10)

        server.takeRequest()
        val url = server.takeRequest().requestUrl!!
        assertThat(url.queryParameter("limit")).isEqualTo("10")
        assertThat(url.queryParameter("offset")).isEqualTo("10")
    }

    @Test
    fun `the poll's fetch is sized by the limit too, so it never sees less than the tray holds`() =
        runTest(dispatcher) {
            enqueuePage()

            repository(store(35)).fetchLatest()

            assertThat(server.takeRequest().requestUrl!!.queryParameter("limit")).isEqualTo("35")
        }

    @Test
    fun `an account with no stored limit falls back to the documented default of 20`() =
        runTest(dispatcher) {
            // Nothing cached, so `GET /api/user` answers first, then the list.
            server.enqueue(MockResponse().setBody("""{ "user": { "id": "u1" } }"""))
            enqueuePage()

            repository(store(null)).refresh()

            assertThat(server.takeRequest().path).isEqualTo("/api/user")
            assertThat(server.takeRequest().requestUrl!!.queryParameter("limit")).isEqualTo("20")
        }

    @Test
    fun `a change saved in Settings is honoured by the very next refresh`() = runTest(dispatcher) {
        val store = store(20)
        enqueuePage()
        repository(store).refresh()
        assertThat(server.takeRequest().requestUrl!!.queryParameter("limit")).isEqualTo("20")

        // Settings PATCHed a new value and published it to the shared store.
        store.publish(40)
        enqueuePage()
        repository(store).refresh()

        assertThat(server.takeRequest().requestUrl!!.queryParameter("limit")).isEqualTo("40")
    }
}
