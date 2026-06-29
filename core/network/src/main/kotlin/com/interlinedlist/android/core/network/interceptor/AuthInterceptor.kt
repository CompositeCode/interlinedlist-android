package com.interlinedlist.android.core.network.interceptor

import com.interlinedlist.android.core.common.session.SessionTokenProvider
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

/**
 * Adds `Authorization: Bearer <token>` to every request when a token is
 * available. Unauthenticated endpoints (e.g. sync-token itself) work fine with
 * a null token — the header is simply omitted.
 */
class AuthInterceptor @Inject constructor(
    private val tokenProvider: SessionTokenProvider,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val token = tokenProvider.currentToken()
        val request = if (token.isNullOrBlank()) {
            chain.request()
        } else {
            chain.request().newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
        }
        return chain.proceed(request)
    }
}
