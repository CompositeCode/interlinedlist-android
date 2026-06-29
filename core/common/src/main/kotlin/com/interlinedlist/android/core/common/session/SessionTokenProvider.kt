package com.interlinedlist.android.core.common.session

/**
 * Read-only access to the persisted bearer token (`il_tok_...`).
 *
 * Declared here so that `:core:network` (the auth interceptor) can depend on it
 * without depending on `:core:datastore`, which provides the implementation.
 * Reads are synchronous because the interceptor runs on OkHttp's background
 * dispatcher.
 */
interface SessionTokenProvider {
    fun currentToken(): String?
}
