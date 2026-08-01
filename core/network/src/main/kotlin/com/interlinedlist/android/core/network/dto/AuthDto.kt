package com.interlinedlist.android.core.network.dto

import kotlinx.serialization.Serializable

/** Request body for `POST /api/auth/sync-token`. */
@Serializable
data class SyncTokenRequest(
    val email: String,
    val password: String,
    /** Human-readable device name shown in the user's Active Sessions list. */
    val deviceLabel: String? = null,
)

/** Response from `POST /api/auth/sync-token`; `token` is the `il_tok_...` value. */
@Serializable
data class SyncTokenResponse(
    val token: String,
    val message: String? = null,
)
