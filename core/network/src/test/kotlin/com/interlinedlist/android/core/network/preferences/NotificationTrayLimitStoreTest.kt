package com.interlinedlist.android.core.network.preferences

import com.google.common.truth.Truth.assertThat
import com.interlinedlist.android.core.network.api.InterlinedListApi
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit

/**
 * The shared `notificationTrayLimit` accessor: the one number both the in-app
 * notifications list and the system-tray group are sized by.
 *
 * The bounds come from the help centre, which publishes them twice — `/help/settings`
 * ("The default is 20 and you can set any value from 10 to 40") and
 * `/help/api/notifications` ("up to the user's configured tray limit (default 20,
 * clamped to 10-40)") — and the default matches the live `GET /api/user`.
 */
class NotificationTrayLimitStoreTest {

    private lateinit var server: MockWebServer
    private lateinit var store: NotificationTrayLimitStore

    // Mirrors the production Json (see NetworkModule).
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
    }

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        store = newStore()
    }

    @After
    fun tearDown() = server.shutdown()

    /** A store with an empty cache, pointed at the test server. */
    private fun newStore(): NotificationTrayLimitStore {
        val api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(InterlinedListApi::class.java)
        return NotificationTrayLimitStore(api, json)
    }

    private fun enqueueUser(trayLimit: Int?) {
        val field = trayLimit?.let { """, "notificationTrayLimit": $it""" } ?: ""
        server.enqueue(
            MockResponse().setBody("""{ "user": { "id": "u1", "username": "me"$field } }"""),
        )
    }

    @Test
    fun `current reads the limit from GET api user`() = runBlocking {
        enqueueUser(40)

        assertThat(store.current()).isEqualTo(40)
        assertThat(server.takeRequest().path).isEqualTo("/api/user")
    }

    @Test
    fun `an absent limit falls back to the documented default`() = runBlocking {
        enqueueUser(null)

        assertThat(store.current()).isEqualTo(20)
    }

    @Test
    fun `the limit is read once and then served from the cache`() = runBlocking {
        enqueueUser(30)

        assertThat(store.current()).isEqualTo(30)
        assertThat(store.current()).isEqualTo(30)

        // A second GET would have no queued response; only one request was made.
        assertThat(server.requestCount).isEqualTo(1)
    }

    @Test
    fun `a failed read falls back to the default without caching it`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500).setBody("""{ "error": "boom" }"""))
        enqueueUser(35)

        // The tray still has a size to work with...
        assertThat(store.current()).isEqualTo(20)
        // ...and the blip is not pinned for the rest of the process.
        assertThat(store.current()).isEqualTo(35)
    }

    @Test
    fun `a stored value outside the server's range is clamped`() = runBlocking {
        enqueueUser(400)
        assertThat(store.current()).isEqualTo(40)

        // A fresh store, so the clamp is exercised on the way in rather than from cache.
        enqueueUser(1)
        assertThat(newStore().current()).isEqualTo(10)
    }

    @Test
    fun `a published limit takes effect without a request`() = runBlocking {
        assertThat(store.publish(25)).isEqualTo(25)

        assertThat(store.current()).isEqualTo(25)
        assertThat(server.requestCount).isEqualTo(0)
    }

    @Test
    fun `publishing an absent limit resolves to the default`() {
        assertThat(store.publish(null)).isEqualTo(20)
    }

    @Test
    fun `the documented bounds match the help centre`() {
        assertThat(NotificationTrayLimitStore.DEFAULT).isEqualTo(20)
        assertThat(NotificationTrayLimitStore.RANGE).isEqualTo(10..40)
    }
}
