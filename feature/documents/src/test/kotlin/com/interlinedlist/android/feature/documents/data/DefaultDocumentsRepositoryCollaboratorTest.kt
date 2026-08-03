package com.interlinedlist.android.feature.documents.data

import com.google.common.truth.Truth.assertThat
import com.interlinedlist.android.core.common.dispatcher.DispatcherProvider
import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.feature.documents.data.remote.DocumentsApi
import com.interlinedlist.android.feature.documents.domain.CollaboratorRole
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

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultDocumentsRepositoryCollaboratorTest {

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
        repository = DefaultDocumentsRepository(
            api, FakeDocumentDao(), FakeFolderDao(), FakePendingOpDao(), FakeSyncMetaDao(), json, dispatchers,
        )
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun `getCollaborators parses the collaborators envelope with nested users`() =
        runTest(testDispatcher) {
            server.enqueue(
                MockResponse().setResponseCode(200).setBody(
                    """
                    {
                      "collaborators": [
                        { "id": "c1", "userId": "u1", "role": "editor",
                          "user": { "id": "u1", "username": "ada", "displayName": "Ada" } },
                        { "id": "c2", "userId": "u2", "role": "viewer", "username": "bob" }
                      ],
                      "pagination": { "total": 2 }
                    }
                    """.trimIndent(),
                ),
            )

            val result = repository.getCollaborators("D1")

            assertThat(result).isInstanceOf(ApiResult.Success::class.java)
            val list = (result as ApiResult.Success).data
            assertThat(list.map { it.userId }).containsExactly("u1", "u2").inOrder()
            assertThat(list[0].role).isEqualTo(CollaboratorRole.EDITOR)
            assertThat(list[0].label).isEqualTo("Ada")
            assertThat(list[1].role).isEqualTo(CollaboratorRole.VIEWER)
            assertThat(server.takeRequest().path).isEqualTo("/api/documents/D1/collaborators")
        }

    @Test
    fun `searchCollaboratorUsers passes the query and maps candidates`() = runTest(testDispatcher) {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "users": [ { "id": "u9", "username": "grace", "displayName": "Grace H", "email": "g@x.io" } ],
                  "total": 1
                }
                """.trimIndent(),
            ),
        )

        val result = repository.searchCollaboratorUsers("D1", "grace")

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        assertThat((result as ApiResult.Success).data.single().username).isEqualTo("grace")
        val recorded = server.takeRequest()
        assertThat(recorded.path).contains("/api/documents/D1/collaborators/users")
        assertThat(recorded.path).contains("search=grace")
        assertThat(recorded.path).contains("excludeCollaborators=true")
    }

    @Test
    fun `inviteCollaborator posts userId and role`() = runTest(testDispatcher) {
        server.enqueue(
            MockResponse().setResponseCode(201).setBody(
                """{ "collaborator": { "id": "c3", "userId": "u3", "role": "admin" } }""",
            ),
        )

        val result = repository.inviteCollaborator("D1", "u3", CollaboratorRole.ADMIN)

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        assertThat((result as ApiResult.Success).data.role).isEqualTo(CollaboratorRole.ADMIN)
        val recorded = server.takeRequest()
        assertThat(recorded.method).isEqualTo("POST")
        assertThat(recorded.path).isEqualTo("/api/documents/D1/collaborators")
        val body = recorded.body.readUtf8()
        assertThat(body).contains("\"userId\":\"u3\"")
        assertThat(body).contains("\"role\":\"admin\"")
    }

    @Test
    fun `updateCollaboratorRole PUTs the new role for the user`() = runTest(testDispatcher) {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))

        val result = repository.updateCollaboratorRole("D1", "u3", CollaboratorRole.EDITOR)

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        val recorded = server.takeRequest()
        assertThat(recorded.method).isEqualTo("PUT")
        assertThat(recorded.path).isEqualTo("/api/documents/D1/collaborators/u3")
        assertThat(recorded.body.readUtf8()).contains("\"role\":\"editor\"")
    }

    @Test
    fun `removeCollaborator DELETEs the user`() = runTest(testDispatcher) {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))

        val result = repository.removeCollaborator("D1", "u3")

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        val recorded = server.takeRequest()
        assertThat(recorded.method).isEqualTo("DELETE")
        assertThat(recorded.path).isEqualTo("/api/documents/D1/collaborators/u3")
    }

    @Test
    fun `sendPresence posts a heartbeat and maps participants`() = runTest(testDispatcher) {
        server.enqueue(
            MockResponse().setResponseCode(201).setBody(
                """
                {
                  "presences": [
                    { "userId": "u1", "user": { "id": "u1", "username": "ada", "displayName": "Ada" } },
                    { "userId": "u2", "username": "bob" }
                  ]
                }
                """.trimIndent(),
            ),
        )

        val result = repository.sendPresence("D1")

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        assertThat((result as ApiResult.Success).data.map { it.label }).containsExactly("Ada", "bob")
        val recorded = server.takeRequest()
        assertThat(recorded.method).isEqualTo("POST")
        assertThat(recorded.path).isEqualTo("/api/documents/D1/presence")
    }

    @Test
    fun `leavePresence DELETEs presence`() = runTest(testDispatcher) {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))

        val result = repository.leavePresence("D1")

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        val recorded = server.takeRequest()
        assertThat(recorded.method).isEqualTo("DELETE")
        assertThat(recorded.path).isEqualTo("/api/documents/D1/presence")
    }
}
