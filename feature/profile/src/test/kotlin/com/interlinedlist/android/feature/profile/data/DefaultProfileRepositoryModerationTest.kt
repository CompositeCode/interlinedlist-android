package com.interlinedlist.android.feature.profile.data

import com.google.common.truth.Truth.assertThat
import com.interlinedlist.android.core.common.dispatcher.DispatcherProvider
import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.core.common.result.AppError
import com.interlinedlist.android.feature.profile.data.remote.ProfileApi
import com.interlinedlist.android.feature.profile.domain.ReportReason
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

/** MockWebServer coverage for the Milestone D moderation endpoints. */
@OptIn(ExperimentalCoroutinesApi::class)
class DefaultProfileRepositoryModerationTest {

    private lateinit var server: MockWebServer
    private lateinit var api: ProfileApi
    private lateinit var dao: FakeProfileDao
    private lateinit var repository: DefaultProfileRepository

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
        api = retrofit.create(ProfileApi::class.java)
        dao = FakeProfileDao()
        repository = DefaultProfileRepository(api, dao, json, dispatchers)
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun `getBlockedUsers parses the blockedUsers envelope`() = runTest(testDispatcher) {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "blockedUsers": [
                    { "id": "u2", "username": "ada", "displayName": "Ada Lovelace", "avatar": "https://cdn/ada.png" },
                    { "id": "u3", "username": "grace" }
                  ],
                  "pagination": { "total": 2, "limit": 20, "offset": 0, "hasMore": false }
                }
                """.trimIndent(),
            ),
        )

        val result = repository.getBlockedUsers()

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        val users = (result as ApiResult.Success).data
        assertThat(users.map { it.username }).containsExactly("ada", "grace").inOrder()
        assertThat(users.first().avatarUrl).isEqualTo("https://cdn/ada.png")

        val recorded = server.takeRequest()
        assertThat(recorded.method).isEqualTo("GET")
        assertThat(recorded.path).startsWith("/api/user/blocks")
    }

    @Test
    fun `getBlockedUsers tolerates a nested user object shape`() = runTest(testDispatcher) {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "blockedUsers": [
                    { "user": { "id": "u2", "username": "ada", "displayName": "Ada" } }
                  ]
                }
                """.trimIndent(),
            ),
        )

        val result = repository.getBlockedUsers()

        val users = (result as ApiResult.Success).data
        assertThat(users).hasSize(1)
        assertThat(users.first().username).isEqualTo("ada")
    }

    @Test
    fun `getBlockedUsers drops entries with no resolvable user`() = runTest(testDispatcher) {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{ "blockedUsers": [ { "displayName": "orphan" }, { "id": "u9", "username": "ok" } ] }""",
            ),
        )

        val users = (repository.getBlockedUsers() as ApiResult.Success).data
        assertThat(users.map { it.username }).containsExactly("ok")
    }

    @Test
    fun `getMutedUsers parses the mutedUsers envelope`() = runTest(testDispatcher) {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{ "mutedUsers": [ { "id": "u5", "username": "noisy" } ], "pagination": { "total": 1 } }""",
            ),
        )

        val users = (repository.getMutedUsers() as ApiResult.Success).data
        assertThat(users.map { it.username }).containsExactly("noisy")
        assertThat(server.takeRequest().path).startsWith("/api/user/mutes")
    }

    @Test
    fun `getModerationStatus combines the block and mute status endpoints`() = runTest(testDispatcher) {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{ "blocked": true }"""))
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{ "muted": false }"""))

        val result = repository.getModerationStatus("ada")

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        val status = (result as ApiResult.Success).data
        assertThat(status.isBlocked).isTrue()
        assertThat(status.isMuted).isFalse()

        assertThat(server.takeRequest().path).isEqualTo("/api/users/ada/block")
        assertThat(server.takeRequest().path).isEqualTo("/api/users/ada/mute")
    }

    @Test
    fun `getModerationStatus fails fast when the block status call fails`() = runTest(testDispatcher) {
        server.enqueue(MockResponse().setResponseCode(404).setBody("""{ "error": "no user" }"""))

        val result = repository.getModerationStatus("ghost")

        assertThat(result).isInstanceOf(ApiResult.Failure::class.java)
        assertThat((result as ApiResult.Failure).error).isInstanceOf(AppError.NotFound::class.java)
    }

    @Test
    fun `blockUser posts to the block endpoint`() = runTest(testDispatcher) {
        server.enqueue(MockResponse().setResponseCode(201))

        val result = repository.blockUser("ada")

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        val recorded = server.takeRequest()
        assertThat(recorded.method).isEqualTo("POST")
        assertThat(recorded.path).isEqualTo("/api/users/ada/block")
    }

    @Test
    fun `unblockUser deletes the block`() = runTest(testDispatcher) {
        server.enqueue(MockResponse().setResponseCode(200))

        repository.unblockUser("ada")

        val recorded = server.takeRequest()
        assertThat(recorded.method).isEqualTo("DELETE")
        assertThat(recorded.path).isEqualTo("/api/users/ada/block")
    }

    @Test
    fun `muteUser posts to the mute endpoint`() = runTest(testDispatcher) {
        server.enqueue(MockResponse().setResponseCode(201))

        repository.muteUser("ada")

        val recorded = server.takeRequest()
        assertThat(recorded.method).isEqualTo("POST")
        assertThat(recorded.path).isEqualTo("/api/users/ada/mute")
    }

    @Test
    fun `unmuteUser deletes the mute`() = runTest(testDispatcher) {
        server.enqueue(MockResponse().setResponseCode(200))

        repository.unmuteUser("ada")

        val recorded = server.takeRequest()
        assertThat(recorded.method).isEqualTo("DELETE")
        assertThat(recorded.path).isEqualTo("/api/users/ada/mute")
    }

    @Test
    fun `reportUser posts the reason and detail body`() = runTest(testDispatcher) {
        server.enqueue(MockResponse().setResponseCode(201))

        val result = repository.reportUser("ada", ReportReason.HARASSMENT, "They keep messaging me.")

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        val recorded = server.takeRequest()
        assertThat(recorded.method).isEqualTo("POST")
        assertThat(recorded.path).isEqualTo("/api/users/ada/report")
        val body = recorded.body.readUtf8()
        assertThat(body).contains("\"reason\":\"harassment\"")
        assertThat(body).contains("\"detail\":\"They keep messaging me.\"")
    }

    @Test
    fun `reportUser omits a blank detail`() = runTest(testDispatcher) {
        server.enqueue(MockResponse().setResponseCode(201))

        repository.reportUser("ada", ReportReason.SPAM, "   ")

        val body = server.takeRequest().body.readUtf8()
        assertThat(body).contains("\"reason\":\"spam\"")
        assertThat(body).doesNotContain("detail")
    }

    @Test
    fun `reportUser maps a 400 to a failure`() = runTest(testDispatcher) {
        server.enqueue(MockResponse().setResponseCode(400).setBody("""{ "error": "bad reason" }"""))

        val result = repository.reportUser("ada", ReportReason.OTHER, null)

        assertThat(result).isInstanceOf(ApiResult.Failure::class.java)
    }
}
