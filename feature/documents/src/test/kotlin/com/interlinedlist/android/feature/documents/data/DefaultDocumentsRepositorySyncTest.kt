package com.interlinedlist.android.feature.documents.data

import com.google.common.truth.Truth.assertThat
import com.interlinedlist.android.core.common.dispatcher.DispatcherProvider
import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.feature.documents.data.local.SyncMetaEntity
import com.interlinedlist.android.feature.documents.data.remote.DocumentsApi
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit

/**
 * Delta-sync + PATCH-concurrency repository behaviour, exercised through the real
 * Retrofit stack against a [MockWebServer]. No live network is touched.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DefaultDocumentsRepositorySyncTest {

    private lateinit var server: MockWebServer
    private lateinit var api: DocumentsApi
    private lateinit var documentDao: FakeDocumentDao
    private lateinit var folderDao: FakeFolderDao
    private lateinit var pendingDao: FakePendingOpDao
    private lateinit var metaDao: FakeSyncMetaDao
    private lateinit var repository: DefaultDocumentsRepository

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; coerceInputValues = true }
    private val testDispatcher = StandardTestDispatcher()
    private val dispatchers = object : DispatcherProvider {
        override val io: CoroutineDispatcher = testDispatcher
        override val default: CoroutineDispatcher = testDispatcher
        override val main: CoroutineDispatcher = testDispatcher
    }

    @Before
    fun setUp() {
        server = MockWebServer().also { it.start() }
        val retrofit = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(OkHttpClient.Builder().build())
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
        api = retrofit.create(DocumentsApi::class.java)
        documentDao = FakeDocumentDao()
        folderDao = FakeFolderDao()
        pendingDao = FakePendingOpDao()
        metaDao = FakeSyncMetaDao()
        repository = DefaultDocumentsRepository(
            api, documentDao, folderDao, pendingDao, metaDao, json, dispatchers,
        )
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun `pullDelta upserts added and updated docs and folders and advances the cursor`() =
        runTest(testDispatcher) {
            server.enqueue(
                MockResponse().setResponseCode(200).setBody(
                    """
                    {
                      "folders": [ { "id": "f1", "name": "Work", "parentId": null } ],
                      "documents": [
                        { "id": "d1", "title": "Report", "content": "body", "folderId": "f1", "version": 3 }
                      ],
                      "lastSyncAt": "2026-07-31T00:00:00.000Z"
                    }
                    """.trimIndent(),
                ),
            )

            val result = repository.pullDelta()

            assertThat(result).isInstanceOf(ApiResult.Success::class.java)
            // First pull sends no cursor.
            assertThat(server.takeRequest().path).isEqualTo("/api/documents/sync")
            assertThat(folderDao.getFolder("f1")?.name).isEqualTo("Work")
            val cached = documentDao.getDocument("d1")
            assertThat(cached?.title).isEqualTo("Report")
            assertThat(cached?.version).isEqualTo(3)
            // Cursor persisted for the next pull.
            assertThat(metaDao.get(SyncMetaEntity.KEY_CURSOR)).isEqualTo("2026-07-31T00:00:00.000Z")
        }

    @Test
    fun `pullDelta sends the persisted cursor on the next pull`() = runTest(testDispatcher) {
        metaDao.put(SyncMetaEntity(SyncMetaEntity.KEY_CURSOR, "2026-01-01T00:00:00.000Z"))
        server.enqueue(
            MockResponse().setResponseCode(200)
                .setBody("""{ "folders": [], "documents": [], "lastSyncAt": "2026-02-01T00:00:00.000Z" }"""),
        )

        repository.pullDelta()

        assertThat(server.takeRequest().path)
            .isEqualTo("/api/documents/sync?lastSyncAt=2026-01-01T00%3A00%3A00.000Z")
    }

    @Test
    fun `pullDelta removes tombstoned documents and folders from the cache`() =
        runTest(testDispatcher) {
            // Seed a doc + folder locally, then pull a tombstone for each.
            server.enqueue(
                MockResponse().setResponseCode(200).setBody(
                    """
                    {
                      "folders": [ { "id": "f1", "name": "Work" } ],
                      "documents": [ { "id": "d1", "title": "Report", "version": 1 } ],
                      "lastSyncAt": "2026-07-31T00:00:00.000Z"
                    }
                    """.trimIndent(),
                ),
            )
            repository.pullDelta()
            server.takeRequest()
            assertThat(documentDao.getDocument("d1")).isNotNull()

            server.enqueue(
                MockResponse().setResponseCode(200).setBody(
                    """
                    {
                      "folders": [ { "id": "f1", "name": "Work", "deletedAt": "2026-07-31T01:00:00.000Z" } ],
                      "documents": [ { "id": "d1", "title": "Report", "deletedAt": "2026-07-31T01:00:00.000Z" } ],
                      "lastSyncAt": "2026-07-31T02:00:00.000Z"
                    }
                    """.trimIndent(),
                ),
            )
            val result = repository.pullDelta()

            assertThat(result).isInstanceOf(ApiResult.Success::class.java)
            assertThat(documentDao.getDocument("d1")).isNull()
            assertThat(folderDao.getFolder("f1")).isNull()
        }

    @Test
    fun `patchDocument sends the version as If-Match and reconciles on success`() =
        runTest(testDispatcher) {
            server.enqueue(
                MockResponse().setResponseCode(200).setBody(
                    """{ "document": { "id": "d1", "title": "New", "content": "c2", "version": 5 } }""",
                ),
            )

            val outcome = repository.patchDocument(
                id = "d1",
                title = "New",
                content = "c2",
                isPublic = false,
                folderId = null,
                expectedVersion = 4,
            )

            assertThat(outcome).isInstanceOf(SaveOutcome.Success::class.java)
            assertThat((outcome as SaveOutcome.Success).document.version).isEqualTo(5)
            val recorded = server.takeRequest()
            assertThat(recorded.method).isEqualTo("PATCH")
            assertThat(recorded.path).isEqualTo("/api/documents/d1")
            assertThat(recorded.getHeader("If-Match")).isEqualTo("4")
            assertThat(documentDao.getDocument("d1")?.version).isEqualTo(5)
        }

    @Test
    fun `patchDocument returns Conflict on a 409 version mismatch`() = runTest(testDispatcher) {
        server.enqueue(
            MockResponse().setResponseCode(409)
                .setBody("""{ "error": "Document was modified by someone else." }"""),
        )

        val outcome = repository.patchDocument(
            id = "d1", title = "New", content = "c", isPublic = false, folderId = null, expectedVersion = 1,
        )

        assertThat(outcome).isInstanceOf(SaveOutcome.Conflict::class.java)
        // A conflict must NOT be queued for retry — the local copy is stale.
        assertThat(pendingDao.snapshot()).isEmpty()
    }

    @Test
    fun `patchDocument queues the edit on a network failure`() = runTest(testDispatcher) {
        // No enqueue → socket closed → IOException → AppError.Network.
        server.shutdown()

        val outcome = repository.patchDocument(
            id = "d1", title = "Offline", content = "c", isPublic = false, folderId = null, expectedVersion = 2,
        )

        assertThat(outcome).isEqualTo(SaveOutcome.Queued)
        val queued = pendingDao.snapshot().single()
        assertThat(queued.documentId).isEqualTo("d1")
        assertThat(queued.title).isEqualTo("Offline")
        assertThat(queued.version).isEqualTo(2)
    }

    @Test
    fun `pushPendingOps posts queued update ops and clears them on success`() =
        runTest(testDispatcher) {
            // Queue an edit via an offline patch first.
            server.shutdown()
            repository.patchDocument(
                id = "d1", title = "Queued", content = "c", isPublic = false, folderId = null, expectedVersion = 1,
            )
            assertThat(pendingDao.snapshot()).hasSize(1)

            // New server for the push.
            server = MockWebServer().also { it.start() }
            val retrofit = Retrofit.Builder()
                .baseUrl(server.url("/"))
                .client(OkHttpClient.Builder().build())
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .build()
            api = retrofit.create(DocumentsApi::class.java)
            repository = DefaultDocumentsRepository(
                api, documentDao, folderDao, pendingDao, metaDao, json, dispatchers,
            )
            server.enqueue(
                MockResponse().setResponseCode(200)
                    .setBody("""{ "folders": [], "documents": [], "lastSyncAt": "2026-07-31T00:00:00.000Z" }"""),
            )

            val result = repository.pushPendingOps()

            assertThat(result).isInstanceOf(ApiResult.Success::class.java)
            val recorded = server.takeRequest()
            assertThat(recorded.method).isEqualTo("POST")
            assertThat(recorded.path).isEqualTo("/api/documents/sync")
            val body = recorded.body.readUtf8()
            assertThat(body).contains("\"id\":\"d1\"")
            assertThat(body).contains("\"op\":\"update\"")
            assertThat(body).contains("\"title\":\"Queued\"")
            // Cleared after a successful push.
            assertThat(pendingDao.snapshot()).isEmpty()
        }

    @Test
    fun `pushPendingOps is a no-op with nothing queued`() = runTest(testDispatcher) {
        val result = repository.pushPendingOps()
        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        assertThat(server.requestCount).isEqualTo(0)
    }
}
