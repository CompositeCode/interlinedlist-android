package com.interlinedlist.android.feature.lists.domain

/**
 * A directed link between two lists, optionally labelled. Titles are resolved for
 * display when the API supplies them, falling back to the list ids so a row always
 * renders something meaningful.
 */
data class ListConnection(
    val id: String,
    val fromListId: String,
    val toListId: String,
    val label: String?,
    val fromListTitle: String,
    val toListTitle: String,
)
