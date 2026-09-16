package com.interlinedlist.android.feature.lists.data

import com.google.common.truth.Truth.assertThat
import com.interlinedlist.android.core.common.dispatcher.DispatcherProvider
import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.core.network.api.InterlinedListApi
import com.interlinedlist.android.feature.lists.data.local.CachedListEntity
import com.interlinedlist.android.feature.lists.data.local.ListDao
import com.interlinedlist.android.feature.lists.data.remote.ListsApi
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit

/**
 * MockWebServer coverage for the grid's freshness poll / presence heartbeat
 * (`POST /api/lists/{id}/data/versions`).
 *
 * The payloads here were captured against the live API: quoting a stale version
 * returns the whole row under `changed` in the same shape `GET .../data` uses
 * (`rowData` + `version`), and an id the server cannot resolve comes back as a
 * bare string in `deleted`. Only `users` was not observable — it needs a second
 * person on the list — so it is decoded tolerantly.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DefaultListsRepositoryFreshnessTest {

    private lateinit var server: MockWebServer
    private lateinit var api: ListsApi
    private lateinit var userApi: InterlinedListApi
    private lateinit var repository: DefaultListsRepository

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; coerceInputValues = true }
    private val dispatcher = StandardTestDispatcher()

    private val testDispatchers = object : DispatcherProvider {
        override val io: CoroutineDispatcher get() = dispatcher
        override val default: CoroutineDispatcher get() = dispatcher
        override val main: CoroutineDispatcher get() = dispatcher
    }

    @Before
    fun setUp() {
        server = MockWebServer().also { it.start() }
        val retrofit = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
        api = retrofit.create(ListsApi::class.java)
        userApi = retrofit.create(InterlinedListApi::class.java)
        repository = DefaultListsRepository(api, userApi, FakeFreshnessDao(), json, testDispatchers)
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun `poll posts the held versions and the focused row`() = runTest(dispatcher) {
        server.enqueue(
            MockResponse().setBody(
                """{ "changed": [], "deleted": [], "users": [], "collaborative": false }""",
            ),
        )

        val result = repository.pollFreshness("L1", mapOf("row_a" to 4, "row_b" to 7), focusedRowId = "row_a")

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        val request = server.takeRequest()
        assertThat(request.method).isEqualTo("POST")
        assertThat(request.path).isEqualTo("/api/lists/L1/data/versions")

        val body = json.parseToJsonElement(request.body.readUtf8()).jsonObject
        assertThat(body["focusedRowId"]?.jsonPrimitive?.content).isEqualTo("row_a")
        val versions = body["rowVersions"]!!.jsonObject
        assertThat(versions["row_a"]?.jsonPrimitive?.int).isEqualTo(4)
        assertThat(versions["row_b"]?.jsonPrimitive?.int).isEqualTo(7)
    }

    @Test
    fun `the live single-user answer means stop polling`() = runTest(dispatcher) {
        server.enqueue(
            MockResponse().setBody(
                """{ "changed": [], "deleted": [], "users": [], "collaborative": false }""",
            ),
        )

        val result = repository.pollFreshness("L1", emptyMap(), focusedRowId = null)

        val freshness = (result as ApiResult.Success).data
        assertThat(freshness.collaborative).isFalse()
        assertThat(freshness.hasChanges).isFalse()
        assertThat(freshness.presence).isEmpty()
        // No focused row means the key is simply omitted, not sent as null.
        assertThat(server.takeRequest().body.readUtf8()).doesNotContain("focusedRowId")
    }

    @Test
    fun `changed rows deleted ids and presence are parsed`() = runTest(dispatcher) {
        // `changed` and `deleted` are the live shapes; `users` follows the docs.
        server.enqueue(
            MockResponse().setBody(
                """
                {
                  "changed": [
                    { "id": "row_b", "version": 9, "rowData": { "status": "blocked", "count": 3 },
                      "createdAt": "2026-09-16T21:20:00.069Z", "updatedAt": "2026-09-16T21:21:00.069Z",
                      "createdByUser": { "id": "usr_1", "username": "me", "displayName": "Me", "avatar": null },
                      "lastEditedByUser": { "id": "usr_2", "username": "casey", "displayName": "Casey" } }
                  ],
                  "deleted": ["row_a"],
                  "users": [
                    { "userId": "usr_2", "name": "Casey", "username": "casey",
                      "color": "#3366ff", "focusedRowId": "row_b" }
                  ],
                  "collaborative": true
                }
                """.trimIndent(),
            ),
        )

        val freshness = (repository.pollFreshness("L1", mapOf("row_a" to 4, "row_b" to 7)) as ApiResult.Success).data

        assertThat(freshness.collaborative).isTrue()
        assertThat(freshness.hasChanges).isTrue()

        val changed = freshness.changed.single()
        assertThat(changed.id).isEqualTo("row_b")
        assertThat(changed.version).isEqualTo(9)
        assertThat(changed.valueFor("status")).isEqualTo("blocked")
        // Any JSON value projects to a display string, exactly as fetched rows do.
        assertThat(changed.valueFor("count")).isEqualTo("3")

        assertThat(freshness.deletedRowIds).containsExactly("row_a")

        val person = freshness.presence.single()
        assertThat(person.userId).isEqualTo("usr_2")
        assertThat(person.label).isEqualTo("Casey")
        assertThat(person.initial).isEqualTo("C")
        assertThat(person.focusedRowId).isEqualTo("row_b")
        assertThat(person.color).isEqualTo("#3366ff")
    }

    @Test
    fun `unconfirmed shapes decode rather than failing the poll`() = runTest(dispatcher) {
        // `data` instead of the live `rowData`, deleted as objects rather than
        // strings, a user keyed by `id`/`displayName`, and extra keys throughout.
        server.enqueue(
            MockResponse().setBody(
                """
                {
                  "changed": [ { "id": "row_b", "data": { "status": "shipped" }, "updatedAt": "now" } ],
                  "deleted": [ { "id": "row_a", "deletedAt": "now" } ],
                  "users": [ { "id": "usr_3", "displayName": "Robin", "avatar": null } ],
                  "collaborative": true,
                  "serverTime": "now"
                }
                """.trimIndent(),
            ),
        )

        val freshness = (repository.pollFreshness("L1", mapOf("row_b" to 7)) as ApiResult.Success).data

        assertThat(freshness.changed.single().valueFor("status")).isEqualTo("shipped")
        assertThat(freshness.deletedRowIds).containsExactly("row_a")
        assertThat(freshness.presence.single().userId).isEqualTo("usr_3")
        assertThat(freshness.presence.single().label).isEqualTo("Robin")
    }

    @Test
    fun `an empty body answer is tolerated`() = runTest(dispatcher) {
        server.enqueue(MockResponse().setBody("{}"))

        val freshness = (repository.pollFreshness("L1", emptyMap()) as ApiResult.Success).data

        assertThat(freshness.changed).isEmpty()
        assertThat(freshness.deletedRowIds).isEmpty()
        assertThat(freshness.presence).isEmpty()
        assertThat(freshness.collaborative).isFalse()
    }

    @Test
    fun `no more than the server's 500-row ceiling is ever asked about`() = runTest(dispatcher) {
        server.enqueue(MockResponse().setBody("""{ "collaborative": true }"""))
        val versions = (1..600).associate { "row_$it" to it }

        repository.pollFreshness("L1", versions)

        val body = json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
        assertThat(body["rowVersions"]!!.jsonObject).hasSize(500)
    }
}

private class FakeFreshnessDao : ListDao {
    private val state = MutableStateFlow<List<CachedListEntity>>(emptyList())
    override fun observeLists(): Flow<List<CachedListEntity>> = state
    override suspend fun upsertAll(lists: List<CachedListEntity>) {}
    override suspend fun upsert(list: CachedListEntity) {}
    override suspend fun deleteById(id: String) {}
    override suspend fun clear() {}
}
