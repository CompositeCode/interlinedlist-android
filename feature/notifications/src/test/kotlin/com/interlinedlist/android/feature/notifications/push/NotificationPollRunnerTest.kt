package com.interlinedlist.android.feature.notifications.push

import com.google.common.truth.Truth.assertThat
import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.feature.notifications.data.DefaultNotificationsRepository
import com.interlinedlist.android.feature.notifications.data.FakeNotificationDao
import com.interlinedlist.android.feature.notifications.data.NotificationPreferencesRepository
import com.interlinedlist.android.feature.notifications.data.TestDispatcherProvider
import com.interlinedlist.android.feature.notifications.data.remote.NotificationsApi
import com.interlinedlist.android.feature.notifications.domain.NotificationChannel
import com.interlinedlist.android.feature.notifications.domain.NotificationPreference
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

@OptIn(ExperimentalCoroutinesApi::class)
class NotificationPollRunnerTest {

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

    private fun notificationsRepo() = DefaultNotificationsRepository(
        api = api,
        notificationDao = dao,
        json = json,
        dispatchers = TestDispatcherProvider(dispatcher),
    )

    /** Preferences repo whose GET result is fixed. */
    private fun prefsRepo(
        result: ApiResult<List<NotificationPreference>> = ApiResult.Success(emptyList()),
    ): NotificationPreferencesRepository = object : NotificationPreferencesRepository {
        override suspend fun getPreferences() = result
        override suspend fun updatePreference(preference: NotificationPreference) =
            ApiResult.Success(Unit)
    }

    private fun runner(
        store: LastSeenNotificationStore,
        raiser: SystemNotificationRaiser,
        prefs: NotificationPreferencesRepository = prefsRepo(),
    ) = NotificationPollRunner(
        notificationsRepository = notificationsRepo(),
        preferencesRepository = prefs,
        lastSeenStore = store,
        raiser = raiser,
    )

    private fun enqueue(body: String, code: Int = 200) {
        server.enqueue(MockResponse().setResponseCode(code).setBody(body))
    }

    @Test
    fun `first poll posts nothing and seeds the marker`() = runTest(dispatcher) {
        enqueue(
            """{ "data": [ { "id": "3", "type": "follow", "subject": "c" },
                          { "id": "2", "type": "follow", "subject": "b" } ],
                "pagination": { "hasMore": false } }""",
        )
        val store = FakeLastSeenNotificationStore(initial = null)
        val raiser = RecordingSystemNotificationRaiser()

        val result = runner(store, raiser).run()

        assertThat(result).isEqualTo(NotificationPollRunner.Result.SUCCESS)
        assertThat(raiser.batches).isEmpty()
        assertThat(store.lastSeenId()).isEqualTo("3")
    }

    @Test
    fun `only new items since last-seen are posted and the marker advances`() = runTest(dispatcher) {
        enqueue(
            """{ "data": [ { "id": "5", "type": "mention", "subject": "e" },
                          { "id": "4", "type": "follow", "subject": "d" },
                          { "id": "3", "type": "follow", "subject": "c" } ],
                "pagination": { "hasMore": false } }""",
        )
        val store = FakeLastSeenNotificationStore(initial = "3")
        val raiser = RecordingSystemNotificationRaiser()

        runner(store, raiser).run()

        assertThat(raiser.postedIds).containsExactly("5", "4").inOrder()
        assertThat(store.lastSeenId()).isEqualTo("5")
    }

    @Test
    fun `push-disabled events are filtered out of the tray`() = runTest(dispatcher) {
        enqueue(
            """{ "data": [ { "id": "5", "type": "follow", "subject": "e" },
                          { "id": "4", "type": "mention", "subject": "d" } ],
                "pagination": { "hasMore": false } }""",
        )
        val store = FakeLastSeenNotificationStore(initial = "3")
        val raiser = RecordingSystemNotificationRaiser()
        val prefs = prefsRepo(
            ApiResult.Success(
                listOf(
                    NotificationPreference(
                        key = "follow",
                        label = "follow",
                        description = "",
                        channels = mapOf(NotificationChannel.PUSH to false),
                    ),
                ),
            ),
        )

        runner(store, raiser, prefs).run()

        // "5" (follow) is dropped; "4" (mention, unmapped) defaults to notify.
        assertThat(raiser.postedIds).containsExactly("4")
        // Marker still advances to the true newest.
        assertThat(store.lastSeenId()).isEqualTo("5")
    }

    @Test
    fun `no new items is a no-op post`() = runTest(dispatcher) {
        enqueue(
            """{ "data": [ { "id": "9", "type": "follow", "subject": "i" } ],
                "pagination": { "hasMore": false } }""",
        )
        val store = FakeLastSeenNotificationStore(initial = "9")
        val raiser = RecordingSystemNotificationRaiser()

        runner(store, raiser).run()

        assertThat(raiser.batches).isEmpty()
        assertThat(store.lastSeenId()).isEqualTo("9")
    }

    @Test
    fun `empty page posts nothing and keeps the marker`() = runTest(dispatcher) {
        enqueue("""{ "data": [], "pagination": { "hasMore": false } }""")
        val store = FakeLastSeenNotificationStore(initial = "4")
        val raiser = RecordingSystemNotificationRaiser()

        runner(store, raiser).run()

        assertThat(raiser.batches).isEmpty()
        assertThat(store.lastSeenId()).isEqualTo("4")
    }

    @Test
    fun `fetch failure asks WorkManager to retry`() = runTest(dispatcher) {
        enqueue("""{ "error": "boom" }""", code = 500)
        val store = FakeLastSeenNotificationStore(initial = "4")
        val raiser = RecordingSystemNotificationRaiser()

        val result = runner(store, raiser).run()

        assertThat(result).isEqualTo(NotificationPollRunner.Result.RETRY)
        assertThat(raiser.batches).isEmpty()
        // Marker untouched on failure.
        assertThat(store.lastSeenId()).isEqualTo("4")
    }

    @Test
    fun `preferences failure falls back to default-notify`() = runTest(dispatcher) {
        enqueue(
            """{ "data": [ { "id": "5", "type": "follow", "subject": "e" } ],
                "pagination": { "hasMore": false } }""",
        )
        val store = FakeLastSeenNotificationStore(initial = "3")
        val raiser = RecordingSystemNotificationRaiser()
        val prefs = prefsRepo(
            ApiResult.Failure(com.interlinedlist.android.core.common.result.AppError.Unknown("x")),
        )

        runner(store, raiser, prefs).run()

        // Preferences unavailable -> default notify -> "5" is posted.
        assertThat(raiser.postedIds).containsExactly("5")
        assertThat(store.lastSeenId()).isEqualTo("5")
    }
}
