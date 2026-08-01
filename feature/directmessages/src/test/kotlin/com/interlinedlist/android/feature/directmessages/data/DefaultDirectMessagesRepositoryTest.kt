package com.interlinedlist.android.feature.directmessages.data

import com.google.common.truth.Truth.assertThat
import com.interlinedlist.android.core.common.dispatcher.DispatcherProvider
import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.feature.directmessages.data.remote.DirectMessagesApi
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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
class DefaultDirectMessagesRepositoryTest {

    private lateinit var server: MockWebServer
    private lateinit var api: DirectMessagesApi
    private lateinit var messageDao: FakeMessageDao
    private lateinit var conversationDao: FakeConversationDao

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; coerceInputValues = true }

    private val testDispatchers = object : DispatcherProvider {
        val d: CoroutineDispatcher = UnconfinedTestDispatcher()
        override val io = d
        override val default = d
        override val main = d
    }

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val client = OkHttpClient.Builder().build()
        val retrofit = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
        api = retrofit.create(DirectMessagesApi::class.java)
        messageDao = FakeMessageDao()
        conversationDao = FakeConversationDao()
    }

    @After
    fun tearDown() = server.shutdown()

    private fun repo(currentUserId: String? = "me") = DefaultDirectMessagesRepository(
        api = api,
        messageDao = messageDao,
        conversationDao = conversationDao,
        currentUserIdProvider = { currentUserId },
        json = json,
        dispatchers = testDispatchers,
    )

    @Test
    fun `refreshInbox caches conversations and returns next cursor`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """
                {
                  "items": [
                    {"id":"m1","senderId":"other","recipientId":"me","body":"hi there",
                     "createdAt":"2026-07-31T10:00:00Z","readAt":null,
                     "user":{"id":"other","username":"adron","displayName":"Adron","avatar":null}}
                  ],
                  "nextCursor": "cursor-2"
                }
                """.trimIndent(),
            ),
        )

        val result = repo().refreshInbox(cursor = null)

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        assertThat((result as ApiResult.Success).data).isEqualTo("cursor-2")

        val conversations = repo().observeConversations().first()
        assertThat(conversations).hasSize(1)
        assertThat(conversations.first().username).isEqualTo("adron")
        assertThat(conversations.first().lastMessageBody).isEqualTo("hi there")
        assertThat(conversations.first().hasUnread).isTrue()
    }

    @Test
    fun `refreshInbox forwards the cursor query param`() = runTest {
        server.enqueue(MockResponse().setBody("""{"items":[],"nextCursor":null}"""))

        repo().refreshInbox(cursor = "page-2")

        val recorded = server.takeRequest()
        assertThat(recorded.path).contains("cursor=page-2")
    }

    @Test
    fun `refreshThread caches messages oldest-first`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """
                {
                  "items": [
                    {"id":"m2","senderId":"me","recipientId":"other","body":"second",
                     "createdAt":"2026-07-31T10:05:00Z"},
                    {"id":"m1","senderId":"other","recipientId":"me","body":"first",
                     "createdAt":"2026-07-31T10:00:00Z"}
                  ],
                  "olderCursor": null,
                  "isMutual": true,
                  "isBlocked": false,
                  "otherUser": {"id":"other","username":"adron","displayName":"Adron","avatar":null}
                }
                """.trimIndent(),
            ),
        )

        val result = repo().refreshThread("adron")

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        val thread = repo().observeThread("adron").first()
        assertThat(thread.map { it.id }).containsExactly("m1", "m2").inOrder()
    }

    @Test
    fun `send posts the message and caches the server copy`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """
                {"id":"srv1","senderId":"me","recipientId":"other","body":"hello",
                 "createdAt":"2026-07-31T11:00:00Z"}
                """.trimIndent(),
            ),
        )

        val result = repo().send(username = "adron", body = "hello")

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        val recorded = server.takeRequest()
        assertThat(recorded.method).isEqualTo("POST")
        assertThat(recorded.path).isEqualTo("/api/dm")
        assertThat(recorded.body.readUtf8()).contains("\"body\":\"hello\"")

        val thread = repo().observeThread("adron").first()
        assertThat(thread.map { it.id }).contains("srv1")
        assertThat(thread.first { it.id == "srv1" }.pending).isFalse()
    }

    @Test
    fun `send caches an optimistic message even when the network fails`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500).setBody("""{"error":"boom"}"""))

        val r = repo()
        val result = r.send(username = "adron", body = "will fail")

        assertThat(result).isInstanceOf(ApiResult.Failure::class.java)
        // The optimistic copy stays visible so the UI can show a retry affordance.
        val thread = r.observeThread("adron").first()
        assertThat(thread).hasSize(1)
        assertThat(thread.first().pending).isTrue()
        assertThat(thread.first().body).isEqualTo("will fail")
    }

    @Test
    fun `markRead updates the server and the local read timestamp`() = runTest {
        messageDao.upsert(
            DirectMessage(
                id = "m1", conversationUsername = "adron", senderId = "other",
                recipientId = "me", body = "hi", imageUrls = emptyList(),
                createdAt = "2026-07-31T10:00:00Z", createdAtMillis = 1L,
                readAt = null, pending = false,
            ).let {
                com.interlinedlist.android.feature.directmessages.data.local.DirectMessageEntity(
                    id = it.id, conversationUsername = it.conversationUsername,
                    senderId = it.senderId, recipientId = it.recipientId, body = it.body,
                    imageUrls = it.imageUrls, createdAt = it.createdAt,
                    createdAtMillis = it.createdAtMillis, readAt = it.readAt, trashed = false,
                )
            },
        )
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))

        val result = repo().markRead("m1")

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        val recorded = server.takeRequest()
        assertThat(recorded.path).isEqualTo("/api/dm/m1/read")
        assertThat(messageDao.all.first { it.id == "m1" }.readAt).isNotNull()
    }

    @Test
    fun `trash marks the local message trashed and restore reverses it`() = runTest {
        messageDao.upsert(
            com.interlinedlist.android.feature.directmessages.data.local.DirectMessageEntity(
                id = "m1", conversationUsername = "adron", senderId = "me",
                recipientId = "other", body = "hi", imageUrls = emptyList(),
                createdAt = "2026-07-31T10:00:00Z", createdAtMillis = 1L, readAt = null,
                trashed = false,
            ),
        )
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}")) // trash
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}")) // restore

        val r = repo()

        assertThat(r.trash("m1")).isInstanceOf(ApiResult.Success::class.java)
        assertThat(server.takeRequest().path).isEqualTo("/api/dm/m1/trash")
        assertThat(messageDao.all.first { it.id == "m1" }.trashed).isTrue()

        assertThat(r.restore("m1", "adron")).isInstanceOf(ApiResult.Success::class.java)
        assertThat(server.takeRequest().path).isEqualTo("/api/dm/m1/restore")
        assertThat(messageDao.all.first { it.id == "m1" }.trashed).isFalse()
    }

    @Test
    fun `pollThreadUpdates merges only genuinely new messages`() = runTest {
        // Seed one cached message.
        messageDao.upsert(
            com.interlinedlist.android.feature.directmessages.data.local.DirectMessageEntity(
                id = "m1", conversationUsername = "adron", senderId = "other",
                recipientId = "me", body = "first", imageUrls = emptyList(),
                createdAt = "2026-07-31T10:00:00Z", createdAtMillis =
                    parseIsoMillis("2026-07-31T10:00:00Z"), readAt = null, trashed = false,
            ),
        )
        server.enqueue(
            MockResponse().setBody(
                """
                {"items":[
                  {"id":"m1","senderId":"other","recipientId":"me","body":"first",
                   "createdAt":"2026-07-31T10:00:00Z"},
                  {"id":"m2","senderId":"other","recipientId":"me","body":"second",
                   "createdAt":"2026-07-31T10:10:00Z"}
                ]}
                """.trimIndent(),
            ),
        )

        val r = repo()
        val merged = r.pollThreadUpdates("adron")

        assertThat(merged).isInstanceOf(ApiResult.Success::class.java)
        assertThat((merged as ApiResult.Success).data).isEqualTo(1)

        val recorded = server.takeRequest()
        assertThat(recorded.path).contains("/api/dm/thread/adron/updates")
        assertThat(recorded.path).contains("after=")

        val thread = r.observeThread("adron").first()
        assertThat(thread.map { it.id }).containsExactly("m1", "m2").inOrder()
    }

    @Test
    fun `unreadCount reads the count field`() = runTest {
        server.enqueue(MockResponse().setBody("""{"count":7}"""))

        val result = repo().unreadCount()

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        assertThat((result as ApiResult.Success).data).isEqualTo(7)
    }

    @Test
    fun `recipients maps the wire list to domain`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """
                {"recipients":[
                  {"id":"u1","username":"adron","displayName":"Adron Hall","avatar":"http://a"}
                ]}
                """.trimIndent(),
            ),
        )

        val result = repo().recipients()

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        val list = (result as ApiResult.Success).data
        assertThat(list).hasSize(1)
        assertThat(list.first().username).isEqualTo("adron")
        assertThat(list.first().avatarUrl).isEqualTo("http://a")
    }

    companion object {
        // Silence unused import warnings on Dispatchers in some Kotlin versions.
        private val unused = Dispatchers.Default
    }
}
