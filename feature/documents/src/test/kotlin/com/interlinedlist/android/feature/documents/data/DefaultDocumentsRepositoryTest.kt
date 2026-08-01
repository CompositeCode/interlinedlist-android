package com.interlinedlist.android.feature.documents.data

import com.google.common.truth.Truth.assertThat
import com.interlinedlist.android.core.common.dispatcher.DispatcherProvider
import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.core.common.result.AppError
import com.interlinedlist.android.feature.documents.data.remote.DocumentsApi
import com.interlinedlist.android.feature.documents.domain.FolderNode
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
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

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultDocumentsRepositoryTest {

    private lateinit var server: MockWebServer
    private lateinit var api: DocumentsApi
    private lateinit var documentDao: FakeDocumentDao
    private lateinit var folderDao: FakeFolderDao
    private lateinit var repository: DefaultDocumentsRepository

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
        api = retrofit.create(DocumentsApi::class.java)
        documentDao = FakeDocumentDao()
        folderDao = FakeFolderDao()
        repository = DefaultDocumentsRepository(
            api, documentDao, folderDao, FakePendingOpDao(), FakeSyncMetaDao(), json, dispatchers,
        )
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun `refreshTree caches the nested folders with embedded and unfiled documents`() =
        runTest(testDispatcher) {
            // GET /api/documents/folders (nested tree with embedded docs).
            server.enqueue(
                MockResponse().setResponseCode(200).setBody(
                    """
                    {
                      "folders": [
                        { "id": "f1", "name": "Work", "parentId": null,
                          "documents": [ { "id": "d1", "title": "Report", "content": "body" } ] },
                        { "id": "f2", "name": "Reports", "parentId": "f1", "documents": [] }
                      ]
                    }
                    """.trimIndent(),
                ),
            )
            // GET /api/documents (unfiled root docs).
            server.enqueue(
                MockResponse().setResponseCode(200).setBody(
                    """{ "documents": [ { "id": "r1", "title": "Loose note" } ] }""",
                ),
            )

            val result = repository.refreshTree()

            assertThat(result).isInstanceOf(ApiResult.Success::class.java)
            assertThat(folderDao.snapshot().map { it.name }).containsExactly("Work", "Reports")

            // Root contents: top-level folder "Work" and the unfiled doc "r1".
            val rootContents = repository.observeFolderContents(null).first()
            assertThat(rootContents.subfolders.map { it.id }).containsExactly("f1")
            assertThat(rootContents.documents.map { it.id }).containsExactly("r1")

            // Folder "f1" contents: subfolder "f2" and the embedded doc "d1".
            val f1Contents = repository.observeFolderContents("f1").first()
            assertThat(f1Contents.subfolders.map { it.id }).containsExactly("f2")
            assertThat(f1Contents.documents.map { it.id }).containsExactly("d1")
        }

    @Test
    fun `refreshTree maps a 403 subscription error to SubscriptionRequired`() =
        runTest(testDispatcher) {
            server.enqueue(
                MockResponse().setResponseCode(403)
                    .setBody("""{ "error": "This feature requires an active subscription." }"""),
            )

            val result = repository.refreshTree()

            assertThat(result).isInstanceOf(ApiResult.Failure::class.java)
            assertThat((result as ApiResult.Failure).error)
                .isInstanceOf(AppError.SubscriptionRequired::class.java)
        }

    @Test
    fun `createFolder posts name and parentId and caches the folder`() = runTest(testDispatcher) {
        server.enqueue(
            MockResponse().setResponseCode(201)
                .setBody("""{ "folder": { "id": "nf", "name": "Archive", "parentId": "f1" } }"""),
        )

        val result = repository.createFolder("Archive", parentId = "f1")

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        val recorded = server.takeRequest()
        assertThat(recorded.method).isEqualTo("POST")
        assertThat(recorded.path).isEqualTo("/api/documents/folders")
        val body = recorded.body.readUtf8()
        assertThat(body).contains("\"name\":\"Archive\"")
        assertThat(body).contains("\"parentId\":\"f1\"")
        assertThat(folderDao.getFolder("nf")).isNotNull()
    }

    @Test
    fun `createFolder treats the synthetic root id as no parent`() = runTest(testDispatcher) {
        server.enqueue(
            MockResponse().setResponseCode(201).setBody("""{ "folder": { "id": "nf", "name": "Top" } }"""),
        )

        repository.createFolder("Top", parentId = FolderNode.ROOT_ID)

        val body = server.takeRequest().body.readUtf8()
        assertThat(body).doesNotContain(FolderNode.ROOT_ID)
    }

    @Test
    fun `renameFolder issues a PUT with the new name and updates the cache`() = runTest(testDispatcher) {
        // Seed a cached folder via create.
        server.enqueue(MockResponse().setResponseCode(201).setBody("""{ "folder": { "id": "f1", "name": "Work" } }"""))
        repository.createFolder("Work", parentId = null)
        server.takeRequest()

        server.enqueue(MockResponse().setResponseCode(200).setBody("""{ "folder": { "id": "f1", "name": "Job" } }"""))

        val result = repository.renameFolder("f1", "Job")

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        val recorded = server.takeRequest()
        assertThat(recorded.method).isEqualTo("PUT")
        assertThat(recorded.path).isEqualTo("/api/documents/folders/f1")
        assertThat(recorded.body.readUtf8()).contains("\"name\":\"Job\"")
        assertThat(folderDao.getFolder("f1")?.name).isEqualTo("Job")
    }

    @Test
    fun `deleteFolder issues a DELETE and prunes the folder subtree from the cache`() =
        runTest(testDispatcher) {
            // Build a small tree: f1 -> f2, each with a document, plus an unrelated folder f3.
            server.enqueue(
                MockResponse().setResponseCode(200).setBody(
                    """
                    {
                      "folders": [
                        { "id": "f1", "name": "Work", "parentId": null,
                          "documents": [ { "id": "d1", "title": "A" } ] },
                        { "id": "f2", "name": "Reports", "parentId": "f1",
                          "documents": [ { "id": "d2", "title": "B" } ] },
                        { "id": "f3", "name": "Other", "parentId": null, "documents": [] }
                      ]
                    }
                    """.trimIndent(),
                ),
            )
            server.enqueue(MockResponse().setResponseCode(200).setBody("""{ "documents": [] }"""))
            repository.refreshTree()
            server.takeRequest(); server.takeRequest()

            server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
            val result = repository.deleteFolder("f1")

            assertThat(result).isInstanceOf(ApiResult.Success::class.java)
            val recorded = server.takeRequest()
            assertThat(recorded.method).isEqualTo("DELETE")
            assertThat(recorded.path).isEqualTo("/api/documents/folders/f1")

            // f1 and its descendant f2 are gone; f3 remains.
            assertThat(folderDao.snapshot().map { it.id }).containsExactly("f3")
            // Documents in the deleted subtree are removed.
            assertThat(documentDao.getDocument("d1")).isNull()
            assertThat(documentDao.getDocument("d2")).isNull()
        }

    @Test
    fun `moveDocument issues a PUT with the target folderId and patches the cache`() =
        runTest(testDispatcher) {
            // Seed a cached document via create (lands at root).
            server.enqueue(MockResponse().setResponseCode(201).setBody("""{ "id": "d1", "title": "T", "content": "c" }"""))
            repository.createDocument("T", "c", isPublic = false, folderId = null)
            server.takeRequest()
            assertThat(documentDao.getDocument("d1")?.folderId).isNull()

            server.enqueue(MockResponse().setResponseCode(200).setBody("""{ "id": "d1", "title": "T", "folderId": "f9" }"""))

            val result = repository.moveDocument("d1", folderId = "f9")

            assertThat(result).isInstanceOf(ApiResult.Success::class.java)
            val recorded = server.takeRequest()
            assertThat(recorded.method).isEqualTo("PUT")
            assertThat(recorded.path).isEqualTo("/api/documents/d1")
            assertThat(recorded.body.readUtf8()).contains("\"folderId\":\"f9\"")
            assertThat(documentDao.getDocument("d1")?.folderId).isEqualTo("f9")
        }

    @Test
    fun `createDocument posts the body and caches the created document`() = runTest(testDispatcher) {
        server.enqueue(
            MockResponse().setResponseCode(201).setBody(
                """{ "document": { "id": "new1", "title": "Fresh", "content": "body", "isPublic": false } }""",
            ),
        )

        val result = repository.createDocument("Fresh", "body", isPublic = false, folderId = null)

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        assertThat((result as ApiResult.Success).data.id).isEqualTo("new1")

        val recorded = server.takeRequest()
        assertThat(recorded.method).isEqualTo("POST")
        assertThat(recorded.path).isEqualTo("/api/documents")
        assertThat(recorded.body.readUtf8()).contains("\"title\":\"Fresh\"")
        assertThat(documentDao.getDocument("new1")).isNotNull()
    }

    @Test
    fun `getDocument detail parses a bare body and caches it`() = runTest(testDispatcher) {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{ "id": "d9", "title": "Detail", "content": "# Heading\n- item" }""",
            ),
        )

        val result = repository.refreshDocument("d9")

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        val doc = (result as ApiResult.Success).data
        assertThat(doc.title).isEqualTo("Detail")
        assertThat(doc.content).contains("# Heading")
        assertThat(documentDao.getDocument("d9")?.content).contains("# Heading")
    }

    @Test
    fun `deleteDocument issues a DELETE and removes the cached row`() = runTest(testDispatcher) {
        server.enqueue(MockResponse().setResponseCode(201).setBody("""{ "id": "gone", "title": "T", "content": "c" }"""))
        repository.createDocument("T", "c", isPublic = false, folderId = null)
        server.takeRequest()
        assertThat(documentDao.getDocument("gone")).isNotNull()

        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        val result = repository.deleteDocument("gone")

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        val recorded = server.takeRequest()
        assertThat(recorded.method).isEqualTo("DELETE")
        assertThat(documentDao.getDocument("gone")).isNull()
    }

    @Test
    fun `searchDocuments passes the query and maps results`() = runTest(testDispatcher) {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{ "data": [ { "id": "s1", "title": "Match", "content": "found" } ] }""",
            ),
        )

        val result = repository.searchDocuments("found")

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        assertThat((result as ApiResult.Success).data.single().title).isEqualTo("Match")
        assertThat(server.takeRequest().path).isEqualTo("/api/documents/search?q=found")
    }

    @Test
    fun `uploadImage posts multipart to the images endpoint`() = runTest(testDispatcher) {
        server.enqueue(MockResponse().setResponseCode(201).setBody("""{ "url": "https://cdn/x.png" }"""))

        val result = repository.uploadImage(
            documentId = "d1",
            fileName = "shot.png",
            mimeType = "image/png",
            bytes = byteArrayOf(1, 2, 3),
        )

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        val recorded = server.takeRequest()
        assertThat(recorded.method).isEqualTo("POST")
        assertThat(recorded.path).isEqualTo("/api/documents/d1/images/upload")
        assertThat(recorded.getHeader("Content-Type")).contains("multipart/form-data")
    }

    @Test
    fun `getTemplates maps template documents`() = runTest(testDispatcher) {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{ "data": [ { "id": "t1", "title": "Recipe", "content": "Ingredients" } ] }""",
            ),
        )

        val result = repository.getTemplates()

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        assertThat((result as ApiResult.Success).data.single().title).isEqualTo("Recipe")
    }
}
