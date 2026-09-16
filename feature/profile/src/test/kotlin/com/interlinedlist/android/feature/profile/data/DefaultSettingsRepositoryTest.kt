package com.interlinedlist.android.feature.profile.data

import com.google.common.truth.Truth.assertThat
import com.interlinedlist.android.core.common.dispatcher.DispatcherProvider
import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.core.common.result.AppError
import com.interlinedlist.android.feature.profile.data.remote.ProfileApi
import com.interlinedlist.android.feature.profile.domain.UserSettingsUpdate
import com.interlinedlist.android.feature.profile.domain.ViewingPreference
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
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
        repository = DefaultSettingsRepository(api, json, dispatchers)
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
                "latitude": 45.52,
                "longitude": -122.68,
                "isPrivateAccount": false,
                "githubDefaultRepo": "adron/notes",
                "notificationTrayLimit": 25
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
}
