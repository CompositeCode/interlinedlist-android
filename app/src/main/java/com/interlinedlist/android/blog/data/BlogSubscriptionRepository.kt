package com.interlinedlist.android.blog.data

import com.interlinedlist.android.blog.BlogLink
import com.interlinedlist.android.blog.BlogSubscriptionOutcome
import com.interlinedlist.android.core.common.dispatcher.DispatcherProvider
import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.core.network.api.InterlinedListApi
import com.interlinedlist.android.core.network.error.safeApiCall
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import retrofit2.Response
import javax.inject.Inject

/**
 * The blog email list: subscribe, and complete the confirm/unsubscribe links.
 *
 * There is no cache. Every operation is a one-shot write whose result the server owns,
 * and the app cannot read the list back — `GET /api/blog/subscriptions` does not exist —
 * so there is no local subscription state that could be shown offline without lying
 * about it.
 */
interface BlogSubscriptionRepository {

    /**
     * The signed-in account's email address, used to prefill the form, or null when it
     * cannot be read (signed out, or offline). The form stays usable either way.
     */
    suspend fun accountEmail(): String?

    /**
     * Starts the double opt-in. Success means **the confirmation email was requested**,
     * not that the address is subscribed — the server answers identically for a new,
     * pending, confirmed or rate-limited address, so the only truthful thing to tell
     * the user is to go and check their inbox. Returns the server's own message.
     */
    suspend fun subscribe(email: String): ApiResult<String>

    /** Completes the double opt-in with the token from the confirmation email. */
    suspend fun confirm(token: String): ApiResult<BlogSubscriptionOutcome>

    /** Removes the address behind the token in a blog email's unsubscribe link. */
    suspend fun unsubscribe(token: String): ApiResult<BlogSubscriptionOutcome>
}

class DefaultBlogSubscriptionRepository @Inject constructor(
    private val blogApi: BlogApi,
    private val userApi: InterlinedListApi,
    private val json: Json,
    private val dispatchers: DispatcherProvider,
) : BlogSubscriptionRepository {

    override suspend fun accountEmail(): String? = withContext(dispatchers.io) {
        runCatching { userApi.getCurrentUser().user.email }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    override suspend fun subscribe(email: String): ApiResult<String> = withContext(dispatchers.io) {
        safeApiCall(json) {
            blogApi.subscribe(BlogSubscribeRequest(email.trim())).message.orEmpty()
        }
    }

    override suspend fun confirm(token: String): ApiResult<BlogSubscriptionOutcome> =
        withContext(dispatchers.io) {
            safeApiCall(json) { blogApi.confirmSubscription(token).outcome() }
        }

    override suspend fun unsubscribe(token: String): ApiResult<BlogSubscriptionOutcome> =
        withContext(dispatchers.io) {
            safeApiCall(json) { blogApi.unsubscribe(token).outcome() }
        }

    /**
     * Turns the token endpoints' `307` into an outcome.
     *
     * A non-redirect status is not a success: a `2xx` carries no `subscription`
     * parameter, so there is nothing to report and [BlogSubscriptionOutcome.INVALID] —
     * "try the link again" — is the only honest answer. A `4xx`/`5xx` is rethrown so
     * `safeApiCall` maps it to the usual [com.interlinedlist.android.core.common.result.AppError].
     */
    private fun Response<Unit>.outcome(): BlogSubscriptionOutcome = when {
        code() in 300..399 -> BlogLink.outcomeFor(headers()["Location"])
        isSuccessful -> BlogSubscriptionOutcome.INVALID
        else -> throw HttpException(this)
    }
}
