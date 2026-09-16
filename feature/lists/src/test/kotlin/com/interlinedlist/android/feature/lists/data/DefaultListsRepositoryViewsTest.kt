package com.interlinedlist.android.feature.lists.data

import com.google.common.truth.Truth.assertThat
import com.interlinedlist.android.core.common.dispatcher.DispatcherProvider
import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.core.common.result.AppError
import com.interlinedlist.android.feature.lists.data.local.CachedListEntity
import com.interlinedlist.android.feature.lists.data.local.ListDao
import com.interlinedlist.android.feature.lists.data.remote.ListsApi
import com.interlinedlist.android.feature.lists.domain.ListViewConfig
import com.interlinedlist.android.feature.lists.domain.ListViewDensity
import com.interlinedlist.android.feature.lists.domain.ListViewMode
import com.interlinedlist.android.feature.lists.domain.ListViewScope
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit

/**
 * MockWebServer coverage for the saved-views endpoints (`/api/lists/{id}/views`):
 * list/create/update/delete/fork round-trips against the real paths and bodies,
 * local validation of `scope` before a request is spent, and the rule that the
 * server's copy of a view — not the one we sent — is what comes back.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DefaultListsRepositoryViewsTest {

    private lateinit var server: MockWebServer
    private lateinit var api: ListsApi
    private lateinit var repository: DefaultListsRepository

    // Mirrors the app's shared Json (explicit nulls off, coerce defaults on).
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
        api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(ListsApi::class.java)
        repository = DefaultListsRepository(api, FakeViewsDao(), json, testDispatchers)
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun `getViews parses shared and personal views and keeps unknown config keys`() = runTest(dispatcher) {
        server.enqueue(
            MockResponse().setBody(
                """
                {
                  "views": [
                    { "id": "v1", "listId": "L1", "userId": "u-other", "name": "Roadmap",
                      "scope": "shared", "isDefault": true, "position": 0,
                      "config": { "mode": "records", "density": "compact", "filters": [],
                                  "groupBy": "status", "sort": { "by": "due" } } },
                    { "id": "v2", "listId": "L1", "userId": "me", "name": "Mine",
                      "scope": "personal", "isDefault": false, "position": 1,
                      "config": { "mode": "erd", "density": "cozy", "filters": [] } }
                  ]
                }
                """.trimIndent(),
            ),
        )

        val result = repository.getViews("L1")

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        val views = (result as ApiResult.Success).data
        assertThat(views.map { it.id }).containsExactly("v1", "v2").inOrder()

        val shared = views[0]
        assertThat(shared.scope).isEqualTo(ListViewScope.SHARED)
        assertThat(shared.isDefault).isTrue()
        assertThat(shared.config.density).isEqualTo(ListViewDensity.COMPACT)
        // Keys this client does not model survive untouched, ready to be written back.
        assertThat(shared.config.raw["groupBy"]).isEqualTo(JsonPrimitive("status"))
        assertThat(shared.config.raw).containsKey("sort")
        assertThat(shared.isOwnedBy("me")).isFalse()

        val personal = views[1]
        assertThat(personal.scope).isEqualTo(ListViewScope.PERSONAL)
        assertThat(personal.isOwnedBy("me")).isTrue()
        // Values the client does not model fall back for display but are not lost.
        assertThat(personal.config.mode).isEqualTo(ListViewMode.RECORDS)
        assertThat(personal.config.storedMode).isEqualTo("erd")
        assertThat(personal.config.density).isEqualTo(ListViewDensity.COMFORTABLE)

        val request = server.takeRequest()
        assertThat(request.method).isEqualTo("GET")
        assertThat(request.path).isEqualTo("/api/lists/L1/views")
    }

    @Test
    fun `createView posts name and scope and returns the created view`() = runTest(dispatcher) {
        server.enqueue(
            MockResponse().setResponseCode(201).setBody(
                """
                { "view": { "id": "v9", "listId": "L1", "userId": "me", "name": "By status",
                            "scope": "shared", "isDefault": false, "position": 2,
                            "config": { "mode": "records", "density": "comfortable", "filters": [] } } }
                """.trimIndent(),
            ),
        )

        val result = repository.createView("L1", "  By status  ", ListViewScope.SHARED)

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        val view = (result as ApiResult.Success).data
        assertThat(view.id).isEqualTo("v9")
        assertThat(view.scope).isEqualTo(ListViewScope.SHARED)
        assertThat(view.position).isEqualTo(2)

        val request = server.takeRequest()
        assertThat(request.method).isEqualTo("POST")
        assertThat(request.path).isEqualTo("/api/lists/L1/views")
        val body = request.body.readUtf8()
        assertThat(body).contains("\"name\":\"By status\"")
        assertThat(body).contains("\"scope\":\"shared\"")
        // isDefault is only sent when asked for, so the server keeps its own default.
        assertThat(body).doesNotContain("isDefault")
    }

    @Test
    fun `createView rejects a missing scope before issuing a request`() = runTest(dispatcher) {
        val result = repository.createView("L1", "Nameless scope", scope = null)

        assertThat(result).isInstanceOf(ApiResult.Failure::class.java)
        assertThat((result as ApiResult.Failure).error).isInstanceOf(AppError.Unknown::class.java)
        assertThat(result.error.message).contains("shared or personal")
        assertThat(server.requestCount).isEqualTo(0)
    }

    @Test
    fun `createView rejects an unrecognised scope before issuing a request`() = runTest(dispatcher) {
        // Anything the API would 400 on parses to null rather than being guessed at.
        val parsed = ListViewScope.fromApi("team")
        assertThat(parsed).isNull()

        val result = repository.createView("L1", "Team view", scope = parsed)

        assertThat(result).isInstanceOf(ApiResult.Failure::class.java)
        assertThat(server.requestCount).isEqualTo(0)
    }

    @Test
    fun `createView rejects a blank name before issuing a request`() = runTest(dispatcher) {
        val result = repository.createView("L1", "   ", ListViewScope.PERSONAL)

        assertThat(result).isInstanceOf(ApiResult.Failure::class.java)
        assertThat((result as ApiResult.Failure).error.message).contains("name")
        assertThat(server.requestCount).isEqualTo(0)
    }

    @Test
    fun `updateView renames a view and sends only the changed field`() = runTest(dispatcher) {
        server.enqueue(
            MockResponse().setBody(
                """
                { "view": { "id": "v1", "listId": "L1", "userId": "me", "name": "Renamed",
                            "scope": "personal", "isDefault": false, "position": 0,
                            "config": { "mode": "records", "density": "comfortable", "filters": [] } } }
                """.trimIndent(),
            ),
        )

        val result = repository.updateView("L1", "v1", name = "Renamed")

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        assertThat((result as ApiResult.Success).data.name).isEqualTo("Renamed")

        val request = server.takeRequest()
        assertThat(request.method).isEqualTo("PUT")
        assertThat(request.path).isEqualTo("/api/lists/L1/views/v1")
        val body = request.body.readUtf8()
        assertThat(body).contains("\"name\":\"Renamed\"")
        assertThat(body).doesNotContain("config")
        assertThat(body).doesNotContain("isDefault")
    }

    @Test
    fun `updateView marks a view as the default`() = runTest(dispatcher) {
        server.enqueue(
            MockResponse().setBody(
                """{ "view": { "id": "v1", "listId": "L1", "name": "Roadmap", "scope": "shared", "isDefault": true } }""",
            ),
        )

        val result = repository.updateView("L1", "v1", isDefault = true)

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        assertThat((result as ApiResult.Success).data.isDefault).isTrue()
        assertThat(server.takeRequest().body.readUtf8()).contains("\"isDefault\":true")
    }

    @Test
    fun `updateView reports the config the server stored not the one that was sent`() = runTest(dispatcher) {
        // The API silently drops config values it does not recognise, so the write
        // we send and the state we end up in can differ.
        server.enqueue(
            MockResponse().setBody(
                """
                { "view": { "id": "v1", "listId": "L1", "name": "Roadmap", "scope": "personal",
                            "config": { "mode": "records", "density": "comfortable", "filters": [] } } }
                """.trimIndent(),
            ),
        )
        val optimistic = ListViewConfig(
            buildJsonObject {
                put("mode", JsonPrimitive("erd"))
                put("density", JsonPrimitive("compact"))
            },
        )

        val result = repository.updateView("L1", "v1", config = optimistic)

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        val stored = (result as ApiResult.Success).data.config
        assertThat(stored.storedMode).isEqualTo("records")
        assertThat(stored.density).isEqualTo(ListViewDensity.COMFORTABLE)

        // We really did send the optimistic values; the server just did not keep them.
        val body = server.takeRequest().body.readUtf8()
        assertThat(body).contains("\"mode\":\"erd\"")
        assertThat(body).contains("\"density\":\"compact\"")
    }

    @Test
    fun `forkView posts to the view and returns a personal copy`() = runTest(dispatcher) {
        server.enqueue(
            MockResponse().setResponseCode(201).setBody(
                """
                { "view": { "id": "v-copy", "listId": "L1", "userId": "me", "name": "Roadmap (copy)",
                            "scope": "personal", "isDefault": false, "position": 3,
                            "config": { "mode": "records", "density": "compact", "filters": [] } } }
                """.trimIndent(),
            ),
        )

        val result = repository.forkView("L1", "v1")

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        val copy = (result as ApiResult.Success).data
        assertThat(copy.id).isEqualTo("v-copy")
        assertThat(copy.name).isEqualTo("Roadmap (copy)")
        assertThat(copy.scope).isEqualTo(ListViewScope.PERSONAL)
        assertThat(copy.isOwnedBy("me")).isTrue()
        // The fork inherits the original's config, including a non-default density.
        assertThat(copy.config.density).isEqualTo(ListViewDensity.COMPACT)

        val request = server.takeRequest()
        assertThat(request.method).isEqualTo("POST")
        assertThat(request.path).isEqualTo("/api/lists/L1/views/v1")
        assertThat(request.body.size).isEqualTo(0)
    }

    @Test
    fun `deleteView deletes by view id`() = runTest(dispatcher) {
        server.enqueue(MockResponse().setBody("""{ "message": "View deleted" }"""))

        val result = repository.deleteView("L1", "v1")

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        val request = server.takeRequest()
        assertThat(request.method).isEqualTo("DELETE")
        assertThat(request.path).isEqualTo("/api/lists/L1/views/v1")
    }

    @Test
    fun `deleteView surfaces a refusal to touch someone else's shared view`() = runTest(dispatcher) {
        server.enqueue(
            MockResponse().setResponseCode(403)
                .setBody("""{ "error": "You cannot modify this view", "code": "forbidden" }"""),
        )

        val result = repository.deleteView("L1", "v1")

        assertThat(result).isInstanceOf(ApiResult.Failure::class.java)
        val error = (result as ApiResult.Failure).error
        assertThat(error).isInstanceOf(AppError.Forbidden::class.java)
        assertThat(error.message).isEqualTo("You cannot modify this view")
    }

    @Test
    fun `createView fails cleanly when the server returns no view`() = runTest(dispatcher) {
        server.enqueue(MockResponse().setResponseCode(201).setBody("{}"))

        val result = repository.createView("L1", "Ghost", ListViewScope.PERSONAL)

        assertThat(result).isInstanceOf(ApiResult.Failure::class.java)
        assertThat((result as ApiResult.Failure).error).isInstanceOf(AppError.Unknown::class.java)
    }
}

/** Minimal no-op [ListDao] — the saved-view endpoints don't touch Room. */
private class FakeViewsDao : ListDao {
    private val state = MutableStateFlow<List<CachedListEntity>>(emptyList())
    override fun observeLists(): Flow<List<CachedListEntity>> = state
    override suspend fun upsertAll(lists: List<CachedListEntity>) {}
    override suspend fun upsert(list: CachedListEntity) {}
    override suspend fun deleteById(id: String) {}
    override suspend fun clear() {}
}
