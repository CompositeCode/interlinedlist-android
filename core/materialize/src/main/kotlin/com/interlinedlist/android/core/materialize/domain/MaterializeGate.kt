package com.interlinedlist.android.core.materialize.domain

import com.interlinedlist.android.core.common.dispatcher.DispatcherProvider
import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.core.model.CustomerStatus
import com.interlinedlist.android.core.network.api.InterlinedListApi
import com.interlinedlist.android.core.network.dto.toDomain
import com.interlinedlist.android.core.network.error.safeApiCall
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/** Whether this account may confirm a conversion that creates something. */
enum class MaterializeAccess {
    /** Not read yet, or unreadable (offline, 401). */
    UNKNOWN,

    /** Positively known to be on the free plan. */
    FREE,

    /** Positively known to have an active subscription. */
    SUBSCRIBER;

    /** True only when we positively know the account cannot create. */
    val isKnownFree: Boolean get() = this == FREE
}

/**
 * The subscriber gate for "Create from…".
 *
 * The menu opens for everyone — the gate is deliberately **not** consulted when
 * drawing an entry point. It is consulted when a conversion is confirmed, and
 * only to stop a free account issuing a write it is certain to be refused;
 * `DefaultMaterializeRepository` turns that into
 * `AppError.SubscriptionRequired`, which the feature modules already render as
 * an upsell through their existing `isSubscriptionGate` handling.
 *
 * It fails **open**: an unreadable status leaves [access] `UNKNOWN` and the
 * request goes to the server, which is the real gate. Blocking on a status we
 * could not read would lock out a paying subscriber whose `/api/user` call
 * happened to fail.
 *
 * Application-scoped so every entry point shares one `/api/user` read.
 */
@Singleton
class MaterializeGate @Inject constructor(
    private val userApi: InterlinedListApi,
    private val json: Json,
    private val dispatchers: DispatcherProvider,
) {

    private val _access = MutableStateFlow(MaterializeAccess.UNKNOWN)

    /** Observable for surfaces that want to pre-badge the menu. Never hides it. */
    val access: StateFlow<MaterializeAccess> = _access.asStateFlow()

    /** Re-reads `customerStatus` from `GET /api/user`. */
    suspend fun refresh(): MaterializeAccess = withContext(dispatchers.io) {
        val resolved = when (val result = safeApiCall(json) { userApi.getCurrentUser().user }) {
            is ApiResult.Success -> result.data.toDomain().customerStatus.toAccess()
            // Unreadable: stay UNKNOWN and let the server decide.
            is ApiResult.Failure -> MaterializeAccess.UNKNOWN
        }
        _access.value = resolved
        resolved
    }

    /** Resolves the status once; a no-op once it is known either way. */
    suspend fun ensureResolved(): MaterializeAccess =
        if (_access.value == MaterializeAccess.UNKNOWN) refresh() else _access.value

    /**
     * Folds a `customerStatus` another surface already loaded into the gate, so
     * a confirm does not re-read `/api/user` the app has just fetched.
     */
    fun record(status: CustomerStatus) {
        _access.value = status.toAccess()
    }

    /**
     * Applies what a refused conversion revealed: the server answered
     * "subscriber only", so subsequent confirms short-circuit to the upsell
     * instead of issuing another write that will be refused.
     */
    fun recordSubscriptionRequired() {
        _access.value = MaterializeAccess.FREE
    }

    private fun CustomerStatus.toAccess(): MaterializeAccess = when {
        isSubscriber -> MaterializeAccess.SUBSCRIBER
        this == CustomerStatus.FREE -> MaterializeAccess.FREE
        // An unrecognised tier is not evidence the account is free.
        else -> MaterializeAccess.UNKNOWN
    }
}
