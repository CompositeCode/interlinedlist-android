package com.interlinedlist.android.feature.notifications.push

import com.google.common.truth.Truth.assertThat
import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.core.network.api.InterlinedListApi
import com.interlinedlist.android.core.network.preferences.NotificationTrayLimitStore
import com.interlinedlist.android.feature.notifications.data.DefaultNotificationsRepository
import com.interlinedlist.android.feature.notifications.data.FakeNotificationDao
import com.interlinedlist.android.feature.notifications.data.NotificationPreferencesRepository
import com.interlinedlist.android.feature.notifications.data.TestDispatcherProvider
import com.interlinedlist.android.feature.notifications.data.remote.NotificationsApi
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

/**
 * Issue #35: the system-tray group is capped by the account's `notificationTrayLimit`
 * rather than by a constant this module chose for itself.
 *
 * Before, [SystemNotificationPoster] collapsed anything over five items into a single
 * summary regardless of what the user had configured. Now the poll resolves the same
 * preference the in-app list uses and hands it to the raiser, so "how many
 * notifications the tray holds" (`/help/settings`) means one thing everywhere.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SystemTrayGroupLimitTest {

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

    // --- the cap the poll hands to the tray ----------------------------------

    @Test
    fun `the poll caps the tray group at the account's limit`() = runTest(dispatcher) {
        enqueuePage("3", "2")
        val raiser = RecordingSystemNotificationRaiser()

        runner(store(12), raiser).run()

        assertThat(raiser.postedIds).containsExactly("3", "2").inOrder()
        assertThat(raiser.maxIndividual).isEqualTo(12)
    }

    @Test
    fun `a different account limit produces a different cap`() = runTest(dispatcher) {
        enqueuePage("3", "2")
        val raiser = RecordingSystemNotificationRaiser()

        runner(store(40), raiser).run()

        assertThat(raiser.maxIndividual).isEqualTo(40)
    }

    @Test
    fun `resolving the cap costs no extra request once the page has been fetched`() =
        runTest(dispatcher) {
            // Only `GET /api/user` and the notifications page are queued: if the runner
            // read the preference twice over the network, the second read would hang.
            server.enqueue(MockResponse().setBody("""{ "user": { "id": "u1", "notificationTrayLimit": 30 } }"""))
            enqueuePage("3", "2")
            val raiser = RecordingSystemNotificationRaiser()

            runner(store(null), raiser).run()

            assertThat(raiser.maxIndividual).isEqualTo(30)
            assertThat(server.requestCount).isEqualTo(2)
        }

    // --- the grouping rule itself --------------------------------------------

    @Test
    fun `a batch within the limit is posted item by item`() {
        assertThat(SystemNotificationPoster.collapsesToSummary(count = 10, maxIndividual = 10))
            .isFalse()
        assertThat(SystemNotificationPoster.collapsesToSummary(count = 9, maxIndividual = 10))
            .isFalse()
    }

    @Test
    fun `a batch over the limit collapses to a single summary`() {
        assertThat(SystemNotificationPoster.collapsesToSummary(count = 11, maxIndividual = 10))
            .isTrue()
    }

    @Test
    fun `the old fixed cap of five no longer decides anything`() {
        // Six items used to collapse; with the account's limit they no longer do.
        assertThat(SystemNotificationPoster.collapsesToSummary(count = 6, maxIndividual = 20))
            .isFalse()
        // ...and a deliberately small tray collapses well before five.
        assertThat(SystemNotificationPoster.collapsesToSummary(count = 3, maxIndividual = 2))
            .isTrue()
    }

    @Test
    fun `a nonsensical cap still surfaces the activity as a summary`() {
        assertThat(SystemNotificationPoster.collapsesToSummary(count = 1, maxIndividual = 0))
            .isFalse()
        assertThat(SystemNotificationPoster.collapsesToSummary(count = 2, maxIndividual = -5))
            .isTrue()
    }

    // --- helpers -------------------------------------------------------------

    private fun store(limit: Int?) =
        NotificationTrayLimitStore(userApi, json).apply { limit?.let { publish(it) } }

    private fun enqueuePage(vararg ids: String) {
        val rows = ids.joinToString(",") { """{ "id": "$it", "type": "follow", "subject": "s$it" }""" }
        server.enqueue(
            MockResponse().setBody("""{ "data": [ $rows ], "pagination": { "hasMore": false } }"""),
        )
    }

    private fun runner(
        trayLimitStore: NotificationTrayLimitStore,
        raiser: RecordingSystemNotificationRaiser,
    ) = NotificationPollRunner(
        notificationsRepository = DefaultNotificationsRepository(
            api = api,
            notificationDao = dao,
            trayLimitStore = trayLimitStore,
            json = json,
            dispatchers = TestDispatcherProvider(dispatcher),
        ),
        preferencesRepository = object : NotificationPreferencesRepository {
            override suspend fun getPreferences(): ApiResult<List<NotificationPreference>> =
                ApiResult.Success(emptyList())

            override suspend fun updatePreference(preference: NotificationPreference) =
                ApiResult.Success(Unit)
        },
        lastSeenStore = FakeLastSeenNotificationStore(initial = "1"),
        trayLimitStore = trayLimitStore,
        raiser = raiser,
    )
}
