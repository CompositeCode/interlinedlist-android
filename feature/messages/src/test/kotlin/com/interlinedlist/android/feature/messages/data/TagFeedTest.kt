package com.interlinedlist.android.feature.messages.data

import com.google.common.truth.Truth.assertThat
import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.core.model.ViewingPreference
import com.interlinedlist.android.core.network.api.InterlinedListApi
import com.interlinedlist.android.core.network.preferences.ViewingPreferenceStore
import com.interlinedlist.android.feature.messages.data.remote.MessagesApi
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

/**
 * The tag-filtered feed (`GET /api/messages?tag=`).
 *
 * It is the *same* feed: same endpoint, same opaque keyset cursor, same
 * `onlyMine` view-preference mechanism, with one extra query parameter. These
 * tests pin the three things that could quietly go wrong when a second feed
 * shares one implementation: the tag reaching the wire intact (encoded exactly
 * once), paging still being cursor-based on the filtered feed, and the tag feed
 * leaving the main feed's cache alone.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TagFeedTest {

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

    /** A real tag from the live site: spaces *and* a comma, in one label. */
    private val awkwardTag = "life is short, o brave girl"

    /** An opaque, base64-padded token: nothing in the app may interpret it. */
    private val cursorAfterT2 = "MjAyNi0wOS0xM1QxMjowMDowMC4wMDBafHQy="

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

    private fun page(ids: List<String>, nextCursor: String? = null): String {
        val rows = ids.joinToString(",") { """{ "id": "$it", "content": "$it" }""" }
        val cursor = nextCursor?.let { "\"$it\"" } ?: "null"
        return """
            { "messages": [$rows],
              "pagination": { "limit": 20, "hasMore": ${nextCursor != null}, "nextCursor": $cursor } }
        """.trimIndent()
    }

    private fun enqueue(body: String) {
        server.enqueue(MockResponse().setResponseCode(200).setBody(body))
    }

    // ---- the tag on the wire ----------------------------------------------

    @Test
    fun `a tag feed sends the tag it was asked for, and the main feed sends none`() =
        runTest(dispatcher) {
            enqueue(page(listOf("t1")))
            enqueue(page(listOf("m1")))
            val repo = repository()

            repo.refreshFeed(tag = "lists")
            repo.refreshFeed()

            assertThat(server.takeRequest().requestUrl!!.queryParameter("tag")).isEqualTo("lists")
            assertThat(server.takeRequest().requestUrl!!.queryParameter("tag")).isNull()
        }

    @Test
    fun `a tag with a space and a comma is encoded exactly once`() = runTest(dispatcher) {
        enqueue(page(listOf("t1")))

        repository().refreshFeed(tag = awkwardTag)

        val url = server.takeRequest().requestUrl!!
        // Decoded, the server sees the tag whole.
        assertThat(url.queryParameter("tag")).isEqualTo(awkwardTag)
        // And on the wire it is percent-encoded once: a second pass would have
        // turned "%20" into "%2520" and matched nothing.
        val encoded = url.encodedQuery!!
        assertThat(encoded).doesNotContain("%25")
        assertThat(encoded).contains("%2C")
    }

    @Test
    fun `the tag rides alongside the view preference, not instead of it`() = runTest(dispatcher) {
        enqueue(page(listOf("t1")))

        repository().refreshFeed(preference = ViewingPreference.MINE, tag = "lists")

        val url = server.takeRequest().requestUrl!!
        assertThat(url.queryParameter("tag")).isEqualTo("lists")
        assertThat(url.queryParameter("onlyMine")).isEqualTo("true")
    }

    // ---- paging on the filtered feed --------------------------------------

    @Test
    fun `the filtered feed pages by cursor, handed back verbatim and with no offset`() =
        runTest(dispatcher) {
            enqueue(page(listOf("t3", "t2"), nextCursor = cursorAfterT2))
            enqueue(page(listOf("t1")))
            val repo = repository()

            val cursor = (repo.refreshFeed(tag = awkwardTag) as ApiResult.Success).data!!
            val next = repo.loadMoreFeed(cursor, tag = awkwardTag)

            assertThat(cursor).isEqualTo(cursorAfterT2)
            assertThat((next as ApiResult.Success).data).isNull() // end of the feed

            val first = server.takeRequest().requestUrl!!
            assertThat(first.queryParameter("cursor")).isNull()
            assertThat(first.queryParameter("offset")).isNull()

            val second = server.takeRequest().requestUrl!!
            // Verbatim: the padded token survives the round trip untouched...
            assertThat(second.queryParameter("cursor")).isEqualTo(cursorAfterT2)
            // ...still carrying the same tag, and still with no offset.
            assertThat(second.queryParameter("tag")).isEqualTo(awkwardTag)
            assertThat(second.queryParameter("offset")).isNull()
        }

    @Test
    fun `paging the filtered feed appends without duplicating or skipping rows`() =
        runTest(dispatcher) {
            enqueue(page(listOf("t3", "t2"), nextCursor = cursorAfterT2))
            enqueue(page(listOf("t1")))
            val repo = repository()

            val cursor = (repo.refreshFeed(tag = "lists") as ApiResult.Success).data!!
            repo.loadMoreFeed(cursor, tag = "lists")

            val ids = repo.observeFeed(tag = "lists").first().map { it.id }
            assertThat(ids).containsExactly("t3", "t2", "t1").inOrder()
            assertThat(ids).containsNoDuplicates()
        }

    // ---- the main feed's cache --------------------------------------------

    @Test
    fun `loading a tag feed leaves the main feed's cached rows untouched`() = runTest(dispatcher) {
        enqueue(page(listOf("m3", "m2", "m1")))
        // The tag feed overlaps the main feed (m2) and brings rows of its own.
        enqueue(page(listOf("m2", "t9")))
        val repo = repository()

        repo.refreshFeed()
        repo.refreshFeed(tag = "lists")

        // The shared row belongs to both feeds at once, in each feed's own order.
        assertThat(repo.observeFeed().first().map { it.id })
            .containsExactly("m3", "m2", "m1").inOrder()
        assertThat(repo.observeFeed(tag = "lists").first().map { it.id })
            .containsExactly("m2", "t9").inOrder()
    }

    @Test
    fun `refreshing a tag feed evicts only that tag's rows`() = runTest(dispatcher) {
        enqueue(page(listOf("m1")))
        enqueue(page(listOf("t1")))
        enqueue(page(listOf("t2")))
        val repo = repository()

        repo.refreshFeed()
        repo.refreshFeed(tag = "lists")
        repo.refreshFeed(tag = "lego")

        // Each feed stands alone: neither tag refresh cleared the other, or the main one.
        assertThat(repo.observeFeed().first().map { it.id }).containsExactly("m1")
        assertThat(repo.observeFeed(tag = "lists").first().map { it.id }).containsExactly("t1")
        assertThat(repo.observeFeed(tag = "lego").first().map { it.id }).containsExactly("t2")
    }

    @Test
    fun `a dig made in a tag feed shows up in the main feed too`() = runTest(dispatcher) {
        enqueue(page(listOf("shared")))
        enqueue(page(listOf("shared")))
        server.enqueue(MockResponse().setResponseCode(201))
        val repo = repository()

        repo.refreshFeed()
        repo.refreshFeed(tag = "lists")
        repo.setDug("shared", dug = true)

        // Feeds are lists *over* the cached messages, not copies of them.
        assertThat(repo.observeFeed().first().single().dugByMe).isTrue()
        assertThat(repo.observeFeed(tag = "lists").first().single().dugByMe).isTrue()
    }

    @Test
    fun `deleting a message drops it from every feed it appeared in`() = runTest(dispatcher) {
        enqueue(page(listOf("gone", "stays")))
        enqueue(page(listOf("gone")))
        server.enqueue(MockResponse().setResponseCode(200))
        val repo = repository()

        repo.refreshFeed()
        repo.refreshFeed(tag = "lists")
        repo.deleteMessage("gone")

        assertThat(repo.observeFeed().first().map { it.id }).containsExactly("stays")
        assertThat(repo.observeFeed(tag = "lists").first()).isEmpty()
    }
}
