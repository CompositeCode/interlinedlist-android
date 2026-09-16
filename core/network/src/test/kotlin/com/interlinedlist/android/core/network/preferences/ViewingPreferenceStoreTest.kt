package com.interlinedlist.android.core.network.preferences

import com.google.common.truth.Truth.assertThat
import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.core.common.result.AppError
import com.interlinedlist.android.core.model.ViewingPreference
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
 * The shared `viewingPreference` accessor: reads the account preference from
 * `GET /api/user` and saves it with a **partial** `PATCH /api/user/update`.
 *
 * The four wire values come from the server's own 400 (`viewingPreference must be
 * one of: my_messages, all_messages, followers_only, following_only`) and the help
 * centre's API reference (`/help/api/users-and-profile`), which lists
 * `viewingPreference` among the fields `PATCH /api/user/update` accepts.
 */
class ViewingPreferenceStoreTest {

    private lateinit var server: MockWebServer
    private lateinit var store: ViewingPreferenceStore

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
        val api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(InterlinedListApi::class.java)
        store = ViewingPreferenceStore(api, json)
    }

    @After
    fun tearDown() = server.shutdown()

    private fun enqueueUser(viewingPreference: String?) {
        val field = viewingPreference?.let { """, "viewingPreference": "$it"""" } ?: ""
        server.enqueue(
            MockResponse().setBody("""{ "user": { "id": "u1", "username": "me"$field } }"""),
        )
    }

    @Test
    fun `read returns the account preference from GET api user`() = runBlocking {
        enqueueUser("following_only")

        val result = store.read()

        assertThat((result as ApiResult.Success).data).isEqualTo(ViewingPreference.FOLLOWING)
        assertThat(server.takeRequest().path).isEqualTo("/api/user")
    }

    @Test
    fun `read maps every wire value the server accepts`() = runBlocking {
        val expected = mapOf(
            "all_messages" to ViewingPreference.ALL,
            "my_messages" to ViewingPreference.MINE,
            "following_only" to ViewingPreference.FOLLOWING,
            "followers_only" to ViewingPreference.FOLLOWERS,
        )
        expected.forEach { (wire, preference) ->
            enqueueUser(wire)
            assertThat((store.read() as ApiResult.Success).data).isEqualTo(preference)
        }
    }

    @Test
    fun `an absent or unknown preference falls back to all messages`() = runBlocking {
        enqueueUser(null)
        assertThat((store.read() as ApiResult.Success).data).isEqualTo(ViewingPreference.ALL)

        enqueueUser("something_new")
        assertThat((store.read() as ApiResult.Success).data).isEqualTo(ViewingPreference.ALL)
    }

    @Test
    fun `write PATCHes only the viewingPreference field`() = runBlocking {
        enqueueUser("followers_only")

        val result = store.write(ViewingPreference.FOLLOWERS)

        assertThat((result as ApiResult.Success).data).isEqualTo(ViewingPreference.FOLLOWERS)
        val request = server.takeRequest()
        assertThat(request.method).isEqualTo("PATCH")
        assertThat(request.path).isEqualTo("/api/user/update")
        // Partial update: nothing but the one field, so no other preference is clobbered.
        assertThat(request.body.readUtf8()).isEqualTo("""{"viewingPreference":"followers_only"}""")
    }

    @Test
    fun `write sends the wire value of each preference`() = runBlocking {
        val expected = mapOf(
            ViewingPreference.ALL to "all_messages",
            ViewingPreference.MINE to "my_messages",
            ViewingPreference.FOLLOWING to "following_only",
            ViewingPreference.FOLLOWERS to "followers_only",
        )
        expected.forEach { (preference, wire) ->
            enqueueUser(wire)
            store.write(preference)
            assertThat(server.takeRequest().body.readUtf8())
                .isEqualTo("""{"viewingPreference":"$wire"}""")
        }
    }

    @Test
    fun `write trusts the value the server echoes back`() = runBlocking {
        // The server normalised the request to something else; server truth wins.
        enqueueUser("all_messages")

        val result = store.write(ViewingPreference.FOLLOWING)

        assertThat((result as ApiResult.Success).data).isEqualTo(ViewingPreference.ALL)
    }

    @Test
    fun `write falls back to the requested value when the echo omits it`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{ "user": { "id": "u1", "username": "me" } }"""))

        val result = store.write(ViewingPreference.MINE)

        assertThat((result as ApiResult.Success).data).isEqualTo(ViewingPreference.MINE)
    }

    @Test
    fun `a rejected value surfaces the server error`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(400).setBody(
                """{ "error": "viewingPreference must be one of: my_messages, all_messages, followers_only, following_only", "code": "bad_request" }""",
            ),
        )

        val result = store.write(ViewingPreference.FOLLOWING)

        val error = (result as ApiResult.Failure).error
        assertThat(error).isInstanceOf(AppError.Unknown::class.java)
        assertThat(error.message).contains("viewingPreference must be one of")
    }
}
