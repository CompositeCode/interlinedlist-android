package com.interlinedlist.android.feature.messages.data

import com.google.common.truth.Truth.assertThat
import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.core.model.ViewingPreference
import com.interlinedlist.android.core.network.api.InterlinedListApi
import com.interlinedlist.android.core.network.preferences.ViewingPreferenceStore
import com.interlinedlist.android.feature.messages.data.remote.MessagesApi
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
 * How the account's `viewingPreference` reaches `GET /api/messages`.
 *
 * The help centre's API reference (`/help/api/messages`) documents the feed's only
 * query parameters as `limit`, `offset`, `onlyMine` and `tag` — there is no
 * following/followers parameter. It also states that search is "scoped to your feed
 * visibility (honors your `viewingPreference`)", i.e. **the server applies the stored
 * preference itself**. So the client's job is: persist the preference, then refresh;
 * `onlyMine=true` stays the per-request mechanism for My Messages.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MessagesFeedViewPreferenceTest {

    private val dispatcher = StandardTestDispatcher()

    // Mirrors the production Json (see core:network NetworkModule).
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
    }

    private lateinit var server: MockWebServer
    private lateinit var api: MessagesApi
    private lateinit var userApi: InterlinedListApi
    private lateinit var dao: FakeMessageDao

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val retrofit = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
        api = retrofit.create(MessagesApi::class.java)
        userApi = retrofit.create(InterlinedListApi::class.java)
        dao = FakeMessageDao()
    }

    @After
    fun tearDown() = server.shutdown()

    private fun repository() = DefaultMessagesRepository(
        api = api,
        userApi = userApi,
        viewingPreferenceStore = ViewingPreferenceStore(userApi, json),
        messageDao = dao,
        sessionStore = fakeSessionStore("me"),
        json = json,
        dispatchers = TestDispatcherProvider(dispatcher),
    )

    private fun enqueuePage(nextCursor: String? = null) {
        val cursor = nextCursor?.let { "\"$it\"" } ?: "null"
        server.enqueue(
            MockResponse().setBody(
                """{ "messages": [ { "id": "m1", "content": "hi" } ],
                     "pagination": { "limit": 20, "hasMore": ${nextCursor != null}, "nextCursor": $cursor } }""",
            ),
        )
    }

    @Test
    fun `All Messages asks for the whole feed with no onlyMine filter`() = runTest(dispatcher) {
        enqueuePage()

        repository().refreshFeed(ViewingPreference.ALL)

        val url = server.takeRequest().requestUrl!!
        assertThat(url.encodedPath).isEqualTo("/api/messages")
        assertThat(url.queryParameter("onlyMine")).isNull()
        assertThat(url.queryParameter("limit")).isEqualTo("20")
    }

    @Test
    fun `My Messages sends onlyMine true`() = runTest(dispatcher) {
        enqueuePage()

        repository().refreshFeed(ViewingPreference.MINE)

        assertThat(server.takeRequest().requestUrl!!.queryParameter("onlyMine")).isEqualTo("true")
    }

    @Test
    fun `Following Only leaves the filtering to the server-side preference`() = runTest(dispatcher) {
        enqueuePage()

        repository().refreshFeed(ViewingPreference.FOLLOWING)

        // No such query parameter exists; the saved preference scopes the feed.
        val url = server.takeRequest().requestUrl!!
        assertThat(url.queryParameter("onlyMine")).isNull()
        assertThat(url.queryParameter("following")).isNull()
        assertThat(url.querySize).isEqualTo(1)
    }

    @Test
    fun `Followers Only leaves the filtering to the server-side preference`() = runTest(dispatcher) {
        enqueuePage()

        repository().refreshFeed(ViewingPreference.FOLLOWERS)

        val url = server.takeRequest().requestUrl!!
        assertThat(url.queryParameter("onlyMine")).isNull()
        assertThat(url.queryParameter("followers")).isNull()
        assertThat(url.querySize).isEqualTo(1)
    }

    @Test
    fun `paging past the first page keeps the preference and the cursor`() = runTest(dispatcher) {
        enqueuePage(nextCursor = "cursor-2")
        enqueuePage()
        val repo = repository()

        val cursor = (repo.refreshFeed(ViewingPreference.MINE) as ApiResult.Success).data!!
        repo.loadMoreFeed(cursor, ViewingPreference.MINE)

        server.takeRequest()
        val url = server.takeRequest().requestUrl!!
        assertThat(url.queryParameter("onlyMine")).isEqualTo("true")
        assertThat(url.queryParameter("cursor")).isEqualTo("cursor-2")
    }

    @Test
    fun `the feed reads the account preference from GET api user`() = runTest(dispatcher) {
        server.enqueue(
            MockResponse().setBody(
                """{ "user": { "id": "u1", "username": "me", "viewingPreference": "following_only" } }""",
            ),
        )

        val result = repository().getViewingPreference()

        assertThat((result as ApiResult.Success).data).isEqualTo(ViewingPreference.FOLLOWING)
        assertThat(server.takeRequest().path).isEqualTo("/api/user")
    }

    @Test
    fun `saving the preference PATCHes the account so the web agrees`() = runTest(dispatcher) {
        server.enqueue(
            MockResponse().setBody(
                """{ "user": { "id": "u1", "username": "me", "viewingPreference": "my_messages" } }""",
            ),
        )

        val result = repository().setViewingPreference(ViewingPreference.MINE)

        assertThat((result as ApiResult.Success).data).isEqualTo(ViewingPreference.MINE)
        val request = server.takeRequest()
        assertThat(request.method).isEqualTo("PATCH")
        assertThat(request.path).isEqualTo("/api/user/update")
        assertThat(request.body.readUtf8()).isEqualTo("""{"viewingPreference":"my_messages"}""")
    }
}
