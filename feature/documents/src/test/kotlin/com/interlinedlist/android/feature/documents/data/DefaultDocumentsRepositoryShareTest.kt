package com.interlinedlist.android.feature.documents.data

import com.google.common.truth.Truth.assertThat
import com.interlinedlist.android.core.common.dispatcher.DispatcherProvider
import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.core.common.result.AppError
import com.interlinedlist.android.feature.documents.data.remote.DocumentsApi
import com.interlinedlist.android.feature.documents.domain.ShareRole
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
 * MockWebServer coverage for the document sharing endpoints: list/create/revoke a
 * share link, resolve a token to a read-only preview, and claim access.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DefaultDocumentsRepositoryShareTest {

    private lateinit var server: MockWebServer
    private lateinit var api: DocumentsApi
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
        repository = DefaultDocumentsRepository(api, FakeDocumentDao(), FakeFolderDao(), json, dispatchers)
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun `getShareLinks parses the shareLinks envelope and maps roles`() = runTest(testDispatcher) {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "shareLinks": [
                    { "id": "s1", "documentId": "D1", "token": "tok-view", "role": "view", "createdAt": "2026-01-01" },
                    { "id": "s2", "documentId": "D1", "token": "tok-edit", "role": "edit", "revokedAt": null }
                  ]
                }
                """.trimIndent(),
            ),
        )

        val result = repository.getShareLinks("D1")

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        val links = (result as ApiResult.Success).data
        assertThat(links.map { it.token }).containsExactly("tok-view", "tok-edit").inOrder()
        assertThat(links[0].role).isEqualTo(ShareRole.VIEW)
        assertThat(links[1].role).isEqualTo(ShareRole.EDIT)
        assertThat(links[0].url()).isEqualTo("https://interlinedlist.com/documents/shared/tok-view")

        val request = server.takeRequest()
        assertThat(request.method).isEqualTo("GET")
        assertThat(request.path).isEqualTo("/api/documents/D1/share-links")
    }

    @Test
    fun `createShareLink posts the chosen role and returns the created link`() = runTest(testDispatcher) {
        server.enqueue(
            MockResponse().setResponseCode(201).setBody(
                """{ "shareLink": { "id": "s9", "token": "new-tok", "role": "admin" } }""",
            ),
        )

        val result = repository.createShareLink("D1", ShareRole.ADMIN)

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        val link = (result as ApiResult.Success).data
        assertThat(link.token).isEqualTo("new-tok")
        assertThat(link.role).isEqualTo(ShareRole.ADMIN)

        val request = server.takeRequest()
        assertThat(request.method).isEqualTo("POST")
        assertThat(request.path).isEqualTo("/api/documents/D1/share-links")
        assertThat(request.body.readUtf8()).contains("\"role\":\"admin\"")
    }

    @Test
    fun `createShareLink tolerates a bare wrapped link body`() = runTest(testDispatcher) {
        server.enqueue(
            MockResponse().setResponseCode(201)
                .setBody("""{ "id": "s7", "token": "bare-tok", "role": "edit" }"""),
        )

        val result = repository.createShareLink("D1", ShareRole.EDIT)

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        assertThat((result as ApiResult.Success).data.token).isEqualTo("bare-tok")
    }

    @Test
    fun `revokeShareLink deletes by token`() = runTest(testDispatcher) {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))

        val result = repository.revokeShareLink("D1", "tok-gone")

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        val request = server.takeRequest()
        assertThat(request.method).isEqualTo("DELETE")
        assertThat(request.path).isEqualTo("/api/documents/D1/share-links/tok-gone")
    }

    @Test
    fun `resolveSharedDocument maps preview metadata and role`() = runTest(testDispatcher) {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "id": "D5", "title": "Public Notes", "content": "# Hello",
                  "role": "edit",
                  "user": { "id": "u2", "username": "grace", "displayName": "Grace H" }
                }
                """.trimIndent(),
            ),
        )

        val result = repository.resolveSharedDocument("shared-tok")

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        val res = (result as ApiResult.Success).data
        assertThat(res.token).isEqualTo("shared-tok")
        assertThat(res.documentId).isEqualTo("D5")
        assertThat(res.title).isEqualTo("Public Notes")
        assertThat(res.content).isEqualTo("# Hello")
        assertThat(res.ownerName).isEqualTo("Grace H")
        assertThat(res.role).isEqualTo(ShareRole.EDIT)
        assertThat(res.canClaim).isTrue()

        assertThat(server.takeRequest().path).isEqualTo("/api/documents/shared/shared-tok")
    }

    @Test
    fun `resolveSharedDocument maps a 404 to NotFound`() = runTest(testDispatcher) {
        server.enqueue(
            MockResponse().setResponseCode(404)
                .setBody("""{ "error": "Share link not found, expired, or revoked", "code": "not_found" }"""),
        )

        val result = repository.resolveSharedDocument("dead-tok")

        assertThat(result).isInstanceOf(ApiResult.Failure::class.java)
        assertThat((result as ApiResult.Failure).error).isInstanceOf(AppError.NotFound::class.java)
    }

    @Test
    fun `claimSharedDocument posts to the shared token`() = runTest(testDispatcher) {
        server.enqueue(MockResponse().setResponseCode(201).setBody("{}"))

        val result = repository.claimSharedDocument("claim-tok")

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        val request = server.takeRequest()
        assertThat(request.method).isEqualTo("POST")
        assertThat(request.path).isEqualTo("/api/documents/shared/claim-tok")
    }
}
