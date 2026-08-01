package com.interlinedlist.android.feature.integrations.data

import com.google.common.truth.Truth.assertThat
import com.interlinedlist.android.core.common.dispatcher.DispatcherProvider
import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.feature.integrations.data.remote.IntegrationsApi
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.coroutines.CoroutineDispatcher
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
import java.io.File
import java.nio.file.Files

/**
 * GitHub repository behaviour against a real Retrofit/OkHttp stack over
 * MockWebServer. Covers parsing repos/issues/labels/assignees, the create-issue
 * and add-comment request shapes, and the graceful "GitHub not linked" state the
 * live API returns (HTTP 400 with { "error": "GitHub account not linked" }).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DefaultIntegrationsRepositoryGitHubTest {

    private lateinit var server: MockWebServer
    private lateinit var repository: DefaultIntegrationsRepository

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    private val dispatcher = StandardTestDispatcher()

    private val testDispatchers = object : DispatcherProvider {
        override val io: CoroutineDispatcher get() = dispatcher
        override val default: CoroutineDispatcher get() = dispatcher
        override val main: CoroutineDispatcher get() = dispatcher
    }

    @Before
    fun setUp() {
        server = MockWebServer().also { it.start() }
        val api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(IntegrationsApi::class.java)
        val tempDir = Files.createTempDirectory("gh-test").toFile()
        val fileStore = object : ExportFileStore {
            override fun exportsDir(): File = File(tempDir, "exports")
        }
        repository = DefaultIntegrationsRepository(api, fileStore, json, testDispatchers)
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun `getGitHubRepos parses nested owner and full_name into domain repos`() = runTest(dispatcher) {
        server.enqueue(
            MockResponse().setBody(
                """
                [
                  { "name": "hello", "owner": { "login": "adron" }, "private": false, "description": "hi" },
                  { "full_name": "octocat/spoon-knife", "private": true }
                ]
                """.trimIndent(),
            ),
        )

        val result = repository.getGitHubRepos()

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        val repos = (result as ApiResult.Success).data
        assertThat(repos).hasSize(2)
        assertThat(repos[0].owner).isEqualTo("adron")
        assertThat(repos[0].name).isEqualTo("hello")
        assertThat(repos[0].fullName).isEqualTo("adron/hello")
        assertThat(repos[0].isPrivate).isFalse()
        // full_name-only repo is recovered.
        assertThat(repos[1].owner).isEqualTo("octocat")
        assertThat(repos[1].name).isEqualTo("spoon-knife")
        assertThat(repos[1].isPrivate).isTrue()
        assertThat(server.takeRequest().path).isEqualTo("/api/github/repos")
    }

    @Test
    fun `getGitHubRepos maps the not-linked 400 to a failure the UI recognises`() = runTest(dispatcher) {
        server.enqueue(
            MockResponse().setResponseCode(400)
                .setBody("""{ "error": "GitHub account not linked" }"""),
        )

        val result = repository.getGitHubRepos()

        assertThat(result).isInstanceOf(ApiResult.Failure::class.java)
        assertThat((result as ApiResult.Failure).error.isGitHubNotLinked()).isTrue()
    }

    @Test
    fun `getGitHubRepos surfaces an empty list when GitHub is connected but has no repos`() =
        runTest(dispatcher) {
            server.enqueue(MockResponse().setBody("[]"))

            val result = repository.getGitHubRepos()

            assertThat(result).isInstanceOf(ApiResult.Success::class.java)
            assertThat((result as ApiResult.Success).data).isEmpty()
        }

    @Test
    fun `getGitHubIssues sends repo and state params and flattens labels and assignees`() =
        runTest(dispatcher) {
            server.enqueue(
                MockResponse().setBody(
                    """
                    [
                      {
                        "number": 7,
                        "title": "Fix bug",
                        "body": "It broke",
                        "state": "open",
                        "labels": [ { "name": "bug", "color": "d73a4a" }, { "name": "p1" } ],
                        "assignees": [ { "login": "adron" } ]
                      },
                      { "title": "no number, dropped" }
                    ]
                    """.trimIndent(),
                ),
            )

            val result = repository.getGitHubIssues(repo = "adron/hello", state = "open")

            assertThat(result).isInstanceOf(ApiResult.Success::class.java)
            val issues = (result as ApiResult.Success).data
            // The numberless entry is dropped.
            assertThat(issues).hasSize(1)
            assertThat(issues[0].number).isEqualTo(7)
            assertThat(issues[0].title).isEqualTo("Fix bug")
            assertThat(issues[0].labels).containsExactly("bug", "p1").inOrder()
            assertThat(issues[0].assignees).containsExactly("adron")
            assertThat(issues[0].isOpen).isTrue()

            val request = server.takeRequest()
            assertThat(request.requestUrl!!.queryParameter("repo")).isEqualTo("adron/hello")
            assertThat(request.requestUrl!!.queryParameter("state")).isEqualTo("open")
        }

    @Test
    fun `createGitHubIssue posts repo title body and comma-joined labels and assignees`() =
        runTest(dispatcher) {
            server.enqueue(
                MockResponse().setResponseCode(201)
                    .setBody("""{ "number": 12, "title": "New issue", "state": "open" }"""),
            )

            val result = repository.createGitHubIssue(
                repo = "adron/hello",
                title = "New issue",
                body = "Please fix",
                labels = listOf("bug", "p1"),
                assignees = listOf("adron"),
            )

            assertThat(result).isInstanceOf(ApiResult.Success::class.java)
            assertThat((result as ApiResult.Success).data.number).isEqualTo(12)

            val request = server.takeRequest()
            assertThat(request.method).isEqualTo("POST")
            assertThat(request.path).isEqualTo("/api/github/issues")
            val sent = json.parseToJsonElementMap(request.body.readUtf8())
            assertThat(sent["repo"]).isEqualTo("adron/hello")
            assertThat(sent["title"]).isEqualTo("New issue")
            assertThat(sent["body"]).isEqualTo("Please fix")
            assertThat(sent["labels"]).isEqualTo("bug,p1")
            assertThat(sent["assignees"]).isEqualTo("adron")
        }

    @Test
    fun `createGitHubIssue omits empty labels and assignees from the body`() = runTest(dispatcher) {
        server.enqueue(
            MockResponse().setResponseCode(201)
                .setBody("""{ "number": 1, "title": "Bare" }"""),
        )

        repository.createGitHubIssue(repo = "a/b", title = "Bare", body = null)

        val sent = json.parseToJsonElementMap(server.takeRequest().body.readUtf8())
        assertThat(sent.containsKey("labels")).isFalse()
        assertThat(sent.containsKey("assignees")).isFalse()
        assertThat(sent.containsKey("body")).isFalse()
    }

    @Test
    fun `createGitHubIssue falls back to a synthetic issue when the response lacks a number`() =
        runTest(dispatcher) {
            // Some proxies wrap the issue; if we can't read a number, keep the UI optimistic.
            server.enqueue(MockResponse().setResponseCode(201).setBody("""{ "ok": true }"""))

            val result = repository.createGitHubIssue(repo = "a/b", title = "Ghost", body = "b")

            assertThat(result).isInstanceOf(ApiResult.Success::class.java)
            val issue = (result as ApiResult.Success).data
            assertThat(issue.title).isEqualTo("Ghost")
            assertThat(issue.body).isEqualTo("b")
        }

    @Test
    fun `addGitHubIssueComment posts the body to the comment path`() = runTest(dispatcher) {
        server.enqueue(MockResponse().setResponseCode(201).setBody("""{ "id": 1 }"""))

        val result = repository.addGitHubIssueComment(
            owner = "adron",
            repo = "hello",
            number = 7,
            body = "Thanks!",
        )

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        val request = server.takeRequest()
        assertThat(request.method).isEqualTo("POST")
        assertThat(request.path).isEqualTo("/api/github/issues/adron/hello/7/comments")
        assertThat(json.parseToJsonElementMap(request.body.readUtf8())["body"]).isEqualTo("Thanks!")
    }

    @Test
    fun `getGitHubLabels and getGitHubAssignees parse into domain models`() = runTest(dispatcher) {
        server.enqueue(
            MockResponse().setBody(
                """[ { "name": "bug", "color": "d73a4a" }, { "name": "docs" }, { "color": "nope" } ]""",
            ),
        )
        server.enqueue(
            MockResponse().setBody(
                """[ { "login": "adron", "avatar_url": "http://x/y.png" }, { "avatar_url": "z" } ]""",
            ),
        )

        val labels = repository.getGitHubLabels("adron", "hello")
        val assignees = repository.getGitHubAssignees("adron", "hello")

        assertThat((labels as ApiResult.Success).data.map { it.name })
            .containsExactly("bug", "docs").inOrder()
        // The label with no name is dropped.
        assertThat((assignees as ApiResult.Success).data.map { it.login }).containsExactly("adron")

        assertThat(server.takeRequest().path).isEqualTo("/api/github/repos/adron/hello/labels")
        assertThat(server.takeRequest().path).isEqualTo("/api/github/repos/adron/hello/assignees")
    }
}

/** Parses a flat JSON object of string values for assertion convenience. */
private fun Json.parseToJsonElementMap(body: String): Map<String, String?> {
    val obj = parseToJsonElement(body)
    return (obj as kotlinx.serialization.json.JsonObject).mapValues { (_, v) ->
        (v as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull
    }
}

private val kotlinx.serialization.json.JsonPrimitive.contentOrNull: String?
    get() = if (this is kotlinx.serialization.json.JsonNull) null else content
