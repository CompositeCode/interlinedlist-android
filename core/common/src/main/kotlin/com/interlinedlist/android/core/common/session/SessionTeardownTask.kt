package com.interlinedlist.android.core.common.session

/**
 * A unit of work that must run while the session is still usable, immediately
 * before it is torn down by sign-out or account deletion.
 *
 * Declared here alongside [SessionTokenProvider] for the same reason: `:feature:auth`
 * owns the single sign-out path and needs to run contributed teardown steps without
 * depending on the feature modules that contribute them (which would invert the module
 * graph). Implementations are contributed with Dagger's `@IntoSet`, so adding one is a
 * one-line `@Binds @IntoSet` in the owning feature module — no change here or in
 * `:feature:auth`.
 *
 * Contract for implementations:
 * - the bearer token is still persisted, so authenticated calls are allowed;
 * - be idempotent — teardown may run for a session that was already partly cleaned up;
 * - failures are swallowed by the caller; a broken step must never strand a user
 *   signed in.
 */
interface SessionTeardownTask {

    /** Runs while the session's bearer token is still valid. */
    suspend fun onSessionEnding()
}
