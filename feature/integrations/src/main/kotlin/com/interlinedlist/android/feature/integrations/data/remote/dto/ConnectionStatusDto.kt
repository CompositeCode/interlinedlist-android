package com.interlinedlist.android.feature.integrations.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The `/api/auth/<provider>/status` payload. The OpenAPI spec only documents a
 * "Successful response", so this DTO accepts the field names those endpoints use
 * in practice and treats them all as optional. `connected` is the canonical flag;
 * a present `handle`/`username` is treated as a fallback signal of connection.
 */
@Serializable
data class ConnectionStatusDto(
    val connected: Boolean? = null,
    val handle: String? = null,
    val username: String? = null,
    @SerialName("displayName") val displayName: String? = null,
) {
    /** True when the flag says so, or when a handle/username is present. */
    val isConnected: Boolean
        get() = connected ?: (!handle.isNullOrBlank() || !username.isNullOrBlank())

    /** Best available human handle for display, if any. */
    val bestHandle: String?
        get() = handle?.takeIf { it.isNotBlank() }
            ?: username?.takeIf { it.isNotBlank() }
            ?: displayName?.takeIf { it.isNotBlank() }
}
