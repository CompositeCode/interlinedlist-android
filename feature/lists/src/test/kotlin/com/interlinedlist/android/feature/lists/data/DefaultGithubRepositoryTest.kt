package com.interlinedlist.android.feature.lists.data

import com.google.common.truth.Truth.assertThat
import com.interlinedlist.android.core.common.dispatcher.DispatcherProvider
import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.feature.lists.data.remote.GithubApi
import com.interlinedlist.android.feature.lists.ui.github.GithubLinkProblem
import com.interlinedlist.android.feature.lists.ui.github.toGithubLinkProblem
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

/**
 * The GitHub proxy calls the repo picker makes. Both `/api/github/repos` and
 * `/api/github/orgs` return a **bare array** (confirmed against the live API),
 * and `next-issue-number` returns `{ "nextNumber": n }`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DefaultGithubRepositoryTest {

    private lateinit var server: MockWebServer
    private lateinit var repository: DefaultGithubRepository

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
            .create(GithubApi::class.java)
        repository = DefaultGithubRepository(api, json, testDispatchers)
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun `getRepos maps the bare GitHub array and keeps repository visibility`() = runTest(dispatcher) {
        server.enqueue(
            MockResponse().setBody(
                """
                [
                  { "name": "Hello-World", "owner": { "login": "octocat" }, "private": false,
                    "description": "My first repo" },
                  { "full_name": "acme/secret-plans", "private": true }
                ]
                """.trimIndent(),
            ),
        )

        val repos = (repository.getRepos() as ApiResult.Success).data

        assertThat(repos.map { it.fullName })
            .containsExactly("octocat/Hello-World", "acme/secret-plans").inOrder()
        assertThat(repos[0].isPrivate).isFalse()
        // Recovered from `full_name` when `owner`/`name` are absent.
        assertThat(repos[1].owner).isEqualTo("acme")
        assertThat(repos[1].isPrivate).isTrue()
        assertThat(server.takeRequest().path).isEqualTo("/api/github/repos")
    }

    @Test
    fun `getRepos scopes to one organization with the org query parameter`() = runTest(dispatcher) {
        server.enqueue(MockResponse().setBody("[]"))

        repository.getRepos(org = "acme")

        assertThat(server.takeRequest().path).isEqualTo("/api/github/repos?org=acme")
    }

    @Test
    fun `getRepos drops a repository it cannot identify`() = runTest(dispatcher) {
        // No owner and no full_name — the picker's whole output is "owner/repo",
        // and a half-identified one would only be rejected by POST /api/lists.
        server.enqueue(MockResponse().setBody("""[ { "private": false }, { "full_name": "a/b" } ]"""))

        val repos = (repository.getRepos() as ApiResult.Success).data

        assertThat(repos.map { it.fullName }).containsExactly("a/b")
    }

    @Test
    fun `getOrgs maps the organization logins`() = runTest(dispatcher) {
        server.enqueue(
            MockResponse().setBody(
                """[ { "login": "acme", "avatar_url": "https://x/y.png" }, { "login": "" } ]""",
            ),
        )

        val orgs = (repository.getOrgs() as ApiResult.Success).data

        assertThat(orgs.map { it.login }).containsExactly("acme")
        assertThat(orgs.first().avatarUrl).isEqualTo("https://x/y.png")
        assertThat(server.takeRequest().path).isEqualTo("/api/github/orgs")
    }

    @Test
    fun `getNextIssueNumber reads nextNumber for the owner and repo`() = runTest(dispatcher) {
        server.enqueue(MockResponse().setBody("""{ "nextNumber": 11231 }"""))

        val next = (repository.getNextIssueNumber("octocat/Hello-World") as ApiResult.Success).data

        assertThat(next).isEqualTo(11231)
        assertThat(server.takeRequest().path)
            .isEqualTo("/api/github/repos/octocat/Hello-World/next-issue-number")
    }

    @Test
    fun `getNextIssueNumber answers null for a malformed repo without a request`() = runTest(dispatcher) {
        val next = (repository.getNextIssueNumber("nosuchslash") as ApiResult.Success).data

        assertThat(next).isNull()
        assertThat(server.requestCount).isEqualTo(0)
    }

    @Test
    fun `an unlinked GitHub account is reported as something the user can fix`() = runTest(dispatcher) {
        // Every /api/github/… endpoint answers 400 with this body when unlinked.
        server.enqueue(
            MockResponse().setResponseCode(400)
                .setBody("""{ "error": "GitHub account not linked" }"""),
        )

        val failure = repository.getRepos() as ApiResult.Failure

        assertThat(failure.error.toGithubLinkProblem()).isEqualTo(GithubLinkProblem.NOT_LINKED)
    }

    @Test
    fun `a refused GitHub token asks the user to reconnect for Issues`() = runTest(dispatcher) {
        // The proxy forwards GitHub's own 401 (observed: code "github_error").
        server.enqueue(
            MockResponse().setResponseCode(401)
                .setBody("""{ "error": "Unauthorized", "code": "github_error" }"""),
        )

        val failure = repository.getRepos() as ApiResult.Failure

        assertThat(failure.error.toGithubLinkProblem()).isEqualTo(GithubLinkProblem.NEEDS_ISSUES_SCOPE)
    }

    @Test
    fun `an ordinary failure is not mistaken for a linking problem`() = runTest(dispatcher) {
        server.enqueue(MockResponse().setResponseCode(500).setBody("""{ "error": "boom" }"""))

        val failure = repository.getRepos() as ApiResult.Failure

        assertThat(failure.error.toGithubLinkProblem()).isNull()
    }
}
