package com.interlinedlist.android.core.network.dto

import kotlinx.serialization.Serializable

/** Standard API error envelope: `{ "error": "..." }`. */
@Serializable
data class ErrorDto(
    val error: String? = null,
)
