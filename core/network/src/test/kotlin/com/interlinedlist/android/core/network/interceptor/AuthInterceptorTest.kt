package com.interlinedlist.android.core.network.interceptor

import com.google.common.truth.Truth.assertThat
import com.interlinedlist.android.core.common.session.SessionTokenProvider
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test

class AuthInterceptorTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun clientWithToken(token: String?): OkHttpClient {
        val provider = object : SessionTokenProvider {
            override fun currentToken(): String? = token
        }
        return OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(provider))
            .build()
    }

    @Test
    fun `adds bearer header when token present`() {
        server.enqueue(MockResponse().setBody("{}"))

        clientWithToken("il_tok_abc")
            .newCall(Request.Builder().url(server.url("/")).build())
            .execute()
            .close()

        val recorded = server.takeRequest()
        assertThat(recorded.getHeader("Authorization")).isEqualTo("Bearer il_tok_abc")
    }

    @Test
    fun `omits header when token missing`() {
        server.enqueue(MockResponse().setBody("{}"))

        clientWithToken(null)
            .newCall(Request.Builder().url(server.url("/")).build())
            .execute()
            .close()

        val recorded = server.takeRequest()
        assertThat(recorded.getHeader("Authorization")).isNull()
    }
}
