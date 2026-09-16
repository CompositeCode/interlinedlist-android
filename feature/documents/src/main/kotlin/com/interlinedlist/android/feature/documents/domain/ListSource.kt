package com.interlinedlist.android.feature.documents.domain

/**
 * One of the signed-in user's lists, reduced to what the "Derived From List"
 * Powered Document mode needs: something to show in the picker and the `listId`
 * the AI endpoint resolves server-side.
 *
 * Only the id is sent — `/api/ai/suggest` loads the list's schema and rows itself
 * under the owning user, so the client never ships list content.
 */
data class ListSource(
    val id: String,
    val title: String,
    val description: String? = null,
)
