package com.interlinedlist.android.feature.lists.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * Result of `POST /api/lists/{id}/refresh` (manual re-sync of a GitHub-backed
 * list). The API may report how many rows were added/updated; all fields are
 * optional so any success shape maps cleanly.
 */
@Serializable
data class RefreshResultDto(
    val success: Boolean? = null,
    val message: String? = null,
    val added: Int? = null,
    val updated: Int? = null,
    val removed: Int? = null,
    val itemCount: Int? = null,
    val rowCount: Int? = null,
)
