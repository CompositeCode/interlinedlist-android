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

    private fun conversationsBody(
        username: String,
        unreadCount: Int,
        body: String,
        createdAt: String,
        nextCursor: String? = null,
    ) = """
        {
          "items": [
            {
              "pairKey": "me:$username",
              "otherUser": {"id":"$username-id","username":"$username","displayName":"$username","avatar":null},
              "lastMessage": {"id":"dm-$username","senderId":"$username-id","recipientId":"me",
                              "body":"$body","createdAt":"$createdAt","readAt":null},
              "unreadCount": $unreadCount
            }
          ],
          "nextCursor": ${if (nextCursor == null) "null" else "\"$nextCursor\""}
        }
    """.trimIndent()

    @Test
    fun `refreshInbox reads the conversations endpoint and returns the next cursor`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                conversationsBody(
                    username = "adron", unreadCount = 2, body = "hi there",
                    createdAt = "2026-07-31T10:00:00Z", nextCursor = "cursor-2",
                ),
            ),
        )

        val result = repo().refreshInbox(cursor = null)

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        assertThat((result as ApiResult.Success).data).isEqualTo("cursor-2")
        assertThat(server.takeRequest().path).startsWith("/api/dm/conversations")

        val conversations = repo().observeConversations().first()
        assertThat(conversations).hasSize(1)
        with(conversations.first()) {
            assertThat(username).isEqualTo("adron")
            assertThat(pairKey).isEqualTo("me:adron")
            assertThat(lastMessageBody).isEqualTo("hi there")
            assertThat(unreadCount).isEqualTo(2)
            assertThat(hasUnread).isTrue()
        }
    }

    @Test
    fun `refreshInbox emits one row per conversation`() = runTest {
        // Two conversations; the same pairKey never appears twice in a response,
        // so the inbox must render exactly one row for each.
        server.enqueue(
            MockResponse().setBody(
                """
                {
                  "items": [
                    {"pairKey":"me:adron",
                     "otherUser":{"id":"u2","username":"adron","displayName":"Adron","avatar":null},
                     "lastMessage":{"id":"dm_2","senderId":"u2","recipientId":"me","body":"newest",
                                    "createdAt":"2026-07-31T12:00:00Z","readAt":null},
                     "unreadCount":1},
                    {"pairKey":"me:blake",
                     "otherUser":{"id":"u3","username":"blake","displayName":"Blake","avatar":null},
                     "lastMessage":{"id":"dm_1","senderId":"me","recipientId":"u3","body":"older",
                                    "createdAt":"2026-07-31T09:00:00Z","readAt":null},
                     "unreadCount":0}
                  ],
                  "nextCursor": null
                }
                """.trimIndent(),
            ),
        )

        assertThat(repo().refreshInbox()).isInstanceOf(ApiResult.Success::class.java)

        val conversations = repo().observeConversations().first()
        // Newest activity first, one row per conversation.
        assertThat(conversations.map { it.username }).containsExactly("adron", "blake").inOrder()
        assertThat(conversations.first().lastMessageBody).isEqualTo("newest")
        assertThat(conversations.last().hasUnread).isFalse()
    }

    @Test
    fun `cursor paging forwards the cursor and appends the next page`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                conversationsBody(
                    username = "adron", unreadCount = 1, body = "page one",
                    createdAt = "2026-07-31T12:00:00Z", nextCursor = "page-2",
                ),
            ),
        )
        server.enqueue(
            MockResponse().setBody(
                conversationsBody(
                    username = "blake", unreadCount = 0, body = "page two",
                    createdAt = "2026-07-31T09:00:00Z", nextCursor = null,
                ),
            ),
        )

        val r = repo()
        val first = r.refreshInbox(cursor = null)
        assertThat((first as ApiResult.Success).data).isEqualTo("page-2")
        assertThat(server.takeRequest().path).doesNotContain("cursor=")

        val second = r.refreshInbox(cursor = "page-2")
        assertThat((second as ApiResult.Success).data).isNull()
        assertThat(server.takeRequest().path).contains("cursor=page-2")

        // Paging appends: page one's conversation is still cached.
        val conversations = r.observeConversations().first()
        assertThat(conversations.map { it.username }).containsExactly("adron", "blake").inOrder()
    }

    @Test
    fun `cached unread counts add up to the unread-count endpoint`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """
                {
                  "items": [
                    {"pairKey":"me:adron",
                     "otherUser":{"id":"u2","username":"adron"},
                     "lastMessage":{"id":"dm_2","senderId":"u2","recipientId":"me","body":"a",
                                    "createdAt":"2026-07-31T12:00:00Z","readAt":null},
                     "unreadCount":3},
                    {"pairKey":"me:blake",
                     "otherUser":{"id":"u3","username":"blake"},
                     "lastMessage":{"id":"dm_1","senderId":"u3","recipientId":"me","body":"b",
                                    "createdAt":"2026-07-31T11:00:00Z","readAt":null},
                     "unreadCount":4}
                  ],
                  "nextCursor": null
                }
                """.trimIndent(),
            ),
        )
        server.enqueue(MockResponse().setBody("""{"count":7}"""))

        val r = repo()
        r.refreshInbox()
        val endpointCount = (r.unreadCount() as ApiResult.Success).data

        val summed = r.observeConversations().first().sumOf { it.unreadCount }
        assertThat(summed).isEqualTo(endpointCount)
        assertThat(r.observeConversations().first().count { it.hasUnread }).isEqualTo(2)
    }

    @Test
    fun `a conversation without a participant username is skipped`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"items":[{"pairKey":"me:ghost","unreadCount":1}],"nextCursor":null}""",
            ),
        )

        assertThat(repo().refreshInbox()).isInstanceOf(ApiResult.Success::class.java)
        assertThat(repo().observeConversations().first()).isEmpty()
    }

    @Test
    fun `opening a thread clears that conversation's unread count`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                conversationsBody(
                    username = "adron", unreadCount = 2, body = "hi",
                    createdAt = "2026-07-31T10:00:00Z",
                ),
            ),
        )
        server.enqueue(
            MockResponse().setBody("""{"items":[],"olderCursor":null,"isMutual":true,"isBlocked":false}"""),
        )

        val r = repo()
        r.refreshInbox()
        assertThat(r.observeConversations().first().first().unreadCount).isEqualTo(2)

        r.refreshThread("adron")

        assertThat(r.observeConversations().first().first().unreadCount).isEqualTo(0)
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
    fun `send unwraps the documented message envelope`() = runTest {
        // POST /api/dm returns `{ "message": { ... } }` (help centre + OpenAPI).
        server.enqueue(
            MockResponse().setResponseCode(201).setBody(
                """
                {"message":{"id":"dm_001","pairKey":"u1:u2","senderId":"me","recipientId":"other",
                 "body":"hello","imageUrls":[],"createdAt":"2026-07-31T11:00:00Z","readAt":null}}
                """.trimIndent(),
            ),
        )

        val r = repo()
        val result = r.send(username = "adron", body = "hello")

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        assertThat((result as ApiResult.Success).data.id).isEqualTo("dm_001")
        val thread = r.observeThread("adron").first()
        assertThat(thread.map { it.id }).containsExactly("dm_001")
        assertThat(thread.first().pending).isFalse()
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
