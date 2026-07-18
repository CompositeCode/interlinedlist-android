package com.interlinedlist.android.feature.profile.data

import com.google.common.truth.Truth.assertThat
import com.interlinedlist.android.core.common.dispatcher.DispatcherProvider
import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.core.common.result.AppError
import com.interlinedlist.android.core.model.CustomerStatus
import com.interlinedlist.android.feature.profile.data.remote.ProfileApi
import com.interlinedlist.android.feature.profile.domain.FollowStatus
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
class DefaultProfileRepositoryTest {

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
    fun `refreshCurrentUser parses the wrapped user and caches it as current`() = runTest(testDispatcher) {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "user": {
                    "id": "u1",
                    "username": "adron",
                    "displayName": "Adron Hall",
                    "avatar": "https://cdn/av.png",
                    "bio": "Building things.",
                    "customerStatus": "subscriber:annual"
                  }
                }
                """.trimIndent(),
            ),
        )

        val result = repository.refreshCurrentUser()

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        val user = (result as ApiResult.Success).data
        assertThat(user.username).isEqualTo("adron")
        assertThat(user.avatarUrl).isEqualTo("https://cdn/av.png")
        assertThat(user.customerStatus).isEqualTo(CustomerStatus.SUBSCRIBER_ANNUAL)
        assertThat(user.isCurrentUser).isTrue()

        val recorded = server.takeRequest()
        assertThat(recorded.method).isEqualTo("GET")
        assertThat(recorded.path).isEqualTo("/api/user")

        val cached = repository.observeCurrentUser().first()
        assertThat(cached?.username).isEqualTo("adron")
    }

    @Test
    fun `refreshCurrentUser maps a 401 to Unauthorized`() = runTest(testDispatcher) {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{ "error": "Session expired." }"""))

        val result = repository.refreshCurrentUser()

        assertThat(result).isInstanceOf(ApiResult.Failure::class.java)
        assertThat((result as ApiResult.Failure).error).isInstanceOf(AppError.Unauthorized::class.java)
    }

    @Test
    fun `refreshUser fetches another user by username and caches without the current flag`() = runTest(testDispatcher) {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{ "user": { "id": "u2", "username": "ada", "displayName": "Ada Lovelace" } }""",
            ),
        )

        val result = repository.refreshUser("ada")

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        val user = (result as ApiResult.Success).data
        assertThat(user.username).isEqualTo("ada")
        assertThat(user.isCurrentUser).isFalse()

        val recorded = server.takeRequest()
        assertThat(recorded.path).isEqualTo("/api/users/ada")

        assertThat(repository.observeUser("ada").first()?.displayName).isEqualTo("Ada Lovelace")
        // A viewed user must not become the observed "current" user.
        assertThat(repository.observeCurrentUser().first()).isNull()
    }

    @Test
    fun `refreshUser maps a 404 to NotFound`() = runTest(testDispatcher) {
        server.enqueue(MockResponse().setResponseCode(404).setBody("""{ "error": "No such user" }"""))

        val result = repository.refreshUser("ghost")

        assertThat(result).isInstanceOf(ApiResult.Failure::class.java)
        assertThat((result as ApiResult.Failure).error).isInstanceOf(AppError.NotFound::class.java)
    }

    @Test
    fun `updateProfile PATCHes the fields and updates the cache`() = runTest(testDispatcher) {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{ "user": { "id": "u1", "username": "adron", "displayName": "New Name", "bio": "New bio" } }""",
            ),
        )

        val result = repository.updateProfile(displayName = "New Name", bio = "New bio")

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        assertThat((result as ApiResult.Success).data.displayName).isEqualTo("New Name")

        val recorded = server.takeRequest()
        assertThat(recorded.method).isEqualTo("PATCH")
        assertThat(recorded.path).isEqualTo("/api/user/update")
        val body = recorded.body.readUtf8()
        assertThat(body).contains("\"displayName\":\"New Name\"")
        assertThat(body).contains("\"bio\":\"New bio\"")

        assertThat(repository.observeCurrentUser().first()?.displayName).isEqualTo("New Name")
    }

    @Test
    fun `updateProfile re-fetches when the server echoes a thin body`() = runTest(testDispatcher) {
        // PATCH returns nothing useful...
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        // ...so the repo re-fetches the full user.
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{ "user": { "id": "u1", "username": "adron", "displayName": "Fresh", "bio": "b" } }""",
            ),
        )

        val result = repository.updateProfile(displayName = "Fresh", bio = "b")

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        assertThat((result as ApiResult.Success).data.displayName).isEqualTo("Fresh")

        assertThat(server.takeRequest().path).isEqualTo("/api/user/update")
        assertThat(server.takeRequest().path).isEqualTo("/api/user")
    }

    @Test
    fun `setAvatarFromUrl posts the url then refreshes the cached user`() = runTest(testDispatcher) {
        // Avatar endpoint returns just a URL...
        server.enqueue(MockResponse().setResponseCode(201).setBody("""{ "avatarUrl": "https://cdn/new.png" }"""))
        // ...and the repo refreshes the full user.
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{ "user": { "id": "u1", "username": "adron", "avatar": "https://cdn/new.png" } }""",
            ),
        )

        val result = repository.setAvatarFromUrl("https://cdn/new.png")

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        assertThat((result as ApiResult.Success).data.avatarUrl).isEqualTo("https://cdn/new.png")

        val avatarRequest = server.takeRequest()
        assertThat(avatarRequest.method).isEqualTo("POST")
        assertThat(avatarRequest.path).isEqualTo("/api/user/avatar/from-url")
        assertThat(avatarRequest.body.readUtf8()).contains("\"url\":\"https://cdn/new.png\"")

        assertThat(server.takeRequest().path).isEqualTo("/api/user")
    }

    @Test
    fun `uploadAvatar posts multipart then refreshes the cached user`() = runTest(testDispatcher) {
        server.enqueue(MockResponse().setResponseCode(201).setBody("""{ "avatarUrl": "https://cdn/up.png" }"""))
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{ "user": { "id": "u1", "username": "adron", "avatar": "https://cdn/up.png" } }""",
            ),
        )

        val result = repository.uploadAvatar(
            bytes = byteArrayOf(1, 2, 3, 4),
            fileName = "avatar.png",
            mimeType = "image/png",
        )

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        assertThat((result as ApiResult.Success).data.avatarUrl).isEqualTo("https://cdn/up.png")

        val uploadRequest = server.takeRequest()
        assertThat(uploadRequest.method).isEqualTo("POST")
        assertThat(uploadRequest.path).isEqualTo("/api/user/avatar/upload")
        assertThat(uploadRequest.getHeader("Content-Type")).contains("multipart/form-data")
    }

    @Test
    fun `searchUsers passes the query and limit and maps results`() = runTest(testDispatcher) {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{ "users": [ { "id": "1", "username": "ada", "displayName": "Ada" }, { "id": "2", "username": "adron" } ] }""",
            ),
        )

        val result = repository.searchUsers("ad")

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        val users = (result as ApiResult.Success).data
        assertThat(users.map { it.username }).containsExactly("ada", "adron").inOrder()

        val recorded = server.takeRequest()
        assertThat(recorded.path).isEqualTo("/api/users/search?q=ad&limit=20")
    }

    @Test
    fun `searchUsers reads the generic data envelope too`() = runTest(testDispatcher) {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{ "data": [ { "id": "1", "username": "ada" } ] }""",
            ),
        )

        val result = repository.searchUsers("ada")

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        assertThat((result as ApiResult.Success).data.single().username).isEqualTo("ada")
    }

    // --- Following ---

    @Test
    fun `followUser posts to the follow endpoint`() = runTest(testDispatcher) {
        server.enqueue(MockResponse().setResponseCode(201))

        val result = repository.followUser("u2")

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        val recorded = server.takeRequest()
        assertThat(recorded.method).isEqualTo("POST")
        assertThat(recorded.path).isEqualTo("/api/follow/u2")
    }

    @Test
    fun `unfollowUser deletes on the follow endpoint`() = runTest(testDispatcher) {
        server.enqueue(MockResponse().setResponseCode(200))

        val result = repository.unfollowUser("u2")

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        val recorded = server.takeRequest()
        assertThat(recorded.method).isEqualTo("DELETE")
        assertThat(recorded.path).isEqualTo("/api/follow/u2")
    }

    @Test
    fun `getFollowStatus maps an explicit following status`() = runTest(testDispatcher) {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{ "status": "following" }"""))

        val result = repository.getFollowStatus("u2")

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        assertThat((result as ApiResult.Success).data).isEqualTo(FollowStatus.FOLLOWING)
        assertThat(server.takeRequest().path).isEqualTo("/api/follow/u2/status")
    }

    @Test
    fun `getFollowStatus reads boolean flags into a requested status`() = runTest(testDispatcher) {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{ "isFollowing": false, "requested": true }"""))

        val result = repository.getFollowStatus("u2")

        assertThat((result as ApiResult.Success).data).isEqualTo(FollowStatus.REQUESTED)
    }

    @Test
    fun `getFollowStatus falls back to not-following when nothing is set`() = runTest(testDispatcher) {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))

        val result = repository.getFollowStatus("u2")

        assertThat((result as ApiResult.Success).data).isEqualTo(FollowStatus.NOT_FOLLOWING)
    }

    @Test
    fun `getFollowCounts maps follower and following tallies`() = runTest(testDispatcher) {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{ "followers": 12, "following": 34 }"""))

        val result = repository.getFollowCounts("u2")

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        val counts = (result as ApiResult.Success).data
        assertThat(counts.followers).isEqualTo(12)
        assertThat(counts.following).isEqualTo(34)
        assertThat(server.takeRequest().path).isEqualTo("/api/follow/u2/counts")
    }

    @Test
    fun `getFollowCounts reads the count-suffixed aliases`() = runTest(testDispatcher) {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody("""{ "followersCount": 5, "followingCount": 8 }"""),
        )

        val counts = (repository.getFollowCounts("u2") as ApiResult.Success).data

        assertThat(counts.followers).isEqualTo(5)
        assertThat(counts.following).isEqualTo(8)
    }

    @Test
    fun `getFollowers resolves the username via a cached id then lists followers`() = runTest(testDispatcher) {
        // Seed the cache so no username-lookup round-trip is needed.
        server.enqueue(
            MockResponse().setResponseCode(200).setBody("""{ "user": { "id": "u2", "username": "ada" } }"""),
        )
        repository.refreshUser("ada")
        server.takeRequest() // consume the /api/users/ada request

        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{ "users": [ { "id": "f1", "username": "bob", "displayName": "Bob" } ] }""",
            ),
        )

        val result = repository.getFollowers("ada")

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        assertThat((result as ApiResult.Success).data.single().username).isEqualTo("bob")
        // Cached id means the followers request is the only one left — no re-lookup.
        val recorded = server.takeRequest()
        assertThat(recorded.path).startsWith("/api/follow/u2/followers")
    }

    @Test
    fun `getFollowers resolves the id via a username lookup when uncached`() = runTest(testDispatcher) {
        // First request: resolve username -> id.
        server.enqueue(
            MockResponse().setResponseCode(200).setBody("""{ "user": { "id": "u9", "username": "ada" } }"""),
        )
        // Second request: the followers list keyed on the resolved id.
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{ "followers": [ { "id": "f2", "username": "cara" } ] }""",
            ),
        )

        val result = repository.getFollowers("ada")

        assertThat((result as ApiResult.Success).data.single().username).isEqualTo("cara")
        assertThat(server.takeRequest().path).isEqualTo("/api/users/ada")
        assertThat(server.takeRequest().path).startsWith("/api/follow/u9/followers")
    }

    @Test
    fun `getFollowing lists the following users`() = runTest(testDispatcher) {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody("""{ "user": { "id": "u2", "username": "ada" } }"""),
        )
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{ "following": [ { "id": "g1", "username": "dan" } ] }""",
            ),
        )

        val result = repository.getFollowing("ada")

        assertThat((result as ApiResult.Success).data.single().username).isEqualTo("dan")
        server.takeRequest()
        assertThat(server.takeRequest().path).startsWith("/api/follow/u2/following")
    }

    @Test
    fun `getFollowRequests maps nested and inlined requesters`() = runTest(testDispatcher) {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "requests": [
                    { "user": { "id": "r1", "username": "eve", "displayName": "Eve" } },
                    { "id": "r2", "username": "frank" }
                  ]
                }
                """.trimIndent(),
            ),
        )

        val result = repository.getFollowRequests()

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        val users = (result as ApiResult.Success).data
        assertThat(users.map { it.username }).containsExactly("eve", "frank").inOrder()
        assertThat(server.takeRequest().path).isEqualTo("/api/follow/requests")
    }

    @Test
    fun `approveFollowRequest posts to the approve endpoint`() = runTest(testDispatcher) {
        server.enqueue(MockResponse().setResponseCode(201))

        val result = repository.approveFollowRequest("r1")

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        val recorded = server.takeRequest()
        assertThat(recorded.method).isEqualTo("POST")
        assertThat(recorded.path).isEqualTo("/api/follow/r1/approve")
    }

    @Test
    fun `rejectFollowRequest posts to the reject endpoint`() = runTest(testDispatcher) {
        server.enqueue(MockResponse().setResponseCode(201))

        val result = repository.rejectFollowRequest("r1")

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        val recorded = server.takeRequest()
        assertThat(recorded.method).isEqualTo("POST")
        assertThat(recorded.path).isEqualTo("/api/follow/r1/reject")
    }

    @Test
    fun `removeFollower deletes on the remove endpoint`() = runTest(testDispatcher) {
        server.enqueue(MockResponse().setResponseCode(200))

        val result = repository.removeFollower("f1")

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        val recorded = server.takeRequest()
        assertThat(recorded.method).isEqualTo("DELETE")
        assertThat(recorded.path).isEqualTo("/api/follow/f1/remove")
    }

    @Test
    fun `followUser maps a 404 to NotFound`() = runTest(testDispatcher) {
        server.enqueue(MockResponse().setResponseCode(404).setBody("""{ "error": "No such user" }"""))

        val result = repository.followUser("ghost")

        assertThat(result).isInstanceOf(ApiResult.Failure::class.java)
        assertThat((result as ApiResult.Failure).error).isInstanceOf(AppError.NotFound::class.java)
    }
}
