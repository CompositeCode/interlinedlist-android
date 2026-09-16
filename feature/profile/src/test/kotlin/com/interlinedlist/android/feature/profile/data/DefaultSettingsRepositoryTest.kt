package com.interlinedlist.android.feature.profile.data

import com.google.common.truth.Truth.assertThat
import com.interlinedlist.android.core.common.dispatcher.DispatcherProvider
import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.core.common.result.AppError
import com.interlinedlist.android.core.network.api.InterlinedListApi
import com.interlinedlist.android.core.network.preferences.NotificationTrayLimitStore
import com.interlinedlist.android.feature.profile.data.remote.ProfileApi
import com.interlinedlist.android.feature.profile.domain.Coordinates
import com.interlinedlist.android.feature.profile.domain.LocationUpdate
import com.interlinedlist.android.feature.profile.domain.UserSettingsUpdate
import com.interlinedlist.android.feature.profile.domain.ViewingPreference
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultSettingsRepositoryTest {

    private lateinit var server: MockWebServer
    private lateinit var api: ProfileApi
    private lateinit var repository: DefaultSettingsRepository
    private lateinit var trayLimitStore: NotificationTrayLimitStore

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    private val testDispatcher = StandardTestDispatcher()
    private val dispatchers = object : DispatcherProvider {
        override val io: CoroutineDispatcher = testDispatcher
        override val default: CoroutineDispatcher = testDispatcher
        override val main: CoroutineDispatcher = testDispatcher
    }

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val retrofit = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(OkHttpClient.Builder().build())
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
        api = retrofit.create(ProfileApi::class.java)
        trayLimitStore =
            NotificationTrayLimitStore(retrofit.create(InterlinedListApi::class.java), json)
        // The theme half of the settings surface has its own suite
        // (SettingsThemeSyncTest); here it only has to exist and not interfere.
        repository = DefaultSettingsRepository(
            api,
            trayLimitStore,
            FakeThemeSettingsStore(),
            json,
            dispatchers,
        )
    }

    @After
    fun tearDown() = server.shutdown()

    private fun enqueueUser(
        viewingPreference: String = "all_messages",
        showPreviews: Boolean = true,
        maxMessageLength: Int = 666,
        defaultPubliclyVisible: Boolean = true,
        messagesPerPage: Int = 20,
        showAdvancedPostSettings: Boolean = false,
        isPrivateAccount: Boolean = false,
        notificationTrayLimit: Int = 25,
        latitude: String = "45.52",
        longitude: String = "-122.68",
    ) = server.enqueue(
        MockResponse().setResponseCode(200).setBody(
            """
            {
              "user": {
                "id": "u1",
                "username": "adron",
                "displayName": "Adron Hall",
                "theme": "dark",
                "maxMessageLength": $maxMessageLength,
                "defaultPubliclyVisible": $defaultPubliclyVisible,
                "messagesPerPage": $messagesPerPage,
                "viewingPreference": "$viewingPreference",
                "showPreviews": $showPreviews,
                "showAdvancedPostSettings": $showAdvancedPostSettings,
                "latitude": $latitude,
                "longitude": $longitude,
                "isPrivateAccount": $isPrivateAccount,
                "githubDefaultRepo": "adron/notes",
                "notificationTrayLimit": $notificationTrayLimit
              }
            }
            """.trimIndent(),
        ),
    )

    /** Reads the body of the next recorded request as a JSON object. */
    private fun MockWebServer.takeJsonBody() =
        json.parseToJsonElement(takeRequest().body.readUtf8()) as JsonObject

    @Test
    fun `refresh reads every preference field from the user endpoint`() = runTest(testDispatcher) {
        enqueueUser(viewingPreference = "following_only", showPreviews = false)

        val result = repository.refresh()

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        val settings = (result as ApiResult.Success).data
        assertThat(settings.viewingPreference).isEqualTo(ViewingPreference.FOLLOWING)
        assertThat(settings.showPreviews).isFalse()
        assertThat(settings.theme).isEqualTo("dark")
        assertThat(settings.maxMessageLength).isEqualTo(666)
        assertThat(settings.defaultPubliclyVisible).isTrue()
        assertThat(settings.messagesPerPage).isEqualTo(20)
        assertThat(settings.showAdvancedPostSettings).isFalse()
        assertThat(settings.latitude).isEqualTo(45.52)
        assertThat(settings.longitude).isEqualTo(-122.68)
        assertThat(settings.isPrivateAccount).isFalse()
        assertThat(settings.githubDefaultRepo).isEqualTo("adron/notes")
        assertThat(settings.notificationTrayLimit).isEqualTo(25)

        val recorded = server.takeRequest()
        assertThat(recorded.method).isEqualTo("GET")
        assertThat(recorded.path).isEqualTo("/api/user")
    }

    @Test
    fun `refresh publishes the settings to observers`() = runTest(testDispatcher) {
        assertThat(repository.observeSettings().first()).isNull()
        enqueueUser(viewingPreference = "followers_only")

        repository.refresh()

        assertThat(repository.observeSettings().first()?.viewingPreference)
            .isEqualTo(ViewingPreference.FOLLOWERS)
    }

    @Test
    fun `update sends only the touched field so a partial PATCH cannot clobber the rest`() =
        runTest(testDispatcher) {
            enqueueUser(viewingPreference = "my_messages")

            repository.update(UserSettingsUpdate(viewingPreference = ViewingPreference.MINE))

            val recorded = server.takeRequest()
            assertThat(recorded.method).isEqualTo("PATCH")
            assertThat(recorded.path).isEqualTo("/api/user/update")
            val body = json.parseToJsonElement(recorded.body.readUtf8()) as JsonObject
            assertThat(body.keys).containsExactly("viewingPreference")
            assertThat(body.getValue("viewingPreference").jsonPrimitive.content)
                .isEqualTo("my_messages")
        }

    @Test
    fun `viewingPreference round-trips through the update call`() = runTest(testDispatcher) {
        enqueueUser(viewingPreference = "followers_only")

        val result = repository.update(UserSettingsUpdate(viewingPreference = ViewingPreference.FOLLOWERS))

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        assertThat((result as ApiResult.Success).data.viewingPreference)
            .isEqualTo(ViewingPreference.FOLLOWERS)
        assertThat(server.takeJsonBody().getValue("viewingPreference").jsonPrimitive.content)
            .isEqualTo("followers_only")
        assertThat(repository.observeSettings().first()?.viewingPreference)
            .isEqualTo(ViewingPreference.FOLLOWERS)
    }

    @Test
    fun `showPreviews round-trips through the update call`() = runTest(testDispatcher) {
        enqueueUser(showPreviews = false)

        val result = repository.update(UserSettingsUpdate(showPreviews = false))

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        assertThat((result as ApiResult.Success).data.showPreviews).isFalse()
        val body = server.takeJsonBody()
        assertThat(body.keys).containsExactly("showPreviews")
        assertThat(body.getValue("showPreviews").jsonPrimitive.content).isEqualTo("false")
        assertThat(repository.observeSettings().first()?.showPreviews).isFalse()
    }

    @Test
    fun `update re-reads the user when the server echoes a thin body`() = runTest(testDispatcher) {
        // PATCH answers without a user...
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{ "message": "User updated successfully" }"""))
        // ...so the repository falls back to GET /api/user.
        enqueueUser(viewingPreference = "my_messages")

        val result = repository.update(UserSettingsUpdate(viewingPreference = ViewingPreference.MINE))

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        assertThat((result as ApiResult.Success).data.viewingPreference).isEqualTo(ViewingPreference.MINE)
        assertThat(server.takeRequest().path).isEqualTo("/api/user/update")
        assertThat(server.takeRequest().path).isEqualTo("/api/user")
    }

    @Test
    fun `update failure surfaces the error and leaves the cached settings alone`() =
        runTest(testDispatcher) {
            enqueueUser(showPreviews = true)
            repository.refresh()
            server.takeRequest()

            server.enqueue(MockResponse().setResponseCode(500).setBody("""{ "error": "boom" }"""))
            val result = repository.update(UserSettingsUpdate(showPreviews = false))

            assertThat(result).isInstanceOf(ApiResult.Failure::class.java)
            assertThat((result as ApiResult.Failure).error).isInstanceOf(AppError.Server::class.java)
            assertThat(repository.observeSettings().first()?.showPreviews).isTrue()
        }

    @Test
    fun `refresh tolerates a user without preference fields by falling back to defaults`() =
        runTest(testDispatcher) {
            server.enqueue(
                MockResponse().setResponseCode(200)
                    .setBody("""{ "user": { "id": "u1", "username": "adron" } }"""),
            )

            val result = repository.refresh()

            val settings = (result as ApiResult.Success).data
            assertThat(settings.viewingPreference).isEqualTo(ViewingPreference.ALL)
            assertThat(settings.showPreviews).isTrue()
            assertThat(settings.notificationTrayLimit).isNull()
        }

    // --- Message preferences (issue #32) -------------------------------------
    // Each of the four saves alone, and the two numeric ones go out as JSON
    // numbers rather than the strings the auto-generated spec claims.

    @Test
    fun `defaultPubliclyVisible PATCHes alone as a JSON boolean`() = runTest(testDispatcher) {
        enqueueUser(defaultPubliclyVisible = false)

        val result = repository.update(UserSettingsUpdate(defaultPubliclyVisible = false))

        val body = server.takeJsonBody()
        assertThat(body.keys).containsExactly("defaultPubliclyVisible")
        val sent = body.getValue("defaultPubliclyVisible").jsonPrimitive
        assertThat(sent.isString).isFalse()
        assertThat(sent.booleanOrNull).isFalse()
        assertThat((result as ApiResult.Success).data.defaultPubliclyVisible).isFalse()
        assertThat(repository.observeSettings().first()?.defaultPubliclyVisible).isFalse()
    }

    @Test
    fun `showAdvancedPostSettings PATCHes alone as a JSON boolean`() = runTest(testDispatcher) {
        enqueueUser(showAdvancedPostSettings = true)

        val result = repository.update(UserSettingsUpdate(showAdvancedPostSettings = true))

        val body = server.takeJsonBody()
        assertThat(body.keys).containsExactly("showAdvancedPostSettings")
        val sent = body.getValue("showAdvancedPostSettings").jsonPrimitive
        assertThat(sent.isString).isFalse()
        assertThat(sent.booleanOrNull).isTrue()
        assertThat((result as ApiResult.Success).data.showAdvancedPostSettings).isTrue()
    }

    @Test
    fun `maxMessageLength PATCHes alone as a JSON number`() = runTest(testDispatcher) {
        enqueueUser(maxMessageLength = 1000)

        val result = repository.update(UserSettingsUpdate(maxMessageLength = 1000))

        val body = server.takeJsonBody()
        assertThat(body.keys).containsExactly("maxMessageLength")
        val sent = body.getValue("maxMessageLength").jsonPrimitive
        assertThat(sent.isString).isFalse()
        assertThat(sent.intOrNull).isEqualTo(1000)
        assertThat((result as ApiResult.Success).data.maxMessageLength).isEqualTo(1000)
        assertThat(repository.observeSettings().first()?.maxMessageLength).isEqualTo(1000)
    }

    @Test
    fun `messagesPerPage PATCHes alone as a JSON number`() = runTest(testDispatcher) {
        enqueueUser(messagesPerPage = 30)

        val result = repository.update(UserSettingsUpdate(messagesPerPage = 30))

        val body = server.takeJsonBody()
        assertThat(body.keys).containsExactly("messagesPerPage")
        val sent = body.getValue("messagesPerPage").jsonPrimitive
        assertThat(sent.isString).isFalse()
        assertThat(sent.intOrNull).isEqualTo(30)
        assertThat((result as ApiResult.Success).data.messagesPerPage).isEqualTo(30)
    }

    @Test
    fun `a rejected numeric save leaves the cached value untouched`() = runTest(testDispatcher) {
        enqueueUser(maxMessageLength = 666)
        repository.refresh()
        server.takeRequest()

        server.enqueue(
            MockResponse().setResponseCode(400)
                .setBody("""{ "error": "maxMessageLength out of range", "code": "bad_request" }"""),
        )
        val result = repository.update(UserSettingsUpdate(maxMessageLength = 9_999))

        assertThat(result).isInstanceOf(ApiResult.Failure::class.java)
        assertThat(repository.observeSettings().first()?.maxMessageLength).isEqualTo(666)
    }

    // --- Private account (issue #34) -----------------------------------------

    @Test
    fun `isPrivateAccount PATCHes alone as a JSON boolean`() = runTest(testDispatcher) {
        enqueueUser(isPrivateAccount = true)

        val result = repository.update(UserSettingsUpdate(isPrivateAccount = true))

        val body = server.takeJsonBody()
        assertThat(body.keys).containsExactly("isPrivateAccount")
        val sent = body.getValue("isPrivateAccount").jsonPrimitive
        assertThat(sent.isString).isFalse()
        assertThat(sent.booleanOrNull).isTrue()
        assertThat((result as ApiResult.Success).data.isPrivateAccount).isTrue()
        assertThat(repository.observeSettings().first()?.isPrivateAccount).isTrue()
    }

    @Test
    fun `going public again PATCHes isPrivateAccount alone`() = runTest(testDispatcher) {
        enqueueUser(isPrivateAccount = false)

        val result = repository.update(UserSettingsUpdate(isPrivateAccount = false))

        val body = server.takeJsonBody()
        assertThat(body.keys).containsExactly("isPrivateAccount")
        assertThat(body.getValue("isPrivateAccount").jsonPrimitive.booleanOrNull).isFalse()
        assertThat((result as ApiResult.Success).data.isPrivateAccount).isFalse()
    }

    @Test
    fun `a rejected private-account save leaves the cached value untouched`() =
        runTest(testDispatcher) {
            enqueueUser(isPrivateAccount = false)
            repository.refresh()
            server.takeRequest()

            server.enqueue(
                MockResponse().setResponseCode(500).setBody("""{ "error": "boom" }"""),
            )
            val result = repository.update(UserSettingsUpdate(isPrivateAccount = true))

            assertThat(result).isInstanceOf(ApiResult.Failure::class.java)
            assertThat(repository.observeSettings().first()?.isPrivateAccount).isFalse()
        }

    // --- Notification tray limit (issue #35) ---------------------------------
    // The one preference a *different* feature module reads, so it has to reach the
    // shared :core:network accessor as well as the settings cache.

    @Test
    fun `notificationTrayLimit PATCHes alone as a JSON number`() = runTest(testDispatcher) {
        enqueueUser(notificationTrayLimit = 40)

        val result = repository.update(UserSettingsUpdate(notificationTrayLimit = 40))

        val body = server.takeJsonBody()
        assertThat(body.keys).containsExactly("notificationTrayLimit")
        val sent = body.getValue("notificationTrayLimit").jsonPrimitive
        assertThat(sent.isString).isFalse()
        assertThat(sent.intOrNull).isEqualTo(40)
        assertThat((result as ApiResult.Success).data.notificationTrayLimit).isEqualTo(40)
        assertThat(repository.observeSettings().first()?.notificationTrayLimit).isEqualTo(40)
    }

    @Test
    fun `a saved tray limit reaches the shared store the notifications feature reads`() =
        runTest(testDispatcher) {
            enqueueUser(notificationTrayLimit = 40)

            repository.update(UserSettingsUpdate(notificationTrayLimit = 40))

            // No further response is queued: the store answers from what was published,
            // so :feature:notifications sees the change without another GET /api/user.
            assertThat(trayLimitStore.current()).isEqualTo(40)
        }

    @Test
    fun `a refresh republishes the tray limit to the shared store`() = runTest(testDispatcher) {
        enqueueUser(notificationTrayLimit = 10)

        repository.refresh()

        assertThat(trayLimitStore.current()).isEqualTo(10)
    }

    @Test
    fun `an account without a stored tray limit publishes the documented default`() =
        runTest(testDispatcher) {
            server.enqueue(
                MockResponse().setResponseCode(200)
                    .setBody("""{ "user": { "id": "u1", "username": "adron" } }"""),
            )

            repository.refresh()

            assertThat(trayLimitStore.current()).isEqualTo(20)
        }

    // --- Profile location (issue #37) ----------------------------------------

    @Test
    fun `saving a location PATCHes both coordinates and nothing else`() = runTest(testDispatcher) {
        enqueueUser(latitude = "47.6062", longitude = "-122.3321")

        val result = repository.update(
            UserSettingsUpdate(location = LocationUpdate.Set(Coordinates(47.6062, -122.3321))),
        )

        val body = server.takeJsonBody()
        assertThat(body.keys).containsExactly("latitude", "longitude")
        val latitude = body.getValue("latitude").jsonPrimitive
        assertThat(latitude.isString).isFalse()
        assertThat(latitude.doubleOrNull).isEqualTo(47.6062)
        assertThat(body.getValue("longitude").jsonPrimitive.doubleOrNull).isEqualTo(-122.3321)
        assertThat((result as ApiResult.Success).data.latitude).isEqualTo(47.6062)
        assertThat(result.data.longitude).isEqualTo(-122.3321)
        assertThat(repository.observeSettings().first()?.latitude).isEqualTo(47.6062)
    }

    /**
     * The clear path is modelled and serialised correctly but **the live API refuses
     * it**, so nothing in the app sends one (see `LocationUpdate.Clear`). The two
     * tests below keep that state of affairs honest: the first pins the request shape
     * so re-enabling it later is a one-liner, the second pins what the server does
     * with it today.
     */
    @Test
    fun `a clear would carry both coordinates as explicit nulls`() = runTest(testDispatcher) {
        enqueueUser(latitude = "null", longitude = "null")

        repository.update(UserSettingsUpdate(location = LocationUpdate.Clear))

        val body = server.takeJsonBody()
        // The keys must be present *and* null: `explicitNulls = false` would have
        // dropped a Kotlin null, leaving an empty, pointless request.
        assertThat(body.keys).containsExactly("latitude", "longitude")
        assertThat(body.getValue("latitude")).isEqualTo(JsonNull)
        assertThat(body.getValue("longitude")).isEqualTo(JsonNull)
    }

    @Test
    fun `the API refuses a clear and the cached location survives it`() =
        runTest(testDispatcher) {
            enqueueUser(latitude = "47.6062", longitude = "-122.3321")
            repository.refresh()
            server.takeRequest()

            // Verbatim from the live endpoint, probed with a real account: a null
            // latitude, an empty string and the string "null" all answer with this.
            server.enqueue(
                MockResponse().setResponseCode(400).setBody(
                    """
                    {
                      "error": "latitude must be a number between -90 and 90",
                      "code": "bad_request"
                    }
                    """.trimIndent(),
                ),
            )
            val result = repository.update(UserSettingsUpdate(location = LocationUpdate.Clear))

            assertThat(result).isInstanceOf(ApiResult.Failure::class.java)
            // A 400 normalises to Unknown, which keeps the server's own wording — so a
            // clear wired up by mistake would surface the reason, not a generic error.
            val error = (result as ApiResult.Failure).error
            assertThat(error).isInstanceOf(AppError.Unknown::class.java)
            assertThat(error.message).isEqualTo("latitude must be a number between -90 and 90")
            assertThat(repository.observeSettings().first()?.latitude).isEqualTo(47.6062)
            assertThat(repository.observeSettings().first()?.longitude).isEqualTo(-122.3321)
        }

    @Test
    fun `a rejected location save leaves the cached coordinates untouched`() =
        runTest(testDispatcher) {
            enqueueUser(latitude = "47.6062", longitude = "-122.3321")
            repository.refresh()
            server.takeRequest()

            server.enqueue(MockResponse().setResponseCode(500).setBody("""{ "error": "boom" }"""))
            val result = repository.update(
                UserSettingsUpdate(location = LocationUpdate.Set(Coordinates(10.0, 10.0))),
            )

            assertThat(result).isInstanceOf(ApiResult.Failure::class.java)
            assertThat(repository.observeSettings().first()?.latitude).isEqualTo(47.6062)
            assertThat(repository.observeSettings().first()?.longitude).isEqualTo(-122.3321)
        }

    @Test
    fun `a user with no coordinates reads as having no location`() = runTest(testDispatcher) {
        enqueueUser(latitude = "null", longitude = "null")

        val settings = (repository.refresh() as ApiResult.Success).data

        assertThat(settings.latitude).isNull()
        assertThat(settings.longitude).isNull()
    }

}
