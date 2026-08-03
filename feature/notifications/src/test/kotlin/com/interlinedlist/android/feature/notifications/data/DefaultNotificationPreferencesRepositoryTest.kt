package com.interlinedlist.android.feature.notifications.data

import com.google.common.truth.Truth.assertThat
import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.core.common.result.AppError
import com.interlinedlist.android.feature.notifications.data.remote.NotificationPreferencesApi
import com.interlinedlist.android.feature.notifications.domain.NotificationChannel
import com.interlinedlist.android.feature.notifications.domain.NotificationPreference
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.Json.Default.parseToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultNotificationPreferencesRepositoryTest {

    private val dispatcher = StandardTestDispatcher()
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    private lateinit var server: MockWebServer
    private lateinit var api: NotificationPreferencesApi

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val contentType = "application/json".toMediaType()
        api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
            .create(NotificationPreferencesApi::class.java)
    }

    @After
    fun tearDown() = server.shutdown()

    private fun repository() = DefaultNotificationPreferencesRepository(
        api = api,
        json = json,
        dispatchers = TestDispatcherProvider(dispatcher),
    )

    private fun enqueueJson(code: Int, body: String) {
        server.enqueue(MockResponse().setResponseCode(code).setBody(body))
    }

    @Test
    fun `getPreferences parses varying channel sets across events`() = runTest(dispatcher) {
        // Live-shaped payload: each event exposes a DIFFERENT set of channel keys.
        enqueueJson(
            200,
            """
            {
              "events": [
                { "key": "dig", "label": "Digs on your messages",
                  "description": "When someone digs your message.",
                  "channels": { "push": true, "inApp": false } },
                { "key": "follow", "label": "New followers",
                  "description": "When someone follows you.",
                  "channels": { "push": true, "email": true } },
                { "key": "mention", "label": "Mentions",
                  "description": "When someone @-mentions you.",
                  "channels": { "email": true, "inApp": true, "push": false } },
                { "key": "reply", "label": "Replies",
                  "description": "When someone replies.",
                  "channels": { "email": true } }
              ]
            }
            """.trimIndent(),
        )
        val repo = repository()

        val result = repo.getPreferences()

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        val prefs = (result as ApiResult.Success).data
        assertThat(prefs.map { it.key }).containsExactly("dig", "follow", "mention", "reply").inOrder()

        val dig = prefs.first { it.key == "dig" }
        assertThat(dig.availableChannels)
            .containsExactly(NotificationChannel.PUSH, NotificationChannel.IN_APP).inOrder()
        assertThat(dig.isEnabled(NotificationChannel.PUSH)).isTrue()
        assertThat(dig.isEnabled(NotificationChannel.IN_APP)).isFalse()
        // A channel the event does NOT offer reports disabled and is not rendered.
        assertThat(dig.isEnabled(NotificationChannel.EMAIL)).isFalse()

        val follow = prefs.first { it.key == "follow" }
        assertThat(follow.availableChannels)
            .containsExactly(NotificationChannel.PUSH, NotificationChannel.EMAIL).inOrder()

        val mention = prefs.first { it.key == "mention" }
        assertThat(mention.availableChannels).containsExactly(
            NotificationChannel.PUSH, NotificationChannel.IN_APP, NotificationChannel.EMAIL,
        ).inOrder()

        val reply = prefs.first { it.key == "reply" }
        assertThat(reply.availableChannels).containsExactly(NotificationChannel.EMAIL)
    }

    @Test
    fun `getPreferences drops unknown channel keys`() = runTest(dispatcher) {
        enqueueJson(
            200,
            """
            { "events": [ { "key": "dig", "label": "Digs", "description": "d",
                "channels": { "push": true, "sms": true } } ] }
            """.trimIndent(),
        )
        val repo = repository()

        val prefs = (repo.getPreferences() as ApiResult.Success).data
        val dig = prefs.single()
        // "sms" is unknown and dropped; only push survives.
        assertThat(dig.availableChannels).containsExactly(NotificationChannel.PUSH)
    }

    @Test
    fun `getPreferences maps a 401 to Unauthorized`() = runTest(dispatcher) {
        enqueueJson(401, """{ "error": "Sign in required." }""")
        val repo = repository()

        val result = repo.getPreferences()

        assertThat(result).isInstanceOf(ApiResult.Failure::class.java)
        assertThat((result as ApiResult.Failure).error)
            .isInstanceOf(AppError.Unauthorized::class.java)
    }

    @Test
    fun `updatePreference PATCHes the event key and its channel map`() = runTest(dispatcher) {
        enqueueJson(200, "")
        val repo = repository()

        val result = repo.updatePreference(
            NotificationPreference(
                key = "dig",
                label = "Digs",
                description = "d",
                channels = mapOf(
                    NotificationChannel.PUSH to false,
                    NotificationChannel.IN_APP to true,
                ),
            ),
        )

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        val request = server.takeRequest()
        assertThat(request.method).isEqualTo("PATCH")
        assertThat(request.path).contains("api/user/notification-preferences")

        // Round-trip the body: it carries the event key + the exact channel booleans.
        val body = parseToJsonElement(request.body.readUtf8()).jsonObject
        assertThat(body["key"]!!.jsonPrimitive.content).isEqualTo("dig")
        val channels = body["channels"]!!.jsonObject
        assertThat(channels["push"]!!.jsonPrimitive.content).isEqualTo("false")
        assertThat(channels["inApp"]!!.jsonPrimitive.content).isEqualTo("true")
        // Only the event's OWN channels are sent — no email key on a push/inApp event.
        assertThat(channels.containsKey("email")).isFalse()
    }

    @Test
    fun `updatePreference maps a 400 to a failure`() = runTest(dispatcher) {
        enqueueJson(400, """{ "error": "Invalid channel." }""")
        val repo = repository()

        val result = repo.updatePreference(
            NotificationPreference(
                key = "dig", label = "Digs", description = "d",
                channels = mapOf(NotificationChannel.PUSH to true),
            ),
        )

        assertThat(result).isInstanceOf(ApiResult.Failure::class.java)
    }
}
