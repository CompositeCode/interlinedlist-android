package com.interlinedlist.android.blog.data

import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

/** Body of `POST /api/blog/subscribe`. The endpoint takes nothing else. */
@Serializable
data class BlogSubscribeRequest(val email: String)

/**
 * `POST /api/blog/subscribe` always answers `200 { ok, message }` for any syntactically
 * valid address — new, already pending, already confirmed or rate-limited alike — so
 * the response deliberately reveals nothing about subscription state. Both fields are
 * defaulted because the envelope is documented, not schema'd.
 */
@Serializable
data class BlogSubscribeResponse(
    val ok: Boolean = false,
    val message: String? = null,
)

/**
 * The blog's email-list endpoints. All three are public (`x-auth-type: none`) — the
 * bearer token the shared client attaches is simply ignored.
 *
 * The two token endpoints answer **`307`** with a `Location` of
 * `/blog?subscription=confirmed|unsubscribed|invalid`, which is the only signal of
 * whether the token was any good. They are therefore declared as `Response<Unit>` and
 * called through a client with redirects disabled — following the redirect would fetch
 * the blog's HTML and throw the answer away.
 *
 * `GET` (not `POST`) is used for unsubscribe on purpose: `POST /api/blog/unsubscribe`
 * is the RFC-8058 one-click target for mail clients and returns a bare `200` to
 * *anything* — no token, no body, malformed JSON — so it cannot tell the user whether
 * the unsubscribe actually happened. The `GET` link is the one a human is meant to
 * follow, and it reports the outcome.
 */
interface BlogApi {

    @POST("api/blog/subscribe")
    suspend fun subscribe(@Body body: BlogSubscribeRequest): BlogSubscribeResponse

    @GET("api/blog/subscribe/confirm")
    suspend fun confirmSubscription(@Query("token") token: String): Response<Unit>

    @GET("api/blog/unsubscribe")
    suspend fun unsubscribe(@Query("token") token: String): Response<Unit>
}
